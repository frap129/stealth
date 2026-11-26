package com.cosmos.unreddit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkUtilTest {
    @Test
    fun `returns audio track for a legacy reddit video url`() {
        val inputLink: String = "https://v.redd.it/video/CMAF_96.mp4"

        val actualSoundTrack: String? = LinkUtil.getRedditSoundTrack(inputLink)

        assertEquals("https://v.redd.it/video/CMAF_AUDIO_128.mp4", actualSoundTrack)
    }

    @Test
    fun `returns audio track for a legacy dash url and preserves query`() {
        val inputLink: String = "https://v.redd.it/video/DASH_720.mp4?foo=bar"

        val actualSoundTrack: String? = LinkUtil.getRedditSoundTrack(inputLink)

        assertEquals("https://v.redd.it/video/DASH_AUDIO_128.mp4?foo=bar", actualSoundTrack)
    }

    @Test
    fun `returns null audio track for packaged media with embedded audio`() {
        val inputLink: String =
            "https://packaged-media.redd.it/video/pb/m2-vp9-res_456p.mp4?m=DASHPlaylist.mpd"

        val actualSoundTrack: String? = LinkUtil.getRedditSoundTrack(inputLink)

        assertNull(actualSoundTrack)
    }

    @Test
    fun `returns null audio track for blank and non-video urls`() {
        assertNull(LinkUtil.getRedditSoundTrack(""))
        assertNull(LinkUtil.getRedditSoundTrack("https://example.com/video.mp4"))
    }

    @Test
    fun `ignores video-like query parameters in packaged media urls`() {
        val inputLink: String =
            "https://packaged-media.redd.it/video/pb/m2-res_220p.mp4?m=DASH_720"

        val actualSoundTrack: String? = LinkUtil.getRedditSoundTrack(inputLink)

        assertNull(actualSoundTrack)
    }

    @Test
    fun `detects dash and hls streaming manifests`() {
        assertTrue(
            LinkUtil.isStreamingManifest(
                "https://v.redd.it/video/DASHPlaylist.mpd?f=sd",
            ),
        )
        assertTrue(
            LinkUtil.isStreamingManifest(
                "https://v.redd.it/video/HLSPlaylist.m3u8?f=sd",
            ),
        )
        assertFalse(
            LinkUtil.isStreamingManifest(
                "https://packaged-media.redd.it/video/pb/m2-vp9-res_456p.mp4",
            ),
        )
        assertFalse(LinkUtil.isStreamingManifest(""))
    }

    @Test
    fun `detects animated reddit mp4 urls on preview hosts`() {
        val inputPreviousHostLink: String =
            "https://preview.redd.it/5apc9mfquqyd1.gif?width=640&format=mp4&s=abc"
        val inputCurrentHostLink: String =
            "https://cf.preview.redd.it/5apc9mfquqyd1.gif?width=640&format=mp4&s=abc"

        val actualPreviousHostResult: Boolean = LinkUtil.isAnimatedRedditMp4(inputPreviousHostLink)
        val actualCurrentHostResult: Boolean = LinkUtil.isAnimatedRedditMp4(inputCurrentHostLink)

        assertTrue(actualPreviousHostResult)
        assertTrue(actualCurrentHostResult)
    }

    @Test
    fun `rejects non animated reddit mp4 urls`() {
        assertFalse(LinkUtil.isAnimatedRedditMp4("https://preview.redd.it/a.gif?format=png8"))
        assertFalse(LinkUtil.isAnimatedRedditMp4("https://cf.preview.redd.it/a.gif?format=png8"))
        assertFalse(LinkUtil.isAnimatedRedditMp4("https://example.com/a.gif?format=mp4"))
        assertFalse(LinkUtil.isAnimatedRedditMp4("https://preview.redd.it/a.mp4?format=mp4"))
        assertFalse(LinkUtil.isAnimatedRedditMp4(""))
    }

    @Test
    fun `accepts reddit preview hosts and rejects lookalikes`() {
        assertTrue(LinkUtil.isRedditPreviewUrl("https://preview.redd.it/a.gif"))
        assertTrue(LinkUtil.isRedditPreviewUrl("https://cf.preview.redd.it/a.gif"))
        assertFalse(LinkUtil.isRedditPreviewUrl("https://preview.redd.it.example.com/a.gif"))
        assertFalse(LinkUtil.isRedditPreviewUrl("https://evilpreview.redd.it/a.gif"))
        assertFalse(LinkUtil.isRedditPreviewUrl(""))
    }

    @Test
    fun `detects reddit streaming manifests and rejects external hosts`() {
        assertTrue(
            LinkUtil.isRedditStreamingManifest(
                "https://v.redd.it/video/HLSPlaylist.m3u8?f=hd",
            ),
        )
        assertTrue(
            LinkUtil.isRedditStreamingManifest(
                "https://v.redd.it/video/DASHPlaylist.mpd?f=hd",
            ),
        )
        assertFalse(
            LinkUtil.isRedditStreamingManifest(
                "https://example.com/video/HLSPlaylist.m3u8",
            ),
        )
        assertFalse(
            LinkUtil.isRedditStreamingManifest(
                "https://v.redd.it/video/CMAF_720.mp4",
            ),
        )
        assertFalse(LinkUtil.isRedditStreamingManifest(""))
    }

    @Test
    fun `builds dash fallback from reddit hls preserving query`() {
        val inputLink: String =
            "https://v.redd.it/video/HLSPlaylist.m3u8?f=hd&a=token"
        val expectedLink: String =
            "https://v.redd.it/video/DASHPlaylist.mpd?f=hd&a=token"

        val actualLink: String? = LinkUtil.getRedditDashFallback(inputLink)

        assertEquals(expectedLink, actualLink)
    }

    @Test
    fun `returns null dash fallback for non reddit or non hls urls`() {
        assertNull(
            LinkUtil.getRedditDashFallback(
                "https://v.redd.it/video/DASHPlaylist.mpd?f=hd",
            ),
        )
        assertNull(
            LinkUtil.getRedditDashFallback(
                "https://example.com/video/HLSPlaylist.m3u8",
            ),
        )
        assertNull(LinkUtil.getRedditDashFallback(""))
    }

    @Test
    fun `detects gif urls without mistaking mp4 responses for gifs`() {
        assertTrue(LinkUtil.isGifUrl("https://i.redd.it/a.gif"))
        assertTrue(LinkUtil.isGifUrl("https://cf.preview.redd.it/a.gif?auto=webp&s=abc"))
        assertFalse(LinkUtil.isGifUrl("https://cf.preview.redd.it/a.gif?format=mp4&s=abc"))
        assertFalse(LinkUtil.isGifUrl("https://i.redd.it/a.gifv"))
        assertFalse(LinkUtil.isGifUrl("https://i.redd.it/a.mp4"))
        assertFalse(LinkUtil.isGifUrl(""))
    }
}
