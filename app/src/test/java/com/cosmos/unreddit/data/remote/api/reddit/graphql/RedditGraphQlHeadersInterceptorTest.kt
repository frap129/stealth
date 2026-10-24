package com.cosmos.unreddit.data.remote.api.reddit.graphql

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditGraphQlHeadersInterceptorTest {
    @Test
    fun `sets the graphql fingerprint headers`() {
        val inputServer: MockWebServer = MockWebServer()
        inputServer.start()
        try {
            val mockContext: Context = mockk(relaxed = true)
            every { mockContext.resources.displayMetrics } returns
                DisplayMetrics().apply {
                    density = 2.75f
                    widthPixels = 1080
                    heightPixels = 2400
                }
            val inputClient: OkHttpClient =
                OkHttpClient.Builder()
                    .addInterceptor(
                        RedditGraphQlHeadersInterceptor(
                            context = mockContext,
                            graphQlHost = inputServer.url("/").host,
                        ),
                    )
                    .build()
            inputServer.enqueue(MockResponse().setResponseCode(200))
            inputClient.newCall(Request.Builder().url(inputServer.url("/")).build()).execute().close()
            val actualRequest: RecordedRequest = inputServer.takeRequest()
            assertEquals("graphql-go", actualRequest.getHeader("X-Reddit-Direction"))
            assertEquals("1", actualRequest.getHeader("x-reddit-compression"))
            assertEquals(
                "Reddit/Version 2026.42.0/Build 2642001/Android ${Build.VERSION.RELEASE}",
                actualRequest.getHeader("User-Agent"),
            )
            assertEquals("https://gql-fed.reddit.com", actualRequest.getHeader("Origin"))
            assertEquals("2.75", actualRequest.getHeader("x-reddit-dpr"))
            assertEquals("392", actualRequest.getHeader("x-reddit-width"))
            assertEquals("872", actualRequest.getHeader("x-reddit-height"))
            assertEquals(
                "${Build.MANUFACTURER};${Build.MODEL}",
                actualRequest.getHeader("Device-Name"),
            )
            assertEquals("GOOD", actualRequest.getHeader("X-Reddit-Http-QoS"))
            assertEquals("v2t2", actualRequest.getHeader("X-Reddit-Device-Tier"))
            assertEquals(
                "available-codecs=video/avc, video/hevc, video/x-vnd.on2.vp9",
                actualRequest.getHeader("X-Reddit-Media-Codecs"),
            )
            assertEquals("down-rate-mbps=55.000", actualRequest.getHeader("X-Reddit-QoS"))
            assertNotNull(actualRequest.getHeader("Accept-Language"))
        } finally {
            inputServer.shutdown()
        }
    }

    @Test
    fun `leaves requests outside the graphql host untouched`() {
        val inputServer: MockWebServer = MockWebServer()
        inputServer.start()
        try {
            val mockContext: Context = mockk(relaxed = true)
            val inputClient: OkHttpClient =
                OkHttpClient.Builder()
                    .addInterceptor(RedditGraphQlHeadersInterceptor(context = mockContext))
                    .build()
            inputServer.enqueue(MockResponse().setResponseCode(200))
            inputClient.newCall(Request.Builder().url(inputServer.url("/")).build()).execute().close()
            val actualRequest: RecordedRequest = inputServer.takeRequest()
            assertNull(actualRequest.getHeader("X-Reddit-Direction"))
            assertNull(actualRequest.getHeader("Origin"))
            assertNull(actualRequest.getHeader("X-Reddit-Device-Tier"))
            val actualUserAgent: String? = actualRequest.getHeader("User-Agent")
            assertTrue(actualUserAgent?.startsWith("Reddit/") != true)
        } finally {
            inputServer.shutdown()
        }
    }
}
