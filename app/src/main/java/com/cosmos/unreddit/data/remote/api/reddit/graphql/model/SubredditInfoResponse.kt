package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubredditInfoResponse(
    val data: SubredditInfoData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class SubredditInfoData(val subredditInfoByName: GraphQlSubredditInfo)

@JsonClass(generateAdapter = true)
data class GraphQlSubredditInfo(
    val id: String,
    val name: String,
    val prefixedName: String,
    val title: String,
    val path: String,
    val createdAt: String,
    val publicDescriptionText: String? = null,
    val description: GraphQlContent? = null,
    val isNsfw: Boolean = false,
    val isQuarantined: Boolean = false,
    val subscribersCount: Double? = null,
    val communityStats: GraphQlCommunityStats? = null,
    val styles: GraphQlSubredditStyles? = null,
    val wiki: GraphQlWiki? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlCommunityStats(val weeklyActiveUsersCount: Int? = null)

@JsonClass(generateAdapter = true)
data class GraphQlSubredditStyles(
    val icon: String? = null,
    val bannerBackgroundImage: String? = null,
    val primaryColor: String? = null,
    val legacyPrimaryColor: String? = null,
    val legacyBannerBackgroundImage: String? = null,
    val mobileBannerImage: String? = null,
    val legacyIcon: GraphQlPersonIcon? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlWiki(val indexPage: GraphQlWikiPage? = null)

@JsonClass(generateAdapter = true)
data class GraphQlWikiPage(val status: String? = null)
