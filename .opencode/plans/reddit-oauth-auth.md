# Reddit OAuth Authentication for Stealth

## Problem

Reddit now blocks anonymous access to its JSON API at the network-security layer. Both `www.reddit.com` and `old.reddit.com` return HTTP 403 with "You've been blocked by network security." The existing `RedditSource` (which uses unauthenticated requests to `www.reddit.com`) is completely broken.

## Solution

Port Redlib's anonymous OAuth impersonation strategy to Stealth. Replace the existing `RedditSource`'s unauthenticated `www.reddit.com` calls with authenticated `oauth.reddit.com` calls. No UI changes, no new source option - the existing "Reddit" source works again transparently.

## Non-Goals

- No user login / user OAuth flow
- No persisted auth state (token, loid, session) across app restarts
- No automatic source switching (user must manually switch to Teddit/scraping if Reddit auth is exhausted)
- No HTTP/2 SETTINGS frame fingerprinting (TLS-layer only)

## Decisions

- **Anonymous impersonation only** - no user OAuth login, preserves Stealth's privacy model
- **Both MobileSpoof + GenericWeb backends** with auto-fallback for maximum resilience
- **TLS fingerprint spoofing** via `impersonator-bctls` (SSLContext only, no OkHttp fork)
- **Replace existing Reddit source** - not a new source option (the old one is broken anyway)
- **In-memory token only** - no persistence, acquired fresh on app launch (estimated ~200ms, not a guarantee)

## Architecture

### New Files

```
app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/auth/
  RedditTokenManager.kt      -- Token lifecycle: acquire, refresh, fallback
  MobileSpoofBackend.kt      -- POST /auth/v2/oauth/access-token/loid
  GenericWebBackend.kt        -- POST /api/v1/access_token
  RedditDevice.kt             -- Spoofed device identity (UA, UUID, headers)
  RedditAppVersions.kt        -- ~150 real Android APK version strings
  AuthSession.kt              -- Immutable snapshot of current auth state

app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/
  RedditAuthInterceptor.kt    -- OkHttp interceptor: Bearer + spoofed headers + shuffling
```

### Modified Files

```
di/NetworkModule.kt           -- Add @AuthRedditOkHttp client; @RedditOfficial points to oauth.reddit.com
data/remote/api/reddit/RedditApi.kt  -- Add BASE_URL_OAUTH constant
app/build.gradle.kts          -- Add impersonator-bctls dependency
settings.gradle.kts           -- Add JitPack repository (if not present)
```

### Unchanged Files

- `RedditSource.kt` - still calls `RedditApi` with Moshi adapters (see endpoint compatibility below)
- `CurrentSource.kt` - still delegates to `redditSource`
- `BaseRedditSource.kt` - sealed interface unchanged
- `RedditScrapingSource.kt`, `TedditSource.kt` - unchanged
- All UI code - unchanged

## Endpoint Compatibility Matrix

All `RedditApi` endpoints use the same paths on `oauth.reddit.com` as on `www.reddit.com`. The key difference: `oauth.reddit.com` returns JSON natively (no `.json` suffix needed), so `JsonInterceptor` is removed from the auth client.

| RedditApi Method | Path | .json needed? | Works on oauth.reddit.com? | Notes |
|---|---|---|---|---|
| `getSubreddit` | `/r/{sub}/{sort}` | No | Yes | `raw_json=1` still applies |
| `getSubredditInfo` | `/r/{sub}/about` | No | Yes | |
| `searchInSubreddit` | `/r/{sub}/search` | No | Yes | |
| `getSubredditRules` | `/r/{sub}/about/rules` | No | Yes | Returns `Call<Child>` - Moshi converter handles JSON directly |
| `getPost` | `{permalink}` | No | Yes | |
| `getMoreChildren` | `/api/morechildren` | No | Yes | Always routed through `redditSource` by `CurrentSource` |
| `getUserInfo` | `/user/{user}/about` | No | Yes | |
| `getUserPosts` | `/user/{user}/submitted/` | No | Yes | |
| `getUserComments` | `/user/{user}/comments/` | No | Yes | |
| `searchPost` | `/search?type=link` | No | Yes | Always routed through `redditSource` by `CurrentSource` |
| `searchUser` | `/search?type=user` | No | Yes | Always routed through `redditSource` by `CurrentSource` |
| `searchSubreddit` | `/search?type=sr` | No | Yes | |
| `consentOver18` | `/over18` (POST) | N/A | Not used | Only used by `RedditScrapingSource` via `@RedditScrap` Retrofit (unchanged) |

