package com.cosmos.unreddit.data.remote.api.reddit

import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GraphQL search drops NSFW results for logged-out tokens regardless of filters, so search
 * uses the REST endpoints, which honor include_over_18 with the same token.
 */
interface RedditSearchApi {
    @GET("search?type=link&include_over_18=1&raw_json=1")
    suspend fun searchPost(
        @Query("q") query: String,
        @Query("sort") sort: String?,
        @Query("t") timeSorting: String?,
        @Query("after") after: String?,
    ): Listing

    @GET("r/{subreddit}/search?restrict_sr=1&include_over_18=1&raw_json=1")
    suspend fun searchInSubreddit(
        @Path("subreddit") subreddit: String,
        @Query("q") query: String,
        @Query("sort") sort: String?,
        @Query("t") timeSorting: String?,
        @Query("after") after: String?,
    ): Listing

    @GET("search?type=sr&include_over_18=1&raw_json=1")
    suspend fun searchSubreddit(
        @Query("q") query: String,
        @Query("sort") sort: String?,
        @Query("t") timeSorting: String?,
        @Query("after") after: String?,
    ): Listing

    companion object {
        const val BASE_URL = "https://oauth.reddit.com/"
    }
}
