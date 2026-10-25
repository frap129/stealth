package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommentForest
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommentTree
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlComment
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMore
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlSubmittedComment
import com.cosmos.unreddit.data.remote.api.reddit.model.Child
import com.cosmos.unreddit.data.remote.api.reddit.model.CommentChild
import com.cosmos.unreddit.data.remote.api.reddit.model.CommentData
import com.cosmos.unreddit.data.remote.api.reddit.model.Data
import com.cosmos.unreddit.data.remote.api.reddit.model.JsonMore
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.ListingData
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChild
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChildren
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreData
import java.util.Locale
import javax.inject.Inject

class GraphQlCommentMapper @Inject constructor() {
    fun toChildren(
        forest: CommentForest,
        postId: String,
        postTitle: String,
        postPermalink: String,
        subreddit: String,
    ): List<Child> {
        val context: CommentMappingContext =
            CommentMappingContext(postId, postTitle, postPermalink, subreddit, forest.appliedSort)
        return buildChildren(forest.trees, 0, -1, -1, null, context).first
    }

    fun toCommentChild(
        comment: GraphQlComment,
        postId: String,
        postTitle: String,
        postPermalink: String,
        subreddit: String,
    ): CommentChild? {
        if (!isRenderable(comment)) return null
        val commentId: String = requireNotNull(comment.id)
        val context: CommentMappingContext =
            CommentMappingContext(postId, postTitle, postPermalink, subreddit, null)
        return CommentChild(toCommentData(comment, commentId, null, 0, context))
    }

    fun toMoreChildren(
        forest: CommentForest,
        postId: String,
        postTitle: String,
        postPermalink: String,
        subreddit: String,
    ): MoreChildren {
        val context: CommentMappingContext =
            CommentMappingContext(postId, postTitle, postPermalink, subreddit, forest.appliedSort)
        val deletedDepths: MutableList<Int> = mutableListOf()
        val visibleCommentIdsByDepth: MutableMap<Int, String> = mutableMapOf()
        val children: MutableList<Child> = mutableListOf()
        for (tree: CommentTree in forest.trees) {
            while (deletedDepths.isNotEmpty() && deletedDepths.last() >= tree.depth) {
                deletedDepths.removeAt(deletedDepths.lastIndex)
            }
            val effectiveDepth: Int = tree.depth - deletedDepths.size
            visibleCommentIdsByDepth.keys
                .filter { depth: Int -> depth >= effectiveDepth }
                .forEach { depth: Int -> visibleCommentIdsByDepth.remove(depth) }
            val comment: GraphQlComment? = tree.node
            if (comment != null && isRenderable(comment)) {
                val commentId: String = requireNotNull(comment.id)
                visibleCommentIdsByDepth[effectiveDepth] = commentId
                children +=
                    CommentChild(toCommentData(comment, commentId, null, effectiveDepth, context))
            } else if (tree.more != null) {
                val parentCommentId: String? =
                    visibleCommentIdsByDepth
                        .filterKeys { depth: Int -> depth < effectiveDepth }
                        .maxByOrNull { entry: Map.Entry<Int, String> -> entry.key }
                        ?.value
                        ?: tree.parentId.takeIf { effectiveDepth > 0 }
                children += MoreChild(tree.toMoreData(parentCommentId, effectiveDepth, context))
            } else {
                deletedDepths.add(tree.depth)
            }
        }
        return MoreChildren(JsonMore(Data(children)))
    }

    private fun buildChildren(
        trees: List<CommentTree>,
        startIndex: Int,
        parentDepth: Int,
        renderDepth: Int,
        parentCommentId: String?,
        context: CommentMappingContext,
    ): Pair<List<Child>, Int> {
        val children: MutableList<Child> = mutableListOf()
        var index: Int = startIndex
        while (index < trees.size && trees[index].depth > parentDepth) {
            val tree: CommentTree = trees[index]
            val comment: GraphQlComment? = tree.node
            when {
                comment != null && isRenderable(comment) -> {
                    val commentId: String = requireNotNull(comment.id)
                    val effectiveDepth: Int = renderDepth + 1
                    val (replies, next) =
                        buildChildren(
                            trees,
                            index + 1,
                            tree.depth,
                            effectiveDepth,
                            commentId,
                            context,
                        )
                    children += CommentChild(
                        toCommentData(
                            comment,
                            commentId,
                            replies.toListing(),
                            effectiveDepth,
                            context,
                        ),
                    )
                    index = next
                }
                tree.more != null -> {
                    children +=
                        MoreChild(tree.toMoreData(parentCommentId, renderDepth + 1, context))
                    index++
                }
                else -> {
                    val (promoted, next) =
                        buildChildren(
                            trees,
                            index + 1,
                            tree.depth,
                            renderDepth,
                            parentCommentId,
                            context,
                        )
                    children += promoted
                    index = next
                }
            }
        }
        return children to index
    }

    private fun toCommentData(
        comment: GraphQlComment,
        commentId: String,
        replies: Listing?,
        depth: Int,
        context: CommentMappingContext,
    ): CommentData = CommentData(
        totalAwards = comment.awardings.sumOf { it.total },
        authorFlairRichText = null,
        linkId = context.postId,
        replies = replies,
        author = comment.authorInfo?.name.orEmpty(),
        score = comment.score,
        awardings = emptyList(),
        bodyHtml = comment.content?.html ?: comment.content?.markdown.orEmpty(),
        edited = comment.editedAt?.let { GraphQlTimestamp.toEpochSeconds(it) } ?: NOT_EDITED,
        isSubmitter = comment.isOP,
        stickied = comment.isStickied,
        scoreHidden = comment.isScoreHidden,
        permalink = comment.permalink.orEmpty(),
        id = commentId,
        name = commentId,
        created = GraphQlTimestamp.toEpochSeconds(comment.createdAt.orEmpty()),
        controversiality = 0,
        flair = comment.authorFlair?.text,
        depth = depth,
        distinguished = comment.distinguishedAs?.lowercase(Locale.US),
        subreddit = context.subreddit,
        linkTitle = context.postTitle,
        linkPermalink = context.postPermalink,
        linkAuthor = null,
    )

    private fun CommentTree.toMoreData(
        parentCommentId: String?,
        effectiveDepth: Int,
        context: CommentMappingContext,
    ): MoreData {
        val more: GraphQlMore = requireNotNull(more)
        return MoreData(
            count = more.count,
            name = parentId ?: MORE_NAME,
            id = more.cursor,
            depth = effectiveDepth,
            parentId = parentCommentId.orEmpty(),
            children = mutableListOf(),
            cursor = more.cursor,
            sortType = context.appliedSort,
        )
    }

    private fun List<Child>.toListing(): Listing? =
        if (isEmpty()) {
            null
        } else {
            Listing(LISTING_KIND, ListingData(null, size, this, null, null))
        }

    private data class CommentMappingContext(
        val postId: String,
        val postTitle: String,
        val postPermalink: String,
        val subreddit: String,
        val appliedSort: String?,
    )

    companion object {
        internal fun isRenderable(comment: GraphQlComment): Boolean =
            comment.typename != DELETED_COMMENT_TYPENAME && comment.id != null

        internal fun isRenderable(comment: GraphQlSubmittedComment): Boolean =
            comment.typename != DELETED_COMMENT_TYPENAME && comment.id != null

        private const val LISTING_KIND: String = "Listing"
        private const val MORE_NAME: String = "more"
        private const val NOT_EDITED: Long = -1L
        private const val DELETED_COMMENT_TYPENAME: String = "DeletedComment"
    }
}
