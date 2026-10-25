package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RedditGraphQlClient(
    private val client: OkHttpClient,
    moshi: Moshi,
    private val ioDispatcher: CoroutineDispatcher,
    private val endpoint: String = ENDPOINT,
) : GraphQlExecutor {
    private val requestAdapter = moshi.adapter(GraphQlRequest::class.java)

    override suspend fun execute(operation: GraphQlOperation, variables: Map<String, Any?>): String =
        withContext(ioDispatcher) {
            val body = requestAdapter.toJson(GraphQlRequest.from(operation, variables))
            val request =
                Request.Builder()
                    .url(endpoint)
                    .post(body.toRequestBody(JSON))
                    .header("X-APOLLO-OPERATION-NAME", operation.name)
                    .header("X-APOLLO-OPERATION-ID", operation.hash)
                    .header("X-Reddit-Direction", "graphql-go")
                    .header("Accept", "application/json")
                    .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GraphQlException(response.code, responseBody)
                }
                val remaining = response.header("x-ratelimit-remaining")?.toFloatOrNull()
                if (remaining != null && remaining <= RATE_LIMIT_FLOOR) {
                    throw RateLimitException("GraphQL rate limit near exhaustion")
                }
                responseBody
            }
        }

    companion object {
        const val ENDPOINT = "https://gql-fed.reddit.com/"
        private const val RATE_LIMIT_FLOOR = 4f
        private val JSON = "application/json".toMediaType()
    }
}
