# Reddit OAuth Authentication Implementation Plan

> **For agentic workers:** REQUIRED: Use subagent-driven-development (if subagents available). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace broken unauthenticated Reddit API calls with anonymous OAuth-authenticated calls via `oauth.reddit.com`, porting Redlib's impersonation strategy.

**Architecture:** New auth layer between OkHttp and Retrofit. `RedditAuthInterceptor` acquires anonymous Bearer tokens from Reddit's public OAuth endpoints via `RedditTokenManager`, attaches spoofed device headers, handles 401 retry, and falls back across backends (MobileSpoof -> GenericWeb -> EXHAUSTED). TLS fingerprint spoofed via `impersonator-bctls` to mimic Chrome on Android. All in-memory, no persistence.

**Tech Stack:** Kotlin 1.7.20, OkHttp 4.x, Retrofit 2.9.0, Moshi 1.14.0 (codegen), Hilt 2.44.2, impersonator-bctls 1.0.12, JUnit 4, MockWebServer, kotlinx-coroutines-test

---

## File Structure

### New Files

All paths relative to `app/src/main/java/com/cosmos/unreddit/`

| File | Responsibility |
|------|---------------|
| `data/remote/api/reddit/auth/AuthSession.kt` | Immutable auth state snapshot + `BackendType` enum |
| `data/remote/api/reddit/auth/AuthBackend.kt` | `AuthBackend` interface + `TokenResult` data class + `TokenResponse` Moshi model |
| `data/remote/api/reddit/auth/RedditAppVersions.kt` | 150 real Android Reddit app version strings (from Redlib) |
| `data/remote/api/reddit/auth/RedditDevice.kt` | Spoofed mobile device identity generator |
| `data/remote/api/reddit/auth/RedditTlsFactory.kt` | Chrome-on-Android TLS fingerprint spoofing via impersonator-bctls |
| `data/remote/api/reddit/auth/MobileSpoofBackend.kt` | Token acquisition via `POST /auth/v2/oauth/access-token/loid` |
| `data/remote/api/reddit/auth/GenericWebBackend.kt` | Token acquisition via `POST /api/v1/access_token` |
| `data/remote/api/reddit/auth/RedditTokenManager.kt` | Token lifecycle, single-flight Mutex, fallback state machine, proactive refresh |
| `data/remote/api/reddit/RedditAuthInterceptor.kt` | OkHttp interceptor: Bearer header + spoofed headers + 401 retry + rate limit |

### Test Files

All paths relative to `app/src/test/java/com/cosmos/unreddit/`

| File | Tests |
|------|-------|
| `data/remote/api/reddit/auth/RedditDeviceTest.kt` | Device identity format validation |
| `data/remote/api/reddit/auth/MobileSpoofBackendTest.kt` | Request format + response parsing via MockWebServer |
| `data/remote/api/reddit/auth/GenericWebBackendTest.kt` | Request format + response parsing via MockWebServer |
| `data/remote/api/reddit/auth/RedditTokenManagerTest.kt` | State machine transitions, caching, fallback behavior |

### Test Support Files

| File | Purpose |
|------|---------|
| `app/src/test/java/android/util/Log.java` | Shadow class for `android.util.Log` — enables JVM unit tests without Robolectric |

### Modified Files

| File | Change |
|------|--------|
| `buildSrc/src/main/kotlin/Dependencies.kt` | Add version constants for new deps |
| `app/build.gradle.kts` | Add impersonator-bctls + test dependencies |
| `app/src/.../reddit/RedditApi.kt` | Add `BASE_URL_OAUTH` constant |
| `app/src/.../di/NetworkModule.kt` | Add `@AuthRedditOkHttp` qualifier + provider; rewire `@RedditOfficial` to use it |
| `app/proguard-rules.pro` | Add BouncyCastle + impersonator keep rules |

---

## Chunk 1: Foundation

### Task 1: Add Dependencies

**Files:**

- Modify: `buildSrc/src/main/kotlin/Dependencies.kt:50-55`
- Modify: `app/build.gradle.kts:126-150`

- [ ] **Step 1: Add version constants to Dependencies.kt**

Add new version constants after the existing `coroutines` line (line 50):

```kotlin
const val coroutines = "1.4.1"

const val impersonatorBctls = "1.0.12"

const val coroutinesTest = "1.6.4"
const val mockWebServer = "4.10.0"
const val mockk = "1.13.3"
```

- [ ] **Step 2: Add implementation dependency in build.gradle.kts**

After the existing `okio` line (line 133), add this single new line:

```kotlin
implementation("com.github.zhkl0228:impersonator-bctls:${Dependencies.Versions.impersonatorBctls}")
```

- [ ] **Step 3: Add test dependencies in build.gradle.kts**

After the existing `testImplementation("junit:junit:...")` line (line 146), add these three new lines:

```kotlin
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Dependencies.Versions.coroutinesTest}")
testImplementation("com.squareup.okhttp3:mockwebserver:${Dependencies.Versions.mockWebServer}")
testImplementation("io.mockk:mockk:${Dependencies.Versions.mockk}")
```

- [ ] **Step 4: Verify Gradle sync**

Run: `cd /home/joe/software/stealth && ./gradlew :app:dependencies --configuration debugCompileClasspath 2>&1 | head -50`
Expected: Sync succeeds, `impersonator-bctls` appears in dependency tree

- [ ] **Step 5: Commit**

```bash
git add buildSrc/src/main/kotlin/Dependencies.kt app/build.gradle.kts
git commit -m "build: add impersonator-bctls and test dependencies"
```

---

### Task 2: Create AuthSession.kt

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/AuthSession.kt`

- [ ] **Step 1: Create AuthSession data class and BackendType enum**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

data class AuthSession(
    val version: Long,
    val accessToken: String,
    val expiresAt: Long,
    val backendType: BackendType,
    val spoofHeaders: Map<String, String>,
    val loid: String?,
    val session: String?
)

enum class BackendType {
    MOBILE_SPOOF,
    GENERIC_WEB
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/joe/software/stealth && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/AuthSession.kt
git commit -m "feat(auth): add AuthSession data class and BackendType enum"
```

---

