package com.cosmos.unreddit.data.remote.datasource

import com.cosmos.unreddit.data.model.Sort
import com.cosmos.unreddit.data.model.Sorting
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutChild
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutData
import com.cosmos.unreddit.data.remote.api.reddit.model.Child
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.ListingData
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.data.remote.api.reddit.model.PostData
import com.cosmos.unreddit.data.remote.api.reddit.source.BaseRedditSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPostDataSourceTest {
    @Test
    fun `appends nsfw subreddit posts when nsfw is enabled`() {
        runTest {
            val mockSource: BaseRedditSource = mockk()
            val inputSorting: Sorting = Sorting(Sort.RELEVANCE)
            coEvery {
                mockSource.searchPost("query", Sort.RELEVANCE, null, null)
            } returns listing(listOf(postChild("t3_sfw")))
            coEvery {
                mockSource.searchSubreddit("query", null, null, null)
            } returns listing(
                listOf(
                    aboutChild("gonewild", isOver18 = true),
                    aboutChild("Android", isOver18 = false),
                ),
            )
            coEvery {
                mockSource.getSubreddit("gonewild", Sort.RELEVANCE, null, null)
            } returns listing(listOf(postChild("t3_nsfw", isOver18 = true)))
            val inputDataSource =
                SearchPostDataSource(mockSource, "query", inputSorting, includeNsfw = true)

            val actualListing = inputDataSource.getResponse("query", inputSorting, null)

            assertEquals(
                listOf("t3_sfw", "t3_nsfw"),
                actualListing.data.children.map { (it as PostChild).data.name },
            )
            coVerify(exactly = 1) {
                mockSource.getSubreddit("gonewild", Sort.RELEVANCE, null, null)
            }
            coVerify(exactly = 0) {
                mockSource.getSubreddit("Android", any(), any(), any())
            }
        }
    }

    @Test
    fun `skips nsfw fallback when nsfw is disabled`() {
        runTest {
            val mockSource: BaseRedditSource = mockk()
            val inputSorting: Sorting = Sorting(Sort.RELEVANCE)
            coEvery {
                mockSource.searchPost("query", Sort.RELEVANCE, null, null)
            } returns listing(listOf(postChild("t3_sfw")))
            val inputDataSource = SearchPostDataSource(mockSource, "query", inputSorting)

            val actualListing = inputDataSource.getResponse("query", inputSorting, null)

            assertEquals(
                listOf("t3_sfw"),
                actualListing.data.children.map { (it as PostChild).data.name },
            )
            coVerify(exactly = 0) {
                mockSource.searchSubreddit(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `skips nsfw fallback after the first page`() {
        runTest {
            val mockSource: BaseRedditSource = mockk()
            val inputSorting: Sorting = Sorting(Sort.RELEVANCE)
            coEvery {
                mockSource.searchPost("query", Sort.RELEVANCE, null, "cursor")
            } returns listing(listOf(postChild("t3_sfw")))
            val inputDataSource =
                SearchPostDataSource(mockSource, "query", inputSorting, includeNsfw = true)

            val actualListing = inputDataSource.getResponse("query", inputSorting, "cursor")

            assertEquals(
                listOf("t3_sfw"),
                actualListing.data.children.map { (it as PostChild).data.name },
            )
            coVerify(exactly = 0) {
                mockSource.searchSubreddit(any(), any(), any(), any())
            }
        }
    }

    private fun listing(children: List<Child>, after: String? = null): Listing =
        Listing("Listing", ListingData(null, children.size, children, after, null))

    private fun aboutChild(name: String, isOver18: Boolean): AboutChild =
        AboutChild(
            AboutData(
                wikiEnabled = false,
                displayName = name,
                headerImg = null,
                title = name,
                primaryColor = null,
                activeUserCount = null,
                iconImg = null,
                subscribers = null,
                quarantine = false,
                publicDescriptionHtml = null,
                communityIcon = "",
                bannerBackgroundImage = "",
                keyColor = null,
                bannerBackgroundColor = null,
                over18 = isOver18,
                descriptionHtml = null,
                url = "https://www.reddit.com/r/$name",
                created = 0L,
            ),
        )

    private fun postChild(id: String, isOver18: Boolean = false): PostChild =
        PostChild(
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
                isOver18 = isOver18,
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
            ),
        )
}
