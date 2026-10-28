package com.cosmos.unreddit.data.remote.api.reddit

import com.cosmos.unreddit.data.remote.api.reddit.model.AboutChild
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.di.NetworkModule
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class RedditSearchApiTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var api: RedditSearchApi

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        api =
            Retrofit
                .Builder()
                .baseUrl(mockServer.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(NetworkModule.provideRedditMoshi()))
                .build()
                .create(RedditSearchApi::class.java)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `searchPost requests nsfw link results`() =
        runTest {
            mockServer.enqueue(MockResponse().setBody(EMPTY_LISTING))

            val actualListing = api.searchPost("pixel", "top", "week", "t3_after")

            val actualRequest: RecordedRequest = mockServer.takeRequest()
            assertEquals("/search", actualRequest.requestUrl!!.encodedPath)
            assertEquals("link", actualRequest.requestUrl!!.queryParameter("type"))
            assertNsfwRawJson(actualRequest)
            assertEquals("pixel", actualRequest.requestUrl!!.queryParameter("q"))
            assertEquals("top", actualRequest.requestUrl!!.queryParameter("sort"))
            assertEquals("week", actualRequest.requestUrl!!.queryParameter("t"))
            assertEquals("t3_after", actualRequest.requestUrl!!.queryParameter("after"))
            assertEquals("t3_next", actualListing.data.after)
        }

    @Test
    fun `searchInSubreddit restricts nsfw results to the subreddit`() =
        runTest {
            mockServer.enqueue(MockResponse().setBody(EMPTY_LISTING))

            api.searchInSubreddit("Android", "pixel", null, null, null)

            val actualRequest: RecordedRequest = mockServer.takeRequest()
            assertEquals("/r/Android/search", actualRequest.requestUrl!!.encodedPath)
            assertEquals("1", actualRequest.requestUrl!!.queryParameter("restrict_sr"))
            assertNsfwRawJson(actualRequest)
        }

    @Test
    fun `searchSubreddit requests nsfw subreddit results`() =
        runTest {
            mockServer.enqueue(MockResponse().setBody(EMPTY_LISTING))

            api.searchSubreddit("android", null, null, null)

            val actualRequest: RecordedRequest = mockServer.takeRequest()
            assertEquals("/search", actualRequest.requestUrl!!.encodedPath)
            assertEquals("sr", actualRequest.requestUrl!!.queryParameter("type"))
            assertNsfwRawJson(actualRequest)
        }

    @Test
    fun `parses nsfw flags from search results`() =
        runTest {
            mockServer.enqueue(MockResponse().setBody(NSFW_LISTING))

            val actualChildren = api.searchPost("query", null, null, null).data.children

            assertTrue((actualChildren[0] as PostChild).data.isOver18)
            assertEquals(true, (actualChildren[1] as AboutChild).data.over18)
        }

    private fun assertNsfwRawJson(request: RecordedRequest) {
        assertEquals("1", request.requestUrl!!.queryParameter("include_over_18"))
        assertEquals("1", request.requestUrl!!.queryParameter("raw_json"))
    }

    private companion object {
        private const val EMPTY_LISTING: String =
            """{"kind":"Listing","data":{"after":"t3_next","dist":0,"modhash":"","children":[],"before":null}}"""

        private val NSFW_LISTING: String =
            """
            {"kind":"Listing","data":{"after":null,"dist":2,"modhash":"","before":null,"children":[
              {"kind":"t3","data":{
                "subreddit":"example","link_flair_richtext":[],"author_flair_richtext":[],
                "title":"Post","subreddit_name_prefixed":"r/example","name":"t3_a",
                "upvote_ratio":1.0,"total_awards_received":0,"is_original_content":false,
                "score":1,"is_self":true,"domain":"self.example","archived":false,
                "over_18":true,"all_awardings":[],"spoiler":false,"locked":false,
                "author":"author","num_comments":0,"permalink":"/r/example/comments/a/post/",
                "stickied":false,"url":"https://www.reddit.com/r/example/comments/a/post/",
                "created_utc":1793157720.0,"is_video":false
              }},
              {"kind":"t5","data":{
                "display_name":"example","title":"Example","community_icon":"",
                "banner_background_image":"","over18":true,
                "url":"/r/example/","created_utc":1793157720.0
              }}
            ]}}
            """.trimIndent()
    }
}
