package com.cosmos.unreddit.data.remote.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cosmos.unreddit.data.model.Sort
import com.cosmos.unreddit.data.model.Sorting
import com.cosmos.unreddit.data.remote.api.reddit.model.Child
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.data.remote.api.reddit.source.BaseRedditSource
import com.cosmos.unreddit.util.extension.interlace
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class SmartPostListDataSource(
    private val source: BaseRedditSource,
    private val query: List<String>,
    private val sorting: Sorting,
    private val defaultDispatcher: CoroutineDispatcher,
    @Suppress("UNUSED_PARAMETER") mainImmediateDispatcher: CoroutineDispatcher,
    private val excludePinnedPosts: Boolean = false,
) : PagingSource<List<String>, Child>() {

    override val keyReuseSupported: Boolean = true

    override suspend fun load(params: LoadParams<List<String>>): LoadResult<List<String>, Child> {
        return try {
            loadPage(params)
        } catch (exception: IOException) {
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        } catch (exception: JsonDataException) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<List<String>, Child>): List<String>? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey
        }
    }

    private suspend fun loadPage(
        params: LoadParams<List<String>>
    ): LoadResult<List<String>, Child> {
        val key = params.key
        val activeIndexes = if (key == null) {
            query.indices.toList()
        } else {
            key.indices.filter { key[it].isNotBlank() }
        }

        if (activeIndexes.isEmpty()) {
            return LoadResult.Page(emptyList(), null, null)
        }

        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        val responses: Map<Int, Listing> = coroutineScope {
            activeIndexes
                .map { index ->
                    async(defaultDispatcher) {
                        index to semaphore.withPermit {
                            source.getSubreddit(
                                query[index],
                                sorting.generalSorting,
                                sorting.timeSorting,
                                key?.getOrNull(index)
                            )
                        }
                    }
                }
                .awaitAll()
                .toMap()
        }

        val data = withContext(defaultDispatcher) {
            query.indices
                .mapNotNull { responses[it]?.data?.children }
                .sort(sorting)
                .filterNot { child -> excludePinnedPosts && (child as PostChild).data.isStickied }
                .distinctBy { (it as PostChild).data.name }
        }

        val nextKey = query.indices
            .map { responses[it]?.data?.after ?: "" }
            .takeIf { slots -> slots.any { it.isNotBlank() } }

        return LoadResult.Page(data, null, nextKey)
    }

    private fun List<List<Child>>.sort(sorting: Sorting): List<Child> {
        return when (sorting.generalSorting) {
            // If sorting is set to NEW, simply flatten the lists and sort the posts by date
            Sort.NEW -> flatten().sortedByDescending { (it as PostChild).data.created }
            // If sorting is set to TOP, simply flatten the lists and sort the posts by score
            Sort.TOP -> flatten().sortedByDescending { (it as PostChild).data.score }
            // For all the other sorting methods, interlace the lists to have a consistent result
            // [['a', 'b', 'c'], ['e', 'f', 'g'], ['h', 'i']] ==> ['a', 'e', 'h', 'b', 'f', 'i', 'c', 'g']
            else -> interlace()
        }
    }

    companion object {
        private const val MAX_CONCURRENT_REQUESTS = 4
    }
}
