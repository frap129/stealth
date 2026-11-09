package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CommunityPickerSearchResponse(
    val data: CommunityPickerSearchData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class CommunityPickerSearchData(
    val subredditTypeahead: GraphQlSubredditTypeahead? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlSubredditTypeahead(
    val subreddits: GraphQlSubredditSearchConnection? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlSubredditSearchConnection(
    val edges: List<GraphQlSubredditSearchEdge> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GraphQlSubredditSearchEdge(val node: GraphQlCommunityPickerNode)

@JsonClass(generateAdapter = true)
data class GraphQlCommunityPickerNode(
    val id: String? = null,
    val name: String? = null,
    val prefixedName: String? = null,
    val subscribersCount: Double? = null,
    val isNsfw: Boolean = false,
    val isQuarantined: Boolean = false,
    val publicDescriptionText: String? = null,
    val styles: GraphQlSubredditStyles? = null,
)
