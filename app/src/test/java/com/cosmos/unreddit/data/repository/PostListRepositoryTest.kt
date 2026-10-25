package com.cosmos.unreddit.data.repository

import androidx.paging.PagingSource
import com.cosmos.unreddit.data.local.RedditDatabase
import com.cosmos.unreddit.data.model.Sort
import com.cosmos.unreddit.data.model.Sorting
import com.cosmos.unreddit.data.remote.api.reddit.model.Child
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.ListingData
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.data.remote.api.reddit.model.PostData
import com.cosmos.unreddit.data.remote.api.reddit.source.BaseRedditSource
import com.cosmos.unreddit.data.remote.datasource.SmartPostListDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostListRepositoryTest {
    @Test
    fun `home feed filters pinned posts while subreddit feed keeps them`() = runTest {
        val inputPosts: List<PostData> =
            listOf(
                postData("pinned", isStickied = true),
                postData("recent"),
            )
        val mockSource: BaseRedditSource = mockk()
        coEvery {
            mockSource.getSubreddit(any(), any(), any(), any())
        } returns
            Listing(
                "Listing",
                ListingData(null, inputPosts.size, inputPosts.map { PostChild(it) }, null, null),
            )
        val mockDatabase: RedditDatabase = mockk(relaxed = true)
        val inputRepository =
            PostListRepository(
                mockSource,
                mockDatabase,
                UnconfinedTestDispatcher(testScheduler),
                UnconfinedTestDispatcher(testScheduler),
            )
        val inputSorting: Sorting = Sorting(Sort.HOT)

        val actualHomePage =
            loadPage(inputRepository.getPostListDataSource(listOf("a"), inputSorting))
        val actualSubredditPage =
            loadPage(inputRepository.getPostListDataSource("a", inputSorting))

        assertEquals(
            listOf("recent"),
            actualHomePage.data.map { (it as PostChild).data.name },
        )
        assertEquals(
            listOf("pinned", "recent"),
            actualSubredditPage.data.map { (it as PostChild).data.name },
        )
    }

    private suspend fun loadPage(
        dataSource: SmartPostListDataSource,
    ): PagingSource.LoadResult.Page<List<String>, Child> {
        val actualResult =
            dataSource.load(
                PagingSource.LoadParams.Refresh<List<String>>(null, 25, false),
            )
        assertTrue(actualResult is PagingSource.LoadResult.Page<*, *>)
        return actualResult as PagingSource.LoadResult.Page<List<String>, Child>
    }

    private fun postData(
        id: String,
        isStickied: Boolean = false,
    ): PostData =
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
            isStickied = isStickied,
            url = "https://example.com/$id",
            created = 0,
            media = null,
            mediaMetadata = null,
            isRedditGallery = null,
            isVideo = false,
        )
}
