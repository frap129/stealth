package com.cosmos.unreddit.data.remote.api.reddit.source

import com.cosmos.unreddit.data.model.Sort
import com.cosmos.unreddit.data.model.TimeSorting
import com.cosmos.unreddit.data.remote.api.reddit.graphql.GraphQlExecutor
import com.cosmos.unreddit.data.remote.api.reddit.graphql.GraphQlOperation
import com.cosmos.unreddit.data.remote.api.reddit.graphql.RedditGraphQlOperations
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutChild
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutUserChild
import com.cosmos.unreddit.data.remote.api.reddit.model.CommentChild
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChild
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class RedditSourceTest {
    private val moshi: Moshi = Moshi.Builder().build()
    private lateinit var executor: FakeGraphQlExecutor

    @Test
    fun `getSubreddit sends feed ids to posts by ids in feed order`() =
        runTest {
            val source =
                createSource(
                    subredditFeedJson(listOf("t3_b", "sortcell", "t3_a"), "t3_cursor"),
                    postsByIdsJson(postJson("t3_a", "A"), postJson("t3_b", "B")),
                )

            val actualListing = source.getSubreddit("Android", Sort.TOP, TimeSorting.DAY, null)

            val actualChildren = actualListing.data.children.map { (it as PostChild).data.name }
            assertEquals(listOf("t3_b", "t3_a"), actualChildren)
            assertEquals("t3_cursor", actualListing.data.after)
            assertEquals(RedditGraphQlOperations.SubredditFeedSdui, executor.calls[0].operation)
            assertEquals("Android", executor.calls[0].variables["subredditName"])
            assertEquals("TOP", executor.calls[0].variables["sort"])
            assertEquals("DAY", executor.calls[0].variables["time"])
            assertFalse(executor.calls[0].variables.containsKey("after"))
            assertEquals(RedditGraphQlOperations.PostsByIds, executor.calls[1].operation)
            assertEquals(listOf("t3_b", "t3_a"), executor.calls[1].variables["ids"])
            assertEquals(true, executor.calls[1].variables["includeExtendedVideoAsset"])
            assertEquals(true, executor.calls[1].variables["includePlaybackMp4s"])
        }

    @Test
    fun `popular feed uses the popular operation and maps posts`() =
        runTest {
            val source: RedditSource =
                createSource(
                    popularFeedJson(listOf("t3_b", "t3_a"), "t3_cursor"),
                    postsByIdsJson(postJson("t3_a", "A"), postJson("t3_b", "B")),
                )
            val actualListing: Listing =
                source.getSubreddit("popular", Sort.TOP, TimeSorting.DAY, null)
            val actualChildren: List<String> =
                actualListing.data.children.map { (it as PostChild).data.name }
            assertEquals(listOf("t3_b", "t3_a"), actualChildren)
            assertEquals("t3_cursor", actualListing.data.after)
            assertEquals(RedditGraphQlOperations.PopularFeedSdui, executor.calls[0].operation)
            assertEquals("TOP", executor.calls[0].variables["sort"])
            assertEquals("DAY", executor.calls[0].variables["time"])
            assertFalse(executor.calls[0].variables.containsKey("subredditName"))
            assertEquals(RedditGraphQlOperations.PostsByIds, executor.calls[1].operation)
        }

    @Test
    fun `getSubreddit preserves feed order and skips missing posts`() =
        runTest {
            val source =
                createSource(
                    subredditFeedJson(listOf("t3_a", "t3_b", "t3_c"), null),
                    postsByIdsJson(postJson("t3_c", "C"), postJson("t3_a", "A")),
                )

            val actualListing = source.getSubreddit("Android", Sort.HOT, null, null)

            val actualChildren = actualListing.data.children.map { (it as PostChild).data.name }
            assertEquals(listOf("t3_a", "t3_c"), actualChildren)
            assertNull(actualListing.data.after)
        }

    @Test
    fun `getSubreddit skips null and deleted posts by ids`() =
        runTest {
            val deletedPost: String =
                """{"__typename":"DeletedSubredditPost","id":"t3_deleted"}"""
            val unavailablePost: String =
                """{"__typename":"UnavailablePost","id":"t3_unavailable"}"""
            val source: RedditSource =
                createSource(
                    subredditFeedJson(
                        listOf("t3_valid", "t3_deleted", "t3_unavailable"),
                        null,
                    ),
                    postsByIdsJson(
                        "null",
                        deletedPost,
                        postJson("t3_valid", "Valid"),
                        unavailablePost,
                    ),
                )

            val actualListing = source.getSubreddit("Android", Sort.HOT, null, null)

            assertEquals(
                listOf("t3_valid"),
                actualListing.data.children.map { (it as PostChild).data.name },
            )
        }

    @Test
    fun `getSubreddit throws IOException when feed elements are null`() =
        runTest {
            val source: RedditSource =
                createSource(
                    """{"data":{"subredditV3":{"elements":null}},"errors":[{"message":"Feed unavailable"}]}""",
                )

            try {
                source.getSubreddit("Android", Sort.HOT, null, null)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertTrue(exception.message?.contains("Feed unavailable") == true)
            }
        }

    @Test
    fun `getSubreddit maps a blank feed end cursor to null`() =
        runTest {
            val source: RedditSource =
                createSource(
                    subredditFeedJson(listOf("t3_a"), ""),
                    postsByIdsJson(postJson("t3_a", "A")),
                )

            val actualListing = source.getSubreddit("Android", Sort.HOT, null, null)

            assertNull(actualListing.data.after)
        }

    @Test
    fun `getSubreddit skips the posts request when the feed has no post ids`() =
        runTest {
            val source = createSource(subredditFeedJson(listOf("sortcell"), "t3_cursor"))

            val actualListing = source.getSubreddit("Android", Sort.HOT, null, null)

            assertTrue(actualListing.data.children.isEmpty())
            assertEquals("t3_cursor", actualListing.data.after)
            assertEquals(1, executor.calls.size)
            assertEquals(RedditGraphQlOperations.SubredditFeedSdui, executor.calls.single().operation)
        }

    @Test
    fun `getSubreddit throws IOException when the feed root is null`() =
        runTest {
            val source: RedditSource =
                createSource(
                    """{"data":{"subredditV3":null},"errors":[{"message":"Subreddit not found"}]}""",
                )

            try {
                source.getSubreddit("missing", Sort.HOT, null, null)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Subreddit not found", exception.message)
            }
        }

    @Test
    fun `getPost sends posts by ids then post comments`() =
        runTest {
            val source =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(includeDeleted = true),
                )

            val actualListings =
                source.getPost("/r/Android/comments/1wzvqsb/we_are/", 25, Sort.BEST)

            assertEquals(2, actualListings.size)
            assertEquals(1, actualListings[1].data.children.size)
            val actualPost = actualListings[0].data.children.single() as PostChild
            assertEquals("t3_1wzvqsb", actualPost.data.name)
            val actualComment = actualListings[1].data.children.single() as CommentChild
            assertEquals("t3_1wzvqsb", actualComment.data.linkId)
            assertEquals("Post title", actualComment.data.linkTitle)
            assertEquals(
                "/r/Android/comments/1wzvqsb/we_are/",
                actualComment.data.linkPermalink,
            )
            assertEquals("r/Android", actualComment.data.subreddit)

            assertEquals(RedditGraphQlOperations.PostsByIds, executor.calls[0].operation)
            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals(true, executor.calls[0].variables["includeExtendedVideoAsset"])
            assertEquals(true, executor.calls[0].variables["includePlaybackMp4s"])
            assertEquals(RedditGraphQlOperations.PostComments, executor.calls[1].operation)
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
            assertEquals("CONFIDENCE", executor.calls[1].variables["sortType"])
            assertEquals(25, executor.calls[1].variables["count"])
            assertEquals(false, executor.calls[1].variables["includePremiumAvatarTreatment"])
            assertEquals(false, executor.calls[1].variables["includeConsistencyToken"])
        }

    @Test
    fun `getPost prefixes a missing post id`() =
        runTest {
            val source =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("/r/Android/comments/1wzvqsb/", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("TOP", executor.calls[1].variables["sortType"])
            assertFalse(executor.calls[1].variables.containsKey("count"))
        }

    @Test
    fun `getPost accepts a bare post id`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("1wzvqsb", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost accepts a prefixed bare id`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("t3_1wzvqsb", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
        }

    @Test
    fun `getPost accepts a gallery permalink`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("/gallery/1wzvqsb/", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost parses an id after a gallery-named subreddit`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("/r/gallery/comments/1wzvqsb/we_are/", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost parses an id after a comments-named subreddit`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("/r/comments/comments/1wzvqsb/", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost ignores a gallery slug after the post route`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("/r/pics/comments/1wzvqsb/gallery/kz9x2", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost ignores a comments slug after the post route`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost("/r/pics/comments/1wzvqsb/comments/kz9x2", null, Sort.TOP)

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost parses an absolute gallery subreddit permalink`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost(
                "https://www.reddit.com/r/gallery/comments/1wzvqsb/we_are/",
                null,
                Sort.TOP,
            )

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost strips query and fragment before parsing the id`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    postCommentsJson(),
                )

            source.getPost(
                "/r/Android/comments/1wzvqsb/we_are/?utm_source=share#frag",
                null,
                Sort.TOP,
            )

            assertEquals(listOf("t3_1wzvqsb"), executor.calls[0].variables["ids"])
            assertEquals("t3_1wzvqsb", executor.calls[1].variables["id"])
        }

    @Test
    fun `getPost rejects an id that is not lowercase alphanumeric`() =
        runTest {
            val source: RedditSource = createSource()

            try {
                source.getPost("/r/Android/comments/NotAnId/", null, Sort.TOP)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Could not parse post id from permalink", exception.message)
            }
            assertTrue(executor.calls.isEmpty())
        }

    @Test
    fun `getPost throws IOException when posts by ids returns no post`() =
        runTest {
            val source: RedditSource = createSource("""{"data":{"postsInfoByIds":[]}}""")

            try {
                source.getPost("/r/Android/comments/1wzvqsb/", null, Sort.TOP)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Post not found", exception.message)
            }
            assertEquals(1, executor.calls.size)
        }

    @Test
    fun `getPost throws IOException when comments post info is null`() =
        runTest {
            val source: RedditSource =
                createSource(
                    postsByIdsJson(postJson("t3_1wzvqsb", "Post title")),
                    """{"data":{"postInfoById":null},"errors":[{"message":"Post unavailable"}]}""",
                )

            try {
                source.getPost("/r/Android/comments/1wzvqsb/", null, Sort.TOP)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Post unavailable", exception.message)
            }
        }

    @Test
    fun `getMoreChildren sends the cursor and returns a cursor aware more child`() =
        runTest {
            val source =
                createSource(
                    postCommentsJson(
                        appliedSort = "NEW",
                        includeMore = true,
                        includeDeleted = true,
                    ),
                )

            val actualMoreChildren = source.getMoreChildren("c1", "t3_1", "NEW")

            val actualThings = actualMoreChildren.json.data.things
            assertEquals(2, actualThings.size)
            val actualComments = actualThings.filterIsInstance<CommentChild>()
            assertEquals(1, actualComments.size)
            val actualComment: CommentChild = actualComments.single()
            assertEquals("t1_a", actualComment.data.name)
            assertEquals("Post title", actualComment.data.linkTitle)
            assertEquals(
                "https://www.reddit.com/comments/1wzvqsb/",
                actualComment.data.linkPermalink,
            )
            assertEquals("r/Android", actualComment.data.subreddit)
            val actualMore = actualThings.filterIsInstance<MoreChild>().single()
            assertEquals("c1", actualMore.data.cursor)
            assertEquals("NEW", actualMore.data.sortType)
            assertEquals(1, executor.calls.size)
            assertEquals(RedditGraphQlOperations.PostComments, executor.calls[0].operation)
            assertEquals("t3_1", executor.calls[0].variables["id"])
            assertEquals("NEW", executor.calls[0].variables["sortType"])
            assertEquals("c1", executor.calls[0].variables["after"])
            assertEquals(false, executor.calls[0].variables["includePremiumAvatarTreatment"])
            assertEquals(false, executor.calls[0].variables["includeConsistencyToken"])
        }

    @Test
    fun `getSubredditInfo sends the required booleans`() =
        runTest {
            val source =
                createSource(
                    """{"data":{"subredditInfoByName":{"__typename":"Subreddit","id":"t5_1","name":"Android","prefixedName":"r/Android","title":"Android","path":"/r/Android/","createdAt":"2010-01-01T00:00:00.000000+0000"}}}""",
                )

            val actualChild = source.getSubredditInfo("Android") as AboutChild

            assertEquals("Android", actualChild.data.displayName)
            assertEquals(RedditGraphQlOperations.SubredditInfoByName, executor.calls.single().operation)
            assertEquals("Android", executor.calls.single().variables["subredditName"])
            assertEquals(false, executor.calls.single().variables["includeRecapFields"])
            assertEquals(false, executor.calls.single().variables["includeDevvitData"])
        }

    @Test
    fun `getSubredditInfo rejects a non-subreddit union variant`() =
        runTest {
            val source: RedditSource =
                createSource(
                    """{"data":{"subredditInfoByName":{"__typename":"UnavailableSubreddit"}}}""",
                )

            try {
                source.getSubredditInfo("missing")
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Subreddit unavailable", exception.message)
            }
        }

    @Test
    fun `searchSubreddit sends community search and returns about children`() =
        runTest {
            val source =
                createSource(
                    """{"data":{"targetableCommunityInfo":[{"subredditInfo":{"name":"Android"},"iconUrl":"https://example.com/icon.png","description":"desc","subscriberCount":1000}]}}""",
                )

            val actualListing =
                source.searchSubreddit("android", Sort.RELEVANCE, TimeSorting.ALL, "cursor")

            val actualChild = actualListing.data.children.single() as AboutChild
            assertEquals("Android", actualChild.data.displayName)
            assertEquals(1000, actualChild.data.subscribers)
            assertEquals(RedditGraphQlOperations.CommunitySearch, executor.calls.single().operation)
            assertEquals("android", executor.calls.single().variables["query"])
            assertNull(actualListing.data.after)
        }

    @Test
    fun `getUserComments hydrates submitted comments with their post context`() =
        runTest {
            val source =
                createSource(
                    submittedCommentsJson("t1_cursor", true),
                    liveCommentsJson("t1_b", "t1_a"),
                )

            val actualListing =
                source.getUserComments("author", Sort.TOP, TimeSorting.YEAR, "after")

            val actualChildren = actualListing.data.children.map { it as CommentChild }
            assertEquals(listOf("t1_a", "t1_b"), actualChildren.map { it.data.name })
            assertEquals("t3_1", actualChildren[0].data.linkId)
            assertEquals("Post title", actualChildren[0].data.linkTitle)
            assertEquals("https://www.reddit.com/comments/1/", actualChildren[0].data.linkPermalink)
            assertEquals("r/Android", actualChildren[0].data.subreddit)
            assertEquals(0, actualChildren[0].data.depth)
            assertNull(actualChildren[0].data.replies)
            assertEquals("t1_cursor", actualListing.data.after)

            assertEquals(RedditGraphQlOperations.SubmittedComments, executor.calls[0].operation)
            assertEquals("author", executor.calls[0].variables["name"])
            assertEquals("TOP", executor.calls[0].variables["sort"])
            assertEquals("YEAR", executor.calls[0].variables["range"])
            assertEquals("after", executor.calls[0].variables["after"])
            assertEquals(RedditGraphQlOperations.LiveCommentsByIds, executor.calls[1].operation)
            assertEquals(listOf("t1_a", "t1_b"), executor.calls[1].variables["ids"])
            assertEquals(false, executor.calls[1].variables["includePremiumAvatarTreatment"])
            assertEquals(true, executor.calls[1].variables["includeCommentsHtmlField"])
        }

    @Test
    fun `getUserComments returns an empty listing when no submitted comments are renderable`() =
        runTest {
            val source: RedditSource =
                createSource(
                    """{"data":{"redditorInfoByName":{"comments":{"pageInfo":{"endCursor":"t1_cursor","hasNextPage":true},"edges":[{"node":{"__typename":"DeletedComment","createdAt":"2026-10-23T12:48:55.300000+0000"}}]}}}}""",
                )

            val actualListing = source.getUserComments("author", Sort.TOP, null, null)

            assertTrue(actualListing.data.children.isEmpty())
            assertEquals("t1_cursor", actualListing.data.after)
            assertEquals(1, executor.calls.size)
        }

    @Test
    fun `getUserComments skips live comments omitted by hydration`() =
        runTest {
            val source: RedditSource =
                createSource(
                    submittedCommentsJson("t1_cursor", false),
                    liveCommentsJson("t1_a"),
                )

            val actualListing = source.getUserComments("author", Sort.TOP, null, null)

            assertEquals(1, actualListing.data.children.size)
            val actualComment: CommentChild =
                actualListing.data.children.single() as CommentChild
            assertEquals("t1_a", actualComment.data.name)
            assertNull(actualListing.data.after)
        }

    @Test
    fun `getUserPosts hydrates the profile feed in feed order`() =
        runTest {
            val source =
                createSource(
                    submittedPostsJson(listOf("t3_b", "t3_a"), "t3_cursor", true),
                    postsByIdsJson(postJson("t3_a", "A"), postJson("t3_b", "B")),
                )

            val actualListing = source.getUserPosts("author", Sort.TOP, TimeSorting.MONTH, null)

            assertEquals(
                listOf("t3_b", "t3_a"),
                actualListing.data.children.map { (it as PostChild).data.name },
            )
            assertEquals("t3_cursor", actualListing.data.after)
            assertEquals(RedditGraphQlOperations.SubmittedPostsFeedSdui, executor.calls[0].operation)
            assertEquals("author", executor.calls[0].variables["username"])
            assertEquals("TOP", executor.calls[0].variables["sort"])
            assertEquals("MONTH", executor.calls[0].variables["time"])
            assertEquals(listOf("t3_b", "t3_a"), executor.calls[1].variables["ids"])
        }

    @Test
    fun `getUserPosts clears the cursor on the terminal profile page`() =
        runTest {
            val source: RedditSource =
                createSource(
                    submittedPostsJson(listOf("t3_a"), "t3_cursor", false),
                    postsByIdsJson(postJson("t3_a", "A")),
                )

            val actualListing = source.getUserPosts("author", Sort.TOP, null, null)

            assertNull(actualListing.data.after)
        }

    @Test
    fun `getUserPosts maps BEST to the post feed sort`() =
        runTest {
            val source: RedditSource =
                createSource(
                    submittedPostsJson(listOf("t3_a"), null, false),
                    postsByIdsJson(postJson("t3_a", "A")),
                )

            source.getUserPosts("author", Sort.BEST, null, null)

            assertEquals("BEST", executor.calls[0].variables["sort"])
        }

    @Test
    fun `getUserPosts maps RISING to the post feed sort`() =
        runTest {
            val source: RedditSource =
                createSource(
                    submittedPostsJson(listOf("t3_a"), null, false),
                    postsByIdsJson(postJson("t3_a", "A")),
                )

            source.getUserPosts("author", Sort.RISING, null, null)

            assertEquals("RISING", executor.calls[0].variables["sort"])
        }

    @Test
    fun `getUserComments omits unsupported profile sorts`() =
        runTest {
            val source: RedditSource =
                createSource(
                    submittedCommentsJson("t1_cursor", true),
                    liveCommentsJson("t1_a", "t1_b"),
                )

            source.getUserComments("author", Sort.BEST, null, null)

            assertFalse(executor.calls[0].variables.containsKey("sort"))
        }

    @Test
    fun `searchInSubreddit scopes the query and maps search posts`() =
        runTest {
            val source = createSource(searchPostsJson(listOf("t3_a"), "t3_cursor", true))

            val actualListing =
                source.searchInSubreddit("Android", "pixel", Sort.NEW, TimeSorting.WEEK, "after")

            assertEquals("t3_a", (actualListing.data.children.single() as PostChild).data.name)
            assertEquals("t3_cursor", actualListing.data.after)
            assertEquals(RedditGraphQlOperations.SearchPosts, executor.calls.single().operation)
            assertEquals("pixel subreddit:Android", executor.calls.single().variables["query"])
            assertEquals("SEARCH", executor.calls.single().variables["productSurface"])
            assertEquals("NEW", executor.calls.single().variables["sort"])
            assertEquals("after", executor.calls.single().variables["afterCursor"])
            assertFalse(executor.calls.single().variables.containsKey("time"))
        }

    @Test
    fun `searchPost omits a null sort and cursor`() =
        runTest {
            val source = createSource(searchPostsJson(listOf("t3_a"), null, false))

            val actualListing = source.searchPost("pixel", null, TimeSorting.WEEK, null)

            assertEquals(1, actualListing.data.children.size)
            assertNull(actualListing.data.after)
            assertEquals(RedditGraphQlOperations.SearchPosts, executor.calls.single().operation)
            assertEquals("pixel", executor.calls.single().variables["query"])
            assertEquals("SEARCH", executor.calls.single().variables["productSurface"])
            assertFalse(executor.calls.single().variables.containsKey("sort"))
            assertFalse(executor.calls.single().variables.containsKey("afterCursor"))
        }

    @Test
    fun `searchPost skips deleted and unavailable post nodes`() =
        runTest {
            val source: RedditSource =
                createSource(
                    searchPostsRawJson(
                        edges =
                            """
                            {"node":{"__typename":"DeletedSubredditPost","id":"t3_deleted"}},
                            {"node":${postJson("t3_valid", "Valid")}},
                            {"node":{"__typename":"UnavailablePost","id":"t3_unavailable"}}
                            """.trimIndent(),
                    ),
                )

            val actualListing = source.searchPost("query", null, null, null)

            assertEquals(1, actualListing.data.children.size)
            val actualPost: PostChild = actualListing.data.children.single() as PostChild
            assertEquals("t3_valid", actualPost.data.name)
        }

    @Test
    fun `searchUser maps search people to about user children`() =
        runTest {
            val source = createSource(searchPeopleJson("author"))

            val actualListing = source.searchUser("author", Sort.TOP, null, null)

            val actualChild = actualListing.data.children.single() as AboutUserChild
            assertEquals("author", actualChild.data.name)
            assertEquals(RedditGraphQlOperations.SearchPeople, executor.calls.single().operation)
            assertEquals("author", executor.calls.single().variables["query"])
            assertEquals("SEARCH", executor.calls.single().variables["productSurface"])
            assertEquals("TOP", executor.calls.single().variables["sort"])
            assertFalse(executor.calls.single().variables.containsKey("afterCursor"))
        }

    @Test
    fun `getUserInfo maps a user profile`() =
        runTest {
            val source =
                createSource(
                    """{"data":{"redditorInfoByName":{"__typename":"Redditor","id":"t2_1","name":"author","prefixedName":"u/author","karma":{"total":1234}}}}""",
                )

            val actualChild = source.getUserInfo("author") as AboutUserChild

            assertEquals("author", actualChild.data.name)
            assertEquals(1234, actualChild.data.totalKarma)
            assertEquals(RedditGraphQlOperations.UserProfileDetails, executor.calls.single().operation)
            assertEquals("author", executor.calls.single().variables["username"])
        }

    @Test
    fun `getUserInfo maps a deleted redditor to a suspended user`() =
        runTest {
            val source: RedditSource =
                createSource(
                    """{"data":{"redditorInfoByName":{"__typename":"DeletedRedditor","id":"t2_deleted"}}}""",
                )

            val actualChild: AboutUserChild = source.getUserInfo("deleted") as AboutUserChild

            assertEquals(true, actualChild.data.isSuspended)
            assertEquals("t2_deleted", actualChild.data.id)
            assertEquals("", actualChild.data.name)
            assertNull(actualChild.data.subreddit)
        }

    @Test
    fun `getUserInfo maps an unavailable redditor to a suspended user`() =
        runTest {
            val source: RedditSource =
                createSource(
                    """{"data":{"redditorInfoByName":{"__typename":"UnavailableRedditor","id":"t2_hidden"}}}""",
                )

            val actualChild: AboutUserChild = source.getUserInfo("hidden") as AboutUserChild

            assertEquals(true, actualChild.data.isSuspended)
            assertEquals("t2_hidden", actualChild.data.id)
            assertEquals("", actualChild.data.name)
        }

    @Test
    fun `null data with errors throws IOException carrying the message`() =
        runTest {
            val source =
                createSource(
                    """{"data":null,"errors":[{"message":"Not found"},{"message":"Try again"}]}""",
                )

            try {
                source.getSubredditInfo("missing")
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Not found; Try again", exception.message)
            }
        }

    @Test
    fun `null data without errors throws IOException`() =
        runTest {
            val source = createSource("""{"data":null}""")

            try {
                source.searchSubreddit("missing", null, null, null)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Empty GraphQL response", exception.message)
            }
        }

    @Test
    fun `searchSubreddit surfaces errors when communities are null`() =
        runTest {
            val source: RedditSource =
                createSource(
                    """{"data":{"targetableCommunityInfo":null},"errors":[{"message":"Not found"}]}""",
                )

            try {
                source.searchSubreddit("missing", null, null, null)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertTrue(exception.message?.contains("Not found") == true)
            }
        }

    @Test
    fun `malformed nested payload throws IOException`() =
        runTest {
            val source: RedditSource = createSource("""{"data":{"subredditInfoByName":[]}}""")

            try {
                source.getSubredditInfo("Android")
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertTrue(exception.message?.contains("Malformed GraphQL response") == true)
            }
        }

    @Test
    fun `getSubreddit throws IOException when errors replace data`() =
        runTest {
            val source =
                createSource(
                    """{"data":null,"errors":[{"message":"Subreddit not found"}]}""",
                )

            try {
                source.getSubreddit("missing", Sort.HOT, null, null)
                fail("Expected IOException")
            } catch (exception: IOException) {
                assertEquals("Subreddit not found", exception.message)
            }
            assertEquals(RedditGraphQlOperations.SubredditFeedSdui, executor.calls.single().operation)
        }

    private fun createSource(vararg responses: String): RedditSource {
        executor = FakeGraphQlExecutor(*responses)
        return RedditSource(executor, moshi, Dispatchers.Unconfined)
    }

    private fun postsByIdsJson(vararg posts: String): String =
        """{"data":{"postsInfoByIds":[${posts.joinToString(",")}]}}"""

    private fun postJson(id: String, title: String): String {
        val postId = id.removePrefix("t3_")
        return """
            {
              "__typename": "SubredditPost",
              "id": "$id",
              "postTitle": "$title",
              "permalink": "/r/Android/comments/$postId/post/",
              "url": "https://www.reddit.com/r/Android/comments/$postId/post/",
              "createdAt": "2026-10-07T15:47:48.255000+0000",
              "subreddit": {"name": "Android", "prefixedName": "r/Android"}
            }
            """.trimIndent()
    }

    private fun subredditFeedJson(ids: List<String>, endCursor: String?): String {
        val edges = ids.joinToString(",") { """{"node":{"groupId":"$it"}}""" }
        return """
            {"data":{"subredditV3":{"elements":{
              "pageInfo":{"endCursor":${endCursor.jsonValue()}},
              "edges":[$edges]
            }}}}
            """.trimIndent()
    }

    private fun popularFeedJson(ids: List<String>, endCursor: String?): String {
        val edges: String = ids.joinToString(",") { """{"node":{"groupId":"$it"}}""" }
        return """
            {"data":{"popularV3":{"elements":{
              "pageInfo":{"endCursor":${endCursor.jsonValue()}},
              "edges":[$edges]
            }}}}
            """.trimIndent()
    }

    private fun submittedPostsJson(
        ids: List<String>,
        endCursor: String?,
        hasNextPage: Boolean,
    ): String {
        val edges = ids.joinToString(",") { """{"node":{"groupId":"$it"}}""" }
        return """
            {"data":{"profileFeed":{"elements":{
              "pageInfo":{"endCursor":${endCursor.jsonValue()},"hasNextPage":$hasNextPage},
              "edges":[$edges]
            }}}}
            """.trimIndent()
    }

    private fun searchPostsJson(
        ids: List<String>,
        endCursor: String?,
        hasNextPage: Boolean,
    ): String {
        val edges = ids.joinToString(",") { """{"node":${postJson(it, "Result")}}""" }
        return """
            {"data":{"search":{"general":{"posts":{
              "pageInfo":{"endCursor":${endCursor.jsonValue()},"hasNextPage":$hasNextPage},
              "edges":[$edges]
            }}}}}
            """.trimIndent()
    }

    private fun searchPostsRawJson(edges: String): String =
        """
        {"data":{"search":{"general":{"posts":{
          "pageInfo":{"endCursor":null,"hasNextPage":false},
          "edges":[$edges]
        }}}}}
        """.trimIndent()

    private fun searchPeopleJson(name: String): String =
        """
        {"data":{"search":{"general":{"authors":{
          "pageInfo":{"endCursor":null,"hasNextPage":false},
          "edges":[{"node":{"id":"t2_1","name":"$name","prefixedName":"u/$name","karma":{"total":10}}}]
        }}}}}
        """.trimIndent()

    private fun submittedCommentsJson(endCursor: String?, hasNextPage: Boolean): String =
        """
        {"data":{"redditorInfoByName":{"comments":{
          "pageInfo":{"endCursor":${endCursor.jsonValue()},"hasNextPage":$hasNextPage},
          "edges":[
            {"node":{"__typename":"Comment","id":"t1_a","createdAt":"2026-10-07T15:47:48.255000+0000","score":5,"content":{"preview":"preview"},"postInfo":{"id":"t3_1","title":"Post title","subreddit":{"prefixedName":"r/Android"}}}},
            {"node":{"__typename":"Comment","id":"t1_b","createdAt":"2026-10-07T15:47:48.255000+0000","score":6,"content":{"preview":"preview"},"postInfo":{"id":"t3_2","title":"Second title"}}},
            {"node":{"__typename":"DeletedComment","createdAt":"2026-10-23T12:48:55.300000+0000","content":{"preview":"deleted"},"postInfo":{"id":"t3_deleted","title":"Deleted"}}}
          ]
        }}}}
        """.trimIndent()

    private fun liveCommentsJson(vararg ids: String): String {
        val comments: List<String> =
            ids.map { id: String ->
                """
                {"__typename":"Comment","id":"$id","createdAt":"2026-10-07T15:47:48.255000+0000","score":5,
                 "content":{"html":"<p>hi</p>","markdown":"hi"},"authorInfo":{"name":"author"}}
                """.trimIndent()
            } + listOf(
                """
                {"__typename":"DeletedComment","createdAt":"2026-10-23T12:48:55.300000+0000",
                 "content":{"preview":"deleted"}}
                """.trimIndent(),
                """
                {"__typename":"Comment","createdAt":"2026-10-23T12:48:55.300000+0000",
                 "content":{"preview":"unidentified"}}
                """.trimIndent(),
            )
        return """{"data":{"commentsByIds":[${comments.joinToString(",")}]}}"""
    }

    private fun postCommentsJson(
        appliedSort: String = "CONFIDENCE",
        includeMore: Boolean = false,
        includeDeleted: Boolean = false,
    ): String {
        val more =
            if (includeMore) {
                """,{"depth":1,"parentId":"t1_a","childCount":2,"more":{"count":2,"cursor":"c1"},"node":null}"""
            } else {
                ""
            }
        val deleted =
            if (includeDeleted) {
                """,{"depth":2,"parentId":"t1_a","childCount":2,"more":null,"node":{"__typename":"DeletedComment","createdAt":"2026-10-23T12:48:55.300000+0000","isInitiallyCollapsed":true,"moderationInfo":null,"removedByCategory":"DELETED"}}"""
            } else {
                ""
            }
        return """
            {"data":{"postInfoById":{
              "id":"t3_1wzvqsb",
              "title":"Post title",
              "subreddit":{"name":"Android","prefixedName":"r/Android"},
              "commentCount":1,
              "commentForest":{
                "appliedSort":"$appliedSort",
                "pageInfo":{"commentCount":1,"hasNextPage":false},
                "trees":[{"depth":0,"parentId":null,"childCount":1,"more":null,"node":{
                  "__typename":"Comment",
                  "id":"t1_a",
                  "createdAt":"2026-10-07T15:47:48.255000+0000",
                  "content":{"html":"<p>hi</p>","markdown":"hi"},
                  "authorInfo":{"name":"author"},
                  "permalink":"/r/Android/comments/1wzvqsb/comment/a/"
                }}$more$deleted]
              }
            }}}
            """.trimIndent()
    }

    private fun String?.jsonValue(): String = if (this == null) "null" else "\"$this\""

    private data class GraphQlCall(
        val operation: GraphQlOperation,
        val variables: Map<String, Any?>,
    )

    private class FakeGraphQlExecutor(private vararg val responses: String) : GraphQlExecutor {
        val calls: MutableList<GraphQlCall> = mutableListOf()
        private var nextResponse: Int = 0

        override suspend fun execute(
            operation: GraphQlOperation,
            variables: Map<String, Any?>,
        ): String {
            calls += GraphQlCall(operation, variables)
            check(nextResponse < responses.size) { "No response for ${operation.name}" }
            return responses[nextResponse++]
        }
    }
}
