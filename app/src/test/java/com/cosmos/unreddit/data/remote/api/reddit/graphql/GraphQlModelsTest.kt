package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.CommunitySearchResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.LiveCommentsByIdsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.PostCommentsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.PostsByIdsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SearchPeopleResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SearchPostsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubmittedCommentsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubmittedPostsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubredditFeedResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubredditInfoResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.UserProfileResponse
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphQlModelsTest {
    @Test
    fun `parses a post comments response`() {
        val inputJson =
            """{"data":{"postInfoById":{"id":"t3_1wzvqsb","title":"t","subreddit":{"prefixedName":"r/Android"},"commentCount":1,"commentForest":{"appliedSort":"CONFIDENCE","pageInfo":{"commentCount":1,"hasNextPage":false},"trees":[{"depth":0,"parentId":null,"childCount":0,"more":null,"node":{"__typename":"Comment","id":"t1_a","createdAt":"2026-10-07T15:47:48.255000+0000","content":{"html":"<p>hi</p>","markdown":"hi","preview":"hi"},"authorInfo":{"name":"a"},"authorFlair":{"text":"Flair"},"score":1,"voteState":"NONE","isOP":false,"isStickied":false,"isScoreHidden":false,"isRemoved":false,"isLocked":false,"awardings":[{"total":2}],"permalink":"/r/Android/comments/1wzvqsb/a/"}}]}}},"extensions":{"traceID":"x"}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(PostCommentsResponse::class.java).fromJson(inputJson)!!
        val actualData = actualResponse.data!!
        assertEquals("t3_1wzvqsb", actualData.postInfoById.id)
        assertEquals("r/Android", actualData.postInfoById.subreddit!!.prefixedName)
        assertEquals("CONFIDENCE", actualData.postInfoById.commentForest.appliedSort)
        assertEquals(1, actualData.postInfoById.commentForest.trees.size)
        val actualTree = actualData.postInfoById.commentForest.trees.first()
        assertEquals(0, actualTree.depth)
        assertNull(actualTree.parentId)
        val actualComment = actualTree.node!!
        assertEquals("t1_a", actualComment.id)
        assertEquals("a", actualComment.authorInfo!!.name)
        assertEquals("<p>hi</p>", actualComment.content!!.html)
        assertEquals(2, actualComment.awardings.first().total)
    }

    @Test
    fun `parses a posts by ids response`() {
        val inputJson =
            """{"data":{"postsInfoByIds":[{"id":"t3_1wzvqsb","postTitle":"A post","permalink":"/r/Android/comments/1wzvqsb/a/","url":"https://example.com","domain":"example.com","createdAt":"2026-10-07T15:47:48.255000+0000","editedAt":null,"score":42,"upvoteRatio":0.95,"commentCount":3,"isSelfPost":false,"isNsfw":false,"isSpoiler":false,"isStickied":false,"isLocked":false,"isArchived":false,"content":{"html":"<p>body</p>","markdown":"body"},"flair":{"text":"Discussion"},"authorFlair":{"text":"Author flair"},"authorInfo":{"name":"author"},"awardings":[{"total":2}],"media":{"still":{"source":{"url":"https://example.com/image.jpg","width":100,"height":50}},"video":{"dashUrl":"https://example.com/dash","hlsUrl":"https://example.com/hls","duration":12,"width":100,"height":50,"hasAudio":true}},"gallery":{"items":[{"id":"item","mediaId":"media","caption":"cap","url":"https://example.com/g.jpg","width":10,"height":20}]},"subreddit":{"name":"Android","prefixedName":"r/Android"}}]}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(PostsByIdsResponse::class.java).fromJson(inputJson)!!
        val actualNode = actualResponse.data!!.postsInfoByIds.single()
        assertEquals("t3_1wzvqsb", actualNode.id)
        assertEquals("A post", actualNode.postTitle)
        assertEquals(42, actualNode.score)
        assertEquals(0.95, actualNode.upvoteRatio!!, 0.0)
        assertEquals("r/Android", actualNode.subreddit!!.prefixedName)
        assertEquals("Discussion", actualNode.flair!!.text)
        assertEquals("https://example.com/image.jpg", actualNode.media!!.still!!.source!!.url)
        assertEquals("https://example.com/dash", actualNode.media!!.video!!.dashUrl)
        assertEquals(1, actualNode.gallery!!.items.size)
        assertEquals("media", actualNode.gallery!!.items.first().mediaId)
        assertEquals(2, actualNode.awardings.first().total)
    }

    @Test
    fun `parses a subreddit feed response`() {
        val inputJson =
            """{"data":{"subredditV3":{"elements":{"pageInfo":{"endCursor":"t3_abc","hasNextPage":true},"edges":[{"node":{"groupId":"t3_abc"}},{"node":null}]}}}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(SubredditFeedResponse::class.java).fromJson(inputJson)!!
        val actualElements = actualResponse.data!!.subredditV3.elements
        assertEquals("t3_abc", actualElements.pageInfo.endCursor)
        assertTrue(actualElements.pageInfo.hasNextPage)
        assertEquals(2, actualElements.edges.size)
        assertEquals("t3_abc", actualElements.edges.first().node!!.groupId)
        assertNull(actualElements.edges.last().node)
    }

    @Test
    fun `parses a submitted posts response`() {
        val inputJson =
            """{"data":{"profileFeed":{"elements":{"pageInfo":{"endCursor":null,"hasNextPage":false},"edges":[{"node":{"groupId":"t3_xyz"}}]}}}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(SubmittedPostsResponse::class.java).fromJson(inputJson)!!
        val actualElements = actualResponse.data!!.profileFeed.elements
        assertTrue(!actualElements.pageInfo.hasNextPage)
        assertEquals("t3_xyz", actualElements.edges.single().node!!.groupId)
    }

    @Test
    fun `parses a subreddit info response`() {
        val inputJson =
            """{"data":{"subredditInfoByName":{"id":"t5_1","name":"Android","prefixedName":"r/Android","title":"Android","path":"/r/Android/","createdAt":"2010-01-01T00:00:00.000000+0000","publicDescriptionText":"desc","description":{"markdown":"desc","richtext":"desc"},"isNsfw":false,"isQuarantined":false,"subscribersCount":1000000.0,"communityStats":{"weeklyActiveUsersCount":123},"styles":{"bannerBackgroundImage":null,"icon":"https://example.com/icon.png","legacyBannerBackgroundImage":"https://example.com/legacy-banner.png","legacyIcon":{"url":"https://example.com/legacy-icon.png"},"legacyPrimaryColor":"#00ff00","mobileBannerImage":"https://example.com/mobile-banner.png","primaryColor":"#ff0000"},"wiki":{"indexPage":{"status":"MODERATOR_ONLY"}}}}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(SubredditInfoResponse::class.java).fromJson(inputJson)!!
        val actualInfo = actualResponse.data!!.subredditInfoByName
        val actualStyles = actualInfo.styles!!
        assertEquals("Android", actualInfo.name)
        assertEquals("Android", actualInfo.title)
        assertEquals(1000000.0, actualInfo.subscribersCount!!, 0.0)
        assertEquals(123, actualInfo.communityStats!!.weeklyActiveUsersCount)
        assertEquals("https://example.com/icon.png", actualStyles.icon)
        assertNull(actualStyles.bannerBackgroundImage)
        assertEquals("https://example.com/legacy-banner.png", actualStyles.legacyBannerBackgroundImage)
        assertEquals("https://example.com/mobile-banner.png", actualStyles.mobileBannerImage)
        assertEquals("https://example.com/legacy-icon.png", actualStyles.legacyIcon!!.url)
        assertNull(actualInfo.description!!.html)
        assertEquals("desc", actualInfo.description!!.markdown)
        assertEquals("desc", actualInfo.description!!.richtext)
        assertEquals("MODERATOR_ONLY", actualInfo.wiki!!.indexPage!!.status)
    }

    @Test
    fun `parses a user profile response`() {
        val inputJson =
            """{"data":{"redditorInfoByName":{"id":"t2_1","name":"author","prefixedName":"u/author","icon":{"url":"https://example.com/icon.png","width":256,"height":256},"snoovatarIcon":{"url":"https://example.com/snoo.png"},"isEmployee":false,"karma":{"total":1234},"profileInfo":{"createdAt":"2015-01-01T00:00:00.000000+0000","publicDescriptionText":"bio","title":"Title","isNsfw":false}}}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(UserProfileResponse::class.java).fromJson(inputJson)!!
        val actualUser = actualResponse.data!!.redditorInfoByName
        assertEquals("author", actualUser.name)
        assertEquals(1234, actualUser.karma!!.total)
        assertEquals("Title", actualUser.profileInfo!!.title)
        assertEquals("https://example.com/snoo.png", actualUser.snoovatarIcon!!.url)
    }

    @Test
    fun `parses a submitted comments response`() {
        val inputJson =
            """{"data":{"redditorInfoByName":{"comments":{"pageInfo":{"endCursor":"t1_z","hasNextPage":false},"edges":[{"node":{"id":"t1_a","createdAt":"2026-10-07T15:47:48.255000+0000","score":5,"content":{"html":"<p>hi</p>","markdown":"hi"},"postInfo":{"id":"t3_1","title":"Post title"}}}]}}}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(SubmittedCommentsResponse::class.java).fromJson(inputJson)!!
        val actualComments = actualResponse.data!!.redditorInfoByName.comments
        assertEquals("t1_z", actualComments.pageInfo.endCursor)
        val actualComment = actualComments.edges.single().node
        assertEquals("t1_a", actualComment.id)
        assertEquals("hi", actualComment.content!!.markdown)
        assertEquals("Post title", actualComment.postInfo!!.title)
    }

    @Test
    fun `parses a live comments by ids response`() {
        val inputJson =
            """{"data":{"commentsByIds":[{"id":"t1_a","createdAt":"2026-10-07T15:47:48.255000+0000","content":{"html":"<p>hi</p>","markdown":"hi","preview":"hi"},"authorInfo":{"name":"a"},"score":1,"voteState":"NONE","isOP":true,"isStickied":false,"isScoreHidden":false,"isRemoved":false,"isLocked":false,"permalink":"/r/Android/comments/1/a/"}]}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(LiveCommentsByIdsResponse::class.java).fromJson(inputJson)!!
        val actualComment = actualResponse.data!!.commentsByIds.single()
        assertEquals("t1_a", actualComment.id)
        assertTrue(actualComment.isOP)
        assertEquals("NONE", actualComment.voteState)
    }

    @Test
    fun `parses a search posts response`() {
        val inputJson =
            """{"data":{"search":{"general":{"posts":{"pageInfo":{"endCursor":"t3_2","hasNextPage":true},"edges":[{"node":{"id":"t3_1","postTitle":"Result","permalink":"/r/Android/comments/1/a/","url":"https://example.com","createdAt":"2026-10-07T15:47:48.255000+0000","subreddit":{"name":"Android","prefixedName":"r/Android"}}}]}}}}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(SearchPostsResponse::class.java).fromJson(inputJson)!!
        val actualPosts = actualResponse.data!!.search.general.posts!!
        assertEquals("t3_2", actualPosts.pageInfo.endCursor)
        val actualNode = actualPosts.edges.single().node
        assertEquals("t3_1", actualNode.id)
        assertEquals("Result", actualNode.postTitle)
        assertEquals("r/Android", actualNode.subreddit!!.prefixedName)
    }

    @Test
    fun `parses a search people response`() {
        val inputJson =
            """{"data":{"search":{"general":{"authors":{"pageInfo":{"endCursor":"t2_1","hasNextPage":false},"edges":[{"node":{"id":"t2_1","name":"author","prefixedName":"u/author","icon_96":{"url":"https://example.com/icon-96.png","dimensions":{"width":96,"height":96}},"karma":{"total":10},"profile":{"isNsfw":true,"createdAt":"2026-10-07T15:47:48.255000+0000","styles":{"legacyIcon":{"url":"https://example.com/legacy-icon.png"}}}}}]}}}}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(SearchPeopleResponse::class.java).fromJson(inputJson)!!
        val actualAuthors = actualResponse.data!!.search.general.authors!!
        assertEquals("t2_1", actualAuthors.pageInfo.endCursor)
        val actualNode = actualAuthors.edges.single().node
        assertEquals("author", actualNode.name)
        assertEquals(10, actualNode.karma!!.total)
        assertEquals("https://example.com/icon-96.png", actualNode.icon!!.url)
        assertEquals(true, actualNode.profile!!.isNsfw)
        assertEquals("2026-10-07T15:47:48.255000+0000", actualNode.profile!!.createdAt)
        assertEquals("https://example.com/legacy-icon.png", actualNode.profile!!.styles!!.legacyIcon!!.url)
    }

    @Test
    fun `parses a community search response`() {
        val inputJson =
            """{"data":{"targetableCommunityInfo":[{"subredditInfo":{"name":"Android"},"iconUrl":"https://example.com/icon.png","description":"desc","subscriberCount":1000}]}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(CommunitySearchResponse::class.java).fromJson(inputJson)!!
        val actualCommunity = actualResponse.data!!.targetableCommunityInfo.single()
        assertEquals("Android", actualCommunity.subredditInfo!!.name)
        assertNull(actualCommunity.subredditInfo!!.prefixedName)
        assertEquals(1000, actualCommunity.subscriberCount)
        assertEquals("desc", actualCommunity.description)
    }

    @Test
    fun `ignores unknown fields`() {
        val inputJson =
            """{"data":{"postInfoById":{"id":"t3_1","title":"t","commentCount":0,"commentForest":{"pageInfo":{"hasNextPage":false},"trees":[],"unexpectedField":true},"unexpectedField":true}},"unexpectedRoot":{"a":1}}"""
        val actualResponse =
            Moshi.Builder().build().adapter(PostCommentsResponse::class.java).fromJson(inputJson)!!
        assertEquals("t3_1", actualResponse.data!!.postInfoById.id)
        assertTrue(actualResponse.data!!.postInfoById.commentForest.trees.isEmpty())
    }

    @Test
    fun `parses errors with null data`() {
        val inputJson = """{"data":null,"errors":[{"message":"Not found","path":["postInfoById"]}]}"""
        val actualResponse =
            Moshi.Builder().build().adapter(PostCommentsResponse::class.java).fromJson(inputJson)!!
        assertNull(actualResponse.data)
        assertEquals(1, actualResponse.errors!!.size)
        assertEquals("Not found", actualResponse.errors!!.first().message)
        assertEquals("postInfoById", actualResponse.errors!!.first().path!!.first())
    }
}
