package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlUserMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlKarma
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMediaSource
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPersonIcon
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPersonNode
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPersonProfile
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPersonStyles
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlProfileInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlUserProfile
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SearchPeopleResponse
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutUserData
import com.cosmos.unreddit.data.remote.api.reddit.model.Subreddit
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphQlUserMapperTest {
    @Test
    fun `maps a user profile to about user data`() {
        val inputProfile: GraphQlUserProfile =
            GraphQlUserProfile(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                icon = GraphQlMediaSource("https://example.com/icon.png", 256, 256),
                snoovatarIcon = GraphQlMediaSource("https://example.com/snoo.png"),
                isEmployee = true,
                karma = GraphQlKarma(total = 1_234),
                profileInfo =
                    GraphQlProfileInfo(
                        createdAt = CREATED_AT,
                        publicDescriptionText = "Profile bio",
                        title = "Profile title",
                        isNsfw = true,
                    ),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputProfile)
        val actualSubreddit: Subreddit = requireNotNull(actualUser.subreddit)
        assertEquals(false, actualUser.isSuspended)
        assertEquals(true, actualUser.isEmployee)
        assertEquals("t2_1", actualUser.id)
        assertEquals("https://example.com/icon.png", actualUser.iconImg)
        assertEquals(0, actualUser.linkKarma)
        assertEquals(1_234, actualUser.totalKarma)
        assertEquals("author", actualUser.name)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualUser.created)
        assertEquals("https://example.com/snoo.png", actualUser.snoovatarImg)
        assertEquals(0, actualUser.commentKarma)
        assertEquals("Profile title", actualSubreddit.title)
        assertEquals(true, actualSubreddit.over18)
        assertEquals("Profile bio", actualSubreddit.publicDescription)
        assertEquals("Profile bio", actualSubreddit.description)
        assertEquals("https://www.reddit.com/user/author", actualSubreddit.url)
        assertEquals("u/author", actualSubreddit.displayNamePrefixed)
        assertEquals("author", actualSubreddit.name)
        assertEquals("https://example.com/icon.png", actualSubreddit.iconImg)
        assertEquals("https://example.com/icon.png", actualSubreddit.communityIcon)
    }

    @Test
    fun `maps a suspended user profile with minimal identity`() {
        val inputProfile: GraphQlUserProfile =
            GraphQlUserProfile(
                typename = "DeletedRedditor",
                id = "t2_deleted",
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData =
            inputMapper.toAboutUserData(inputProfile, isSuspended = true)
        assertEquals("t2_deleted", actualUser.id)
        assertEquals("", actualUser.name)
        assertEquals(true, actualUser.isSuspended)
        assertNull(actualUser.subreddit)
    }

    @Test
    fun `maps absent optional user profile fields to defaults`() {
        val inputProfile: GraphQlUserProfile =
            GraphQlUserProfile(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputProfile)
        assertEquals(false, actualUser.isEmployee)
        assertNull(actualUser.subreddit)
        assertNull(actualUser.iconImg)
        assertEquals(-1, actualUser.totalKarma)
        assertEquals(-1L, actualUser.created)
        assertNull(actualUser.snoovatarImg)
    }

    @Test
    fun `maps a person node to about user data`() {
        val inputNode: GraphQlPersonNode =
            GraphQlPersonNode(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                icon = GraphQlMediaSource("https://example.com/icon.png"),
                karma = GraphQlKarma(total = 10),
                profile =
                    GraphQlPersonProfile(
                        createdAt = CREATED_AT,
                        isNsfw = false,
                        styles =
                            GraphQlPersonStyles(
                                legacyIcon = GraphQlPersonIcon("https://example.com/legacy-icon.png"),
                            ),
                    ),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        val actualSubreddit: Subreddit = requireNotNull(actualUser.subreddit)
        assertEquals(false, actualUser.isSuspended)
        assertEquals(false, actualUser.isEmployee)
        assertEquals("t2_1", actualUser.id)
        assertEquals("https://example.com/icon.png", actualUser.iconImg)
        assertEquals(0, actualUser.linkKarma)
        assertEquals(10, actualUser.totalKarma)
        assertEquals("author", actualUser.name)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualUser.created)
        assertNull(actualUser.snoovatarImg)
        assertEquals(0, actualUser.commentKarma)
        assertEquals("", actualSubreddit.title)
        assertEquals(false, actualSubreddit.over18)
        assertEquals("", actualSubreddit.publicDescription)
        assertEquals("", actualSubreddit.description)
        assertEquals("https://www.reddit.com/user/author", actualSubreddit.url)
        assertEquals("u/author", actualSubreddit.displayNamePrefixed)
        assertEquals("author", actualSubreddit.name)
    }

    @Test
    fun `maps a person node without optional fields to defaults`() {
        val inputNode: GraphQlPersonNode =
            GraphQlPersonNode(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        assertNull(actualUser.subreddit)
        assertNull(actualUser.iconImg)
        assertEquals(-1, actualUser.totalKarma)
        assertEquals(-1L, actualUser.created)
        assertNull(actualUser.snoovatarImg)
    }

    @Test
    fun `parses a zulu timestamp with fractional seconds`() {
        val inputProfile: GraphQlUserProfile =
            GraphQlUserProfile(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                profileInfo = GraphQlProfileInfo(createdAt = "2026-10-07T15:47:48.2Z"),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputProfile)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualUser.created)
    }

    @Test
    fun `parses an ISO offset timestamp`() {
        val inputNode: GraphQlPersonNode =
            GraphQlPersonNode(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                profile = GraphQlPersonProfile(createdAt = "2026-10-07T17:47:48.255000+02:00"),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualUser.created)
    }

    @Test
    fun `falls back to minus one for a malformed timestamp`() {
        val inputProfile: GraphQlUserProfile =
            GraphQlUserProfile(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                profileInfo = GraphQlProfileInfo(createdAt = "not-a-timestamp"),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputProfile)
        assertEquals(-1L, actualUser.created)
    }

    @Test
    fun `preserves epoch zero for a valid timestamp`() {
        val inputProfile: GraphQlUserProfile =
            GraphQlUserProfile(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                profileInfo = GraphQlProfileInfo(createdAt = "1970-01-01T00:00:00+0000"),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputProfile)
        assertEquals(EPOCH_ZERO, actualUser.created)
    }

    @Test
    fun `maps a blank user profile icon to null`() {
        val inputProfile: GraphQlUserProfile =
            GraphQlUserProfile(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                icon = GraphQlMediaSource(""),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputProfile)
        assertNull(actualUser.iconImg)
    }

    @Test
    fun `falls back to the legacy icon when the person icon is blank`() {
        val inputNode: GraphQlPersonNode =
            GraphQlPersonNode(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                icon = GraphQlMediaSource(""),
                profile =
                    GraphQlPersonProfile(
                        styles =
                            GraphQlPersonStyles(
                                legacyIcon = GraphQlPersonIcon("https://example.com/legacy-icon.png"),
                            ),
                    ),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        assertEquals("https://example.com/legacy-icon.png", actualUser.iconImg)
    }

    @Test
    fun `maps blank person icons to null`() {
        val inputNode: GraphQlPersonNode =
            GraphQlPersonNode(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                icon = GraphQlMediaSource(""),
                profile =
                    GraphQlPersonProfile(
                        styles = GraphQlPersonStyles(legacyIcon = GraphQlPersonIcon("")),
                    ),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        val actualSubreddit: Subreddit = requireNotNull(actualUser.subreddit)
        assertNull(actualUser.iconImg)
        assertEquals("", actualSubreddit.iconImg)
    }

    @Test
    fun `maps a person node parsed from search people JSON`() {
        val inputNode: GraphQlPersonNode = parsePersonNode(SAFE_PERSON_SEARCH_JSON)
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        val actualSubreddit: Subreddit = requireNotNull(actualUser.subreddit)
        assertEquals("https://example.com/icon-96.png", actualUser.iconImg)
        assertEquals(10, actualUser.totalKarma)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualUser.created)
        assertEquals(false, actualSubreddit.over18)
        assertEquals("", actualSubreddit.title)
        assertEquals("", actualSubreddit.publicDescription)
        assertEquals("https://www.reddit.com/user/author", actualSubreddit.url)
        assertEquals("u/author", actualSubreddit.displayNamePrefixed)
    }

    @Test
    fun `maps an NSFW person node parsed from search people JSON with the legacy icon`() {
        val inputNode: GraphQlPersonNode = parsePersonNode(NSFW_PERSON_SEARCH_JSON)
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        val actualSubreddit: Subreddit = requireNotNull(actualUser.subreddit)
        assertEquals("https://example.com/legacy-icon.png", actualUser.iconImg)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualUser.created)
        assertEquals(true, actualSubreddit.over18)
        assertEquals("", actualSubreddit.title)
        assertEquals("", actualSubreddit.publicDescription)
    }

    @Test
    fun `falls back to minus one for an invalid person createdAt`() {
        val inputNode: GraphQlPersonNode =
            GraphQlPersonNode(
                id = "t2_1",
                name = "author",
                prefixedName = "u/author",
                profile = GraphQlPersonProfile(createdAt = "not-a-timestamp"),
            )
        val inputMapper: GraphQlUserMapper = GraphQlUserMapper()
        val actualUser: AboutUserData = inputMapper.toAboutUserData(inputNode)
        assertEquals(-1L, actualUser.created)
    }

    private fun parsePersonNode(inputJson: String): GraphQlPersonNode =
        requireNotNull(
            Moshi.Builder().build()
                .adapter(SearchPeopleResponse::class.java)
                .fromJson(inputJson)!!
                .data?.search?.general?.authors,
        ).edges.single().node

    private companion object {
        private const val CREATED_AT: String = "2026-10-07T15:47:48.255000+0000"
        private const val EXPECTED_CREATED_AT_SECONDS: Long = 1791388068L
        private const val EPOCH_ZERO: Long = 0L
        private const val SAFE_PERSON_SEARCH_JSON: String =
            """{"data":{"search":{"general":{"authors":{"pageInfo":{"endCursor":"t2_1","hasNextPage":false},"edges":[{"node":{"id":"t2_1","name":"author","prefixedName":"u/author","icon_96":{"url":"https://example.com/icon-96.png","dimensions":{"width":96,"height":96}},"karma":{"total":10},"profile":{"isNsfw":false,"createdAt":"2026-10-07T15:47:48.255000+0000","styles":{"legacyIcon":{"url":"https://example.com/legacy-icon.png"}}}}}]}}}}}"""
        private const val NSFW_PERSON_SEARCH_JSON: String =
            """{"data":{"search":{"general":{"authors":{"pageInfo":{"endCursor":"t2_2","hasNextPage":false},"edges":[{"node":{"id":"t2_2","name":"nsfw-author","prefixedName":"u/nsfw-author","karma":{"total":5},"profile":{"isNsfw":true,"createdAt":"2026-10-07T15:47:48.255000+0000","styles":{"legacyIcon":{"url":"https://example.com/legacy-icon.png"}}}}}]}}}}}"""
    }
}
