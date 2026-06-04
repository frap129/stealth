package com.cosmos.unreddit.data.remote.api.reddit.auth

data class AuthSession(
    val version: Long,
    val accessToken: String,
    val expiresAt: Long,
    val backendType: BackendType,
    val spoofHeaders: Map<String, String>,
    val loid: String?,
    val session: String?,
) {
    override fun toString(): String = "AuthSession(version=$version, expiresAt=$expiresAt, backendType=$backendType)"
}

enum class BackendType {
    MOBILE_SPOOF,
    GENERIC_WEB,
}
