package com.cosmos.unreddit.data.remote.api.reddit

import com.cosmos.unreddit.data.remote.api.reddit.auth.AuthSession
import com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTokenManager
import com.cosmos.unreddit.di.CoroutinesScopesModule.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditAuthInterceptor
    @Inject
    constructor(
        private val tokenManager: RedditTokenManager,
        @ApplicationScope
        private val scope: CoroutineScope,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val session = runBlocking { tokenManager.getOrAcquireSession() }

            val request = buildAuthenticatedRequest(chain, session)
            val response = chain.proceed(request)

            if (response.code == 401) {
                response.close()
                val newSession =
                    runBlocking {
                        tokenManager.forceRefresh()
                        tokenManager.getOrAcquireSession()
                    }
                return chain.proceed(buildAuthenticatedRequest(chain, newSession))
            }

            if (response.code == 429) {
                scope.launch {
                    try {
                        tokenManager.forceRefresh()
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            } else {
                val remaining = response.header("x-ratelimit-remaining")?.toFloatOrNull()
                if (remaining != null && remaining < 10) {
                    scope.launch {
                        try {
                            tokenManager.forceRefresh()
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            return response
        }

        private fun buildAuthenticatedRequest(
            chain: Interceptor.Chain,
            session: AuthSession,
        ): Request {
            val request = chain.request()
            if (request.url.host !in REDDIT_HOSTS) return request

            val headers = mutableListOf<Pair<String, String>>()
            headers.add("Authorization" to "Bearer ${session.accessToken}")
            session.spoofHeaders.forEach { (key, value) -> headers.add(key to value) }
            session.loid?.let { headers.add("x-reddit-loid" to it) }
            session.session?.let { headers.add("x-reddit-session" to it) }
            headers.shuffle()

            val requestBuilder = request.newBuilder()
            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }
            return requestBuilder.build()
        }

        companion object {
            private val REDDIT_HOSTS = setOf("reddit.com", "www.reddit.com", "oauth.reddit.com")
        }
    }
