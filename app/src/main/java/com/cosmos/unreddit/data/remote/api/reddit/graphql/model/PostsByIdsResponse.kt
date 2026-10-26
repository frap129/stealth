package com.cosmos.unreddit.data.remote.api.reddit.graphql.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PostsByIdsResponse(
    val data: PostsByIdsData?,
    val errors: List<GraphQlError>? = null,
)

@JsonClass(generateAdapter = true)
data class PostsByIdsData(val postsInfoByIds: List<GraphQlPostNode?>? = null)

@JsonClass(generateAdapter = true)
data class GraphQlPostNode(
    @Json(name = "__typename") val typename: String? = null,
    val id: String? = null,
    val postTitle: String? = null,
    val permalink: String? = null,
    val url: String? = null,
    val domain: String? = null,
    val createdAt: String? = null,
    val editedAt: String? = null,
    val score: Int = 0,
    val upvoteRatio: Double? = null,
    val commentCount: Int = 0,
    val isSelfPost: Boolean = false,
    val isNsfw: Boolean = false,
    val isSpoiler: Boolean = false,
    val isStickied: Boolean = false,
    val isLocked: Boolean = false,
    val isArchived: Boolean = false,
    val distinguishedAs: String? = null,
    val suggestedCommentSort: String? = null,
    val content: GraphQlContent? = null,
    val flair: GraphQlFlair? = null,
    val authorFlair: GraphQlFlair? = null,
    val authorInfo: GraphQlAuthorInfo? = null,
    val awardings: List<GraphQlAwarding> = emptyList(),
    val media: GraphQlMedia? = null,
    val gallery: GraphQlGallery? = null,
    val subreddit: GraphQlSubredditRef? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlFlair(val text: String? = null)

@JsonClass(generateAdapter = true)
data class GraphQlMedia(
    val still: GraphQlStill? = null,
    val streaming: GraphQlStreaming? = null,
    val video: GraphQlStreaming? = null,
    val packagedMedia: GraphQlPackagedMedia? = null,
    val animated: GraphQlAnimatedMedia? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlAnimatedMedia(
    @Json(name = "mp4_source") val mp4Source: GraphQlMediaSource? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlStill(val source: GraphQlMediaSource? = null)

@JsonClass(generateAdapter = true)
data class GraphQlDimensions(val width: Int = 0, val height: Int = 0)

@JsonClass(generateAdapter = true)
data class GraphQlMediaSource(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val dimensions: GraphQlDimensions? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlStreaming(
    val dashUrl: String? = null,
    val hlsUrl: String? = null,
    val scrubberMediaUrl: String? = null,
    val isGif: Boolean = false,
    val duration: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val hasAudio: Boolean = false,
    val dimensions: GraphQlDimensions? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlPackagedMedia(val playbackMp4s: GraphQlPlaybackMp4s? = null)

@JsonClass(generateAdapter = true)
data class GraphQlPlaybackMp4s(
    val permutations: List<GraphQlPlaybackPermutation> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GraphQlPlaybackPermutation(
    val source: GraphQlMediaSource? = null,
    val bitrateBps: Int = 0,
)

@JsonClass(generateAdapter = true)
data class GraphQlGallery(val items: List<GraphQlGalleryItem> = emptyList())

@JsonClass(generateAdapter = true)
data class GraphQlGalleryItem(
    val id: String? = null,
    val mediaId: String? = null,
    val caption: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val media: GraphQlGalleryMedia? = null,
)

@JsonClass(generateAdapter = true)
data class GraphQlGalleryMedia(
    val id: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val still: GraphQlStill? = null,
    val dimensions: GraphQlDimensions? = null,
)
