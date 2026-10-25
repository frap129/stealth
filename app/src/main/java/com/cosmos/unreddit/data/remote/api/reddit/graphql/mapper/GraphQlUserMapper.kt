package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPersonNode
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPersonProfile
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlProfileInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlUserProfile
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutUserData
import com.cosmos.unreddit.data.remote.api.reddit.model.Subreddit
import javax.inject.Inject

class GraphQlUserMapper @Inject constructor() {
    fun toAboutUserData(
        user: GraphQlUserProfile,
        isSuspended: Boolean = false,
    ): AboutUserData {
        val profileInfo: GraphQlProfileInfo? = user.profileInfo
        val iconUrl: String? = user.icon?.url?.takeIf { it.isNotBlank() }
        val name: String = user.name.orEmpty()
        val prefixedName: String = user.prefixedName.orEmpty()
        return buildAboutUserData(
            id = user.id.orEmpty(),
            name = name,
            iconImg = iconUrl,
            isSuspended = isSuspended,
            isEmployee = user.isEmployee,
            totalKarma = user.karma?.total ?: UNKNOWN_KARMA,
            createdAt = parseCreatedAt(profileInfo?.createdAt),
            snoovatarImg = user.snoovatarIcon?.url,
            subreddit = profileInfo?.let {
                buildSubreddit(
                    name = name,
                    prefixedName = prefixedName,
                    iconImg = iconUrl,
                    title = it.title.orEmpty(),
                    over18 = it.isNsfw,
                    description = it.publicDescriptionText.orEmpty(),
                )
            },
        )
    }

    fun toAboutUserData(user: GraphQlPersonNode): AboutUserData {
        val profile: GraphQlPersonProfile? = user.profile
        val iconUrl: String? = user.icon?.url?.takeIf { it.isNotBlank() }
            ?: profile?.styles?.legacyIcon?.url?.takeIf { it.isNotBlank() }
        return buildAboutUserData(
            id = user.id,
            name = user.name,
            iconImg = iconUrl,
            isSuspended = false,
            isEmployee = false,
            totalKarma = user.karma?.total ?: UNKNOWN_KARMA,
            createdAt = parseCreatedAt(profile?.createdAt),
            snoovatarImg = null,
            subreddit = profile?.let {
                buildSubreddit(
                    name = user.name,
                    prefixedName = user.prefixedName,
                    iconImg = iconUrl,
                    title = "",
                    over18 = it.isNsfw,
                    description = "",
                )
            },
        )
    }

    private fun buildAboutUserData(
        id: String,
        name: String,
        iconImg: String?,
        isSuspended: Boolean,
        isEmployee: Boolean,
        totalKarma: Int,
        createdAt: Long,
        snoovatarImg: String?,
        subreddit: Subreddit?,
    ): AboutUserData =
        AboutUserData(
            isSuspended = isSuspended,
            isEmployee = isEmployee,
            subreddit = subreddit,
            id = id,
            iconImg = iconImg,
            linkKarma = 0,
            totalKarma = totalKarma,
            name = name,
            created = createdAt,
            snoovatarImg = snoovatarImg,
            commentKarma = 0,
        )

    private fun buildSubreddit(
        name: String,
        prefixedName: String,
        iconImg: String?,
        title: String,
        over18: Boolean,
        description: String,
    ): Subreddit =
        Subreddit(
            bannerImg = null,
            communityIcon = iconImg,
            iconColor = "",
            headerImg = null,
            title = title,
            over18 = over18,
            primaryColor = "",
            iconImg = iconImg.orEmpty(),
            description = description,
            subscribers = 0,
            displayNamePrefixed = prefixedName,
            keyColor = "",
            name = name,
            isDefaultBanner = false,
            url = "https://www.reddit.com/user/$name",
            publicDescription = description,
        )

    private fun parseCreatedAt(createdAt: String?): Long =
        createdAt?.let { GraphQlTimestamp.parseOrNull(it) } ?: UNKNOWN_CREATED

    private companion object {
        private const val UNKNOWN_KARMA: Int = -1
        private const val UNKNOWN_CREATED: Long = -1L
    }
}
