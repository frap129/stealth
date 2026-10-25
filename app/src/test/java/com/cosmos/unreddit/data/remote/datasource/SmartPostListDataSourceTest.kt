package com.cosmos.unreddit.data.remote.datasource

import androidx.paging.PagingSource
import com.cosmos.unreddit.data.model.Sort
import com.cosmos.unreddit.data.model.Sorting
import com.cosmos.unreddit.data.model.TimeSorting
import com.cosmos.unreddit.data.remote.api.reddit.model.Child
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.ListingData
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChildren
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.data.remote.api.reddit.model.PostData
import com.cosmos.unreddit.data.remote.api.reddit.source.BaseRedditSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SmartPostListDataSourceTest {
    @Test
    fun `home fanout caps concurrency and never refetches exhausted subs`() = runTest {
        val inputCalls = AtomicInteger(0)
        val inputCurrent = AtomicInteger(0)
        val inputMax = AtomicInteger(0)
        val inputSource: BaseRedditSource =
            object : BaseRedditSource by unimplementedSource() {
                override suspend fun getSubreddit(
                    subreddit: String,
                    sort: Sort,
                    timeSorting: TimeSorting?,
                    after: String?,
                ): Listing {
                    inputCalls.incrementAndGet()
                    val now = inputCurrent.incrementAndGet()
                    inputMax.updateAndGet { maxOf(it, now) }
                    delay(50)
                    inputCurrent.decrementAndGet()
                    val next = if (subreddit == "a") null else "c1"
                    return Listing(
                        "Listing",
                        ListingData(
                            null,
                            1,
                            listOf(PostChild(postData("${subreddit}_${after ?: "first"}"))),
                            next,
                            null,
                        ),
                    )
                }
            }
        val inputMain = UnconfinedTestDispatcher(testScheduler)
        val inputDataSource = SmartPostListDataSource(
            inputSource,
            listOf("a", "b", "c", "d", "e"),
            Sorting(Sort.HOT),
            Dispatchers.Default,
            inputMain,
        )

        val actualFirst = inputDataSource.load(
            PagingSource.LoadParams.Refresh<List<String>>(null, 25, false),
        )
        assertTrue(actualFirst is PagingSource.LoadResult.Page<*, *>)
        val actualFirstPage = actualFirst as PagingSource.LoadResult.Page<List<String>, Child>
        assertEquals(4, inputMax.get())
        assertEquals(listOf("", "c1", "c1", "c1", "c1"), actualFirstPage.nextKey)

        val actualSecond = inputDataSource.load(
            PagingSource.LoadParams.Refresh(actualFirstPage.nextKey, 25, false),
        )
        assertTrue(actualSecond is PagingSource.LoadResult.Page<*, *>)
        val actualSecondPage = actualSecond as PagingSource.LoadResult.Page<List<String>, Child>
        assertEquals(9, inputCalls.get())

        val actualFirstIds = actualFirstPage.data.map { (it as PostChild).data.name }.toSet()
        val actualSecondIds = actualSecondPage.data.map { (it as PostChild).data.name }.toSet()
        assertTrue(actualFirstIds.intersect(actualSecondIds).isEmpty())
    }

    private fun unimplementedSource(): BaseRedditSource =
        object : BaseRedditSource {
            override suspend fun getSubreddit(
                subreddit: String,
                sort: Sort,
                timeSorting: TimeSorting?,
                after: String?,
            ): Listing = unsupported()

            override suspend fun getSubredditInfo(subreddit: String): Child = unsupported()

            override suspend fun searchInSubreddit(
                subreddit: String,
                query: String,
                sort: Sort?,
                timeSorting: TimeSorting?,
                after: String?,
            ): Listing = unsupported()

            override suspend fun getPost(
                permalink: String,
                limit: Int?,
                sort: Sort,
            ): List<Listing> = unsupported()

            override suspend fun getMoreChildren(
                cursor: String,
                linkId: String,
                sortType: String,
            ): MoreChildren = unsupported()

            override suspend fun getUserInfo(user: String): Child = unsupported()

            override suspend fun getUserPosts(
                user: String,
                sort: Sort,
                timeSorting: TimeSorting?,
                after: String?,
            ): Listing = unsupported()

            override suspend fun getUserComments(
                user: String,
                sort: Sort,
                timeSorting: TimeSorting?,
                after: String?,
            ): Listing = unsupported()

            override suspend fun searchPost(
                query: String,
                sort: Sort?,
                timeSorting: TimeSorting?,
                after: String?,
            ): Listing = unsupported()

            override suspend fun searchUser(
                query: String,
                sort: Sort?,
                timeSorting: TimeSorting?,
                after: String?,
            ): Listing = unsupported()

            override suspend fun searchSubreddit(
                query: String,
                sort: Sort?,
                timeSorting: TimeSorting?,
                after: String?,
            ): Listing = unsupported()
        }

    private fun unsupported(): Nothing = throw UnsupportedOperationException()

    private fun postData(id: String): PostData =
        PostData(
            subreddit = "subreddit",
            linkFlairRichText = emptyList(),
            authorFlairRichText = null,
            title = id,
            prefixedSubreddit = "r/subreddit",
            name = id,
            ratio = null,
            totalAwards = 0,
            isOC = false,
            flair = null,
            authorFlair = null,
            galleryData = null,
            score = 0,
            hint = null,
            isSelf = false,
            crossposts = null,
            domain = "example.com",
            selfTextHtml = null,
            suggestedSort = null,
            isArchived = false,
            isOver18 = false,
            mediaPreview = null,
            awardings = emptyList(),
            isSpoiler = false,
            isLocked = false,
            distinguished = null,
            author = "author",
            commentsNumber = 0,
            permalink = "/r/subreddit/comments/$id",
            isStickied = false,
            url = "https://example.com/$id",
            created = 0,
            media = null,
            mediaMetadata = null,
            isRedditGallery = null,
            isVideo = false,
        )
}
