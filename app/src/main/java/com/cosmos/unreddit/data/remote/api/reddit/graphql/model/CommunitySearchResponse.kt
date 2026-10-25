package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CommunitySearchResponse(
    val data: CommunitySearchData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class CommunitySearchData(
    val targetableCommunityInfo: List<GraphQlTargetableCommunity>? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlTargetableCommunity(
    val subredditInfo: GraphQlSubredditRef? = null,
    val iconUrl: String? = null,
    val description: String? = null,
    val subscriberCount: Int? = null,
)
