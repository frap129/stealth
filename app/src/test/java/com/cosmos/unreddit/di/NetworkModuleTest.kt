package com.cosmos.unreddit.di

import com.cosmos.unreddit.data.remote.RedditCommentsPathInterceptor
import com.cosmos.unreddit.data.remote.api.reddit.RedditAuthInterceptor
import com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `auth reddit client forces http11`() {
        val client =
            NetworkModule.provideAuthRedditOkHttpClient(
                RedditAuthInterceptor(
                    RedditTokenManager(),
                    CoroutineScope(SupervisorJob()),
                ),
            )

        assertEquals(listOf(Protocol.HTTP_1_1), client.protocols)
        assertTrue(client.interceptors.any { it is RedditCommentsPathInterceptor })
    }
}
