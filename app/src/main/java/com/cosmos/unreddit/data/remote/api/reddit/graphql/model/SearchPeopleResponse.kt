package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchPeopleResponse(
    val data: SearchPeopleData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class SearchPeopleData(val search: GraphQlSearch)

@JsonClass(generateAdapter = true)
data class GraphQlPersonSearch(
    val pageInfo: GraphQlPageInfo,
    val edges: List<GraphQlPersonEdge>,
)

@JsonClass(generateAdapter = true)
data class GraphQlPersonEdge(val node: GraphQlPersonNode)

@JsonClass(generateAdapter = true)
data class GraphQlPersonNode(
    val id: String,
    val name: String,
    val prefixedName: String,
    @Json(name = "icon_96") val icon: GraphQlMediaSource? = null,
    val karma: GraphQlKarma? = null,
    val profile: GraphQlPersonProfile? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlPersonProfile(
    val isNsfw: Boolean = false,
    val createdAt: String? = null,
    val styles: GraphQlPersonStyles? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlPersonStyles(val legacyIcon: GraphQlPersonIcon? = null)

@JsonClass(generateAdapter = true)
data class GraphQlPersonIcon(val url: String? = null)
