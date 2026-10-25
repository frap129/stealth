package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubredditFeedResponse(
    val data: SubredditFeedData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class SubredditFeedData(val subredditV3: GraphQlFeedRoot? = null)

@JsonClass(generateAdapter = true)
data class GraphQlFeedRoot(val elements: GraphQlFeedElements? = null)

@JsonClass(generateAdapter = true)
data class GraphQlFeedElements(
    val pageInfo: GraphQlPageInfo,
    val edges: List<GraphQlFeedEdge>,
)

@JsonClass(generateAdapter = true)
data class GraphQlPageInfo(val endCursor: String? = null, val hasNextPage: Boolean = false)

@JsonClass(generateAdapter = true)
data class GraphQlFeedEdge(val node: GraphQlFeedNode? = null)

@JsonClass(generateAdapter = true)
data class GraphQlFeedNode(val groupId: String? = null)
