package com.cosmos.unreddit.util

import android.content.Context
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache

internal data class PlayerTrackConfig(
    val highQualityStart: Boolean,
    val initialBitrateEstimate: Long = 0L,
    val minDurationForQualityIncreaseMs: Int = 0,
    val maxDurationForQualityDecreaseMs: Int = 0,
    val minDurationToRetainAfterDiscardMs: Int = 0,
    val bandwidthFraction: Float = 0f,
)

internal fun playerTrackConfig(url: String): PlayerTrackConfig =
    if (LinkUtil.isRedditStreamingManifest(url)) {
        PlayerTrackConfig(
            highQualityStart = true,
            initialBitrateEstimate = HIGH_INITIAL_BITRATE_ESTIMATE,
            minDurationForQualityIncreaseMs = MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            maxDurationForQualityDecreaseMs = MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            minDurationToRetainAfterDiscardMs = MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            bandwidthFraction = BANDWIDTH_FRACTION,
        )
    } else {
        PlayerTrackConfig(highQualityStart = false)
    }

private const val HIGH_INITIAL_BITRATE_ESTIMATE: Long = 10_000_000L
private const val MIN_DURATION_FOR_QUALITY_INCREASE_MS: Int = 0
private const val MAX_DURATION_FOR_QUALITY_DECREASE_MS: Int = 25_000
private const val MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS: Int = 25_000
private const val BANDWIDTH_FRACTION: Float = 0.7f

class ExoPlayerHelper(context: Context) {

    private val cacheDir = context.cacheDir

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent(LinkUtil.USER_AGENT)

    private val exoDatabaseProvider = ExoDatabaseProvider(context)

    private val simpleCache = SimpleCache(
        cacheDir,
        LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024),
        exoDatabaseProvider
    )

    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(simpleCache)
        .setUpstreamDataSourceFactory(httpDataSourceFactory)

    val defaultMediaSourceFactory by lazy { DefaultMediaSourceFactory(cacheDataSourceFactory) }

    fun createPlayer(context: Context, url: String): SimpleExoPlayer {
        val config = playerTrackConfig(url)
        val builder = SimpleExoPlayer.Builder(context)
            .setMediaSourceFactory(defaultMediaSourceFactory)
        if (config.highQualityStart) {
            // Reddit ladders are bitrate-monotonic; start at the best rendition, then adapt.
            val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
                .setInitialBitrateEstimate(config.initialBitrateEstimate)
                .build()
            val trackSelector = DefaultTrackSelector(
                context,
                AdaptiveTrackSelection.Factory(
                    config.minDurationForQualityIncreaseMs,
                    config.maxDurationForQualityDecreaseMs,
                    config.minDurationToRetainAfterDiscardMs,
                    config.bandwidthFraction,
                ),
            )
            builder.setBandwidthMeter(bandwidthMeter)
            builder.setTrackSelector(trackSelector)
        }
        return builder.build()
    }

    fun setRequestProperties(requestProperties: Map<String, String>) {
        httpDataSourceFactory.setDefaultRequestProperties(requestProperties)
    }

    fun getMediaItem(url: String): MediaItem {
        return MediaItem.fromUri(url)
    }

    fun getMediaSource(url: String): MediaSource {
        return getMediaSource(getMediaItem(url))
    }

    fun getMediaSource(mediaItem: MediaItem): MediaSource {
        return defaultMediaSourceFactory.createMediaSource(mediaItem)
    }

    fun clearCache() {
        simpleCache.release()
        SimpleCache.delete(cacheDir, exoDatabaseProvider)
    }
}