`RawJsonInterceptor` (appends `raw_json=1`) is kept on the auth client because `oauth.reddit.com` still respects this parameter.

## Component Details

### AuthSession

Immutable snapshot of all auth state. Replaced atomically on refresh.

```kotlin
data class AuthSession(
    val version: Long,                // Monotonically increasing version counter (for staleness detection)
    val accessToken: String,
    val expiresAt: Long,              // System.currentTimeMillis() + expiresIn * 1000
    val backendType: BackendType,     // MOBILE_SPOOF or GENERIC_WEB
    val spoofHeaders: Map<String, String>, // Backend-specific spoofed headers EXCLUDING Authorization, loid, session
    val loid: String?,                // From token response headers, echoed on every API request
    val session: String?              // From token response headers, echoed on every API request
)

enum class BackendType { MOBILE_SPOOF, GENERIC_WEB }
```

**`spoofHeaders` contains backend-specific identity headers only:**
- For MobileSpoof: `User-Agent`, `x-reddit-retry`, `x-reddit-compression`, `x-reddit-qos`, `x-reddit-media-codecs`, `Content-Type`, `client-vendor-id`, `X-Reddit-Device-Id`
- For GenericWeb: `User-Agent`, `Origin`

**The interceptor derives at request time:**
- `Authorization: Bearer {accessToken}` (from `AuthSession.accessToken`)
- `x-reddit-loid` (from `AuthSession.loid`, if non-null)
- `x-reddit-session` (from `AuthSession.session`, if non-null)

This separation ensures no duplication: `spoofHeaders` is the backend-set identity, the interceptor adds the token and tracking headers.

On refresh or backend switch: the entire `AuthSession` is replaced with a new instance (incremented `version`). The interceptor reads the current session atomically via `AtomicReference<AuthSession?>`. No partial state is ever visible.

### RedditTokenManager

`@Singleton` that owns the token lifecycle.

**State:**
- `currentSession: AtomicReference<AuthSession?>` - initially null
- `sessionVersion: AtomicLong` - monotonically increasing, starts at 0
- `backend: AuthBackend` - starts as MobileSpoofBackend
- `consecutiveFailures: Int` - per-backend counter
- `refreshMutex: Mutex` - sole concurrency primitive for all session mutations
- `refreshJob: Job?` - proactive refresh daemon

All mutable state (`backend`, `consecutiveFailures`, `currentSession`, `sessionVersion`) is only mutated while holding `refreshMutex`. The `AtomicReference` and `AtomicLong` allow lock-free reads from the interceptor hot path.

```kotlin
interface AuthBackend {
    suspend fun acquireToken(httpClient: OkHttpClient): TokenResult
}

data class TokenResult(
    val accessToken: String,
    val expiresIn: Long,
    val spoofHeaders: Map<String, String>,  // Backend-specific identity headers only
    val loid: String?,
    val session: String?
)
```

**Concurrency model (single-flight):**

```
suspend fun getOrAcquireSession(): AuthSession {
    // Fast path: return current session if valid
    currentSession.get()?.let { session ->
        if (session.expiresAt > System.currentTimeMillis()) return session
    }
    // Slow path: acquire under mutex
    return refreshMutex.withLock {
        // Double-check after acquiring lock
        currentSession.get()?.let { session ->
            if (session.expiresAt > System.currentTimeMillis()) return session
        }
        // Actually acquire
        acquireNewSession()
    }
}
```

All concurrent callers block on the same `Mutex`. The first caller acquires the token; subsequent callers get the result. On failure, the exception propagates to ALL waiting callers (they each see the IOException).

**`forceRefresh()` uses version-based staleness detection:**

```
suspend fun forceRefresh() {
    val versionBeforeRefresh = sessionVersion.get()
    refreshMutex.withLock {
        // If session was already refreshed by another caller while we waited, skip
        if (sessionVersion.get() > versionBeforeRefresh) return
        // Unconditionally acquire a new session (ignores expiresAt)
        acquireNewSession()  // increments sessionVersion
    }
}
```

Unlike `getOrAcquireSession()` which checks `expiresAt`, `forceRefresh()` captures the version *before* waiting on the mutex and only skips if a *newer* session was created while waiting. This ensures:
- Proactive refresh (daemon) always rotates the token even when still valid
- 401-triggered refresh always gets a new token
- Concurrent `forceRefresh()` calls coalesce: only the first one acquires, others see the version bump and return

