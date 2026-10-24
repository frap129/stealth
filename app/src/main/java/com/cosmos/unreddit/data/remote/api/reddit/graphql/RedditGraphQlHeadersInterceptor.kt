package com.cosmos.unreddit.data.remote.api.reddit.graphql

import android.content.Context
import android.os.Build
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds Reddit GraphQL fingerprint headers to requests targeting [graphQlHost].
 *
 * Must be registered AFTER RedditAuthInterceptor so these header overrides replace the
 * auth session's spoofed User-Agent, QoS and Origin values.
 */
class RedditGraphQlHeadersInterceptor(
    private val context: Context,
    private val graphQlHost: String = GRAPHQL_HOST,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != graphQlHost) return chain.proceed(request)
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val newRequest =
            request.newBuilder()
                .header("User-Agent", GRAPHQL_USER_AGENT)
                .header("Accept-Language", Locale.getDefault().toLanguageTag())
                .header("x-reddit-compression", "1")
                .header("X-Reddit-Direction", "graphql-go")
                .header("x-reddit-dpr", density.toString())
                .header("x-reddit-width", (metrics.widthPixels / density).toInt().toString())
                .header("x-reddit-height", (metrics.heightPixels / density).toInt().toString())
                .header("Device-Name", "${Build.MANUFACTURER};${Build.MODEL}")
                .header("X-Reddit-Http-QoS", "GOOD")
                .header("X-Reddit-Device-Tier", "v2t2")
                .header("X-Reddit-Media-Codecs", "available-codecs=video/avc, video/hevc, video/x-vnd.on2.vp9")
                .header("X-Reddit-QoS", "down-rate-mbps=55.000")
                .header("Origin", "https://gql-fed.reddit.com")
                .build()
        return chain.proceed(newRequest)
    }

    companion object {
        const val GRAPHQL_HOST = "gql-fed.reddit.com"
        val GRAPHQL_USER_AGENT = "Reddit/Version 2026.42.0/Build 2642001/Android ${Build.VERSION.RELEASE}"
    }
}
