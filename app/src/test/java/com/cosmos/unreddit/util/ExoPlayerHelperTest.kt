package com.cosmos.unreddit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExoPlayerHelperTest {
    @Test
    fun `uses high quality start for reddit manifests`() {
        val expectedConfig: PlayerTrackConfig =
            PlayerTrackConfig(
                highQualityStart = true,
                initialBitrateEstimate = 10_000_000L,
                minDurationForQualityIncreaseMs = 0,
                maxDurationForQualityDecreaseMs = 25_000,
                minDurationToRetainAfterDiscardMs = 25_000,
                bandwidthFraction = 0.7f,
            )

        val actualHlsConfig: PlayerTrackConfig =
            playerTrackConfig("https://v.redd.it/video/HLSPlaylist.m3u8?f=hd")
        val actualDashConfig: PlayerTrackConfig =
            playerTrackConfig("https://v.redd.it/video/DASHPlaylist.mpd?f=hd")

        assertEquals(expectedConfig, actualHlsConfig)
        assertEquals(expectedConfig, actualDashConfig)
    }

    @Test
    fun `uses default track selection for packaged and external streams`() {
        val expectedConfig: PlayerTrackConfig =
            PlayerTrackConfig(highQualityStart = false)

        val actualPackagedConfig: PlayerTrackConfig =
            playerTrackConfig("https://packaged-media.redd.it/video/pb/m2-res_720p.mp4")
        val actualExternalConfig: PlayerTrackConfig =
            playerTrackConfig("https://example.com/video/HLSPlaylist.m3u8")

        assertEquals(expectedConfig, actualPackagedConfig)
        assertEquals(expectedConfig, actualExternalConfig)
    }
}