### Task 3: Create RedditAppVersions.kt

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditAppVersions.kt`

- [ ] **Step 1: Create the version list object**

This is a static data file containing 150 real Android Reddit app version strings sourced from Redlib. Used by `RedditDevice` to spoof a realistic User-Agent.

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

internal object RedditAppVersions {
    val LIST: Array<String> = arrayOf(
        "Version 2024.22.1/Build 1652272",
        "Version 2024.23.1/Build 1665606",
        "Version 2024.24.1/Build 1682520",
        "Version 2024.25.0/Build 1693595",
        "Version 2024.25.2/Build 1700401",
        "Version 2024.25.3/Build 1703490",
        "Version 2024.26.0/Build 1710470",
        "Version 2024.26.1/Build 1717435",
        "Version 2024.28.0/Build 1737665",
        "Version 2024.28.1/Build 1741165",
        "Version 2024.30.0/Build 1770787",
        "Version 2024.31.0/Build 1786202",
        "Version 2024.32.0/Build 1809095",
        "Version 2024.32.1/Build 1813258",
        "Version 2024.33.0/Build 1819908",
        "Version 2024.34.0/Build 1837909",
        "Version 2024.35.0/Build 1861437",
        "Version 2024.36.0/Build 1875012",
        "Version 2024.37.0/Build 1888053",
        "Version 2024.38.0/Build 1902791",
        "Version 2024.39.0/Build 1916713",
        "Version 2024.40.0/Build 1928580",
        "Version 2024.41.0/Build 1941199",
        "Version 2024.41.1/Build 1947805",
        "Version 2024.42.0/Build 1952440",
        "Version 2024.43.0/Build 1972250",
        "Version 2024.44.0/Build 1988458",
        "Version 2024.45.0/Build 2001943",
        "Version 2024.46.0/Build 2012731",
        "Version 2024.47.0/Build 2029755",
        "Version 2023.48.0/Build 1319123",
        "Version 2023.49.0/Build 1321715",
        "Version 2023.49.1/Build 1322281",
        "Version 2023.50.0/Build 1332338",
        "Version 2023.50.1/Build 1345844",
        "Version 2024.02.0/Build 1368985",
        "Version 2024.03.0/Build 1379408",
        "Version 2024.04.0/Build 1391236",
        "Version 2024.05.0/Build 1403584",
        "Version 2024.06.0/Build 1418489",
        "Version 2024.07.0/Build 1429651",
        "Version 2024.08.0/Build 1439531",
        "Version 2024.10.0/Build 1470045",
        "Version 2024.10.1/Build 1478645",
        "Version 2024.11.0/Build 1480707",
        "Version 2024.12.0/Build 1494694",
        "Version 2024.13.0/Build 1505187",
        "Version 2024.14.0/Build 1520556",
        "Version 2024.15.0/Build 1536823",
        "Version 2024.16.0/Build 1551366",
        "Version 2024.17.0/Build 1568106",
        "Version 2024.18.0/Build 1577901",
        "Version 2024.18.1/Build 1585304",
        "Version 2024.19.0/Build 1593346",
        "Version 2024.20.0/Build 1612800",
        "Version 2024.20.1/Build 1615586",
        "Version 2024.20.2/Build 1624969",
        "Version 2024.20.3/Build 1624970",
        "Version 2024.21.0/Build 1631686",
        "Version 2024.22.0/Build 1645257",
        "Version 2023.21.0/Build 956283",
        "Version 2023.22.0/Build 968223",
        "Version 2023.23.0/Build 983896",
        "Version 2023.24.0/Build 998541",
        "Version 2023.25.0/Build 1014750",
        "Version 2023.25.1/Build 1018737",
        "Version 2023.26.0/Build 1019073",
        "Version 2023.27.0/Build 1031923",
        "Version 2023.28.0/Build 1046887",
        "Version 2023.29.0/Build 1059855",
        "Version 2023.30.0/Build 1078734",
        "Version 2023.31.0/Build 1091027",
        "Version 2023.32.0/Build 1109919",
        "Version 2023.32.1/Build 1114141",
        "Version 2023.33.1/Build 1129741",
        "Version 2023.34.0/Build 1144243",
        "Version 2023.35.0/Build 1157967",
        "Version 2023.36.0/Build 1168982",
        "Version 2023.37.0/Build 1182743",
        "Version 2023.38.0/Build 1198522",
        "Version 2023.39.0/Build 1211607",
        "Version 2023.39.1/Build 1221505",
        "Version 2023.40.0/Build 1221521",
        "Version 2023.41.0/Build 1233125",
        "Version 2023.41.1/Build 1239615",
        "Version 2023.42.0/Build 1245088",
        "Version 2023.43.0/Build 1257426",
        "Version 2023.44.0/Build 1268622",
        "Version 2023.45.0/Build 1281371",
        "Version 2023.47.0/Build 1303604",
        "Version 2022.42.0/Build 638508",
        "Version 2022.43.0/Build 648277",
        "Version 2022.44.0/Build 664348",
        "Version 2022.45.0/Build 677985",
        "Version 2023.01.0/Build 709875",
        "Version 2023.02.0/Build 717912",
        "Version 2023.03.0/Build 729220",
        "Version 2023.04.0/Build 744681",
        "Version 2023.05.0/Build 755453",
        "Version 2023.06.0/Build 775017",
        "Version 2023.07.0/Build 788827",
        "Version 2023.07.1/Build 790267",
        "Version 2023.08.0/Build 798718",
        "Version 2023.09.0/Build 812015",
        "Version 2023.09.1/Build 816833",
        "Version 2023.10.0/Build 821148",
        "Version 2023.11.0/Build 830610",
        "Version 2023.12.0/Build 841150",
        "Version 2023.13.0/Build 852246",
        "Version 2023.14.0/Build 861593",
        "Version 2023.14.1/Build 864826",
        "Version 2023.15.0/Build 870628",
        "Version 2023.16.0/Build 883294",
        "Version 2023.16.1/Build 886269",
        "Version 2023.17.0/Build 896030",
        "Version 2023.17.1/Build 900542",
        "Version 2023.18.0/Build 911877",
        "Version 2023.19.0/Build 927681",
        "Version 2023.20.0/Build 943980",
        "Version 2023.20.1/Build 946732",
        "Version 2022.20.0/Build 487703",
        "Version 2022.21.0/Build 492436",
        "Version 2022.22.0/Build 498700",
        "Version 2022.23.0/Build 502374",
        "Version 2022.23.1/Build 506606",
        "Version 2022.24.0/Build 510950",
        "Version 2022.24.1/Build 513462",
        "Version 2022.25.0/Build 515072",
        "Version 2022.25.1/Build 516394",
        "Version 2022.25.2/Build 519915",
        "Version 2022.26.0/Build 521193",
        "Version 2022.27.0/Build 527406",
        "Version 2022.27.1/Build 529687",
        "Version 2022.28.0/Build 533235",
        "Version 2022.30.0/Build 548620",
        "Version 2022.31.0/Build 556666",
        "Version 2022.31.1/Build 562612",
        "Version 2022.32.0/Build 567875",
        "Version 2022.33.0/Build 572600",
        "Version 2022.34.0/Build 579352",
        "Version 2022.35.0/Build 588016",
        "Version 2022.35.1/Build 589034",
        "Version 2022.36.0/Build 593102",
        "Version 2022.37.0/Build 601691",
        "Version 2022.38.0/Build 607460",
        "Version 2022.39.0/Build 615385",
        "Version 2022.39.1/Build 619019",
        "Version 2022.40.0/Build 624782",
        "Version 2022.41.0/Build 630468",
        "Version 2022.41.1/Build 634168"
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/joe/software/stealth && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditAppVersions.kt
git commit -m "feat(auth): add Reddit Android app version list from Redlib"
```

