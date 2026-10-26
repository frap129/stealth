package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGallery
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGalleryItem
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGalleryMedia
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMedia
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMediaSource
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPlaybackPermutation
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPostNode
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlStreaming
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryData
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryDataItem
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryImage
import com.cosmos.unreddit.data.remote.api.reddit.model.GalleryItem
import com.cosmos.unreddit.data.remote.api.reddit.model.Image
import com.cosmos.unreddit.data.remote.api.reddit.model.ImageSource
import com.cosmos.unreddit.data.remote.api.reddit.model.Media
import com.cosmos.unreddit.data.remote.api.reddit.model.MediaMetadata
import com.cosmos.unreddit.data.remote.api.reddit.model.MediaPreview
import com.cosmos.unreddit.data.remote.api.reddit.model.PostData
import com.cosmos.unreddit.data.remote.api.reddit.model.RedditVideoPreview
import com.cosmos.unreddit.util.LinkUtil
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import javax.inject.Inject

class GraphQlPostMapper @Inject constructor() {
    fun toPostData(node: GraphQlPostNode): PostData {
        val media: Media? = node.media?.toMedia()
        return PostData(
            subreddit = node.subreddit?.name.orEmpty(),
            linkFlairRichText = emptyList(),
            authorFlairRichText = null,
            title = node.postTitle.orEmpty(),
            prefixedSubreddit = node.subreddit?.prefixedName ?: "r/${node.subreddit?.name.orEmpty()}",
            name = node.id.orEmpty(),
            ratio = node.upvoteRatio,
            totalAwards = node.awardings.sumOf { it.total },
            isOC = false,
            flair = node.flair?.text,
            authorFlair = node.authorFlair?.text,
            galleryData = node.gallery?.toGalleryData(),
            score = node.score,
            hint = null,
            isSelf = node.isSelfPost,
            crossposts = null,
            domain = node.domain.orEmpty(),
            selfTextHtml = node.content?.html,
            suggestedSort = node.suggestedCommentSort?.lowercase(Locale.US),
            isArchived = node.isArchived,
            isOver18 = node.isNsfw,
            mediaPreview = node.media?.toPreview(),
            awardings = emptyList(),
            isSpoiler = node.isSpoiler,
            isLocked = node.isLocked,
            distinguished = node.distinguishedAs?.lowercase(Locale.US),
            author = node.authorInfo?.name.orEmpty(),
            commentsNumber = node.commentCount,
            permalink = node.permalink.orEmpty(),
            isStickied = node.isStickied,
            url = node.url.orEmpty(),
            created = GraphQlTimestamp.toEpochSeconds(node.createdAt.orEmpty()),
            media = media,
            mediaMetadata = node.gallery?.toMediaMetadata(),
            isRedditGallery = node.gallery != null,
            isVideo = media != null,
        )
    }

    private fun GraphQlMedia.toPreview(): MediaPreview? {
        val source: GraphQlMediaSource = still?.source ?: return null
        return MediaPreview(
            images = listOf(
                Image(
                    imageSource =
                        ImageSource(source.url, source.resolvedWidth, source.resolvedHeight),
                    resolutions = emptyList(),
                    variants = null,
                ),
            ),
            videoPreview = null,
        )
    }

    private fun GraphQlMedia.toMedia(): Media? {
        val streaming: GraphQlStreaming? = streaming ?: video
        val packagedUrl: String? =
            packagedMedia?.playbackMp4s?.permutations
                .orEmpty()
                .filter { permutation: GraphQlPlaybackPermutation ->
                    permutation.source?.url?.isProgressiveRedditMp4() == true
                }
                .maxWithOrNull(
                    compareBy<GraphQlPlaybackPermutation> { it.resolutionArea() }
                        .thenBy { it.bitrateBps },
                )
                ?.source?.url
        val animatedSource: GraphQlMediaSource? = animated?.mp4Source
        val animatedUrl: String? =
            animatedSource?.url?.takeIf { url: String -> LinkUtil.isAnimatedRedditMp4(url) }
        val fallbackUrl: String =
            packagedUrl
                ?: streaming?.dashUrl?.takeIf { url: String -> url.isPlayableManifest(".mpd") }
                ?: streaming?.hlsUrl?.takeIf { url: String -> url.isPlayableManifest(".m3u8") }
                ?: streaming?.scrubberMediaUrl?.takeIf { url: String -> url.isProgressiveRedditMp4() }
                ?: animatedUrl
                ?: return null
        return Media(
            type = "video",
            embed = null,
            redditVideoPreview = RedditVideoPreview(
                fallbackUrl = fallbackUrl,
                width = streaming?.resolvedWidth?.takeIf { width: Int -> width > 0 }
                    ?: animatedSource?.resolvedWidth
                    ?: 0,
                height = streaming?.resolvedHeight?.takeIf { height: Int -> height > 0 }
                    ?: animatedSource?.resolvedHeight
                    ?: 0,
                duration = streaming?.duration ?: 0,
                isGif = streaming?.isGif == true || animatedUrl != null,
            ),
        )
    }

