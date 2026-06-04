package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

internal interface AuthBackend {
    val type: BackendType

    suspend fun fetchToken(device: RedditDevice): TokenResult
}

internal sealed class TokenResult {
    data class Success(
        val accessToken: String,
        val expiresInSeconds: Long,
        val loid: String?,
        val session: String?,
        val spoofHeaders: Map<String, String>,
    ) : TokenResult() {
        override fun toString(): String = "Success(expiresInSeconds=$expiresInSeconds)"
    }

    data class Failure(
        val code: Int,
        val message: String,
    ) : TokenResult()
}

@JsonClass(generateAdapter = true)
internal data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: Long,
) {
    override fun toString(): String = "TokenResponse(expiresIn=$expiresIn)"
}
