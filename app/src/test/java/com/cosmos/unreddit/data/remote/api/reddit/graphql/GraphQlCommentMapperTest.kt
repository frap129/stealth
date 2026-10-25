package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.cosmos.unreddit.data.model.PosterType
import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlCommentMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommentForest
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommentPageInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommentTree
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlAuthorFlair
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlAuthorInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlAwarding
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlComment
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlContent
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMore
import com.cosmos.unreddit.data.remote.api.reddit.model.CommentChild
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphQlCommentMapperTest {
    @Test
    fun `maps a more entry as a reply of its parent`() {
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_a", depth = 0, parentId = null),
                        createMoreTree(parentId = "t1_a", depth = 1, cursor = "c1", count = 3),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(1, actualChildren.size)
        val actualParent: CommentChild = actualChildren[0] as CommentChild
        val actualReplies = requireNotNull(actualParent.data.replies)
        assertEquals(1, actualReplies.data.children.size)
        val actualMore: MoreChild = actualReplies.data.children[0] as MoreChild
        assertEquals("c1", actualMore.data.cursor)
        assertEquals(3, actualMore.data.count)
        assertEquals("t1_a", actualMore.data.parentId)
        assertEquals(1, actualMore.data.depth)
        assertTrue(actualMore.data.children.isEmpty())
    }

    @Test
    fun `propagates the applied sort to more entries`() {
        val inputForest: CommentForest =
            createForest(
                appliedSort = "NEW",
                trees =
                    listOf(
                        createCommentTree(id = "t1_a", depth = 0, parentId = null),
                        createMoreTree(parentId = "t1_a", depth = 1, cursor = "c1", count = 3),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        val actualParent: CommentChild = actualChildren[0] as CommentChild
        val actualMore: MoreChild =
            requireNotNull(actualParent.data.replies).data.children[0] as MoreChild
        assertEquals("NEW", actualMore.data.sortType)
    }

    @Test
    fun `maps more children including the returned more entry`() {
        val inputForest: CommentForest =
            createForest(
                appliedSort = "NEW",
                trees =
                    listOf(
                        createCommentTree(id = "t1_b", depth = 1, parentId = "t1_a"),
                        createMoreTree(parentId = "t1_a", depth = 1, cursor = "c2", count = 2),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toMoreChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(2, actualChildren.json.data.things.size)
        val actualComment: CommentChild = actualChildren.json.data.things[0] as CommentChild
        assertEquals("t1_b", actualComment.data.name)
        assertNull(actualComment.data.replies)
        assertEquals(POST_ID, actualComment.data.linkId)
        assertEquals(POST_TITLE, actualComment.data.linkTitle)
        assertEquals(POST_PERMALINK, actualComment.data.linkPermalink)
        assertEquals(SUBREDDIT, actualComment.data.subreddit)
        val actualMore: MoreChild = actualChildren.json.data.things[1] as MoreChild
        assertEquals("c2", actualMore.data.id)
        assertEquals("c2", actualMore.data.cursor)
        assertEquals("t1_a", actualMore.data.parentId)
        assertEquals(1, actualMore.data.depth)
        assertEquals("NEW", actualMore.data.sortType)
        assertTrue(actualMore.data.children.isEmpty())
    }

    @Test
    fun `maps multiple reply levels`() {
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_a", depth = 0, parentId = null),
                        createCommentTree(id = "t1_b", depth = 1, parentId = "t1_a"),
                        createCommentTree(id = "t1_c", depth = 2, parentId = "t1_b"),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(1, actualChildren.size)
        val actualParent: CommentChild = actualChildren[0] as CommentChild
        val actualReplies = requireNotNull(actualParent.data.replies)
        assertEquals("Listing", actualReplies.kind)
        val actualReply: CommentChild = actualReplies.data.children.single() as CommentChild
        val actualNestedReply: CommentChild =
            requireNotNull(actualReply.data.replies).data.children.single() as CommentChild
        assertEquals("t1_a", actualParent.data.name)
        assertEquals(0, actualParent.data.depth)
        assertEquals("t1_b", actualReply.data.name)
        assertEquals(1, actualReply.data.depth)
        assertEquals("t1_c", actualNestedReply.data.name)
        assertEquals(2, actualNestedReply.data.depth)
        assertNull(actualNestedReply.data.replies)
    }

    @Test
    fun `maps childless comments without replies`() {
        val inputForest: CommentForest =
            createForest(
                trees = listOf(createCommentTree(id = "t1_a", depth = 0, parentId = null)),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        val actualComment: CommentChild = actualChildren.single() as CommentChild
        assertNull(actualComment.data.replies)
    }

    @Test
    fun `skips trees without a comment or more entry`() {
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        CommentTree(
                            depth = 0,
                            parentId = null,
                            childCount = 0,
                            more = null,
                            node = null,
                        ),
                        createCommentTree(id = "t1_a", depth = 0, parentId = null),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        val actualComment: CommentChild = actualChildren.single() as CommentChild
        assertEquals("t1_a", actualComment.data.name)
    }

    @Test
    fun `maps an empty forest to no children`() {
        val inputForest: CommentForest = createForest(trees = emptyList())
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertTrue(actualChildren.isEmpty())
    }

    @Test
    fun `maps a root level more entry`() {
        val inputForest: CommentForest =
            createForest(
                appliedSort = "TOP",
                trees =
                    listOf(
                        createMoreTree(parentId = null, depth = 0, cursor = "c-root", count = 5),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(1, actualChildren.size)
        val actualMore: MoreChild = actualChildren[0] as MoreChild
        assertEquals("more", actualMore.data.name)
        assertEquals("", actualMore.data.parentId)
        assertEquals("c-root", actualMore.data.id)
        assertEquals("c-root", actualMore.data.cursor)
        assertEquals(0, actualMore.data.depth)
        assertEquals("TOP", actualMore.data.sortType)
        assertTrue(actualMore.data.children.isEmpty())
    }

    @Test
    fun `maps comment data fields`() {
        val inputComment: GraphQlComment =
            GraphQlComment(
                id = "t1_a",
                createdAt = CREATED_AT,
                editedAt = EDITED_AT,
                permalink = "/r/Android/comments/1/comment/a/",
                score = 42,
                isOP = true,
                isStickied = true,
                isScoreHidden = true,
                distinguishedAs = "MODERATOR",
                authorInfo = GraphQlAuthorInfo(name = "author"),
                authorFlair = GraphQlAuthorFlair(text = "Flair"),
                content = GraphQlContent(markdown = "markdown body"),
                awardings = listOf(GraphQlAwarding(total = 2), GraphQlAwarding(total = 3)),
            )
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        CommentTree(
                            depth = 0,
                            parentId = null,
                            childCount = 0,
                            more = null,
                            node = inputComment,
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        val actualComment = (actualChildren[0] as CommentChild).data
        assertEquals(5, actualComment.totalAwards)
        assertEquals(POST_ID, actualComment.linkId)
        assertEquals("author", actualComment.author)
        assertEquals(42, actualComment.score)
        assertTrue(actualComment.awardings.isEmpty())
        assertEquals("markdown body", actualComment.bodyHtml)
        assertEquals(EXPECTED_EDITED_AT_SECONDS, actualComment.edited)
        assertTrue(actualComment.isSubmitter)
        assertTrue(actualComment.stickied)
        assertTrue(actualComment.scoreHidden)
        assertEquals("/r/Android/comments/1/comment/a/", actualComment.permalink)
        assertEquals("t1_a", actualComment.id)
        assertEquals("t1_a", actualComment.name)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualComment.created)
        assertEquals(0, actualComment.controversiality)
        assertEquals("Flair", actualComment.flair)
        assertEquals(0, actualComment.depth)
        assertEquals("moderator", actualComment.distinguished)
        assertEquals(
            PosterType.MODERATOR,
            PosterType.fromDistinguished(actualComment.distinguished),
        )
        assertEquals(SUBREDDIT, actualComment.subreddit)
        assertEquals(POST_TITLE, actualComment.linkTitle)
        assertEquals(POST_PERMALINK, actualComment.linkPermalink)
        assertNull(actualComment.linkAuthor)
        assertNull(actualComment.authorFlairRichText)
    }

    @Test
    fun `maps a missing edited timestamp to not edited`() {
        val inputForest: CommentForest =
            createForest(
                trees = listOf(createCommentTree(id = "t1_a", depth = 0, parentId = null)),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        val actualComment = (actualChildren[0] as CommentChild).data
        assertEquals(NOT_EDITED, actualComment.edited)
    }

    @Test
    fun `maps a live comment directly to a comment child`() {
        val inputComment: GraphQlComment =
            GraphQlComment(
                typename = "Comment",
                id = "t1_a",
                createdAt = CREATED_AT,
                content = GraphQlContent(html = "<p>hi</p>", markdown = "hi"),
                authorInfo = GraphQlAuthorInfo(name = "author"),
                score = 5,
                permalink = "/r/Android/comments/1/comment/a/",
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChild: CommentChild = requireNotNull(
            inputMapper.toCommentChild(
                inputComment,
                POST_ID,
                POST_TITLE,
                POST_PERMALINK,
                SUBREDDIT,
            ),
        )
        assertEquals("t1_a", actualChild.data.id)
        assertEquals("t1_a", actualChild.data.name)
        assertEquals(POST_ID, actualChild.data.linkId)
        assertEquals(POST_TITLE, actualChild.data.linkTitle)
        assertEquals(POST_PERMALINK, actualChild.data.linkPermalink)
        assertEquals(SUBREDDIT, actualChild.data.subreddit)
        assertEquals(0, actualChild.data.depth)
        assertNull(actualChild.data.replies)
        assertEquals("<p>hi</p>", actualChild.data.bodyHtml)
        assertEquals("author", actualChild.data.author)
        assertEquals(5, actualChild.data.score)
    }

    @Test
    fun `skips deleted and unidentified comment nodes`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(
                typename = "DeletedComment",
                createdAt = CREATED_AT,
            )
        val inputUnidentifiedComment: GraphQlComment =
            GraphQlComment(
                typename = "Comment",
                createdAt = CREATED_AT,
            )
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_a", depth = 0, parentId = null),
                        CommentTree(
                            depth = 0,
                            parentId = null,
                            childCount = 0,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        CommentTree(
                            depth = 0,
                            parentId = null,
                            childCount = 0,
                            more = null,
                            node = inputUnidentifiedComment,
                        ),
                        CommentTree(
                            depth = 0,
                            parentId = null,
                            childCount = 0,
                            more = GraphQlMore(count = 1, cursor = "c-deleted"),
                            node = inputDeletedComment,
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(2, actualChildren.size)
        assertEquals("t1_a", (actualChildren[0] as CommentChild).data.name)
        assertEquals("c-deleted", (actualChildren[1] as MoreChild).data.cursor)
        val actualMoreChildren = inputMapper.toMoreChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(2, actualMoreChildren.json.data.things.size)
        assertNull(
            inputMapper.toCommentChild(
                inputDeletedComment,
                POST_ID,
                POST_TITLE,
                POST_PERMALINK,
                SUBREDDIT,
            ),
        )
        assertNull(
            inputMapper.toCommentChild(
                inputUnidentifiedComment,
                POST_ID,
                POST_TITLE,
                POST_PERMALINK,
                SUBREDDIT,
            ),
        )
    }

    @Test
    fun `promotes replies under a deleted root comment`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(typename = "DeletedComment", createdAt = CREATED_AT)
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        CommentTree(
                            depth = 0,
                            parentId = null,
                            childCount = 1,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        createCommentTree(
                            id = "t1_reply",
                            depth = 1,
                            parentId = "t1_deleted",
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(1, actualChildren.size)
        val actualReply: CommentChild = actualChildren.single() as CommentChild
        assertEquals("t1_reply", actualReply.data.name)
        assertEquals(0, actualReply.data.depth)
    }

    @Test
    fun `promotes grandchildren through a deleted comment`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(typename = "DeletedComment", createdAt = CREATED_AT)
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_root", depth = 0, parentId = null),
                        CommentTree(
                            depth = 1,
                            parentId = "t1_root",
                            childCount = 1,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        createCommentTree(
                            id = "t1_grandchild",
                            depth = 2,
                            parentId = "t1_deleted",
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(1, actualChildren.size)
        val actualRoot: CommentChild = actualChildren.single() as CommentChild
        val actualReplies = requireNotNull(actualRoot.data.replies)
        val actualGrandchild: CommentChild =
            actualReplies.data.children.single() as CommentChild
        assertEquals("t1_grandchild", actualGrandchild.data.name)
        assertEquals(1, actualGrandchild.data.depth)
    }

    @Test
    fun `promotes a more entry under a deleted comment`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(typename = "DeletedComment", createdAt = CREATED_AT)
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        CommentTree(
                            depth = 0,
                            parentId = null,
                            childCount = 1,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        createMoreTree(
                            parentId = "t1_deleted",
                            depth = 1,
                            cursor = "c1",
                            count = 2,
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(1, actualChildren.size)
        val actualMore: MoreChild = actualChildren.single() as MoreChild
        assertEquals("c1", actualMore.data.cursor)
        assertEquals(0, actualMore.data.depth)
        assertEquals("", actualMore.data.parentId)
    }

    @Test
    fun `promoted more entries use the nearest renderable parent`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(typename = "DeletedComment", createdAt = CREATED_AT)
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_root", depth = 0, parentId = null),
                        CommentTree(
                            depth = 1,
                            parentId = "t1_root",
                            childCount = 1,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        createMoreTree(
                            parentId = "t1_deleted",
                            depth = 2,
                            cursor = "c1",
                            count = 2,
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(1, actualChildren.size)
        val actualRoot: CommentChild = actualChildren.single() as CommentChild
        val actualReplies = requireNotNull(actualRoot.data.replies)
        val actualMore: MoreChild = actualReplies.data.children.single() as MoreChild
        assertEquals("t1_root", actualMore.data.parentId)
        assertEquals("c1", actualMore.data.cursor)
        assertEquals(1, actualMore.data.depth)
    }

    @Test
    fun `flat more entries use the nearest renderable parent`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(typename = "DeletedComment", createdAt = CREATED_AT)
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_root", depth = 0, parentId = null),
                        CommentTree(
                            depth = 1,
                            parentId = "t1_root",
                            childCount = 1,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        createMoreTree(
                            parentId = "t1_deleted",
                            depth = 2,
                            cursor = "c1",
                            count = 2,
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualMoreChildren = inputMapper.toMoreChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        assertEquals(2, actualMoreChildren.json.data.things.size)
        val actualMore: MoreChild =
            actualMoreChildren.json.data.things.filterIsInstance<MoreChild>().single()
        assertEquals("t1_root", actualMore.data.parentId)
        assertEquals("c1", actualMore.data.cursor)
        assertEquals(1, actualMore.data.depth)
    }

    @Test
    fun `promotes flat descendants to effective depths`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(typename = "DeletedComment", createdAt = CREATED_AT)
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_a", depth = 2, parentId = null),
                        CommentTree(
                            depth = 2,
                            parentId = null,
                            childCount = 1,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        createCommentTree(
                            id = "t1_r",
                            depth = 3,
                            parentId = "t1_deleted",
                        ),
                        createCommentTree(id = "t1_s", depth = 2, parentId = null),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toMoreChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        val actualComments: List<CommentChild> =
            actualChildren.json.data.things.filterIsInstance<CommentChild>()
        assertEquals(listOf("t1_a", "t1_r", "t1_s"), actualComments.map { it.data.name })
        assertEquals(listOf(2, 2, 2), actualComments.map { it.data.depth })
    }

    @Test
    fun `promotes a flat more under a deleted comment to the visible depth`() {
        val inputDeletedComment: GraphQlComment =
            GraphQlComment(typename = "DeletedComment", createdAt = CREATED_AT)
        val inputForest: CommentForest =
            createForest(
                trees =
                    listOf(
                        createCommentTree(id = "t1_root", depth = 1, parentId = null),
                        CommentTree(
                            depth = 2,
                            parentId = "t1_root",
                            childCount = 1,
                            more = null,
                            node = inputDeletedComment,
                        ),
                        createMoreTree(
                            parentId = "t1_deleted",
                            depth = 3,
                            cursor = "c1",
                            count = 2,
                        ),
                    ),
            )
        val inputMapper: GraphQlCommentMapper = GraphQlCommentMapper()
        val actualChildren = inputMapper.toMoreChildren(
            inputForest,
            POST_ID,
            POST_TITLE,
            POST_PERMALINK,
            SUBREDDIT,
        )
        val actualMore: MoreChild =
            actualChildren.json.data.things.filterIsInstance<MoreChild>().single()
        assertEquals("t1_root", actualMore.data.parentId)
        assertEquals("c1", actualMore.data.cursor)
        assertEquals(2, actualMore.data.depth)
    }

    private fun createForest(
        appliedSort: String? = "CONFIDENCE",
        trees: List<CommentTree>,
    ): CommentForest = CommentForest(
        appliedSort = appliedSort,
        pageInfo = CommentPageInfo(commentCount = trees.size, hasNextPage = false),
        trees = trees,
    )

    private fun createCommentTree(
        id: String,
        depth: Int,
        parentId: String?,
    ): CommentTree = CommentTree(
        depth = depth,
        parentId = parentId,
        childCount = 0,
        more = null,
        node = GraphQlComment(typename = "Comment", id = id, createdAt = CREATED_AT),
    )

    private fun createMoreTree(
        parentId: String?,
        depth: Int,
        cursor: String,
        count: Int,
    ): CommentTree = CommentTree(
        depth = depth,
        parentId = parentId,
        childCount = 0,
        more = GraphQlMore(count = count, cursor = cursor),
        node = null,
    )

    private companion object {
        private const val POST_ID: String = "t3_1"
        private const val POST_TITLE: String = "title"
        private const val POST_PERMALINK: String = "/r/Android/comments/1/"
        private const val SUBREDDIT: String = "r/Android"
        private const val CREATED_AT: String = "2026-10-07T15:47:48.255000+0000"
        private const val EDITED_AT: String = "2026-10-08T15:47:48Z"
        private const val EXPECTED_CREATED_AT_SECONDS: Long = 1791388068L
        private const val EXPECTED_EDITED_AT_SECONDS: Long = 1791474468L
        private const val NOT_EDITED: Long = -1L
    }
}
