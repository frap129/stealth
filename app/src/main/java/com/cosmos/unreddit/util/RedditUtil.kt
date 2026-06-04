package com.cosmos.unreddit.util

object RedditUtil {
    fun joinSubredditList(subreddits: List<String>): String = subreddits.joinToString("%2B")
}
