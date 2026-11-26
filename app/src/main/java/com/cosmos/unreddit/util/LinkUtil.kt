package com.cosmos.unreddit.util

import android.webkit.MimeTypeMap
import com.cosmos.unreddit.data.model.MediaType
import com.cosmos.unreddit.data.remote.api.imgur.model.Image
import com.cosmos.unreddit.util.extension.extension
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object LinkUtil {

    const val USER_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:15.0) Gecko/20100101 Firefox/15.0.1"

    private val HTTP_REGEX = Regex("^\\bhttp\\b")

    private val GIF_REGEX = Regex("gif(v)?")
    private val REDDIT_VIDEO_REGEX = Regex("(DASH|CMAF)_(\\d+)")

    private val SUBREDDIT_REGEX = Regex("/r/[A-Za-z0-9_-]{3,21}")
    private val USER_REGEX = Regex("/u/[A-Za-z0-9_-]{3,20}")

    private val REDDIT_LINK = Regex("(.+?)\\.reddit\\.com")
    private val IMGUR_LINK = Regex("([im]\\.)?(stack\\.)?imgur\\.(com|io)")
    private val GFYCAT_LINK = Regex("(.+?\\.)?gfycat\\.com")
    private val REDGIFS_LINK = Regex("(.+?\\.)?redgifs\\.com")
    private val STREAMABLE_LINK = Regex("(.+?)\\.streamable\\.com")

    private const val REDDIT_SOUNDTRACK_NAME: String = "_AUDIO_128"
    private const val MANIFEST_MPD_EXTENSION: String = ".mpd"
    private const val MANIFEST_HLS_EXTENSION: String = ".m3u8"
    private const val REDDIT_VIDEO_HOST: String = "v.redd.it"
    private const val REDDIT_PACKAGED_MEDIA_HOST: String = "packaged-media.redd.it"
    private const val DASH_PLAYLIST_NAME: String = "DASHPlaylist.mpd"
    private const val HLS_PLAYLIST_NAME: String = "HLSPlaylist.m3u8"
    private const val REDDIT_PREVIEW_DOMAIN: String = "preview.redd.it"
    private const val GIF_PATH_EXTENSION: String = ".gif"
    private const val ANIMATED_MP4_FORMAT: String = "mp4"
    private val REDDIT_VIDEO_HOSTS: Set<String> =
        setOf(REDDIT_VIDEO_HOST, REDDIT_PACKAGED_MEDIA_HOST)

    val String.https: String
        get() = this.replace(HTTP_REGEX, "https")

    fun getImageIdFromImgurLink(link: String): String {
        return link.toHttpUrlOrNull()?.pathSegments?.getOrNull(0) ?: ""
    }

    fun getAlbumIdFromImgurLink(link: String): String {
        return link.toHttpUrlOrNull()?.pathSegments?.getOrNull(1) ?: ""
    }

    fun getUrlFromImgurImage(image: Image, convertToMp4: Boolean = true): String {
        val ext = if (convertToMp4 && image.ext.contains(GIF_REGEX)) {
            ".mp4"
        } else {
            image.ext
        }
        return getUrlFromImgurId(image.hash, ext)
    }

    fun getUrlFromImgurId(hash: String, extension: String = ".jpeg"): String {
        return "https://i.imgur.com/$hash$extension"
    }

    fun getImgurVideo(link: String): String {
        return link.replace(GIF_REGEX, "mp4")
    }

    /**
     * Returns the separate audio track for legacy DASH/CMAF Reddit videos.
     * Packaged-media MP4s contain embedded audio, so null means no extra track is needed.
     */
    fun getRedditSoundTrack(link: String): String? {
        val url = link.toHttpUrlOrNull() ?: return null
        val match = REDDIT_VIDEO_REGEX.find(url.encodedPath) ?: return null

        val format = match.groupValues[1]
        val audioPath = url.encodedPath.replace(
            REDDIT_VIDEO_REGEX,
            "$format$REDDIT_SOUNDTRACK_NAME",
        )
        return url.newBuilder().encodedPath(audioPath).build().toString()
    }

    fun isRedditSoundTrack(link: String): Boolean {
        return link.contains(REDDIT_SOUNDTRACK_NAME)
    }

    fun isStreamingManifest(link: String): Boolean {
        val path = link.toHttpUrlOrNull()?.encodedPath ?: return false
        return path.endsWith(MANIFEST_MPD_EXTENSION, ignoreCase = true) ||
            path.endsWith(MANIFEST_HLS_EXTENSION, ignoreCase = true)
    }

    fun isRedditVideoHost(link: String): Boolean =
        link.toHttpUrlOrNull()?.host in REDDIT_VIDEO_HOSTS

    fun isRedditStreamingManifest(link: String): Boolean {
        val url = link.toHttpUrlOrNull() ?: return false
        if (url.host !in REDDIT_VIDEO_HOSTS) return false
        return url.encodedPath.endsWith(MANIFEST_MPD_EXTENSION, ignoreCase = true) ||
            url.encodedPath.endsWith(MANIFEST_HLS_EXTENSION, ignoreCase = true)
    }

    fun getRedditDashFallback(link: String): String? {
        val url = link.toHttpUrlOrNull() ?: return null
        if (url.host != REDDIT_VIDEO_HOST) return null
        if (!url.encodedPath.endsWith(HLS_PLAYLIST_NAME, ignoreCase = true)) return null
        // Reddit accepts the same signed query on the DASH manifest.
        val dashPath = url.encodedPath.dropLast(HLS_PLAYLIST_NAME.length) + DASH_PLAYLIST_NAME
        return url.newBuilder().encodedPath(dashPath).build().toString()
    }

    fun isRedditPreviewUrl(link: String): Boolean {
        val url: HttpUrl = link.toHttpUrlOrNull() ?: return false
        return url.isRedditPreview()
    }

    fun isGifUrl(link: String): Boolean {
        val url: HttpUrl = link.toHttpUrlOrNull() ?: return false
        val isMp4: Boolean =
            url.queryParameter("format").equals(ANIMATED_MP4_FORMAT, ignoreCase = true)
        return url.encodedPath.endsWith(GIF_PATH_EXTENSION, ignoreCase = true) && !isMp4
    }

    fun isAnimatedRedditMp4(link: String): Boolean {
        val url: HttpUrl = link.toHttpUrlOrNull() ?: return false
        return url.isRedditPreview() &&
            url.encodedPath.endsWith(GIF_PATH_EXTENSION, ignoreCase = true) &&
            url.queryParameter("format").equals(ANIMATED_MP4_FORMAT, ignoreCase = true)
    }

    private fun HttpUrl.isRedditPreview(): Boolean =
        host == REDDIT_PREVIEW_DOMAIN || host.endsWith(".$REDDIT_PREVIEW_DOMAIN")

    fun getGfycatId(link: String): String {
        return link.toHttpUrlOrNull()?.pathSegments?.lastOrNull() ?: return link
    }

    fun getStreamableShortcode(link: String): String {
        return link.toHttpUrlOrNull()?.pathSegments?.getOrNull(0) ?: ""
    }

    fun getLinkType(link: String): MediaType {
        when {
            link.matches(SUBREDDIT_REGEX) -> return MediaType.REDDIT_SUBREDDIT
            link.matches(USER_REGEX) -> return MediaType.REDDIT_USER
            link.startsWith("/r/") -> return MediaType.REDDIT_PERMALINK
        }

        val httpUrl = link.toHttpUrlOrNull() ?: return MediaType.NO_MEDIA
        val domain = httpUrl.host
        val extension by lazy { link.extension }
        val mime by lazy { MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "" }

        return when {
            domain.matches(REDDIT_LINK) -> {
                if (httpUrl.pathSegments.contains("wiki")) {
                    // TODO: Handle Wiki links
                    MediaType.REDDIT_WIKI
                } else if (httpUrl.pathSegments.contains("poll")) {
                    MediaType.REDDIT_POLL
                } else {
                    MediaType.REDDIT_LINK
                }
            }

            domain.matches(IMGUR_LINK) -> {
                when {
                    link.contains("/a/") -> MediaType.IMGUR_ALBUM
                    link.contains("/gallery/") -> MediaType.IMGUR_GALLERY
                    extension.contains("gif") -> MediaType.IMGUR_GIF
                    mime.startsWith("video") -> MediaType.IMGUR_VIDEO
                    mime.startsWith("image") -> MediaType.IMGUR_IMAGE
                    else -> MediaType.IMGUR_LINK
                }
            }

            domain.matches(GFYCAT_LINK) -> MediaType.GFYCAT

            domain.matches(REDGIFS_LINK) -> MediaType.REDGIFS

            domain.matches(STREAMABLE_LINK) -> MediaType.STREAMABLE

            mime.startsWith("image") -> MediaType.IMAGE

            mime.startsWith("video") -> MediaType.VIDEO

            else -> MediaType.LINK
        }
    }

    fun getPermalinkFromMediaUrl(link: String): String {
        return link.toHttpUrlOrNull()?.pathSegments?.lastOrNull() ?: link
    }
}
