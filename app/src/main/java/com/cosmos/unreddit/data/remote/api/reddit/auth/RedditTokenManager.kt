package com.cosmos.unreddit.data.remote.api.reddit.auth

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditTokenManager
    @Inject
    constructor() {
        private var primaryProvider: () -> AuthBackend = { createPrimaryBackend() }
        private var fallbackProvider: () -> AuthBackend = { createFallbackBackend() }

        @Volatile
        private var _backend: AuthBackend? = null
        private var backend: AuthBackend
            get() = _backend ?: primaryProvider().also { _backend = it }
            set(value) {
                _backend = value
            }

        private val currentSession = AtomicReference<AuthSession?>(null)
        private val sessionVersion = AtomicLong(0)
        private val refreshMutex = Mutex()

        @Volatile
        private var backendState = BackendState.MOBILE_SPOOF_ACTIVE
        private var consecutiveFailures = 0

        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var refreshJob: Job? = null

        @VisibleForTesting
        internal fun setTestDependencies(
            initialBackend: AuthBackend,
            fallbackBackend: AuthBackend,
        ) {
            _backend = initialBackend
            primaryProvider = { initialBackend }
            fallbackProvider = { fallbackBackend }
            backendState = BackendState.MOBILE_SPOOF_ACTIVE
            consecutiveFailures = 0
            currentSession.set(null)
            sessionVersion.set(0)
            refreshJob?.cancel()
            refreshJob = null
        }

        suspend fun getOrAcquireSession(): AuthSession =
            refreshMutex.withLock {
                if (backendState == BackendState.EXHAUSTED) {
                    backend = primaryProvider()
                    backendState = BackendState.MOBILE_SPOOF_ACTIVE
                    consecutiveFailures = 0
                    currentSession.set(null)
                }
                currentSession.get()?.let { session ->
                    if (session.expiresAt > System.currentTimeMillis()) return@withLock session
                }
                acquireNewSession()
            }

        suspend fun forceRefresh() {
            val versionBefore = sessionVersion.get()
            refreshMutex.withLock {
                if (sessionVersion.get() > versionBefore) return
                if (backendState == BackendState.EXHAUSTED) {
                    throw IOException("Reddit authentication exhausted, awaiting next getOrAcquireSession() to reset")
                }
                acquireNewSession()
            }
        }

        private suspend fun acquireNewSession(): AuthSession {
            while (true) {
                val device = RedditDevice()
                when (val result = backend.fetchToken(device)) {
                    is TokenResult.Success -> {
                        consecutiveFailures = 0
                        val newVersion = sessionVersion.incrementAndGet()
                        val session =
                            AuthSession(
                                version = newVersion,
                                accessToken = result.accessToken,
                                expiresAt = System.currentTimeMillis() + result.expiresInSeconds * 1000,
                                backendType =
                                    when (backendState) {
                                        BackendState.MOBILE_SPOOF_ACTIVE -> BackendType.MOBILE_SPOOF
                                        else -> BackendType.GENERIC_WEB
                                    },
                                spoofHeaders = result.spoofHeaders,
                                loid = result.loid,
                                session = result.session,
                            )
                        currentSession.set(session)
                        scheduleProactiveRefresh(result.expiresInSeconds)
                        return session
                    }

                    is TokenResult.Failure -> {
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_FAILURES_PER_BACKEND) {
                            when (backendState) {
                                BackendState.MOBILE_SPOOF_ACTIVE -> {
                                    Log.w(TAG, "MobileSpoof failed $MAX_FAILURES_PER_BACKEND times, switching to GenericWeb")
                                    backend = fallbackProvider()
                                    backendState = BackendState.GENERIC_WEB_ACTIVE
                                    consecutiveFailures = 0
                                    continue
                                }

                                BackendState.GENERIC_WEB_ACTIVE -> {
                                    backendState = BackendState.EXHAUSTED
                                    throw IOException("Reddit authentication exhausted: both backends failed")
                                }

                                BackendState.EXHAUSTED -> {
                                    throw IOException("Reddit authentication exhausted")
                                }
                            }
                        } else {
                            throw IOException("Reddit authentication failed: ${result.message}")
                        }
                    }
                }
            }
        }

        private fun scheduleProactiveRefresh(expiresInSeconds: Long) {
            refreshJob?.cancel()
            refreshJob =
                refreshScope.launch {
                    val delaySeconds = maxOf(expiresInSeconds - 120, 60)
                    delay(delaySeconds * 1000)
                    try {
                        forceRefresh()
                    } catch (e: Exception) {
                        Log.w(TAG, "Proactive refresh failed", e)
                        currentSession.get()?.let { session ->
                            if (session.expiresAt > System.currentTimeMillis()) {
                                delay(60_000)
                                try {
                                    forceRefresh()
                                } catch (e2: Exception) {
                                    Log.w(TAG, "Proactive refresh retry failed", e2)
                                }
                            }
                        }
                    }
                }
        }

        private fun createPrimaryBackend(): AuthBackend {
            val client = createDefaultHttpClient()
            val moshi = Moshi.Builder().build()
            return MobileSpoofBackend(client, moshi)
        }

        private fun createFallbackBackend(): AuthBackend {
            val client = createDefaultHttpClient()
            val moshi = Moshi.Builder().build()
            return GenericWebBackend(client, moshi)
        }

        private enum class BackendState {
            MOBILE_SPOOF_ACTIVE,
            GENERIC_WEB_ACTIVE,
            EXHAUSTED,
        }

        companion object {
            private const val TAG = "RedditTokenManager"
            private const val MAX_FAILURES_PER_BACKEND = 5

            private fun createDefaultHttpClient(): OkHttpClient =
                OkHttpClient
                    .Builder()
                    .sslSocketFactory(RedditTlsFactory.socketFactory(), RedditTlsFactory.trustManager())
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()
        }
    }