---

### Task 4: Create RedditDevice.kt + Test

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditDevice.kt`
- Create: `app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditDeviceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditDeviceTest {

    @Test
    fun `uuid is valid UUID format`() {
        val device = RedditDevice()
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue("UUID '${device.uuid}' doesn't match format", uuidRegex.matches(device.uuid))
    }

    @Test
    fun `appVersion is from RedditAppVersions list`() {
        val device = RedditDevice()
        assertTrue(
            "appVersion '${device.appVersion}' not in list",
            device.appVersion in RedditAppVersions.LIST
        )
    }

    @Test
    fun `userAgent contains Reddit prefix and Android version`() {
        val device = RedditDevice()
        assertTrue("UA should start with Reddit/", device.userAgent.startsWith("Reddit/"))
        assertTrue("UA should contain /Android ", "/Android " in device.userAgent)
    }

    @Test
    fun `androidVersion is between 9 and 14`() {
        val device = RedditDevice()
        assertTrue("Android version out of range", device.androidVersion in 9..14)
    }

    @Test
    fun `qos is a positive decimal string`() {
        val device = RedditDevice()
        val qosValue = device.qos.toDoubleOrNull()
        assertNotNull("qos should be a valid decimal", qosValue)
        assertTrue("qos should be positive", qosValue!! > 0)
    }

    @Test
    fun `codecs starts with available-codecs prefix`() {
        val device = RedditDevice()
        assertTrue(
            "codecs should start with 'available-codecs='",
            device.codecs.startsWith("available-codecs=")
        )
        assertTrue("codecs should contain video/avc", "video/avc" in device.codecs)
    }

    @Test
    fun `each instance generates unique values`() {
        val devices = (1..20).map { RedditDevice() }
        val uniqueUuids = devices.map { it.uuid }.toSet()
        assertEquals("Expected 20 unique UUIDs", 20, uniqueUuids.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.RedditDeviceTest" 2>&1 | tail -10`
Expected: FAIL — `RedditDevice` class not found

- [ ] **Step 3: Write the implementation**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import java.util.UUID
import kotlin.random.Random

internal class RedditDevice {
    val uuid: String = UUID.randomUUID().toString()
    val appVersion: String = RedditAppVersions.LIST[Random.nextInt(RedditAppVersions.LIST.size)]
    val androidVersion: Int = Random.nextInt(9, 15)
    val userAgent: String = "Reddit/$appVersion/Android $androidVersion"
    val qos: String = String.format(java.util.Locale.US, "%.3f", Random.nextDouble(1.0, 100.0))
    val codecs: String = buildString {
        append("available-codecs=video/avc, video/hevc")
        if (Random.nextBoolean()) append(", video/x-vnd.on2.vp9")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.RedditDeviceTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all 7 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditDevice.kt \
       app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditDeviceTest.kt
git commit -m "feat(auth): add RedditDevice identity generator with tests"
```

---

### Task 5: Create AuthBackend.kt

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/AuthBackend.kt`

This file contains three small types that are shared by both backends:
- `AuthBackend` interface (the contract each backend implements)
- `TokenResult` (the backend-agnostic result returned after a successful token acquisition)
- `TokenResponse` (Moshi model for deserializing Reddit's JSON token response)

- [ ] **Step 1: Create the interface and shared types**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient

internal interface AuthBackend {
    suspend fun acquireToken(httpClient: OkHttpClient): TokenResult
}

internal data class TokenResult(
    val accessToken: String,
    val expiresIn: Long,
    val spoofHeaders: Map<String, String>,
    val loid: String?,
    val session: String?
)

@JsonClass(generateAdapter = true)
internal data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "scope") val scope: String
)
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/joe/software/stealth && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (Moshi codegen generates `TokenResponseJsonAdapter`)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/AuthBackend.kt
git commit -m "feat(auth): add AuthBackend interface, TokenResult, and TokenResponse"
```

---

### Task 6: Create RedditTlsFactory.kt

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditTlsFactory.kt`

Utility to apply Chrome-on-Android TLS fingerprint spoofing to any `OkHttpClient.Builder`. Used by both `RedditTokenManager` (for token acquisition requests) and `NetworkModule` (for API requests).

- [ ] **Step 1: Create the factory**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.github.zhkl0228.impersonator.ImpersonatorFactory
import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal object RedditTlsFactory {

    fun applySpoofedTls(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        val x509TrustManager = trustManagers[0] as X509TrustManager

        val impersonator = ImpersonatorFactory.android()
        val sslContext = impersonator.newSSLContext(null, trustManagers)

        return builder.sslSocketFactory(sslContext.socketFactory, x509TrustManager)
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/joe/software/stealth && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditTlsFactory.kt
git commit -m "feat(auth): add RedditTlsFactory for TLS fingerprint spoofing"
```

---

## Chunk 2: Auth Backends

### Task 7: Create MobileSpoofBackend.kt + Test

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/MobileSpoofBackend.kt`
- Create: `app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/MobileSpoofBackendTest.kt`

This backend impersonates the official Reddit Android app. It sends a POST to `/auth/v2/oauth/access-token/loid` with Basic auth (client ID `ohXpoqrZYub1kg`), spoofed device headers, and a JSON body requesting full scopes. The response includes an access token plus `x-reddit-loid` and `x-reddit-session` headers that must be forwarded to subsequent API calls.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class MobileSpoofBackendTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `acquireToken sends correct request format`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-reddit-loid", "t2_test123.0.1234567890")
                .setHeader("x-reddit-session", "session_abc123")
                .setBody("""{"access_token":"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test","token_type":"bearer","expires_in":86400,"scope":"*"}""")
        )

        val backend = MobileSpoofBackend(server.url("/auth/v2/oauth/access-token/loid").toString())
        backend.acquireToken(httpClient)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue("Should have Basic auth", request.getHeader("Authorization")!!.startsWith("Basic "))
        assertTrue("Should have User-Agent", request.getHeader("User-Agent")!!.startsWith("Reddit/"))
        assertNotNull("Should have client-vendor-id", request.getHeader("client-vendor-id"))
        assertNotNull("Should have X-Reddit-Device-Id", request.getHeader("X-Reddit-Device-Id"))
        assertEquals(
            request.getHeader("client-vendor-id"),
            request.getHeader("X-Reddit-Device-Id")
        )
        assertNotNull("Should have x-reddit-qos", request.getHeader("x-reddit-qos"))
        assertNotNull("Should have x-reddit-media-codecs", request.getHeader("x-reddit-media-codecs"))
        val body = request.body.readUtf8()
        assertTrue("Body should request scopes", body.contains("\"scopes\""))
    }

    @Test
    fun `acquireToken parses response correctly`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-reddit-loid", "t2_loid_value")
                .setHeader("x-reddit-session", "session_value")
                .setBody("""{"access_token":"test-token-123","token_type":"bearer","expires_in":3600,"scope":"*"}""")
        )

        val backend = MobileSpoofBackend(server.url("/auth/v2/oauth/access-token/loid").toString())
        val result = backend.acquireToken(httpClient)

        assertEquals("test-token-123", result.accessToken)
        assertEquals(3600L, result.expiresIn)
        assertEquals("t2_loid_value", result.loid)
        assertEquals("session_value", result.session)
        assertTrue("spoofHeaders should contain User-Agent", result.spoofHeaders.containsKey("User-Agent"))
        assertTrue("spoofHeaders should contain client-vendor-id", result.spoofHeaders.containsKey("client-vendor-id"))
        assertTrue("spoofHeaders should contain X-Reddit-Device-Id", result.spoofHeaders.containsKey("X-Reddit-Device-Id"))
        assertTrue("spoofHeaders should contain x-reddit-retry", result.spoofHeaders.containsKey("x-reddit-retry"))
        assertEquals("algo=no-retries", result.spoofHeaders["x-reddit-retry"])
        assertTrue("spoofHeaders should contain x-reddit-compression", result.spoofHeaders.containsKey("x-reddit-compression"))
        assertEquals("1", result.spoofHeaders["x-reddit-compression"])
        assertTrue("spoofHeaders should contain x-reddit-qos", result.spoofHeaders.containsKey("x-reddit-qos"))
        assertTrue("spoofHeaders should contain x-reddit-media-codecs", result.spoofHeaders.containsKey("x-reddit-media-codecs"))
        assertTrue("spoofHeaders should contain Content-Type", result.spoofHeaders.containsKey("Content-Type"))
        assertEquals("application/json; charset=UTF-8", result.spoofHeaders["Content-Type"])
    }

    @Test(expected = IOException::class)
    fun `acquireToken throws on HTTP error`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val backend = MobileSpoofBackend(server.url("/auth/v2/oauth/access-token/loid").toString())
        backend.acquireToken(httpClient)
    }

    @Test(expected = IOException::class)
    fun `acquireToken throws IOException on malformed JSON`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val backend = MobileSpoofBackend(server.url("/auth/v2/oauth/access-token/loid").toString())
        backend.acquireToken(httpClient)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.MobileSpoofBackendTest" 2>&1 | tail -10`
Expected: FAIL — `MobileSpoofBackend` class not found

- [ ] **Step 3: Write the implementation**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.squareup.moshi.Moshi
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

internal class MobileSpoofBackend(
    private val tokenUrl: String = TOKEN_URL
) : AuthBackend {

    private val moshi = Moshi.Builder().build()
    private val tokenResponseAdapter = moshi.adapter(TokenResponse::class.java)

    override suspend fun acquireToken(httpClient: OkHttpClient): TokenResult {
        val device = RedditDevice()
        val authHeader = Credentials.basic(CLIENT_ID, "")

        val body = """{"scopes":["*","email","pii"]}"""
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(tokenUrl)
            .post(body)
            .header("Authorization", authHeader)
            .header("User-Agent", device.userAgent)
            .header("client-vendor-id", device.uuid)
            .header("X-Reddit-Device-Id", device.uuid)
            .header("x-reddit-qos", device.qos)
            .header("x-reddit-media-codecs", device.codecs)
            .build()

        val response = httpClient.newCall(request).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("MobileSpoof token request failed: HTTP ${resp.code}")
            }
            val responseBody = resp.body?.string()
                ?: throw IOException("MobileSpoof token response body is null")
            val tokenResponse = try {
                tokenResponseAdapter.fromJson(responseBody)
                    ?: throw IOException("MobileSpoof failed to parse token response")
            } catch (e: Exception) {
                if (e is IOException) throw e
                throw IOException("MobileSpoof failed to parse token response", e)
            }

            TokenResult(
                accessToken = tokenResponse.accessToken,
                expiresIn = tokenResponse.expiresIn,
                spoofHeaders = mapOf(
                    "User-Agent" to device.userAgent,
                    "client-vendor-id" to device.uuid,
                    "X-Reddit-Device-Id" to device.uuid,
                    "x-reddit-retry" to "algo=no-retries",
                    "x-reddit-compression" to "1",
                    "x-reddit-qos" to device.qos,
                    "x-reddit-media-codecs" to device.codecs,
                    "Content-Type" to "application/json; charset=UTF-8"
                ),
                loid = resp.header("x-reddit-loid"),
                session = resp.header("x-reddit-session")
            )
        }
    }

    companion object {
        private const val CLIENT_ID = "ohXpoqrZYub1kg"
        private const val TOKEN_URL = "https://www.reddit.com/auth/v2/oauth/access-token/loid"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.MobileSpoofBackendTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all 4 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/MobileSpoofBackend.kt \
       app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/MobileSpoofBackendTest.kt
git commit -m "feat(auth): add MobileSpoofBackend with tests"
```

---

### Task 8: Create GenericWebBackend.kt + Test

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/GenericWebBackend.kt`
- Create: `app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/GenericWebBackendTest.kt`

Fallback backend that impersonates a generic web client. Uses Basic auth with client ID `3XfBJWliHvqACnXrfIYlLw`, a browser-style User-Agent, and form-encoded body with `grant_type=https://oauth.reddit.com/grants/installed_client` and a random `device_id`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class GenericWebBackendTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `acquireToken sends correct request format`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"web-token","token_type":"bearer","expires_in":86400,"scope":"*"}""")
        )

        val backend = GenericWebBackend(server.url("/api/v1/access_token").toString())
        backend.acquireToken(httpClient)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue("Should have Basic auth", request.getHeader("Authorization")!!.startsWith("Basic "))
        val body = request.body.readUtf8()
        assertTrue("Body should have grant_type", body.contains("grant_type="))
        assertTrue("Body should have device_id", body.contains("device_id="))
        assertTrue("Body should contain installed_client grant",
            body.contains("https%3A%2F%2Foauth.reddit.com%2Fgrants%2Finstalled_client"))
        assertEquals("https://www.reddit.com", request.getHeader("Origin"))
    }

    @Test
    fun `acquireToken parses response correctly`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"web-token-456","token_type":"bearer","expires_in":7200,"scope":"*"}""")
        )

        val backend = GenericWebBackend(server.url("/api/v1/access_token").toString())
        val result = backend.acquireToken(httpClient)

        assertEquals("web-token-456", result.accessToken)
        assertEquals(7200L, result.expiresIn)
        assertNull("Generic web backend should not return loid", result.loid)
        assertNull("Generic web backend should not return session", result.session)
        assertTrue("spoofHeaders should contain User-Agent", result.spoofHeaders.containsKey("User-Agent"))
        assertTrue("spoofHeaders should contain Origin", result.spoofHeaders.containsKey("Origin"))
        assertEquals("https://www.reddit.com", result.spoofHeaders["Origin"])
    }

    @Test
    fun `acquireToken captures loid and session headers when present`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-reddit-loid", "t2_web_loid")
                .setHeader("x-reddit-session", "web_session_abc")
                .setBody("""{"access_token":"web-token","token_type":"bearer","expires_in":3600,"scope":"*"}""")
        )

        val backend = GenericWebBackend(server.url("/api/v1/access_token").toString())
        val result = backend.acquireToken(httpClient)

        assertEquals("t2_web_loid", result.loid)
        assertEquals("web_session_abc", result.session)
    }

    @Test(expected = IOException::class)
    fun `acquireToken throws on HTTP error`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val backend = GenericWebBackend(server.url("/api/v1/access_token").toString())
        backend.acquireToken(httpClient)
    }

    @Test(expected = IOException::class)
    fun `acquireToken throws IOException on malformed JSON`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val backend = GenericWebBackend(server.url("/api/v1/access_token").toString())
        backend.acquireToken(httpClient)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.GenericWebBackendTest" 2>&1 | tail -10`
