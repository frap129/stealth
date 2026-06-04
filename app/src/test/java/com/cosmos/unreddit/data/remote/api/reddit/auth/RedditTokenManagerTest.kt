package com.cosmos.unreddit.data.remote.api.reddit.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

internal class FakeAuthBackend(
    var shouldSucceed: Boolean = true,
    private val tokenPrefix: String = "token",
    override val type: BackendType = BackendType.MOBILE_SPOOF,
) : AuthBackend {
    var callCount = 0

    override suspend fun fetchToken(device: RedditDevice): TokenResult {
        callCount++
        return if (shouldSucceed) {
            TokenResult.Success(
                accessToken = "$tokenPrefix-$callCount",
                expiresInSeconds = 86400,
                spoofHeaders = mapOf("User-Agent" to "FakeAgent/1.0"),
                loid = "fake-loid-$callCount",
                session = "fake-session-$callCount",
            )
        } else {
            TokenResult.Failure(code = -1, message = "Fake backend failure")
        }
    }
}

class RedditTokenManagerTest {
    private lateinit var manager: RedditTokenManager
    private lateinit var primaryBackend: FakeAuthBackend
    private lateinit var fallbackBackend: FakeAuthBackend

    @Before
    fun setUp() {
        manager = RedditTokenManager()
        primaryBackend = FakeAuthBackend(shouldSucceed = true, tokenPrefix = "primary", type = BackendType.MOBILE_SPOOF)
        fallbackBackend = FakeAuthBackend(shouldSucceed = true, tokenPrefix = "fallback", type = BackendType.GENERIC_WEB)
        manager.setTestDependencies(primaryBackend, fallbackBackend)
    }

    @Test
    fun `first call acquires token from initial backend`() =
        runTest {
            val session = manager.getOrAcquireSession()

            assertEquals("primary-1", session.accessToken)
            assertEquals(BackendType.MOBILE_SPOOF, session.backendType)
            assertEquals(1, primaryBackend.callCount)
        }

    @Test
    fun `second call returns cached token`() =
        runTest {
            val session1 = manager.getOrAcquireSession()
            val session2 = manager.getOrAcquireSession()

            assertEquals(session1.accessToken, session2.accessToken)
            assertEquals(session1.version, session2.version)
            assertEquals(1, primaryBackend.callCount)
        }

    @Test
    fun `forceRefresh acquires new token`() =
        runTest {
            val session1 = manager.getOrAcquireSession()
            manager.forceRefresh()
            val session2 = manager.getOrAcquireSession()

            assertNotEquals(session1.accessToken, session2.accessToken)
            assertEquals("primary-2", session2.accessToken)
            assertEquals(2, primaryBackend.callCount)
        }

    @Test
    fun `backend failure below threshold throws`() =
        runTest {
            primaryBackend.shouldSucceed = false

            try {
                manager.getOrAcquireSession()
                fail("Expected IOException")
            } catch (e: IOException) {
                assertTrue(e.message?.contains("failed") == true)
            }
            assertEquals(1, primaryBackend.callCount)
        }

    @Test
    fun `after 5 failures switches to fallback backend`() =
        runTest {
            primaryBackend.shouldSucceed = false
            fallbackBackend.shouldSucceed = true

            repeat(4) {
                try {
                    manager.getOrAcquireSession()
                } catch (e: IOException) {
                    // expected: failures 1-4
                }
            }

            val session = manager.getOrAcquireSession()

            assertEquals("fallback-1", session.accessToken)
            assertEquals(BackendType.GENERIC_WEB, session.backendType)
            assertEquals(5, primaryBackend.callCount)
            assertEquals(1, fallbackBackend.callCount)
        }

    @Test
    fun `both backends exhausted throws IOException`() =
        runTest {
            primaryBackend.shouldSucceed = false
            fallbackBackend.shouldSucceed = false

            repeat(8) {
                try {
                    manager.getOrAcquireSession()
                } catch (e: IOException) {
                    // expected non-exhausted failures
                }
            }

            try {
                manager.getOrAcquireSession()
                fail("Expected IOException")
            } catch (e: IOException) {
                assertTrue(e.message?.contains("exhausted") == true)
            }
        }

    @Test
    fun `exhausted state resets on next call`() =
        runTest {
            primaryBackend.shouldSucceed = false
            fallbackBackend.shouldSucceed = false

            repeat(9) {
                try {
                    manager.getOrAcquireSession()
                } catch (e: IOException) {
                }
            }

            primaryBackend.shouldSucceed = true

            val session = manager.getOrAcquireSession()

            assertNotNull(session)
            assertEquals(BackendType.MOBILE_SPOOF, session.backendType)
        }

    @Test
    fun `forceRefresh throws when exhausted`() =
        runTest {
            primaryBackend.shouldSucceed = false
            fallbackBackend.shouldSucceed = false

            repeat(9) {
                try {
                    manager.getOrAcquireSession()
                } catch (e: IOException) {
                }
            }

            try {
                manager.forceRefresh()
                fail("Expected IOException")
            } catch (e: IOException) {
                assertTrue(e.message?.contains("exhausted") == true)
            }
        }

    @Test
    fun `exhausted with cached token bypasses cache and reacquires`() =
        runTest {
            val oldSession = manager.getOrAcquireSession()
            assertEquals("primary-1", oldSession.accessToken)

            primaryBackend.shouldSucceed = false
            fallbackBackend.shouldSucceed = false

            // Drive to EXHAUSTED via forceRefresh so the cached session stays intact
            repeat(9) {
                try {
                    manager.forceRefresh()
                } catch (e: IOException) {
                }
            }

            primaryBackend.shouldSucceed = true

            // Even though old session is still valid, EXHAUSTED bypasses the cache
            val newSession = manager.getOrAcquireSession()

            assertNotEquals(oldSession.accessToken, newSession.accessToken)
            assertEquals(BackendType.MOBILE_SPOOF, newSession.backendType)
        }
}
