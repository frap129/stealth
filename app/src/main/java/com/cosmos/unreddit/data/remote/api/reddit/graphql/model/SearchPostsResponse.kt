package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchPostsResponse(
    val data: SearchPostsData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class SearchPostsData(val search: GraphQlSearch)

@JsonClass(generateAdapter = true)
data class GraphQlSearch(val general: GraphQlGeneralSearch)

@JsonClass(generateAdapter = true)
data class GraphQlGeneralSearch(
    val posts: GraphQlPostSearch? = null,
    val authors: GraphQlPersonSearch? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlPostSearch(
    val pageInfo: GraphQlPageInfo,
    val edges: List<GraphQlPostEdge>,
)

@JsonClass(generateAdapter = true)
data class GraphQlPostEdge(val node: GraphQlPostNode)