Expected: FAIL — `GenericWebBackend` class not found

- [ ] **Step 3: Write the implementation**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.squareup.moshi.Moshi
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.random.Random

internal class GenericWebBackend(
    private val tokenUrl: String = TOKEN_URL
) : AuthBackend {

    private val moshi = Moshi.Builder().build()
    private val tokenResponseAdapter = moshi.adapter(TokenResponse::class.java)

    override suspend fun acquireToken(httpClient: OkHttpClient): TokenResult {
        val deviceId = generateDeviceId()
        val authHeader = Credentials.basic(CLIENT_ID, "")

        val body = FormBody.Builder()
            .add("grant_type", GRANT_TYPE)
            .add("device_id", deviceId)
            .build()

        val request = Request.Builder()
            .url(tokenUrl)
            .post(body)
            .header("Authorization", authHeader)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Origin", "https://www.reddit.com")
            .build()

        val response = httpClient.newCall(request).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("GenericWeb token request failed: HTTP ${resp.code}")
            }
            val responseBody = resp.body?.string()
                ?: throw IOException("GenericWeb token response body is null")
            val tokenResponse = try {
                tokenResponseAdapter.fromJson(responseBody)
                    ?: throw IOException("GenericWeb failed to parse token response")
            } catch (e: Exception) {
                if (e is IOException) throw e
                throw IOException("GenericWeb failed to parse token response", e)
            }

            TokenResult(
                accessToken = tokenResponse.accessToken,
                expiresIn = tokenResponse.expiresIn,
                spoofHeaders = mapOf(
                    "User-Agent" to BROWSER_USER_AGENT,
                    "Origin" to "https://www.reddit.com"
                ),
                loid = resp.header("x-reddit-loid"),
                session = resp.header("x-reddit-session")
            )
        }
    }

    companion object {
        private const val CLIENT_ID = "3XfBJWliHvqACnXrfIYlLw"
        private const val TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
        private const val GRANT_TYPE = "https://oauth.reddit.com/grants/installed_client"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private val ALPHANUMERIC = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        private fun generateDeviceId(): String {
            return (1..20).map { ALPHANUMERIC[Random.nextInt(ALPHANUMERIC.size)] }.joinToString("")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.GenericWebBackendTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all 5 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/GenericWebBackend.kt \
       app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/GenericWebBackendTest.kt
git commit -m "feat(auth): add GenericWebBackend with tests"
```

---

## Chunk 3: Token Manager + Interceptor

### Task 9: Create RedditTokenManager.kt + Test

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditTokenManager.kt`
- Create: `app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditTokenManagerTest.kt`

The token manager is the core of the auth system. It owns:
- **Lock-free reads:** `AtomicReference<AuthSession?>` for current session, `AtomicLong` for version counter
- **Single-flight mutations:** `Mutex` ensures only one coroutine acquires/refreshes at a time
- **Fallback state machine:** MobileSpoof (5 failures) -> GenericWeb (5 failures) -> EXHAUSTED -> auto-reset
- **Proactive refresh:** Daemon coroutine refreshes token 120s before expiry
- **Version-based staleness:** `forceRefresh()` skips if another coroutine already refreshed

Production code uses `@Inject constructor()`. Tests use `setTestDependencies()` to inject fakes.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RedditTokenManagerTest {

    private val testHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .build()

    private fun createFakeBackend(
        shouldSucceed: Boolean = true,
        tokenPrefix: String = "token"
    ): FakeAuthBackend {
        return FakeAuthBackend(shouldSucceed, tokenPrefix)
    }

    private fun createManager(
        initialBackend: AuthBackend = createFakeBackend(),
        fallbackBackend: AuthBackend = createFakeBackend(tokenPrefix = "fallback")
    ): RedditTokenManager {
        val manager = RedditTokenManager()
        manager.setTestDependencies(initialBackend, fallbackBackend, testHttpClient)
        return manager
    }

    @Test
    fun `first call acquires token from initial backend`() = runTest {
        val backend = createFakeBackend()
        val manager = createManager(initialBackend = backend)

        val session = manager.getOrAcquireSession()

        assertNotNull(session)
        assertEquals(1, backend.callCount)
        assertTrue(session.accessToken.startsWith("token-"))
    }

    @Test
    fun `second call returns cached token`() = runTest {
        val backend = createFakeBackend()
        val manager = createManager(initialBackend = backend)

        val session1 = manager.getOrAcquireSession()
        val session2 = manager.getOrAcquireSession()

        assertEquals(1, backend.callCount)
        assertEquals(session1.accessToken, session2.accessToken)
    }

    @Test
    fun `forceRefresh acquires new token`() = runTest {
        val backend = createFakeBackend()
        val manager = createManager(initialBackend = backend)

        val session1 = manager.getOrAcquireSession()
        manager.forceRefresh()
        val session2 = manager.getOrAcquireSession()

        assertEquals(2, backend.callCount)
        assertNotEquals(session1.accessToken, session2.accessToken)
    }

    @Test
    fun `backend failure below threshold throws`() = runTest {
        val failingBackend = createFakeBackend(shouldSucceed = false)
        val manager = createManager(initialBackend = failingBackend)

        try {
            manager.getOrAcquireSession()
            fail("Should have thrown IOException")
        } catch (e: IOException) {
            assertEquals(1, failingBackend.callCount)
        }
    }

    @Test
    fun `after 5 failures switches to fallback backend`() = runTest {
        val failingBackend = createFakeBackend(shouldSucceed = false)
        val fallbackBackend = createFakeBackend(tokenPrefix = "fallback")
        val manager = createManager(
            initialBackend = failingBackend,
            fallbackBackend = fallbackBackend
        )

        // Fail 4 times (each throws, counter increments)
        repeat(4) {
            try { manager.getOrAcquireSession() } catch (_: IOException) {}
        }
        assertEquals(4, failingBackend.callCount)
        assertEquals(0, fallbackBackend.callCount)

        // 5th failure triggers switch + immediate fallback attempt
        val session = manager.getOrAcquireSession()
        assertEquals(5, failingBackend.callCount)
        assertEquals(1, fallbackBackend.callCount)
        assertTrue(session.accessToken.startsWith("fallback-"))
    }

    @Test
    fun `both backends exhausted throws IOException`() = runTest {
        val failingPrimary = createFakeBackend(shouldSucceed = false)
        val failingFallback = createFakeBackend(shouldSucceed = false, tokenPrefix = "fallback")
        val manager = createManager(
            initialBackend = failingPrimary,
            fallbackBackend = failingFallback
        )

        // Exhaust primary (4 throws + 5th switches)
        repeat(4) {
            try { manager.getOrAcquireSession() } catch (_: IOException) {}
        }

        // 5th primary failure switches to fallback, fallback also fails
        try { manager.getOrAcquireSession() } catch (_: IOException) {}
        // Now consecutive failures on fallback = 1

        // Exhaust fallback (3 more throws to reach 4, then 5th)
        repeat(3) {
            try { manager.getOrAcquireSession() } catch (_: IOException) {}
        }

        // 5th fallback failure -> EXHAUSTED
        try {
            manager.getOrAcquireSession()
            fail("Should have thrown IOException for exhausted")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("exhausted"))
        }
    }

    @Test
    fun `exhausted state resets on next call`() = runTest {
        val failingPrimary = createFakeBackend(shouldSucceed = false)
        val failingFallback = createFakeBackend(shouldSucceed = false, tokenPrefix = "fallback")
        val manager = createManager(
            initialBackend = failingPrimary,
            fallbackBackend = failingFallback
        )

        // Drive to EXHAUSTED (9 calls: 4 primary + 1 switch-and-fallback + 3 fallback + 1 exhausted)
        repeat(9) {
            try { manager.getOrAcquireSession() } catch (_: IOException) {}
        }

        // Now make primary succeed
        failingPrimary.shouldSucceed = true

        // Next call should reset from EXHAUSTED and succeed with primary
        val session = manager.getOrAcquireSession()
        assertNotNull(session)
        assertTrue(session.accessToken.startsWith("token-"))
    }

    @Test
    fun `forceRefresh throws when exhausted`() = runTest {
        val failingPrimary = createFakeBackend(shouldSucceed = false)
        val failingFallback = createFakeBackend(shouldSucceed = false, tokenPrefix = "fallback")
        val manager = createManager(
            initialBackend = failingPrimary,
            fallbackBackend = failingFallback
        )

        // Drive to EXHAUSTED (9 calls)
        repeat(9) {
            try { manager.getOrAcquireSession() } catch (_: IOException) {}
        }

        // forceRefresh should throw, not reset
        try {
            manager.forceRefresh()
            fail("forceRefresh should throw when exhausted")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("exhausted"))
        }
    }

    @Test
    fun `exhausted with cached token bypasses cache and reacquires`() = runTest {
        val backend = createFakeBackend()
        val fallbackBackend = createFakeBackend(shouldSucceed = false, tokenPrefix = "fallback")
        val manager = createManager(initialBackend = backend, fallbackBackend = fallbackBackend)

        // Acquire a valid cached session
        val session1 = manager.getOrAcquireSession()
        assertNotNull(session1)
        assertEquals(1, backend.callCount)

        // Now make primary fail and drive to EXHAUSTED
        backend.shouldSucceed = false
        // forceRefresh fails 5 times on primary (switches to fallback), then fallback fails 5 times
        repeat(10) {
            try { manager.forceRefresh() } catch (_: IOException) {}
        }

        // At this point: EXHAUSTED, but session1 is still in cache and unexpired
        // Make primary succeed again
        backend.shouldSucceed = true

        // getOrAcquireSession() must NOT return the stale cached token;
        // it must reset EXHAUSTED, clear cache, and reacquire from primary
        val session2 = manager.getOrAcquireSession()
        assertNotNull(session2)
        assertNotEquals(session1.accessToken, session2.accessToken)
        assertTrue(session2.accessToken.startsWith("token-"))
    }
}

