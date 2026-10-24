package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditGraphQlClientTest {
    @Test
    fun `posts a persisted query and returns the body`() = runTest {
        val inputServer: MockWebServer = MockWebServer()
        inputServer.start()
        try {
            inputServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"ok":true}}"""))
            val inputClient: OkHttpClient = OkHttpClient.Builder().build()
            val inputMoshi: Moshi = Moshi.Builder().build()
            val inputExecutor: RedditGraphQlClient =
                RedditGraphQlClient(
                    client = inputClient,
                    moshi = inputMoshi,
                    ioDispatcher = Dispatchers.IO,
                    endpoint = inputServer.url("/").toString(),
                )
            val actualBody: String =
                inputExecutor.execute(RedditGraphQlOperations.PostComments, mapOf("id" to "t3_1wzvqsb"))
            assertEquals("""{"data":{"ok":true}}""", actualBody)
            val actualRequest: RecordedRequest = inputServer.takeRequest()
            assertEquals("POST", actualRequest.method)
            assertEquals("PostComments", actualRequest.getHeader("X-APOLLO-OPERATION-NAME"))
            assertEquals(
                RedditGraphQlOperations.PostComments.hash,
                actualRequest.getHeader("X-APOLLO-OPERATION-ID"),
            )
            assertEquals("application/json", actualRequest.getHeader("Accept"))
            assertTrue(actualRequest.getHeader("Content-Type")!!.startsWith("application/json"))
            assertTrue(actualRequest.body.readUtf8().contains("persistedQuery"))
        } finally {
            inputServer.shutdown()
        }
    }

    @Test
    fun `throws GraphQlException with code and body on non-2xx`() = runTest {
        val inputServer: MockWebServer = MockWebServer()
        inputServer.start()
        try {
            inputServer.enqueue(MockResponse().setResponseCode(429).setBody("""{"message":"rate limited"}"""))
            val inputClient: OkHttpClient = OkHttpClient.Builder().build()
            val inputMoshi: Moshi = Moshi.Builder().build()
            val inputExecutor: RedditGraphQlClient =
                RedditGraphQlClient(
                    client = inputClient,
                    moshi = inputMoshi,
                    ioDispatcher = Dispatchers.IO,
                    endpoint = inputServer.url("/").toString(),
                )
            val actualException: GraphQlException =
                try {
                    inputExecutor.execute(RedditGraphQlOperations.PostComments, mapOf("id" to "t3_1wzvqsb"))
                    throw AssertionError("Expected GraphQlException")
                } catch (exception: GraphQlException) {
                    exception
                }
            assertEquals(429, actualException.code)
            assertEquals("""{"message":"rate limited"}""", actualException.body)
            assertEquals("GraphQL HTTP 429", actualException.message)
        } finally {
            inputServer.shutdown()
        }
    }
}
