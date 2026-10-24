package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveCommentsByIdsResponse(
    val data: LiveCommentsData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class LiveCommentsData(val commentsByIds: List<GraphQlComment>)
