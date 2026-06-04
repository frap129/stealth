package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenericWebBackendTest {
    private lateinit var server: MockWebServer
    private lateinit var backend: GenericWebBackend

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder().build()
        val moshi = Moshi.Builder().build()
        backend =
            GenericWebBackend(
                client,
                moshi,
                server.url("/api/v1/access_token").toString(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchToken sends correct request format`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"access_token":"t","expires_in":1}"""),
            )

            backend.fetchToken(RedditDevice())

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
            assertEquals("https://www.reddit.com", request.getHeader("Origin"))
            assertTrue(request.getHeader("User-Agent")!!.startsWith("Mozilla/5.0"))

            val body = request.body.readUtf8()
            assertTrue(body.contains("grant_type=https%3A%2F%2Foauth.reddit.com%2Fgrants%2Finstalled_client"))
            assertTrue(body.contains("device_id="))
        }

    @Test
    fun `fetchToken parses response correctly`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"access_token":"my-token","expires_in":3600}"""),
            )

            val result = backend.fetchToken(RedditDevice())

            assertTrue(result is TokenResult.Success)
            val success = result as TokenResult.Success
            assertEquals("my-token", success.accessToken)
            assertEquals(3600L, success.expiresInSeconds)
            assertNull(success.loid)
            assertNull(success.session)
            assertTrue(success.spoofHeaders["User-Agent"]!!.startsWith("Mozilla/5.0"))
            assertEquals("https://www.reddit.com", success.spoofHeaders["Origin"])
        }

    @Test
    fun `fetchToken captures loid and session headers when present`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("x-reddit-loid", "test-loid")
                    .setHeader("x-reddit-session", "test-session")
                    .setBody("""{"access_token":"t","expires_in":1}"""),
            )

            val result = backend.fetchToken(RedditDevice())

            assertTrue(result is TokenResult.Success)
            val success = result as TokenResult.Success
            assertEquals("test-loid", success.loid)
            assertEquals("test-session", success.session)
        }

    @Test
    fun `fetchToken returns Failure on HTTP error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

            val result = backend.fetchToken(RedditDevice())

            assertTrue(result is TokenResult.Failure)
            val failure = result as TokenResult.Failure
            assertEquals(403, failure.code)
        }

    @Test
    fun `fetchToken returns Failure on malformed JSON`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("not json"),
            )

            val result = backend.fetchToken(RedditDevice())

            assertTrue(result is TokenResult.Failure)
        }
}
