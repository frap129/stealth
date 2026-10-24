package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PostCommentsResponse(
    val data: PostCommentsData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class PostCommentsData(val postInfoById: PostInfoById)

@JsonClass(generateAdapter = true)
data class PostInfoById(
    val id: String,
    val title: String,
    val subreddit: GraphQlSubredditRef?,
    val commentCount: Int,
    val commentForest: CommentForest,
)

@JsonClass(generateAdapter = true)
data class GraphQlSubredditRef(val name: String?, val prefixedName: String?)

@JsonClass(generateAdapter = true)
data class CommentForest(
    val appliedSort: String? = null,
    val pageInfo: CommentPageInfo,
    val trees: List<CommentTree>,
)

@JsonClass(generateAdapter = true)
data class CommentPageInfo(
    val commentCount: Int?,
    val hasNextPage: Boolean,
    val consistencyToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class CommentTree(
    val depth: Int,
    val parentId: String?,
    val childCount: Int,
    val more: GraphQlMore?,
    val node: GraphQlComment?,
)

@JsonClass(generateAdapter = true)
data class GraphQlMore(
    val count: Int,
    val cursor: String,
    val isTooDeepForCount: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class GraphQlComment(
    val id: String,
    val createdAt: String,
    val editedAt: String? = null,
    val isRemoved: Boolean = false,
    val isLocked: Boolean = false,
    val isStickied: Boolean = false,
    val isScoreHidden: Boolean = false,
    val isOP: Boolean = false,
    val distinguishedAs: String? = null,
    val permalink: String? = null,
    val score: Int = 0,
    val voteState: String? = null,
    val authorInfo: GraphQlAuthorInfo? = null,
    val authorFlair: GraphQlAuthorFlair? = null,
    val content: GraphQlContent? = null,
    val awardings: List<GraphQlAwarding> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GraphQlAuthorInfo(val name: String? = null)

@JsonClass(generateAdapter = true)
data class GraphQlAuthorFlair(val text: String? = null)

@JsonClass(generateAdapter = true)
data class GraphQlContent(
    val html: String? = null,
    val markdown: String? = null,
    val preview: String? = null,
    val richtext: String? = null,
    val typeHint: String? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlAwarding(val total: Int = 0)

@JsonClass(generateAdapter = true)
data class GraphQlError(val message: String, val path: List<Any?>? = null)
