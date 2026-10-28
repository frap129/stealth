package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlSubredditMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlCommunityStats
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlContent
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPersonIcon
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlSubredditInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlSubredditStyles
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlWiki
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlWikiPage
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubredditInfoResponse
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutData
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphQlSubredditMapperTest {
    @Test
    fun `maps a subreddit info to about data`() {
        val inputInfo: GraphQlSubredditInfo =
            GraphQlSubredditInfo(
                id = "t5_2qh1i",
                name = "AndroidDev",
                prefixedName = "r/AndroidDev",
                title = "Android Development",
                path = "/r/AndroidDev/",
                createdAt = CREATED_AT,
                publicDescriptionText = "Public description",
                description =
                    GraphQlContent(
                        html = "<p>HTML description</p>",
                        markdown = "Markdown description",
                    ),
                isNsfw = true,
                isQuarantined = true,
                subscribersCount = 6_500_000.0,
                communityStats = GraphQlCommunityStats(weeklyActiveUsersCount = 12_345),
                styles =
                    GraphQlSubredditStyles(
                        icon = "https://styles.example.com/icon.png",
                        bannerBackgroundImage = "https://styles.example.com/banner.png",
                        primaryColor = "#ff4500",
                        legacyPrimaryColor = "#ff0000",
                        legacyBannerBackgroundImage = "https://styles.example.com/legacy-banner.png",
                        mobileBannerImage = "https://styles.example.com/mobile-banner.png",
                        legacyIcon = GraphQlPersonIcon("https://styles.example.com/legacy-icon.png"),
                    ),
                wiki = GraphQlWiki(indexPage = GraphQlWikiPage(status = "MODERATOR_ONLY")),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals(true, actualData.wikiEnabled)
        assertEquals("AndroidDev", actualData.displayName)
        assertEquals("https://styles.example.com/banner.png", actualData.headerImg)
        assertEquals("Android Development", actualData.title)
        assertEquals("#ff4500", actualData.primaryColor)
        assertNull(actualData.activeUserCount)
        assertEquals("https://styles.example.com/icon.png", actualData.iconImg)
        assertEquals(6_500_000, actualData.subscribers)
        assertEquals(true, actualData.quarantine)
        assertEquals("Public description", actualData.publicDescriptionHtml)
        assertEquals("https://styles.example.com/icon.png", actualData.communityIcon)
        assertEquals("https://styles.example.com/banner.png", actualData.bannerBackgroundImage)
        assertEquals("#ff0000", actualData.keyColor)
        assertNull(actualData.bannerBackgroundColor)
        assertEquals(true, actualData.over18)
        assertEquals("<p>HTML description</p>", actualData.descriptionHtml)
        assertEquals("https://www.reddit.com/r/AndroidDev/", actualData.url)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualData.created)
    }

    @Test
    fun `falls back to markdown when html is absent`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                description = GraphQlContent(html = null, markdown = "**Markdown**"),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals("**Markdown**", actualData.descriptionHtml)
    }

    @Test
    fun `falls back to markdown when html is blank`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                description = GraphQlContent(html = "   ", markdown = "**Markdown**"),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals("**Markdown**", actualData.descriptionHtml)
    }

    @Test
    fun `returns null when html and markdown are blank`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                description = GraphQlContent(html = "   ", markdown = ""),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertNull(actualData.descriptionHtml)
    }

    @Test
    fun `escapes public description text`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(publicDescriptionText = "R&D <3")
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals("R&amp;D &lt;3", actualData.publicDescriptionHtml)
    }

    @Test
    fun `escapes markdown when it falls back from html`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                description = GraphQlContent(html = null, markdown = "<b>R&D</b> <3"),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals("&lt;b&gt;R&amp;D&lt;/b&gt; &lt;3", actualData.descriptionHtml)
    }

    @Test
    fun `falls back to legacy banner and icon styles`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                styles =
                    GraphQlSubredditStyles(
                        legacyBannerBackgroundImage = "https://styles.example.com/legacy-banner.png",
                        legacyIcon = GraphQlPersonIcon("https://styles.example.com/legacy-icon.png"),
                    ),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals("https://styles.example.com/legacy-banner.png", actualData.headerImg)
        assertEquals("https://styles.example.com/legacy-banner.png", actualData.bannerBackgroundImage)
        assertEquals("https://styles.example.com/legacy-icon.png", actualData.iconImg)
        assertEquals("https://styles.example.com/legacy-icon.png", actualData.communityIcon)
    }

    @Test
    fun `falls back to the mobile banner style`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                styles =
                    GraphQlSubredditStyles(
                        mobileBannerImage = "https://styles.example.com/mobile-banner.png",
                    ),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals("https://styles.example.com/mobile-banner.png", actualData.headerImg)
        assertEquals("https://styles.example.com/mobile-banner.png", actualData.bannerBackgroundImage)
    }

    @Test
    fun `falls back when banner and icon styles are blank`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                styles =
                    GraphQlSubredditStyles(
                        bannerBackgroundImage = "",
                        legacyBannerBackgroundImage = "",
                        mobileBannerImage = "https://styles.example.com/mobile-banner.png",
                        icon = "",
                        legacyIcon = GraphQlPersonIcon("https://styles.example.com/legacy-icon.png"),
                    ),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals("https://styles.example.com/mobile-banner.png", actualData.headerImg)
        assertEquals("https://styles.example.com/mobile-banner.png", actualData.bannerBackgroundImage)
        assertEquals("https://styles.example.com/legacy-icon.png", actualData.iconImg)
        assertEquals("https://styles.example.com/legacy-icon.png", actualData.communityIcon)
    }

    @Test
    fun `maps all blank banner and icon styles to defaults`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(
                styles =
                    GraphQlSubredditStyles(
                        bannerBackgroundImage = "",
                        legacyBannerBackgroundImage = "",
                        mobileBannerImage = "",
                        icon = "",
                        legacyIcon = GraphQlPersonIcon(""),
                    ),
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertNull(actualData.headerImg)
        assertEquals("", actualData.bannerBackgroundImage)
        assertNull(actualData.iconImg)
        assertEquals("", actualData.communityIcon)
    }

    @Test
    fun `maps absent optional subreddit fields to defaults`() {
        val inputInfo: GraphQlSubredditInfo = minimalInfo()
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals(false, actualData.wikiEnabled)
        assertEquals("Android", actualData.displayName)
        assertNull(actualData.headerImg)
        assertEquals("Android", actualData.title)
        assertNull(actualData.primaryColor)
        assertNull(actualData.activeUserCount)
        assertNull(actualData.iconImg)
        assertNull(actualData.subscribers)
        assertEquals(false, actualData.quarantine)
        assertNull(actualData.publicDescriptionHtml)
        assertEquals("", actualData.communityIcon)
        assertEquals("", actualData.bannerBackgroundImage)
        assertNull(actualData.keyColor)
        assertNull(actualData.bannerBackgroundColor)
        assertEquals(false, actualData.over18)
        assertNull(actualData.descriptionHtml)
        assertEquals("https://www.reddit.com/r/Android/", actualData.url)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualData.created)
    }

    @Test
    fun `escapes markdown parsed from a subreddit info response`() {
        val inputJson =
            """{"data":{"subredditInfoByName":{"id":"t5_1","name":"Android","prefixedName":"r/Android","title":"Android","path":"/r/Android/","createdAt":"2026-10-07T15:47:48.255000+0000","description":{"markdown":"R&D <3","richtext":"R&D <3"}}}}"""
        val inputInfo: GraphQlSubredditInfo =
            requireNotNull(
                Moshi.Builder().build()
                    .adapter(SubredditInfoResponse::class.java)
                    .fromJson(inputJson)!!
                    .data?.subredditInfoByName,
            )
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertNull(inputInfo.description!!.html)
        assertEquals("R&amp;D &lt;3", actualData.descriptionHtml)
        assertNull(actualData.publicDescriptionHtml)
    }

    @Test
    fun `parses a zulu timestamp with fractional seconds`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(createdAt = "2026-10-07T15:47:48.2Z")
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualData.created)
    }

    @Test
    fun `parses an ISO offset timestamp`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(createdAt = "2026-10-07T17:47:48.255000+02:00")
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualData.created)
    }

    @Test
    fun `parses a negative offset timestamp`() {
        val inputInfo: GraphQlSubredditInfo =
            minimalInfo(createdAt = "2026-10-07T10:47:48.255000-05:00")
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualData.created)
    }

    @Test
    fun `falls back to epoch zero for a malformed timestamp`() {
        val inputInfo: GraphQlSubredditInfo = minimalInfo(createdAt = "not-a-timestamp")
        val inputMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
        val actualData: AboutData = inputMapper.toAboutData(inputInfo)
        assertEquals(INVALID_EPOCH_SECONDS, actualData.created)
    }

    private fun minimalInfo(
        createdAt: String = CREATED_AT,
        description: GraphQlContent? = null,
        publicDescriptionText: String? = null,
        styles: GraphQlSubredditStyles? = null,
    ): GraphQlSubredditInfo =
        GraphQlSubredditInfo(
            id = "t5_2qh1i",
            name = "Android",
            prefixedName = "r/Android",
            title = "Android",
            path = "/r/Android/",
            createdAt = createdAt,
            publicDescriptionText = publicDescriptionText,
            description = description,
            styles = styles,
        )

    private companion object {
        private const val CREATED_AT: String = "2026-10-07T15:47:48.255000+0000"
        private const val EXPECTED_CREATED_AT_SECONDS: Long = 1791388068L
        private const val INVALID_EPOCH_SECONDS: Long = 0L
    }
}
