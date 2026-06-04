package com.cosmos.unreddit.data.remote.api.reddit.auth

import java.util.UUID
import kotlin.random.Random

internal class RedditDevice {
    val uuid: String = UUID.randomUUID().toString()
    val appVersion: String = RedditAppVersions.LIST[Random.nextInt(RedditAppVersions.LIST.size)]
    val androidVersion: Int = Random.nextInt(9, 15)
    val userAgent: String = "Reddit/$appVersion/Android $androidVersion"
    val qos: String = String.format(java.util.Locale.US, "%.3f", Random.nextDouble(1.0, 100.0))
    val codecs: String =
        buildString {
            append("available-codecs=video/avc, video/hevc")
            if (Random.nextBoolean()) append(", video/x-vnd.on2.vp9")
        }
}
