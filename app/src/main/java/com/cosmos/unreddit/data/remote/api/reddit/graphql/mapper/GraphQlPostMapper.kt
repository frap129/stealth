package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGallery
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlGalleryItem
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMedia
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlMediaSource
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
import javax.inject.Inject

class GraphQlPostMapper @Inject constructor() {
    fun toPostData(node: GraphQlPostNode): PostData =
        PostData(
            subreddit = node.subreddit?.name.orEmpty(),
            linkFlairRichText = emptyList(),
            authorFlairRichText = null,
            title = node.postTitle,
            prefixedSubreddit = node.subreddit?.prefixedName ?: "r/${node.subreddit?.name.orEmpty()}",
            name = node.id,
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
            suggestedSort = node.suggestedCommentSort,
            isArchived = node.isArchived,
            isOver18 = node.isNsfw,
            mediaPreview = node.media?.toPreview(),
            awardings = emptyList(),
            isSpoiler = node.isSpoiler,
            isLocked = node.isLocked,
            distinguished = node.distinguishedAs,
            author = node.authorInfo?.name.orEmpty(),
            commentsNumber = node.commentCount,
            permalink = node.permalink,
            isStickied = node.isStickied,
            url = node.url,
            created = GraphQlTimestamp.toEpochSeconds(node.createdAt),
            media = node.media?.toMedia(),
            mediaMetadata = node.gallery?.toMediaMetadata(),
            isRedditGallery = node.gallery != null,
            isVideo = node.media?.streaming != null || node.media?.video != null,
        )

    private fun GraphQlMedia.toPreview(): MediaPreview? {
        val source: GraphQlMediaSource = still?.source ?: return null
        return MediaPreview(
            images = listOf(
                Image(
                    imageSource = ImageSource(source.url, source.width, source.height),
                    resolutions = emptyList(),
                    variants = null,
                ),
            ),
            videoPreview = null,
        )
    }

    private fun GraphQlMedia.toMedia(): Media? {
        val video: GraphQlStreaming = streaming ?: video ?: return null
        val fallbackUrl: String = video.dashUrl?.takeIf { it.isNotBlank() }
            ?: video.hlsUrl?.takeIf { it.isNotBlank() }
            ?: return null
        return Media(
            type = "video",
            embed = null,
            redditVideoPreview = RedditVideoPreview(
                fallbackUrl = fallbackUrl,
                width = video.width,
                height = video.height,
                duration = video.duration,
                isGif = false,
            ),
        )
    }

    private fun GraphQlGallery.toGalleryData(): GalleryData =
        GalleryData(
            items = items.mapNotNull { item: GraphQlGalleryItem ->
                item.mediaId?.let { mediaId: String -> GalleryDataItem(item.caption, mediaId) }
            },
        )

    private fun GraphQlGallery.toMediaMetadata(): MediaMetadata =
        MediaMetadata(
            items = items.mapNotNull { item: GraphQlGalleryItem ->
                item.mediaId?.let { mediaId: String ->
                    GalleryItem(
                        mimeType = null,
                        image = item.url?.let { url: String -> GalleryImage(item.height, item.width, url, null) },
                        previews = null,
                        id = mediaId,
                    )
                }
            },
        )
}
