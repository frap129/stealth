package com.cosmos.unreddit.di

import android.content.Context
import com.cosmos.unreddit.data.remote.api.reddit.RedditAuthInterceptor
import com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTokenManager
import com.cosmos.unreddit.data.remote.api.reddit.graphql.RedditGraphQlHeadersInterceptor
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `graphql client has auth and graphql header interceptors`() {
        // Arrange
        val mockContext: Context = mockk(relaxed = true)
        val inputAuthInterceptor =
            RedditAuthInterceptor(
                RedditTokenManager(),
                CoroutineScope(SupervisorJob()),
            )
        val inputHeadersInterceptor =
            RedditGraphQlHeadersInterceptor(
                mockContext,
                RedditGraphQlHeadersInterceptor.GRAPHQL_HOST,
            )

        // Act
        val actualClient =
            NetworkModule.provideRedditGqlOkHttpClient(
                inputAuthInterceptor,
                inputHeadersInterceptor,
            )

        // Assert
        val actualAuthIndex = actualClient.interceptors.indexOf(inputAuthInterceptor)
        val actualHeadersIndex = actualClient.interceptors.indexOf(inputHeadersInterceptor)
        assertTrue(actualAuthIndex >= 0)
        assertTrue(actualHeadersIndex >= 0)
        assertTrue(actualAuthIndex < actualHeadersIndex)
        assertEquals(listOf(Protocol.HTTP_1_1), actualClient.protocols)
    }

    @Test
    fun `reddit auth hosts include the graphql host`() {
        assertTrue(
            RedditAuthInterceptor.REDDIT_HOSTS.contains(
                RedditGraphQlHeadersInterceptor.GRAPHQL_HOST,
            ),
        )
    }
}
