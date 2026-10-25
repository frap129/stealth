package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    val data: UserProfileData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class UserProfileData(val redditorInfoByName: GraphQlUserProfile? = null)

@JsonClass(generateAdapter = true)
data class GraphQlUserProfile(
    @Json(name = "__typename") val typename: String? = null,
    val id: String? = null,
    val name: String? = null,
    val prefixedName: String? = null,
    val icon: GraphQlMediaSource? = null,
    val snoovatarIcon: GraphQlMediaSource? = null,
    val isEmployee: Boolean = false,
    val karma: GraphQlKarma? = null,
    val profileInfo: GraphQlProfileInfo? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlKarma(val total: Int = 0)

@JsonClass(generateAdapter = true)
data class GraphQlProfileInfo(
    val createdAt: String? = null,
    val publicDescriptionText: String? = null,
    val title: String? = null,
    val isNsfw: Boolean = false,
)
