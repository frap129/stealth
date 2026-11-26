package com.cosmos.unreddit.ui.mediaviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashFallbackStateTest {
    @Test
    fun `returns the dash fallback only once`() {
        val inputUrl: String =
            "https://v.redd.it/video/HLSPlaylist.m3u8?f=hd&a=token"
        val expectedUrl: String =
            "https://v.redd.it/video/DASHPlaylist.mpd?f=hd&a=token"
        val state: DashFallbackState = DashFallbackState()

        val actualFirstUrl: String? = state.getUrl(inputUrl)
        val actualSecondUrl: String? = state.getUrl(inputUrl)

        assertEquals(expectedUrl, actualFirstUrl)
        assertNull(actualSecondUrl)
    }

    @Test
    fun `does not return a fallback for non reddit hls urls`() {
        val state: DashFallbackState = DashFallbackState()

        assertNull(state.getUrl("https://example.com/video/HLSPlaylist.m3u8"))
        assertNull(state.getUrl("https://v.redd.it/video/DASHPlaylist.mpd"))
    }
}
