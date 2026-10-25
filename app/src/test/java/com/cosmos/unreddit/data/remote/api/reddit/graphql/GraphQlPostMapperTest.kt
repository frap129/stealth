package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.cosmos.unreddit.data.model.GalleryMedia
import com.cosmos.unreddit.data.model.MediaType
import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlPostMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlAwarding
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlAuthorInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlContent
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlFlair
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGallery
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGalleryItem
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGalleryMedia
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMedia
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMediaSource
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPostNode
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlStreaming
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlStill
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlSubredditRef
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.PostsByIdsResponse
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryData
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryDataItem
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryImage
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryItem
import com.cosmos.unreddit.data.remote.api.reddit.model.ImageSource
import com.cosmos.unreddit.data.remote.api.reddit.model.Media
import com.cosmos.unreddit.data.remote.api.reddit.model.MediaMetadata
import com.cosmos.unreddit.data.remote.api.reddit.model.MediaPreview
import com.cosmos.unreddit.data.remote.api.reddit.model.PostData
import com.cosmos.unreddit.data.remote.api.reddit.model.RedditVideoPreview
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphQlPostMapperTest {
    @Test
    fun `maps a self post`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_1wzvqsb",
                postTitle = "We are the Android team",
                permalink = "/r/Android/comments/1wzvqsb/we_are/",
                url = "https://www.reddit.com/r/Android/comments/1wzvqsb/we_are/",
                domain = "self.Android",
                createdAt = CREATED_AT,
                score = 144,
                upvoteRatio = 0.92,
                commentCount = 110,
                isSelfPost = true,
                isStickied = true,
                distinguishedAs = "moderator",
                suggestedCommentSort = "QA",
                content = GraphQlContent(html = "<p>Hello</p>"),
                flair = GraphQlFlair(text = "Discussion"),
                authorFlair = GraphQlFlair(text = "Android team"),
                authorInfo = GraphQlAuthorInfo(name = "android"),
                awardings = listOf(GraphQlAwarding(total = 2), GraphQlAwarding(total = 3)),
                subreddit = GraphQlSubredditRef(name = "Android", prefixedName = "r/Android"),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        val actualRatio: Double = requireNotNull(actualPost.ratio)
        val actualIsRedditGallery: Boolean = requireNotNull(actualPost.isRedditGallery)
        assertEquals("t3_1wzvqsb", actualPost.name)
        assertEquals("Android", actualPost.subreddit)
        assertEquals("r/Android", actualPost.prefixedSubreddit)
        assertEquals("/r/Android/comments/1wzvqsb/we_are/", actualPost.permalink)
        assertEquals("We are the Android team", actualPost.title)
        assertEquals(144, actualPost.score)
        assertEquals(110, actualPost.commentsNumber)
        assertTrue(actualPost.isSelf)
        assertEquals("https://www.reddit.com/r/Android/comments/1wzvqsb/we_are/", actualPost.url)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualPost.created)
        assertEquals(0.92, actualRatio, 0.0)
        assertEquals(5, actualPost.totalAwards)
        assertEquals("self.Android", actualPost.domain)
        assertEquals("<p>Hello</p>", actualPost.selfTextHtml)
        assertEquals("QA", actualPost.suggestedSort)
        assertEquals("moderator", actualPost.distinguished)
        assertEquals("Discussion", actualPost.flair)
        assertEquals("Android team", actualPost.authorFlair)
        assertEquals("android", actualPost.author)
        assertTrue(actualPost.isStickied)
        assertFalse(actualPost.isArchived)
        assertFalse(actualPost.isOver18)
        assertFalse(actualPost.isSpoiler)
        assertFalse(actualPost.isLocked)
        assertFalse(actualPost.isOC)
        assertNull(actualPost.hint)
        assertNull(actualPost.crossposts)
        assertNull(actualPost.authorFlairRichText)
        assertTrue(actualPost.linkFlairRichText.isEmpty())
        assertTrue(actualPost.awardings.isEmpty())
        assertNull(actualPost.galleryData)
        assertNull(actualPost.media)
        assertNull(actualPost.mediaPreview)
        assertNull(actualPost.mediaMetadata)
        assertFalse(actualIsRedditGallery)
        assertFalse(actualPost.isVideo)
        assertEquals(MediaType.NO_MEDIA, actualPost.mediaType)
    }

    @Test
    fun `maps an image post preview`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_image",
                postTitle = "An image",
                permalink = "/r/Android/comments/image/an_image/",
                url = "https://i.redd.it/image.jpg",
                domain = "i.redd.it",
                createdAt = CREATED_AT,
                media =
                    GraphQlMedia(
                        still = GraphQlStill(source = GraphQlMediaSource("https://i.redd.it/image.jpg", 1200, 800)),
                    ),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        val actualPreview: MediaPreview = requireNotNull(actualPost.mediaPreview)
        val actualImageSource: ImageSource = actualPreview.images.single().imageSource
        assertEquals(MediaType.IMAGE, actualPost.mediaType)
        assertFalse(actualPost.isVideo)
        assertNull(actualPost.media)
        assertEquals("https://i.redd.it/image.jpg", actualImageSource.url)
        assertEquals(1200, actualImageSource.width)
        assertEquals(800, actualImageSource.height)
        assertNull(actualPreview.videoPreview)
    }

    @Test
    fun `maps a gallery post`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_gallery",
                postTitle = "A gallery",
                permalink = "/r/Android/comments/gallery/a_gallery/",
                url = "https://www.reddit.com/gallery/gallery",
                createdAt = CREATED_AT,
                gallery =
                    GraphQlGallery(
                        items =
                            listOf(
                                GraphQlGalleryItem(
                                    id = "item-id",
                                    mediaId = "media-id",
                                    caption = "First",
                                    url = "https://i.redd.it/first.jpg",
                                    width = 10,
                                    height = 20,
                                ),
                            ),
                    ),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        val actualIsRedditGallery: Boolean = requireNotNull(actualPost.isRedditGallery)
        val actualGalleryData: GalleryData = requireNotNull(actualPost.galleryData)
        val actualGalleryItem: GalleryDataItem = actualGalleryData.items.single()
        val actualMetadata: MediaMetadata = requireNotNull(actualPost.mediaMetadata)
        val actualMetadataItem: GalleryItem = actualMetadata.items.single()
        val actualMetadataImage: GalleryImage = requireNotNull(actualMetadataItem.image)
        val actualGalleryUrls: List<String> = actualPost.gallery.map { media: GalleryMedia -> media.url }
        assertTrue(actualIsRedditGallery)
        assertEquals(MediaType.REDDIT_GALLERY, actualPost.mediaType)
        assertFalse(actualPost.isVideo)
        assertEquals("First", actualGalleryItem.caption)
        assertEquals("media-id", actualGalleryItem.mediaId)
        assertEquals("media-id", actualMetadataItem.id)
        assertNull(actualMetadataItem.mimeType)
        assertNull(actualMetadataItem.previews)
        assertEquals("https://i.redd.it/first.jpg", actualMetadataImage.url)
        assertEquals(20, actualMetadataImage.height)
        assertEquals(10, actualMetadataImage.width)
        assertEquals(listOf("https://i.redd.it/first.jpg"), actualGalleryUrls)
        assertTrue(actualGalleryUrls.isNotEmpty())
    }

    @Test
    fun `maps gallery metadata from flat media dimensions`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_gallery_flat",
                postTitle = "A flat gallery",
                permalink = "/r/Android/comments/gallery_flat/a_flat_gallery/",
                url = "https://www.reddit.com/gallery/gallery_flat",
                createdAt = CREATED_AT,
                gallery =
                    GraphQlGallery(
                        items =
                            listOf(
                                GraphQlGalleryItem(
                                    caption = "Flat",
                                    media =
                                        GraphQlGalleryMedia(
                                            id = "media-id",
                                            url = "https://i.redd.it/flat.jpg",
                                            width = 10,
                                            height = 20,
                                            dimensions = null,
                                        ),
                                ),
                            ),
                    ),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        val actualGalleryItem: GalleryDataItem =
            requireNotNull(actualPost.galleryData).items.single()
        val actualMetadataItem: GalleryItem =
            requireNotNull(actualPost.mediaMetadata).items.single()
        val actualMetadataImage: GalleryImage = requireNotNull(actualMetadataItem.image)
        assertTrue(requireNotNull(actualPost.isRedditGallery))
        assertEquals("Flat", actualGalleryItem.caption)
        assertEquals("media-id", actualGalleryItem.mediaId)
        assertEquals("media-id", actualMetadataItem.id)
        assertEquals("https://i.redd.it/flat.jpg", actualMetadataImage.url)
        assertEquals(10, actualMetadataImage.width)
        assertEquals(20, actualMetadataImage.height)
    }

    @Test
    fun `keeps gallery flag when gallery items lack ids`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_gallery",
                postTitle = "A gallery",
                permalink = "/r/Android/comments/gallery/a_gallery/",
                url = "https://www.reddit.com/gallery/gallery",
                createdAt = CREATED_AT,
                gallery = GraphQlGallery(items = listOf(GraphQlGalleryItem(caption = "No ids"))),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        val actualIsRedditGallery: Boolean = requireNotNull(actualPost.isRedditGallery)
        val actualGalleryData: GalleryData = requireNotNull(actualPost.galleryData)
        val actualMetadata: MediaMetadata = requireNotNull(actualPost.mediaMetadata)
        assertTrue(actualIsRedditGallery)
        assertTrue(actualGalleryData.items.isEmpty())
        assertTrue(actualMetadata.items.isEmpty())
    }

    @Test
    fun `maps a streaming video`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_video",
                postTitle = "A video",
                permalink = "/r/Android/comments/video/a_video/",
                url = "https://v.redd.it/video",
                domain = "v.redd.it",
                createdAt = CREATED_AT,
                media =
                    GraphQlMedia(
                        streaming =
                            GraphQlStreaming(
                                dashUrl = "https://v.redd.it/video/DASHPlaylist.mpd",
                                hlsUrl = "https://v.redd.it/video/HLSPlaylist.m3u8",
                                duration = 12,
                                width = 640,
                                height = 360,
                            ),
                    ),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        val actualMedia: Media = requireNotNull(actualPost.media)
        val actualVideo: RedditVideoPreview = requireNotNull(actualMedia.redditVideoPreview)
        assertTrue(actualPost.isVideo)
        assertEquals(MediaType.REDDIT_VIDEO, actualPost.mediaType)
        assertEquals("video", actualMedia.type)
        assertEquals("https://v.redd.it/video/DASHPlaylist.mpd", actualVideo.fallbackUrl)
        assertEquals(640, actualVideo.width)
        assertEquals(360, actualVideo.height)
        assertEquals(12, actualVideo.duration)
        assertFalse(actualVideo.isGif)
        assertEquals("https://v.redd.it/video/DASHPlaylist.mpd", actualPost.mediaUrl)
    }

    @Test
    fun `falls back to hls for a legacy video`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_video",
                postTitle = "A video",
                permalink = "/r/Android/comments/video/a_video/",
                url = "https://v.redd.it/video",
                domain = "v.redd.it",
                createdAt = CREATED_AT,
                media =
                    GraphQlMedia(
                        video =
                            GraphQlStreaming(
                                hlsUrl = "https://v.redd.it/video/HLSPlaylist.m3u8",
                                duration = 8,
                            ),
                    ),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        val actualMedia: Media = requireNotNull(actualPost.media)
        val actualVideo: RedditVideoPreview = requireNotNull(actualMedia.redditVideoPreview)
        assertTrue(actualPost.isVideo)
        assertEquals("https://v.redd.it/video/HLSPlaylist.m3u8", actualVideo.fallbackUrl)
    }

    @Test
    fun `maps a video without urls to no media`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_video",
                postTitle = "A video",
                permalink = "/r/Android/comments/video/a_video/",
                url = "https://v.redd.it/video",
                domain = "v.redd.it",
                createdAt = CREATED_AT,
                media = GraphQlMedia(streaming = GraphQlStreaming(hlsUrl = "   ")),
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        assertTrue(actualPost.isVideo)
        assertNull(actualPost.media)
    }

    @Test
    fun `parses a zulu timestamp with a short fraction`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_zulu",
                postTitle = "Zulu",
                permalink = "/r/Android/comments/zulu/zulu/",
                url = "https://www.reddit.com/r/Android/comments/zulu/zulu/",
                createdAt = "2026-10-07T15:47:48.2Z",
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualPost.created)
    }

    @Test
    fun `parses an ISO offset timestamp`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_offset",
                postTitle = "Offset",
                permalink = "/r/Android/comments/offset/offset/",
                url = "https://www.reddit.com/r/Android/comments/offset/offset/",
                createdAt = "2026-10-07T17:47:48.255000+02:00",
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        assertEquals(EXPECTED_CREATED_AT_SECONDS, actualPost.created)
    }

    @Test
    fun `falls back to epoch zero for a malformed timestamp`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_malformed",
                postTitle = "Malformed",
                permalink = "/r/Android/comments/malformed/malformed/",
                url = "https://www.reddit.com/r/Android/comments/malformed/malformed/",
                createdAt = "not-a-timestamp",
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        assertEquals(INVALID_EPOCH_SECONDS, actualPost.created)
    }

    @Test
    fun `maps a missing subreddit and author to safe defaults`() {
        val inputNode: GraphQlPostNode =
            GraphQlPostNode(
                id = "t3_missing",
                postTitle = "Missing metadata",
                permalink = "/r/comments/missing/missing_metadata/",
                url = "https://example.com/missing",
                createdAt = CREATED_AT,
                subreddit = null,
                authorInfo = null,
                domain = null,
            )
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        assertEquals("", actualPost.subreddit)
        assertEquals("r/", actualPost.prefixedSubreddit)
        assertEquals("", actualPost.author)
        assertEquals("", actualPost.domain)
    }

    @Test
    fun `maps a post node with missing fields to safe defaults`() {
        val inputNode: GraphQlPostNode = GraphQlPostNode(typename = "DeletedSubredditPost")
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(inputNode)
        assertEquals("", actualPost.name)
        assertEquals("", actualPost.title)
        assertEquals("", actualPost.permalink)
        assertEquals("", actualPost.url)
        assertEquals(INVALID_EPOCH_SECONDS, actualPost.created)
    }

    @Test
    fun `maps nested media and gallery dimensions from JSON`() {
        val inputJson =
            """
            {"data":{"postsInfoByIds":[{
              "__typename":"SubredditPost",
              "id":"t3_nested",
              "postTitle":"Nested media",
              "permalink":"/r/Android/comments/nested/",
              "url":"https://www.reddit.com/r/Android/comments/nested/",
              "createdAt":"2026-10-07T15:47:48.255000+0000",
              "media":{
                "still":{"source":{"url":"https://i.redd.it/still.jpg","width":1,"height":2,"dimensions":{"width":1200,"height":800}}},
                "streaming":{"hlsUrl":"https://v.redd.it/nested/HLSPlaylist.m3u8","dashUrl":"https://v.redd.it/nested/DASHPlaylist.mpd","duration":12,"width":10,"height":20,"dimensions":{"width":640,"height":360}}
              },
              "gallery":{
                "items":[{
                  "id":"item-id",
                  "caption":"First",
                  "url":"https://legacy.example/fallback.jpg",
                  "width":3,
                  "height":4,
                  "media":{
                    "__typename":"ImageAsset",
                    "id":"media-id",
                    "url":"https://i.redd.it/first.jpg",
                    "width":5,
                    "height":6,
                    "dimensions":{"width":100,"height":200}
                  }
                }]
              }
            }]}}
            """.trimIndent()
        val actualResponse =
            Moshi.Builder()
                .build()
                .adapter(PostsByIdsResponse::class.java)
                .fromJson(inputJson)!!
        val actualNode: GraphQlPostNode =
            requireNotNull(requireNotNull(actualResponse.data?.postsInfoByIds).single())
        val inputMapper: GraphQlPostMapper = GraphQlPostMapper()
        val actualPost: PostData = inputMapper.toPostData(actualNode)
        val actualPreview: MediaPreview = requireNotNull(actualPost.mediaPreview)
        val actualPreviewSource: ImageSource = actualPreview.images.single().imageSource
        val actualVideo: RedditVideoPreview =
            requireNotNull(requireNotNull(actualPost.media).redditVideoPreview)
        val actualGalleryItem: GalleryDataItem =
            requireNotNull(actualPost.galleryData).items.single()
        val actualMetadataItem: GalleryItem =
            requireNotNull(actualPost.mediaMetadata).items.single()
        val actualMetadataImage: GalleryImage = requireNotNull(actualMetadataItem.image)
        assertTrue(requireNotNull(actualPost.isRedditGallery))
        assertEquals(1200, actualPreviewSource.width)
        assertEquals(800, actualPreviewSource.height)
        assertEquals(640, actualVideo.width)
        assertEquals(360, actualVideo.height)
        assertEquals("First", actualGalleryItem.caption)
        assertEquals("media-id", actualGalleryItem.mediaId)
        assertEquals("media-id", actualMetadataItem.id)
        assertEquals("https://i.redd.it/first.jpg", actualMetadataImage.url)
        assertEquals(200, actualMetadataImage.height)
        assertEquals(100, actualMetadataImage.width)
    }

    private companion object {
        private const val CREATED_AT: String = "2026-10-07T15:47:48.255000+0000"
        private const val EXPECTED_CREATED_AT_SECONDS: Long = 1791388068L
        private const val INVALID_EPOCH_SECONDS: Long = 0L
    }
}
