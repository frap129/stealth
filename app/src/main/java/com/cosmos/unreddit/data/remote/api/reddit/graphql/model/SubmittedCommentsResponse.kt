package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubmittedCommentsResponse(
    val data: SubmittedCommentsData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class SubmittedCommentsData(val redditorInfoByName: GraphQlSubmittedUser)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedUser(val comments: GraphQlSubmittedComments)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedComments(
    val pageInfo: GraphQlPageInfo,
    val edges: List<GraphQlSubmittedEdge>,
)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedEdge(val node: GraphQlSubmittedComment)

@JsonClass(generateAdapter = true)
data class GraphQlSubmittedComment(
    val id: String,
    val createdAt: String,
    val score: Int = 0,
    val content: GraphQlContent? = null,
    val postInfo: GraphQlPostInfo? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlPostInfo(val id: String, val title: String? = null)
