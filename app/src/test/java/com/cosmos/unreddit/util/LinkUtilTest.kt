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
}