    private fun GraphQlPlaybackPermutation.resolutionArea(): Int {
        val mediaSource: GraphQlMediaSource = source ?: return 0
        val width: Int = mediaSource.dimensions?.width ?: mediaSource.width
        val height: Int = mediaSource.dimensions?.height ?: mediaSource.height
        return width * height
    }

    private fun String.isProgressiveRedditMp4(): Boolean {
        val url = toHttpUrlOrNull() ?: return false
        return url.host in REDDIT_VIDEO_HOSTS &&
            url.encodedPath.endsWith(".mp4", ignoreCase = true)
    }

    private fun String.isPlayableManifest(extension: String): Boolean {
        val url = toHttpUrlOrNull() ?: return false
        return url.host in REDDIT_VIDEO_HOSTS &&
            url.encodedPath.endsWith(extension, ignoreCase = true)
    }

    private fun GraphQlGallery.toGalleryData(): GalleryData =
        GalleryData(
            items = items.mapNotNull { item: GraphQlGalleryItem ->
                val mediaId: String =
                    item.media?.id ?: item.mediaId ?: return@mapNotNull null
                GalleryDataItem(item.caption, mediaId)
            },
        )

    private fun GraphQlGallery.toMediaMetadata(): MediaMetadata =
        MediaMetadata(
            items = items.mapNotNull { item: GraphQlGalleryItem ->
                val mediaId: String =
                    item.media?.id ?: item.mediaId ?: return@mapNotNull null
                val galleryMedia: GalleryMediaInfo? = item.toGalleryMediaInfo()
                GalleryItem(
                    mimeType = null,
                    image = galleryMedia?.let {
                        GalleryImage(it.height, it.width, it.url, null)
                    },
                    previews = null,
                    id = mediaId,
                )
            },
        )

    private fun GraphQlGalleryItem.toGalleryMediaInfo(): GalleryMediaInfo? {
        val galleryMedia: GraphQlGalleryMedia? = media
        val mediaUrl: String? = galleryMedia?.url?.takeIf { it.isNotBlank() }
        if (galleryMedia != null && mediaUrl != null) {
            return GalleryMediaInfo(
                url = mediaUrl,
                width = galleryMedia.dimensions?.width ?: galleryMedia.width,
                height = galleryMedia.dimensions?.height ?: galleryMedia.height,
            )
        }
        val stillSource: GraphQlMediaSource? = galleryMedia?.still?.source
        val stillUrl: String? = stillSource?.url?.takeIf { it.isNotBlank() }
        if (stillSource != null && stillUrl != null) {
            return GalleryMediaInfo(
                url = stillUrl,
                width = stillSource.resolvedWidth,
                height = stillSource.resolvedHeight,
            )
        }
        val legacyUrl: String = url?.takeIf { it.isNotBlank() } ?: return null
        return GalleryMediaInfo(legacyUrl, width, height)
    }

    private val GraphQlMediaSource.resolvedWidth: Int
        get() = dimensions?.width ?: width

    private val GraphQlMediaSource.resolvedHeight: Int
        get() = dimensions?.height ?: height

    private val GraphQlStreaming.resolvedWidth: Int
        get() = dimensions?.width ?: width

    private val GraphQlStreaming.resolvedHeight: Int
        get() = dimensions?.height ?: height

    private data class GalleryMediaInfo(
        val url: String,
        val width: Int,
        val height: Int,
    )

    private companion object {
        private val REDDIT_VIDEO_HOSTS: Set<String> =
            setOf("v.redd.it", "packaged-media.redd.it")
    }
}
