package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MobileSpoofBackendTest {
    private lateinit var server: MockWebServer
    private lateinit var backend: MobileSpoofBackend

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder().build()
        val moshi = Moshi.Builder().build()
        backend =
            MobileSpoofBackend(
                client,
                moshi,
                server.url("/auth/v2/oauth/access-token/loid").toString(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `default token url uses reddit web host`() {
        val client = OkHttpClient.Builder().build()
        val moshi = Moshi.Builder().build()
        val defaultBackend = MobileSpoofBackend(client, moshi)

        val field = MobileSpoofBackend::class.java.getDeclaredField("tokenUrl")
        field.isAccessible = true

        assertEquals(
            "https://www.reddit.com/auth/v2/oauth/access-token/loid",
            field.get(defaultBackend),
        )
    }

    @Test
    fun `fetchToken returns Success on 200 with valid JSON`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("x-reddit-loid", "test-loid")
                    .setHeader("x-reddit-session", "test-session")
                    .setBody("""{"access_token":"test-token","expires_in":3600}"""),
            )

            val device = RedditDevice()
            val result = backend.fetchToken(device)

            assertTrue(result is TokenResult.Success)
            val success = result as TokenResult.Success
            assertEquals("test-token", success.accessToken)
            assertEquals(3600L, success.expiresInSeconds)
            assertEquals("test-loid", success.loid)
            assertEquals("test-session", success.session)
        }

    @Test
    fun `fetchToken sends correct headers`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"access_token":"t","expires_in":1}"""),
            )

            val device = RedditDevice()
            backend.fetchToken(device)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
            assertEquals(device.userAgent, request.getHeader("User-Agent"))
            assertEquals(device.uuid, request.getHeader("x-reddit-device-id"))
            assertEquals("""{"scopes":["*"]}""", request.body.readUtf8())
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

    @Test
    fun `fetchToken populates spoofHeaders on success`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"access_token":"t","expires_in":1}"""),
            )

            val device = RedditDevice()
            val result = backend.fetchToken(device) as TokenResult.Success

            assertEquals(device.userAgent, result.spoofHeaders["User-Agent"])
            assertEquals(device.uuid, result.spoofHeaders["x-reddit-device-id"])
            assertNotNull(result.spoofHeaders["x-reddit-qos"])
        }
}
