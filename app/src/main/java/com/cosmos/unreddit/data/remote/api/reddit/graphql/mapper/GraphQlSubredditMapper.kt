package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlContent
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlSubredditInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlSubredditStyles
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutData
import javax.inject.Inject

class GraphQlSubredditMapper @Inject constructor() {
    fun toAboutData(info: GraphQlSubredditInfo): AboutData {
        val styles: GraphQlSubredditStyles? = info.styles
        val bannerImage: String? = styles?.bannerBackgroundImage?.takeIf { it.isNotBlank() }
            ?: styles?.legacyBannerBackgroundImage?.takeIf { it.isNotBlank() }
            ?: styles?.mobileBannerImage?.takeIf { it.isNotBlank() }
        val iconImage: String? = styles?.icon?.takeIf { it.isNotBlank() }
            ?: styles?.legacyIcon?.url?.takeIf { it.isNotBlank() }
        return AboutData(
            wikiEnabled = info.wiki?.indexPage?.status != null,
            displayName = info.name.orEmpty(),
            headerImg = bannerImage,
            title = info.title.orEmpty(),
            primaryColor = styles?.primaryColor,
            // GraphQL exposes weekly active users, not the current online count shown by the UI.
            activeUserCount = null,
            iconImg = iconImage,
            subscribers = info.subscribersCount?.toInt(),
            quarantine = info.isQuarantined,
            publicDescriptionHtml = info.publicDescriptionText.escapeHtml(),
            communityIcon = iconImage.orEmpty(),
            bannerBackgroundImage = bannerImage.orEmpty(),
            keyColor = styles?.legacyPrimaryColor,
            bannerBackgroundColor = null,
            over18 = info.isNsfw,
            descriptionHtml = info.description.toDescriptionHtml(),
            url = "https://www.reddit.com${info.path.orEmpty()}",
            created = GraphQlTimestamp.toEpochSeconds(info.createdAt.orEmpty()),
        )
    }

    private fun GraphQlContent?.toDescriptionHtml(): String? {
        val html: String? = this?.html?.takeIf { it.isNotBlank() }
        if (html != null) return html
        return this?.markdown?.takeIf { it.isNotBlank() }?.escapeHtml()
    }

    private fun String?.escapeHtml(): String? =
        this?.replace("&", "&amp;")
            ?.replace("<", "&lt;")
            ?.replace(">", "&gt;")
}