**Initialization:** Lazy - first call to `getOrAcquireSession()` triggers acquisition. Refresh daemon starts after first success.

**Token acquisition uses a private OkHttpClient** (also with impersonator-bctls TLS but no auth interceptor) for POST requests to `www.reddit.com`. This avoids circular dependency.

**Refresh scope:** `CoroutineScope(SupervisorJob() + Dispatchers.IO)` owned by the singleton. SupervisorJob prevents failed refresh from canceling the scope.

### MobileSpoofBackend

**Canonical request:**

```
POST https://www.reddit.com/auth/v2/oauth/access-token/loid HTTP/1.1
Host: www.reddit.com
Authorization: Basic b2hYcG9xclpZdWIxa2c6    (base64 of "ohXpoqrZYub1kg:")
Content-Type: application/json; charset=UTF-8
User-Agent: Reddit/Version 2024.22.1/Build 1652272/Android 12
client-vendor-id: {uuid}
X-Reddit-Device-Id: {uuid}
x-reddit-qos: 47.382
x-reddit-media-codecs: available-codecs=video/avc, video/hevc, video/x-vnd.on2.vp9

{"scopes": ["*", "email", "pii"]}
```

**Expected response (HTTP 200):**
```json
{
    "access_token": "eyJhbGci...",
    "token_type": "bearer",
    "expires_in": 86400,
    "scope": "*"
}
```

Plus response headers:
- `x-reddit-loid: <opaque string>` - capture and echo on all subsequent API requests
- `x-reddit-session: <opaque string>` - capture and echo on all subsequent API requests

**Error cases:**
- HTTP 403: Reddit blocked the request. Increment failure counter.
- HTTP 429: Rate limited. Increment failure counter.
- Network timeout / IOException: Increment failure counter.
- Any non-200: Increment failure counter.

Creates a fresh `RedditDevice` per token acquisition.

### GenericWebBackend

**Canonical request:**

```
POST https://www.reddit.com/api/v1/access_token HTTP/1.1
Host: www.reddit.com
Authorization: Basic M1hmQkpXbGlIdnFBQ25YcmZJWWxMdzo=    (base64 of "3XfBJWliHvqACnXrfIYlLw:")
Content-Type: application/x-www-form-urlencoded
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0

grant_type=https%3A%2F%2Foauth.reddit.com%2Fgrants%2Finstalled_client&device_id=aB3cD4eF5gH6iJ7kL8mN
```

**Expected response (HTTP 200):**
```json
{
    "access_token": "eyJhbGci...",
    "token_type": "bearer",
    "expires_in": 86400,
    "scope": "*"
}
```

Response headers `x-reddit-loid` and `x-reddit-session` captured if present.

**Error cases:** Same as MobileSpoofBackend.

### Fallback State Machine

```
States:
  MOBILE_SPOOF_ACTIVE (initial)
  GENERIC_WEB_ACTIVE
  EXHAUSTED

Transitions:
  MOBILE_SPOOF_ACTIVE:
    on success       -> reset consecutiveFailures to 0, stay MOBILE_SPOOF_ACTIVE
    on failure       -> consecutiveFailures++
    if consecutiveFailures >= 5 -> log warning, switch to GENERIC_WEB_ACTIVE, reset consecutiveFailures to 0

  GENERIC_WEB_ACTIVE:
    on success       -> reset consecutiveFailures to 0, stay GENERIC_WEB_ACTIVE
    on failure       -> consecutiveFailures++
    if consecutiveFailures >= 5 -> move to EXHAUSTED

  EXHAUSTED:
    throw IOException("Reddit authentication exhausted")
    State automatically resets to MOBILE_SPOOF_ACTIVE (consecutiveFailures = 0) at the
    START of the next getOrAcquireSession() call. This means any subsequent API request
    (whether triggered by user tapping RETRY or by navigating to another screen) will
    restart the full fallback chain from MobileSpoof.
```

MobileSpoof is NOT re-tried after switching to GenericWeb within the same attempt cycle. A fresh RETRY from the user resets the entire state machine.

### Refresh Strategy