internal class FakeAuthBackend(
    var shouldSucceed: Boolean = true,
    private val tokenPrefix: String = "token"
) : AuthBackend {
    var callCount = 0

    override suspend fun acquireToken(httpClient: OkHttpClient): TokenResult {
        callCount++
        if (!shouldSucceed) throw IOException("Fake backend failure")
        return TokenResult(
            accessToken = "$tokenPrefix-$callCount",
            expiresIn = 86400,
            spoofHeaders = mapOf("User-Agent" to "FakeAgent/1.0"),
            loid = "fake-loid-$callCount",
            session = "fake-session-$callCount"
        )
    }
}
```

- [ ] **Step 2: Create android.util.Log shadow for JVM tests**

`android.util.Log` is not available in JVM unit tests. Create a shadow class:

Create `app/src/test/java/android/util/Log.java`:

```java
package android.util;

public class Log {
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTokenManagerTest" 2>&1 | tail -10`
Expected: FAIL — `RedditTokenManager` class not found (or `setTestDependencies` missing)

- [ ] **Step 4: Write the implementation**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit.auth

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditTokenManager @Inject constructor() {

    private var backend: AuthBackend = MobileSpoofBackend()
    private var primaryProvider: () -> AuthBackend = { MobileSpoofBackend() }
    private var fallbackProvider: () -> AuthBackend = { GenericWebBackend() }
    private var tokenHttpClient: OkHttpClient = createDefaultHttpClient()

    private val currentSession = AtomicReference<AuthSession?>(null)
    private val sessionVersion = AtomicLong(0)
    private val refreshMutex = Mutex()

    @Volatile
    private var backendState = BackendState.MOBILE_SPOOF_ACTIVE
    private var consecutiveFailures = 0

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    @VisibleForTesting
    internal fun setTestDependencies(
        initialBackend: AuthBackend,
        fallbackBackend: AuthBackend,
        httpClient: OkHttpClient
    ) {
        backend = initialBackend
        primaryProvider = { initialBackend }
        fallbackProvider = { fallbackBackend }
        tokenHttpClient = httpClient
        backendState = BackendState.MOBILE_SPOOF_ACTIVE
        consecutiveFailures = 0
        currentSession.set(null)
        sessionVersion.set(0)
        refreshJob?.cancel()
    }

    suspend fun getOrAcquireSession(): AuthSession {
        if (backendState != BackendState.EXHAUSTED) {
            currentSession.get()?.let { session ->
                if (session.expiresAt > System.currentTimeMillis()) return session
            }
        }
        return refreshMutex.withLock {
            if (backendState == BackendState.EXHAUSTED) {
                backend = primaryProvider()
                backendState = BackendState.MOBILE_SPOOF_ACTIVE
                consecutiveFailures = 0
                currentSession.set(null)
            }
            currentSession.get()?.let { session ->
                if (session.expiresAt > System.currentTimeMillis()) return@withLock session
            }
            acquireNewSession()
        }
    }

    suspend fun forceRefresh() {
        val versionBefore = sessionVersion.get()
        refreshMutex.withLock {
            if (sessionVersion.get() > versionBefore) return
            if (backendState == BackendState.EXHAUSTED) {
                throw IOException("Reddit authentication exhausted, awaiting next getOrAcquireSession() to reset")
            }
            acquireNewSession()
        }
    }

    private suspend fun acquireNewSession(): AuthSession {
        while (true) {
            try {
                val result = backend.acquireToken(tokenHttpClient)
                consecutiveFailures = 0
                val newVersion = sessionVersion.incrementAndGet()
                val session = AuthSession(
                    version = newVersion,
                    accessToken = result.accessToken,
                    expiresAt = System.currentTimeMillis() + result.expiresIn * 1000,
                    backendType = when (backendState) {
                        BackendState.MOBILE_SPOOF_ACTIVE -> BackendType.MOBILE_SPOOF
                        else -> BackendType.GENERIC_WEB
                    },
                    spoofHeaders = result.spoofHeaders,
                    loid = result.loid,
                    session = result.session
                )
                currentSession.set(session)
                scheduleProactiveRefresh(result.expiresIn)
                return session
            } catch (e: Exception) {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_FAILURES_PER_BACKEND) {
                    when (backendState) {
                        BackendState.MOBILE_SPOOF_ACTIVE -> {
                            Log.w(TAG, "MobileSpoof failed $MAX_FAILURES_PER_BACKEND times, switching to GenericWeb", e)
                            backend = fallbackProvider()
                            backendState = BackendState.GENERIC_WEB_ACTIVE
                            consecutiveFailures = 0
                            continue
                        }
                        BackendState.GENERIC_WEB_ACTIVE -> {
                            backendState = BackendState.EXHAUSTED
                            throw IOException("Reddit authentication exhausted: both backends failed", e)
                        }
                        BackendState.EXHAUSTED -> {
                            throw IOException("Reddit authentication exhausted", e)
                        }
                    }
                } else {
                    throw IOException("Reddit authentication failed", e)
                }
            }
        }
    }

