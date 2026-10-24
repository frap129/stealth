package com.cosmos.unreddit.ui.postdetails

import com.cosmos.unreddit.data.model.Comment
import com.cosmos.unreddit.data.model.Comment.CommentEntity
import com.cosmos.unreddit.data.model.Comment.MoreEntity
import com.cosmos.unreddit.data.model.PosterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentHierarchyTest {
    @Test
    fun `nests a child under its parent`() {
        val inputParent: CommentEntity = createComment(name = "t1_parent", depth = 1)
        val inputChild: CommentEntity = createComment(name = "t1_child", depth = 2)
        val actualComments: List<Comment> =
            CommentHierarchy.restore(listOf(inputParent, inputChild), 1)
        assertEquals(1, actualComments.size)
        assertSame(inputParent, actualComments.single())
        assertEquals(1, inputParent.replies.size)
        assertSame(inputChild, inputParent.replies.single())
        assertEquals(1, inputParent.visibleReplyCount)
    }

    @Test
    fun `nests sibling children under their parent`() {
        val inputParent: CommentEntity = createComment(name = "t1_parent", depth = 1)
        val inputChild: CommentEntity = createComment(name = "t1_child", depth = 2)
        val inputSibling: CommentEntity = createComment(name = "t1_sibling", depth = 2)
        val actualComments: List<Comment> =
            CommentHierarchy.restore(listOf(inputParent, inputChild, inputSibling), 1)
        assertEquals(1, actualComments.size)
        assertEquals(2, inputParent.replies.size)
        assertSame(inputChild, inputParent.replies[0])
        assertSame(inputSibling, inputParent.replies[1])
        assertEquals(2, inputParent.visibleReplyCount)
    }

    @Test
    fun `retains a nested more entry`() {
        val inputParent: CommentEntity = createComment(name = "t1_parent", depth = 1)
        val inputChild: CommentEntity = createComment(name = "t1_child", depth = 2)
        val inputMore: MoreEntity = createMore(name = "more", depth = 2)
        val actualComments: List<Comment> =
            CommentHierarchy.restore(listOf(inputParent, inputChild, inputMore), 1)
        assertEquals(1, actualComments.size)
        assertEquals(2, inputParent.replies.size)
        assertSame(inputChild, inputParent.replies[0])
        assertSame(inputMore, inputParent.replies[1])
        assertTrue(inputParent.replies[1] is MoreEntity)
    }

    @Test
    fun `nests children under separate parents`() {
        val inputFirstParent: CommentEntity = createComment(name = "t1_a", depth = 1)
        val inputFirstChild: CommentEntity = createComment(name = "t1_b", depth = 2)
        val inputSecondParent: CommentEntity = createComment(name = "t1_c", depth = 1)
        val inputSecondChild: CommentEntity = createComment(name = "t1_d", depth = 2)
        val actualComments: List<Comment> = CommentHierarchy.restore(
            listOf(inputFirstParent, inputFirstChild, inputSecondParent, inputSecondChild),
            1,
        )
        assertEquals(2, actualComments.size)
        assertSame(inputFirstParent, actualComments[0])
        assertSame(inputSecondParent, actualComments[1])
        assertEquals(1, inputFirstParent.replies.size)
        assertSame(inputFirstChild, inputFirstParent.replies.single())
        assertEquals(1, inputSecondParent.replies.size)
        assertSame(inputSecondChild, inputSecondParent.replies.single())
    }

    @Test
    fun `stops at a lower depth sibling boundary`() {
        val inputParent: CommentEntity = createComment(name = "t1_parent", depth = 1)
        val inputChild: CommentEntity = createComment(name = "t1_child", depth = 2)
        val inputBoundary: CommentEntity = createComment(name = "t1_boundary", depth = 0)
        val actualComments: List<Comment> =
            CommentHierarchy.restore(listOf(inputParent, inputChild, inputBoundary), 1)
        assertEquals(1, actualComments.size)
        assertSame(inputParent, actualComments.single())
        assertSame(inputChild, inputParent.replies.single())
    }

    private fun createComment(name: String, depth: Int): CommentEntity = CommentEntity(
        totalAwards = 0,
        linkId = POST_ID,
        author = "author",
        score = "0",
        bodyHtml = "",
        edited = -1L,
        isSubmitter = false,
        stickied = false,
        scoreHidden = false,
        permalink = "",
        id = name,
        created = 0L,
        controversiality = 0,
        posterType = PosterType.REGULAR,
        linkTitle = null,
        linkPermalink = null,
        linkAuthor = null,
        subreddit = SUBREDDIT,
        name = name,
        depth = depth,
    )

    private fun createMore(name: String, depth: Int): MoreEntity = MoreEntity(
        count = 1,
        more = mutableListOf(),
        id = name,
        parent = "t1_parent",
        name = name,
        depth = depth,
    )

    private companion object {
        private const val POST_ID: String = "t3_1"
        private const val SUBREDDIT: String = "r/Android"
    }
}
