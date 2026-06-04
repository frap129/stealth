package com.cosmos.unreddit.data.remote.api.reddit.auth
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.random.Random

internal class GenericWebBackend(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val tokenUrl: String = TOKEN_URL,
) : AuthBackend {
    override val type: BackendType = BackendType.GENERIC_WEB

    override suspend fun fetchToken(device: RedditDevice): TokenResult =
        withContext(Dispatchers.IO) {
            val deviceId = generateDeviceId()

            val body =
                FormBody
                    .Builder()
                    .add("grant_type", GRANT_TYPE)
                    .add("device_id", deviceId)
                    .build()

            val request =
                Request
                    .Builder()
                    .url(tokenUrl)
                    .post(body)
                    .header("Authorization", Credentials.basic(CLIENT_ID, ""))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Origin", "https://www.reddit.com")
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

                    TokenResult.Success(
                        accessToken = tokenResponse.accessToken,
                        expiresInSeconds = tokenResponse.expiresIn,
                        loid = response.header("x-reddit-loid"),
                        session = response.header("x-reddit-session"),
                        spoofHeaders =
                            mapOf(
                                "User-Agent" to BROWSER_USER_AGENT,
                                "Origin" to "https://www.reddit.com",
                            ),
                    )
                }
            } catch (e: java.io.IOException) {
                TokenResult.Failure(code = -1, message = e.message ?: "network error")
            } catch (e: Exception) {
                TokenResult.Failure(code = -1, message = e.message ?: "unexpected error")
            }
        }

    companion object {
        private const val CLIENT_ID = "3XfBJWliHvqACnXrfIYlLw"
        private const val TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
        private const val GRANT_TYPE = "https://oauth.reddit.com/grants/installed_client"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private val ALPHANUMERIC = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        private fun generateDeviceId(): String = (1..20).map { ALPHANUMERIC[Random.nextInt(ALPHANUMERIC.size)] }.joinToString("")
    }
}
