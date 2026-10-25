package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PopularFeedResponse(
    val data: PopularFeedData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class PopularFeedData(val popularV3: GraphQlFeedRoot? = null)
