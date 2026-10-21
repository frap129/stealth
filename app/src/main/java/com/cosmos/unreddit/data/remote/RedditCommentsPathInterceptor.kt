package com.cosmos.unreddit.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reddit's edge blocks literal comment endpoints. Encoding the separator slash
 * in comment paths and /api/morechildren bypasses that block.
 */
class RedditCommentsPathInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val commentMatch = COMMENT_PATH.matchEntire(path)
        val encodedPath = when {
            path == MORE_CHILDREN_PATH -> MORE_CHILDREN_ENCODED_PATH
            commentMatch != null ->
                "/r/${commentMatch.groupValues[1]}%2Fcomments${commentMatch.groupValues[2]}"
            else -> return chain.proceed(request)
        }

        val newUrl = request.url.newBuilder()
            .encodedPath(encodedPath)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    companion object {
        private const val MORE_CHILDREN_PATH = "/api/morechildren"
        private const val MORE_CHILDREN_ENCODED_PATH = "/api%2Fmorechildren"
        private val COMMENT_PATH = Regex("^/r/([^/]+)/comments(/.*)?$")
    }
}
