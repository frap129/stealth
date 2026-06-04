package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class MobileSpoofBackend(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val tokenUrl: String = TOKEN_URL,
) : AuthBackend {
    override val type: BackendType = BackendType.MOBILE_SPOOF

    companion object {
        private const val CLIENT_ID = "ohXpoqrZYub1kg"
        private const val TOKEN_URL = "https://accounts.reddit.com/auth/v2/oauth/access-token/loid"
    }

    override suspend fun fetchToken(device: RedditDevice): TokenResult =
        withContext(Dispatchers.IO) {
            val body =
                """{"scopes":["*"]}"""
                    .toRequestBody("application/json".toMediaType())

            val request =
                Request
                    .Builder()
                    .url(tokenUrl)
                    .post(body)
                    .header("Authorization", Credentials.basic(CLIENT_ID, ""))
                    .header("User-Agent", device.userAgent)
                    .header("x-reddit-device-id", device.uuid)
                    .header("x-reddit-qos", device.qos)
                    .header("x-reddit-retry", "algo=no-retry")
                    .header("x-reddit-compression", "1")
                    .header(device.codecs.substringBefore("="), device.codecs.substringAfter("="))
                    .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful || responseBody == null) {
                        return@withContext TokenResult.Failure(
                            code = response.code,
                            message = responseBody ?: "empty body",
                        )
                    }

                    val adapter = moshi.adapter(TokenResponse::class.java)
                    val tokenResponse =
                        adapter.fromJson(responseBody)
                            ?: return@withContext TokenResult.Failure(
                                code = response.code,
                                message = "null token response",
                            )

                    val spoofHeaders =
                        mapOf(
                            "User-Agent" to device.userAgent,
                            "x-reddit-device-id" to device.uuid,
                            "x-reddit-qos" to device.qos,
                            "x-reddit-retry" to "algo=no-retry",
                            "x-reddit-compression" to "1",
                            device.codecs.substringBefore("=") to device.codecs.substringAfter("="),
                        )

                    TokenResult.Success(
                        accessToken = tokenResponse.accessToken,
                        expiresInSeconds = tokenResponse.expiresIn,
                        loid = response.header("x-reddit-loid"),
                        session = response.header("x-reddit-session"),
                        spoofHeaders = spoofHeaders,
                    )
                }
            } catch (e: java.io.IOException) {
                TokenResult.Failure(code = -1, message = e.message ?: "network error")
            } catch (e: Exception) {
                TokenResult.Failure(code = -1, message = e.message ?: "unexpected error")
            }
        }
}
