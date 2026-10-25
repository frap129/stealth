package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubmittedCommentsResponse(
    val data: SubmittedCommentsData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class SubmittedCommentsData(val redditorInfoByName: GraphQlSubmittedUser? = null)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedUser(val comments: GraphQlSubmittedComments? = null)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedComments(
    val pageInfo: GraphQlPageInfo,
    val edges: List<GraphQlSubmittedEdge>,
)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedEdge(val node: GraphQlSubmittedComment)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedComment(
    @Json(name = "__typename") val typename: String? = null,
    val id: String? = null,
    val createdAt: String? = null,
    val score: Int = 0,
    val content: GraphQlContent? = null,
    val postInfo: GraphQlPostInfo? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlPostInfo(
    val id: String,
    val title: String? = null,
    val subreddit: GraphQlSubredditRef? = null,
)