- **Proactive:** Coroutine daemon sleeps for `(expiresIn - 120)` seconds, then calls `forceRefresh()`. Since `forceRefresh()` uses version-based staleness (not expiry-based), it always rotates the token even when still valid. Tokens are ~86400s (24h), so refresh happens at ~23h 58m.
- **Reactive on 401:** `RedditAuthInterceptor` calls `forceRefresh()` (blocking), retries request once with new token.
- **Reactive on rate limit:** Interceptor reads `x-ratelimit-remaining`; spawns non-blocking `forceRefresh()` when < 10.
- **Guard:** `refreshMutex` is the sole concurrency primitive for all session mutations (see concurrency model above).

**Proactive refresh failure handling:**
- If proactive refresh fails but current token is still valid (`expiresAt > now`): log warning, schedule retry after 60-second backoff. Current token remains usable.
- If proactive refresh fails and current token is expired: next API call will trigger reactive refresh via interceptor.
- On network unavailability (no connectivity): proactive refresh silently fails; reactive refresh will fire when connectivity returns and a request is attempted.

**Timeout:** Token acquisition requests use a 15-second timeout (separate from the 60-second API timeout). Backoff between retries within the fallback state machine: none (immediate retry with next attempt, but capped at 5 per backend).

### RedditDevice

Generates fresh identity per token acquisition:

| Field | Value |
|---|---|
| `uuid` | `UUID.randomUUID()` - used for both `client-vendor-id` and `X-Reddit-Device-Id` |
| `appVersion` | Random pick from RedditAppVersions (~150 real versions) |
| `androidVersion` | Random 9..14 |
| `userAgent` | `"Reddit/{appVersion}/Android {androidVersion}"` |
| `qos` | Random float formatted as `"%.3f"` in range 1.000..100.000 |
| `codecs` | `"available-codecs=video/avc, video/hevc"` with optional `", video/x-vnd.on2.vp9"` (50/50) |

### RedditAppVersions

Object containing `val LIST: Array<String>` with ~150 real APK version/build pairs. Sourced from Redlib's `src/oauth_resources.rs` (commit `main` branch, file `src/oauth_resources.rs`, the `ANDROID_APP_VERSION_LIST` array). Example entries:

```
"Version 2024.22.1/Build 1652272"
"Version 2024.23.1/Build 1665606"
```

This list is a direct Kotlin port of Redlib's list and can be updated by re-running Redlib's `scripts/update_oauth_resources.sh` and transcribing the output.

### RedditAuthInterceptor

OkHttp `Interceptor` injected into `@AuthRedditOkHttp` client.

**On request (intercept):**

1. Call `tokenManager.getOrAcquireSession()` to get current `AuthSession`
2. Build header list by combining:
   - `"Authorization" to "Bearer ${session.accessToken}"` (derived at request time)
   - All entries from `session.spoofHeaders` (backend-specific identity headers)
   - `"x-reddit-loid" to session.loid` (if non-null, derived at request time)
   - `"x-reddit-session" to session.session` (if non-null, derived at request time)
3. Shuffle the combined header list with `kotlin.random.Random`
4. Apply all headers to request (replacing any defaults)

**`spoofHeaders` content per backend type:**

MobileSpoof `spoofHeaders`:
| Header | Value |
|---|---|
| `User-Agent` | `Reddit/{version}/Android {os}` |
| `x-reddit-retry` | `algo=no-retries` |
| `x-reddit-compression` | `1` |
| `x-reddit-qos` | `{random float}` |
| `x-reddit-media-codecs` | `{codecs string}` |
| `Content-Type` | `application/json; charset=UTF-8` |
| `client-vendor-id` | `{uuid}` |
| `X-Reddit-Device-Id` | `{uuid}` |

GenericWeb `spoofHeaders`:
| Header | Value |
|---|---|
| `User-Agent` | random browser UA |
| `Origin` | `https://www.reddit.com` |

The interceptor then adds `Authorization`, `x-reddit-loid`, and `x-reddit-session` from `AuthSession` fields (not from `spoofHeaders`), ensuring no duplication.

**On response:**

1. HTTP 401: Call `tokenManager.forceRefresh()` (blocking), retry request once. If retry also 401s, let it propagate as `HttpException`.
2. HTTP 429 / `x-ratelimit-remaining` < 10: Spawn non-blocking `forceRefresh()`, let current response through.
3. HTTP 403: Do NOT intercept. Let it propagate (genuinely restricted content).

### NetworkModule Changes

**New qualifier:** `@AuthRedditOkHttp`