    private fun scheduleProactiveRefresh(expiresIn: Long) {
        refreshJob?.cancel()
        refreshJob = refreshScope.launch {
            val delaySeconds = maxOf(expiresIn - 120, 60)
            delay(delaySeconds * 1000)
            try {
                forceRefresh()
            } catch (e: Exception) {
                Log.w(TAG, "Proactive refresh failed", e)
                currentSession.get()?.let { session ->
                    if (session.expiresAt > System.currentTimeMillis()) {
                        delay(60_000)
                        try {
                            forceRefresh()
                        } catch (e2: Exception) {
                            Log.w(TAG, "Proactive refresh retry failed", e2)
                        }
                    }
                }
            }
        }
    }

    private enum class BackendState {
        MOBILE_SPOOF_ACTIVE,
        GENERIC_WEB_ACTIVE,
        EXHAUSTED
    }

    companion object {
        private const val TAG = "RedditTokenManager"
        private const val MAX_FAILURES_PER_BACKEND = 5

        private fun createDefaultHttpClient(): OkHttpClient {
            return RedditTlsFactory.applySpoofedTls(OkHttpClient.Builder())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest --tests "com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTokenManagerTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all 9 tests pass

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditTokenManager.kt \
       app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/auth/RedditTokenManagerTest.kt \
       app/src/test/java/android/util/Log.java
git commit -m "feat(auth): add RedditTokenManager with fallback state machine and tests"
```

---

### Task 10: Create RedditAuthInterceptor.kt

**Files:**

- Create: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/RedditAuthInterceptor.kt`

OkHttp interceptor that:
1. Acquires a session from `RedditTokenManager`
2. Attaches `Authorization: Bearer <token>` + spoofed device headers (shuffled order)
3. Appends `x-reddit-loid` and `x-reddit-session` if present
4. On 401 response: calls `forceRefresh()`, retries once with new token
5. On 429 or low `x-ratelimit-remaining` (< 10): spawns non-blocking refresh

- [ ] **Step 1: Write the implementation**

```kotlin
package com.cosmos.unreddit.data.remote.api.reddit

import com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditAuthInterceptor @Inject constructor(
    private val tokenManager: RedditTokenManager,
    @com.cosmos.unreddit.di.CoroutinesScopesModule.ApplicationScope
    private val scope: CoroutineScope
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = runBlocking { tokenManager.getOrAcquireSession() }

        val response = chain.proceed(buildAuthenticatedRequest(chain, session))

        if (response.code == 401) {
            response.close()
            val newSession = runBlocking {
                tokenManager.forceRefresh()
                tokenManager.getOrAcquireSession()
            }
            return chain.proceed(buildAuthenticatedRequest(chain, newSession))
        }

        if (response.code == 429) {
            scope.launch {
                try { tokenManager.forceRefresh() } catch (_: Exception) {}
            }
        } else {
            val remaining = response.header("x-ratelimit-remaining")?.toFloatOrNull()
            if (remaining != null && remaining < 10) {
                scope.launch {
                    try { tokenManager.forceRefresh() } catch (_: Exception) {}
                }
            }
        }

        return response
    }

    private fun buildAuthenticatedRequest(
        chain: Interceptor.Chain,
        session: com.cosmos.unreddit.data.remote.api.reddit.auth.AuthSession
    ): okhttp3.Request {
        val headers = mutableListOf<Pair<String, String>>()
        headers.add("Authorization" to "Bearer ${session.accessToken}")
        session.spoofHeaders.forEach { (key, value) -> headers.add(key to value) }
        session.loid?.let { headers.add("x-reddit-loid" to it) }
        session.session?.let { headers.add("x-reddit-session" to it) }
        headers.shuffle()

        val requestBuilder = chain.request().newBuilder()
        headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        return requestBuilder.build()
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/joe/software/stealth && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

Note: If the `@ApplicationScope` import doesn't resolve correctly because the qualifier is a nested annotation, check the exact import path. It should be:
```kotlin
import com.cosmos.unreddit.di.CoroutinesScopesModule.ApplicationScope
```
If the annotation is defined differently, adjust the import. Check the existing code at `app/src/main/java/com/cosmos/unreddit/di/CoroutinesScopesModule.kt` for the exact qualifier definition.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/RedditAuthInterceptor.kt
git commit -m "feat(auth): add RedditAuthInterceptor with 401 retry and rate limit handling"
```

---

## Chunk 4: DI Wiring + Verification

### Task 11: Add BASE_URL_OAUTH to RedditApi.kt

**Files:**

- Modify: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/RedditApi.kt:112-115`

- [ ] **Step 1: Add the new constant**

In the `companion object` block (line 112), add `BASE_URL_OAUTH` after the existing constants:

```kotlin
    companion object {
        const val BASE_URL = "https://www.reddit.com/"
        const val BASE_URL_OLD = "https://old.reddit.com/"
        const val BASE_URL_OAUTH = "https://oauth.reddit.com/"
    }
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/joe/software/stealth && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/RedditApi.kt
git commit -m "feat(auth): add BASE_URL_OAUTH constant to RedditApi"
```

---

### Task 12: Wire NetworkModule

**Files:**

- Modify: `app/src/main/java/com/cosmos/unreddit/di/NetworkModule.kt`

Changes:
1. Add `@AuthRedditOkHttp` qualifier annotation (nested in `NetworkModule`, matching existing pattern)
2. Add `provideAuthRedditOkHttpClient()` provider: impersonator TLS + `RedditAuthInterceptor` + `RawJsonInterceptor` + 60s timeouts. **No `JsonInterceptor`** — `oauth.reddit.com` returns JSON natively without the `.json` path suffix.
3. Change `provideRedditApi()` to use `@AuthRedditOkHttp` client and `BASE_URL_OAUTH` instead of `@RedditOkHttp` and `BASE_URL`

- [ ] **Step 1: Add the qualifier annotation**

Add after the existing `@RedditScrap` annotation (around line 70), following the same pattern:

```kotlin
    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class AuthRedditOkHttp
```

- [ ] **Step 2: Add imports for the new provider**

Add these imports at the top of `NetworkModule.kt`:

```kotlin
import com.cosmos.unreddit.data.remote.api.reddit.RedditAuthInterceptor
import com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTlsFactory
```

- [ ] **Step 3: Add the new OkHttpClient provider**

Add after `provideRedditOkHttpClient()` (after line 132):

```kotlin
    @AuthRedditOkHttp
    @Provides
    @Singleton
    fun provideAuthRedditOkHttpClient(
        redditAuthInterceptor: RedditAuthInterceptor
    ): OkHttpClient {
        return RedditTlsFactory.applySpoofedTls(OkHttpClient.Builder())
            .addInterceptor(redditAuthInterceptor)
            .addInterceptor(RawJsonInterceptor())
            .connectTimeout(TIMEOUT.first, TIMEOUT.second)
            .readTimeout(TIMEOUT.first, TIMEOUT.second)
            .writeTimeout(TIMEOUT.first, TIMEOUT.second)
            .build()
    }
```

- [ ] **Step 4: Rewire provideRedditApi()**

Change `provideRedditApi()` to use the new authenticated client and OAuth base URL. Find the existing method (around line 170):

**Before:**
```kotlin
    @RedditOfficial
    @Provides
    @Singleton
    fun provideRedditApi(
        @RedditMoshi moshi: Moshi,
        @RedditOkHttp okHttpClient: OkHttpClient
    ): RedditApi {
        return Retrofit.Builder()
            .baseUrl(RedditApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addConverterFactory(SortingConverterFactory())
            .client(okHttpClient)
            .build()
            .create(RedditApi::class.java)
    }
```

**After:**
```kotlin
    @RedditOfficial
    @Provides
    @Singleton
    fun provideRedditApi(
        @RedditMoshi moshi: Moshi,
        @AuthRedditOkHttp okHttpClient: OkHttpClient
    ): RedditApi {
        return Retrofit.Builder()
            .baseUrl(RedditApi.BASE_URL_OAUTH)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addConverterFactory(SortingConverterFactory())
            .client(okHttpClient)
            .build()
            .create(RedditApi::class.java)
    }
```

Two changes: `@RedditOkHttp` -> `@AuthRedditOkHttp`, `RedditApi.BASE_URL` -> `RedditApi.BASE_URL_OAUTH`

- [ ] **Step 5: Verify compilation**

Run: `cd /home/joe/software/stealth && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cosmos/unreddit/di/NetworkModule.kt
git commit -m "feat(auth): wire AuthRedditOkHttp with impersonator TLS and auth interceptor"
```

---

### Task 13: ProGuard Rules + Build Verification

**Files:**

- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: Add BouncyCastle and impersonator keep rules**

Append to `app/proguard-rules.pro` after the existing enum rule (line 27):

```proguard

# BouncyCastle (used by impersonator-bctls for TLS fingerprint spoofing)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# impersonator-bctls
-keep class com.github.zhkl0228.impersonator.** { *; }
-dontwarn com.github.zhkl0228.impersonator.**

# Moshi generated adapters for auth
-keep class com.cosmos.unreddit.data.remote.api.reddit.auth.TokenResponseJsonAdapter { *; }
```

- [ ] **Step 2: Run full debug build**

Run: `cd /home/joe/software/stealth && ./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all unit tests**

Run: `cd /home/joe/software/stealth && ./gradlew :app:testDebugUnitTest 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL, all tests pass (including auth tests + existing tests)

- [ ] **Step 4: Run release build to verify ProGuard rules**

Run: `cd /home/joe/software/stealth && ./gradlew :app:assembleRelease 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (minification enabled only for release builds)

Note: If signing config is not set up locally, this may fail. In that case, verify with:
`./gradlew :app:minifyReleaseWithR8 2>&1 | tail -10`

- [ ] **Step 5: Commit**

```bash
git add app/proguard-rules.pro
git commit -m "build: add ProGuard rules for BouncyCastle and impersonator-bctls"
```

---

## Summary

After completing all 13 tasks, the app will:
1. Acquire anonymous OAuth tokens from Reddit's public endpoints (MobileSpoof primary, GenericWeb fallback)
2. Use `oauth.reddit.com` instead of `www.reddit.com` for all API requests via `RedditSource`
3. Attach Bearer auth + spoofed device headers on every request
4. Handle 401 responses with automatic token refresh + retry, and 429/rate-limit with non-blocking refresh
5. Spoof TLS fingerprint as Chrome on Android using impersonator-bctls
6. Proactively refresh tokens before expiry

**No changes to**: `RedditSource.kt`, `CurrentSource.kt`, `TedditSource.kt`, `RedditScrapingSource.kt`, or any UI code. The `@RedditScrap` path (old.reddit.com scraping) remains untouched.
