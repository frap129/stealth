package com.cosmos.unreddit.data.remote.datasource

import com.cosmos.unreddit.data.model.Sort
import com.cosmos.unreddit.data.model.Sorting
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutChild
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.ListingData
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.data.remote.api.reddit.source.BaseRedditSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class SearchPostDataSource(
    private val source: BaseRedditSource,
    query: String,
    sorting: Sorting,
    private val includeNsfw: Boolean = false,
) : PostListDataSource(source, query, sorting) {

    override suspend fun getResponse(query: String, sorting: Sorting, after: String?): Listing {
        val posts = source.searchPost(query, sorting.generalSorting, sorting.timeSorting, after)
        if (!includeNsfw || after != null) return posts

        val nsfwChildren = fetchNsfwPosts(query, sorting.generalSorting)
        if (nsfwChildren.isEmpty()) return posts

        val existingIds = posts.data.children.mapNotNull { (it as? PostChild)?.data?.name }.toSet()
        val additionalPosts =
            nsfwChildren
                .filterNot { it.data.name in existingIds }
                .take(MAX_NSFW_POSTS)
        if (additionalPosts.isEmpty()) return posts

        val children = posts.data.children + additionalPosts
        return Listing(
            posts.kind,
            ListingData(
                posts.data.modhash,
                children.size,
                children,
                posts.data.after,
                posts.data.before,
            ),
        )
    }

    private suspend fun fetchNsfwPosts(query: String, sort: Sort): List<PostChild> =
        try {
            val subreddits =
                source.searchSubreddit(query, null, null).data.children
                    .mapNotNull { (it as? AboutChild)?.data }
                    .filter { it.over18 == true }
                    .map { it.displayName }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(MAX_NSFW_SUBREDDITS)
            if (subreddits.isEmpty()) {
                emptyList()
            } else {
                coroutineScope {
                    subreddits
                        .map { subreddit ->
                            async { source.getSubreddit(subreddit, sort, null) }
                        }.awaitAll()
                }.flatMap { it.data.children }
                    .filterIsInstance<PostChild>()
                    .distinctBy { it.data.name }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    private companion object {
        private const val MAX_NSFW_SUBREDDITS = 3
        private const val MAX_NSFW_POSTS = 25
    }
}