**New provider - `provideAuthRedditOkHttpClient()`:**
```kotlin
@AuthRedditOkHttp
@Provides
@Singleton
fun provideAuthRedditOkHttpClient(
    redditAuthInterceptor: RedditAuthInterceptor
): OkHttpClient {
    val impersonator = ImpersonatorFactory.android()
    return OkHttpClient.Builder()
        .sslSocketFactory(impersonator.sslContext.socketFactory, impersonator.trustManager)
        .addInterceptor(redditAuthInterceptor)
        .addInterceptor(RawJsonInterceptor())
        // No JsonInterceptor - OAuth API returns JSON natively
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
```

**Modified provider - `provideRedditApi()`:**
- Base URL: `RedditApi.BASE_URL` -> `RedditApi.BASE_URL_OAUTH`
- OkHttp client: `@RedditOkHttp` -> `@AuthRedditOkHttp`

**Existing `@RedditOkHttp` client stays** in the module but is no longer used by `@RedditOfficial`. It could be removed if no other code references it, but keeping it is harmless and useful for debugging.

### RedditApi Change

Add one constant:
```kotlin
const val BASE_URL_OAUTH = "https://oauth.reddit.com/"
```

### TLS Fingerprinting

Using `impersonator-bctls` module only (not the forked OkHttp):
- Provides `SSLSocketFactory` + `X509TrustManager` with Chrome/Android TLS fingerprint
- Plugged into standard OkHttpClient via `.sslSocketFactory()`
- Covers JA3/JA4 hash, cipher suite ordering, TLS extensions
- Does NOT cover HTTP/2 SETTINGS frame fingerprinting (acceptable tradeoff)
- Applied to both the auth OkHttp client AND the token acquisition client in RedditTokenManager

## Dependencies

**Add to `app/build.gradle.kts`:**
```
implementation("com.github.zhkl0228:impersonator-bctls:1.0.12")
```

**Add to `settings.gradle.kts` (if not present):**
```
maven { url = uri("https://jitpack.io") }
```

No new Android permissions. No new Android manifest changes.

## Security

**Hardcoded (acceptable - public client IDs):**
- `ohXpoqrZYub1kg` - Reddit Android app public OAuth client ID
- `3XfBJWliHvqACnXrfIYlLw` - Reddit web public client ID
- APK version list - public information

**Not stored/logged:**
- Access tokens never logged, even at DEBUG
- Tokens held in-memory only, never persisted
- `x-reddit-loid` / `x-reddit-session` ephemeral (in-memory)
- No token in crash reports or error messages

**Privacy model preserved:**
- No user identity - anonymous "installed client" grants
- Fresh UUID per token acquisition (no persistent device fingerprint)
- Token rotation every ~24h with new device identity
- No cookies persisted

**Risks:**
- Reddit could revoke Android app client ID -> GenericWeb fallback covers this
- Reddit could block GenericWeb client ID too -> user sees "Something went wrong" and must manually switch to Teddit/scraping sources
- Reddit could detect via HTTP/2 fingerprinting -> TLS-layer mitigation only

## User Experience

- **Normal case:** App works exactly as before (loading spinner, then content)
- **Reddit blocks all auth:** Same "Something went wrong" + RETRY button as today. No automatic source switching.
- **Token expires mid-session:** Transparent refresh, no user-visible interruption
- **Cold start:** Token acquisition during first API call, absorbed by existing loading UI
- **Offline:** Token acquisition fails silently; error surfaces when API call times out (existing behavior)

## Acceptance Criteria

| Flow | Expected Behavior |
|---|---|
| Cold start with Reddit source selected | Token acquired on first API call, content loads normally |
| HTTP 401 during API call | Interceptor force-refreshes token, retries request once, user sees no error |
| Backend fallback (MobileSpoof fails 5x) | Switches to GenericWeb, logs warning, content loads |
| Both backends exhausted (10 failures) | "Something went wrong" + RETRY. Tapping RETRY resets state machine and tries again. |
| HTTP 403 from oauth.reddit.com | Passes through to caller (quarantined/private content), not treated as auth failure |
| Proactive refresh at ~23h 58m | New token acquired transparently, no user interruption |
| Rate limit (`x-ratelimit-remaining` < 10) | Non-blocking token rotation, current request not affected |
| Teddit/scraping source selected | Auth code only executes for `getMoreChildren`/`searchPost`/`searchUser` (see below). All other calls bypass auth entirely. |
| `getMoreChildren` / `searchPost` / `searchUser` | Always routed through `redditSource` (authenticated) by `CurrentSource`, **even when Teddit or scraping is the active source**. These methods use auth regardless of source selection because Teddit/scraping don't support them. |
