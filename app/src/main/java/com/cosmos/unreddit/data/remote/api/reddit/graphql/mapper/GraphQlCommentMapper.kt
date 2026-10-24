package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommentForest
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommentTree
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlComment
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMore
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
        val treesByParent: Map<String?, List<CommentTree>> = forest.trees.groupBy { it.parentId }
        return treesByParent[null].orEmpty().mapNotNull { tree: CommentTree ->
            tree.toChild(treesByParent, context)
        }
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
        val children: List<Child> = forest.trees.mapNotNull { tree: CommentTree ->
            tree.toFlatChild(context)
        }
        return MoreChildren(JsonMore(Data(children)))
    }

    private fun CommentTree.toChild(
        treesByParent: Map<String?, List<CommentTree>>,
        context: CommentMappingContext,
    ): Child? {
        node?.let { comment: GraphQlComment ->
            val children: List<Child> = treesByParent[comment.id].orEmpty().mapNotNull { child: CommentTree ->
                child.toChild(treesByParent, context)
            }
            return CommentChild(toCommentData(comment, children.toListing(), context))
        }
        more?.let {
            return MoreChild(toMoreData(context))
        }
        return null
    }

    private fun CommentTree.toFlatChild(context: CommentMappingContext): Child? {
        node?.let { comment: GraphQlComment ->
            return CommentChild(toCommentData(comment, null, context))
        }
        more?.let {
            return MoreChild(toMoreData(context))
        }
        return null
    }

    private fun CommentTree.toCommentData(
        comment: GraphQlComment,
        replies: Listing?,
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
        id = comment.id,
        name = comment.id,
        created = GraphQlTimestamp.toEpochSeconds(comment.createdAt),
        controversiality = 0,
        flair = comment.authorFlair?.text,
        depth = depth,
        distinguished = comment.distinguishedAs,
        subreddit = context.subreddit,
        linkTitle = context.postTitle,
        linkPermalink = context.postPermalink,
        linkAuthor = null,
    )

    private fun CommentTree.toMoreData(context: CommentMappingContext): MoreData {
        val more: GraphQlMore = requireNotNull(more)
        return MoreData(
            count = more.count,
            name = parentId ?: MORE_NAME,
            id = more.cursor,
            depth = depth,
            parentId = parentId.orEmpty(),
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

    private companion object {
        private const val LISTING_KIND: String = "Listing"
        private const val MORE_NAME: String = "more"
        private const val NOT_EDITED: Long = -1L
    }
}
