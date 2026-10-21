package com.cosmos.unreddit.data.remote

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RedditCommentsPathInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            OkHttpClient.Builder()
                .addInterceptor(RedditCommentsPathInterceptor())
                .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `encodes comments path separator`() {
        val inputPath = "/r/Android/comments/1wzvqsb/"
        val inputQuery = "sort=confidence&raw_json=1"
        val expectedPath = "/r/Android%2Fcomments/1wzvqsb/?sort=confidence&raw_json=1"
        server.enqueue(MockResponse().setResponseCode(200))

        execute(inputPath, inputQuery)
        val actualPath = server.takeRequest().path

        assertEquals(expectedPath, actualPath)
    }

    @Test
    fun `encodes more children path separator`() {
        val inputPath = "/api/morechildren"
        val inputQuery = "api_type=json&children=19reeac5u5nz6&link_id=t3_1x9xx9e"
        val expectedPath =
            "/api%2Fmorechildren?api_type=json&children=19reeac5u5nz6&link_id=t3_1x9xx9e"
        server.enqueue(MockResponse().setResponseCode(200))

        execute(inputPath, inputQuery)
        val actualPath = server.takeRequest().path

        assertEquals(expectedPath, actualPath)
    }

    @Test
    fun `leaves non comment paths unchanged`() {
        val inputPath = "/r/Android/hot"
        val inputQuery = "raw_json=1"
        val expectedPath = "/r/Android/hot?raw_json=1"
        server.enqueue(MockResponse().setResponseCode(200))

        execute(inputPath, inputQuery)
        val actualPath = server.takeRequest().path

        assertEquals(expectedPath, actualPath)
    }

    private fun execute(path: String, query: String) {
        client.newCall(
            Request.Builder()
                .url(url(path, query))
                .build()
        ).execute().close()
    }

    private fun url(path: String, query: String): HttpUrl =
        server.url("/")
            .newBuilder()
            .encodedPath(path)
            .encodedQuery(query)
            .build()
}
