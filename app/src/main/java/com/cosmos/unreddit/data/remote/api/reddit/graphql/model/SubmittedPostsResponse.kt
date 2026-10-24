package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubmittedPostsResponse(
    val data: SubmittedPostsData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class SubmittedPostsData(val profileFeed: GraphQlFeedRoot)
