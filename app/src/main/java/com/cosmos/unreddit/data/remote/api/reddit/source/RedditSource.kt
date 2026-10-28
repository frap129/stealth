package com.cosmos.unreddit.data.remote.api.reddit.source

import com.cosmos.unreddit.data.model.Sort
import com.cosmos.unreddit.data.model.TimeSorting
import com.cosmos.unreddit.data.remote.api.reddit.RedditSearchApi
import com.cosmos.unreddit.data.remote.api.reddit.graphql.GraphQlExecutor
import com.cosmos.unreddit.data.remote.api.reddit.graphql.RedditGraphQlOperations
import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlCommentMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlPostMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlSubredditMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.GraphQlUserMapper
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlComment
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlError
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlFeedElements
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlFeedRoot
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPageInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPostInfo
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlPostNode
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlSubmittedComment
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.LiveCommentsByIdsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.PopularFeedData
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.PopularFeedResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.PostCommentsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.PostsByIdsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SearchPeopleResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubmittedCommentsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubmittedPostsResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubredditFeedResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.SubredditInfoResponse
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.UserProfileResponse
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutChild
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutUserChild
import com.cosmos.unreddit.data.remote.api.reddit.model.Child
import com.cosmos.unreddit.data.remote.api.reddit.model.Data
import com.cosmos.unreddit.data.remote.api.reddit.model.JsonMore
import com.cosmos.unreddit.data.remote.api.reddit.model.Listing
import com.cosmos.unreddit.data.remote.api.reddit.model.ListingData
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChild
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChildren
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreData
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.di.DispatchersModule.IoDispatcher
import com.cosmos.unreddit.di.NetworkModule.RedditMoshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditSource @Inject constructor(
    private val client: GraphQlExecutor,
    private val searchApi: RedditSearchApi,
    @RedditMoshi moshi: Moshi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseRedditSource {

    private val postMapper: GraphQlPostMapper = GraphQlPostMapper()
    private val commentMapper: GraphQlCommentMapper = GraphQlCommentMapper()
    private val subredditMapper: GraphQlSubredditMapper = GraphQlSubredditMapper()
    private val userMapper: GraphQlUserMapper = GraphQlUserMapper()

    private val subredditFeedAdapter: JsonAdapter<SubredditFeedResponse> =
        moshi.adapter(SubredditFeedResponse::class.java)
    private val popularFeedAdapter: JsonAdapter<PopularFeedResponse> =
        moshi.adapter(PopularFeedResponse::class.java)
    private val submittedPostsAdapter: JsonAdapter<SubmittedPostsResponse> =
        moshi.adapter(SubmittedPostsResponse::class.java)
    private val postsByIdsAdapter: JsonAdapter<PostsByIdsResponse> =
        moshi.adapter(PostsByIdsResponse::class.java)
    private val subredditInfoAdapter: JsonAdapter<SubredditInfoResponse> =
        moshi.adapter(SubredditInfoResponse::class.java)
    private val searchPeopleAdapter: JsonAdapter<SearchPeopleResponse> =
        moshi.adapter(SearchPeopleResponse::class.java)
    private val postCommentsAdapter: JsonAdapter<PostCommentsResponse> =
        moshi.adapter(PostCommentsResponse::class.java)
    private val liveCommentsAdapter: JsonAdapter<LiveCommentsByIdsResponse> =
        moshi.adapter(LiveCommentsByIdsResponse::class.java)
    private val submittedCommentsAdapter: JsonAdapter<SubmittedCommentsResponse> =
        moshi.adapter(SubmittedCommentsResponse::class.java)
    private val userProfileAdapter: JsonAdapter<UserProfileResponse> =
        moshi.adapter(UserProfileResponse::class.java)

    /**
     * Fetches a single subreddit feed. Multi-subreddit fan-out belongs to the datasource layer.
     */
    override suspend fun getSubreddit(
        subreddit: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing = withContext(ioDispatcher) {
        if (subreddit.equals(POPULAR_FEED, ignoreCase = true)) {
            return@withContext popularListing(sort, timeSorting, after)
        }
        val variables: Map<String, Any?> =
            buildVariables(
                "subredditName" to subreddit,
                "sort" to sort.toPostFeedSort(),
                "time" to timeSorting?.toFeedRange(),
                "after" to after,
            )
        val response: SubredditFeedResponse =
            parse(subredditFeedAdapter, client.execute(RedditGraphQlOperations.SubredditFeedSdui, variables))
        val data = requireData(response.data, response.errors)
        val feed = requireField(data.subredditV3, response.errors, "Missing subreddit feed")
        val elements = requireField(feed.elements, response.errors, "Missing feed elements")
        postsListing(elements, useHasNextPage = false)
    }

    override suspend fun getSubredditInfo(subreddit: String): Child = withContext(ioDispatcher) {
        val variables =
            mapOf(
                "subredditName" to subreddit,
                "includeRecapFields" to false,
                "includeDevvitData" to false,
            )
        val response: SubredditInfoResponse =
            parse(subredditInfoAdapter, client.execute(RedditGraphQlOperations.SubredditInfoByName, variables))
        val data = requireData(response.data, response.errors)
        val info = requireField(data.subredditInfoByName, response.errors, "Missing subreddit info")
        if (info.typename != null && info.typename != SUBREDDIT_TYPENAME) {
            throw IOException(graphQlErrorMessage(response.errors, "Subreddit unavailable"))
        }
        AboutChild(subredditMapper.toAboutData(info))
    }

    override suspend fun searchInSubreddit(
        subreddit: String,
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing = withContext(ioDispatcher) {
        searchApi.searchInSubreddit(subreddit, query, sort.toRestSearchSort(), timeSorting?.type, after)
    }

    override suspend fun getPost(
        permalink: String,
        limit: Int?,
        sort: Sort,
    ): List<Listing> = withContext(ioDispatcher) {
        val postId = postIdFromPermalink(permalink)
        val post: GraphQlPostNode = postsByIds(listOf(postId)).firstOrNull()
            ?: throw IOException("Post not found")
        val postListing: Listing = listing(listOf(PostChild(postMapper.toPostData(post))))
        val commentVariables: Map<String, Any?> =
            buildVariables(
                "id" to postId,
                "sortType" to sort.toCommentSort(),
                "count" to limit,
                "includePremiumAvatarTreatment" to false,
                "includeConsistencyToken" to false,
            )
        val response: PostCommentsResponse =
            parse(postCommentsAdapter, client.execute(RedditGraphQlOperations.PostComments, commentVariables))
        val data = requireData(response.data, response.errors)
        val postInfo = requireField(data.postInfoById, response.errors, "Missing post info")
        val comments: List<Child> =
            commentMapper.toChildren(
                postInfo.commentForest,
                postInfo.id,
                postInfo.title,
                permalink,
                postInfo.subreddit?.prefixedName.orEmpty(),
            )
        listOf(postListing, listing(comments))
    }

    override suspend fun getMoreChildren(
        cursor: String,
        linkId: String,
        sortType: String,
    ): MoreChildren = withContext(ioDispatcher) {
        val variables: Map<String, Any?> =
            buildVariables(
                "id" to linkId,
                "sortType" to sortType,
                "after" to cursor,
                "includePremiumAvatarTreatment" to false,
                "includeConsistencyToken" to false,
            )
        val response: PostCommentsResponse =
            parse(postCommentsAdapter, client.execute(RedditGraphQlOperations.PostComments, variables))
        val data = requireData(response.data, response.errors)
        val postInfo = requireField(data.postInfoById, response.errors, "Missing post info")
        commentMapper
            .toMoreChildren(
                postInfo.commentForest,
                postInfo.id,
                postInfo.title,
                postPermalink(postInfo.id),
                postInfo.subreddit?.prefixedName.orEmpty(),
            )
            .withCursorAndSortType(cursor, sortType)
    }

    override suspend fun getUserInfo(user: String): Child = withContext(ioDispatcher) {
        val response: UserProfileResponse =
            parse(
                userProfileAdapter,
                client.execute(RedditGraphQlOperations.UserProfileDetails, mapOf("username" to user)),
            )
        val data = requireData(response.data, response.errors)
        val profile = requireField(data.redditorInfoByName, response.errors, "Missing user profile")
        val isSuspended =
            profile.typename == UNAVAILABLE_REDDITOR_TYPENAME ||
                profile.typename == DELETED_REDDITOR_TYPENAME
        AboutUserChild(userMapper.toAboutUserData(profile, isSuspended))
    }

    override suspend fun getUserPosts(
        user: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing = withContext(ioDispatcher) {
        val variables: Map<String, Any?> =
            buildVariables(
                "username" to user,
                "sort" to sort.toPostFeedSort(),
                "time" to timeSorting?.toFeedRange(),
                "after" to after,
            )
        val response: SubmittedPostsResponse =
            parse(
                submittedPostsAdapter,
                client.execute(RedditGraphQlOperations.SubmittedPostsFeedSdui, variables),
            )
        val data = requireData(response.data, response.errors)
        val feed = requireField(data.profileFeed, response.errors, "Missing profile feed")
        val elements = requireField(feed.elements, response.errors, "Missing feed elements")
        postsListing(elements, useHasNextPage = true)
    }

    /**
     * Hydrates submitted comments through [RedditGraphQlOperations.LiveCommentsByIds]. Live
     * responses may omit deleted or unavailable comments; omitted IDs are skipped.
     */
    override suspend fun getUserComments(
        user: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing = withContext(ioDispatcher) {
        val variables: Map<String, Any?> =
            buildVariables(
                "name" to user,
                "sort" to sort.toProfileFeedSort(),
                "range" to timeSorting?.toFeedRange(),
                "after" to after,
            )
        val response: SubmittedCommentsResponse =
            parse(
                submittedCommentsAdapter,
                client.execute(RedditGraphQlOperations.SubmittedComments, variables),
            )
        val data = requireData(response.data, response.errors)
        val userInfo = requireField(data.redditorInfoByName, response.errors, "Missing submitted comments user")
        val submitted = requireField(userInfo.comments, response.errors, "Missing submitted comments")
        val submittedComments: List<GraphQlSubmittedComment> =
            submitted.edges.map { it.node }.filter { GraphQlCommentMapper.isRenderable(it) }
        val ids: List<String> = submittedComments.mapNotNull { it.id }
        if (ids.isEmpty()) {
            return@withContext listing(emptyList(), submitted.pageInfo.nextPageCursorOrNull())
        }
        val postInfoByCommentId: Map<String, GraphQlPostInfo?> =
            submittedComments
                .mapNotNull { comment: GraphQlSubmittedComment ->
                    comment.id?.let { id: String -> id to comment.postInfo }
                }
                .toMap()
        val liveResponse: LiveCommentsByIdsResponse =
            parse(
                liveCommentsAdapter,
                client.execute(
                    RedditGraphQlOperations.LiveCommentsByIds,
                    mapOf(
                        "ids" to ids,
                        "includePremiumAvatarTreatment" to false,
                        "includeCommentsHtmlField" to true,
                    ),
                ),
            )
        val liveData = requireData(liveResponse.data, liveResponse.errors)
        val liveComments = requireField(liveData.commentsByIds, liveResponse.errors, "Missing live comments")
        val commentsById: Map<String, GraphQlComment> =
            liveComments
                .filter { GraphQlCommentMapper.isRenderable(it) }
                .mapNotNull { comment: GraphQlComment ->
                    comment.id?.let { id: String -> id to comment }
                }
                .toMap()
        val comments: List<Child> =
            ids.mapNotNull { commentId: String ->
                val comment: GraphQlComment = commentsById[commentId] ?: return@mapNotNull null
                val postInfo: GraphQlPostInfo? = postInfoByCommentId[commentId]
                val postId: String = postInfo?.id.orEmpty()
                commentMapper.toCommentChild(
                    comment,
                    postId,
                    postInfo?.title.orEmpty(),
                    postPermalink(postId),
                    postInfo?.subreddit?.prefixedName.orEmpty(),
                )
            }
        listing(comments, submitted.pageInfo.nextPageCursorOrNull())
    }

    override suspend fun searchPost(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing = withContext(ioDispatcher) {
        searchApi.searchPost(query, sort.toRestSearchSort(), timeSorting?.type, after)
    }

    override suspend fun searchUser(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing = withContext(ioDispatcher) {
        val variables: Map<String, Any?> =
            buildVariables(
                "query" to query,
                "productSurface" to SEARCH_PRODUCT_SURFACE,
                "afterCursor" to after,
                "sort" to sort.toSearchSort(),
            )
        val response: SearchPeopleResponse =
            parse(searchPeopleAdapter, client.execute(RedditGraphQlOperations.SearchPeople, variables))
        val data = requireData(response.data, response.errors)
        val search = requireField(data.search, response.errors, "Missing search")
        val general = requireField(search.general, response.errors, "Missing general search")
        val authors = requireField(general.authors, response.errors, "Missing search authors")
        listing(
            authors.edges.map { AboutUserChild(userMapper.toAboutUserData(it.node)) },
            authors.pageInfo.nextPageCursorOrNull(),
        )
    }

    override suspend fun searchSubreddit(
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing = withContext(ioDispatcher) {
        searchApi.searchSubreddit(query, sort.toRestSearchSort(), timeSorting?.type, after)
    }

    private suspend fun postsListing(
        elements: GraphQlFeedElements,
        useHasNextPage: Boolean,
    ): Listing {
        val ids: List<String> =
            elements.edges
                .mapNotNull { it.node?.groupId }
                .filter { it.startsWith(POST_ID_PREFIX) }
        val posts: List<GraphQlPostNode> = postsByIds(ids)
        val postsById: Map<String, GraphQlPostNode> = posts.associateBy { it.id.orEmpty() }
        val children: List<Child> =
            ids.mapNotNull { id: String ->
                postsById[id]?.let { post: GraphQlPostNode -> PostChild(postMapper.toPostData(post)) }
            }
        val cursor: String? =
            if (useHasNextPage) {
                elements.pageInfo.nextPageCursorOrNull()
            } else {
                elements.pageInfo.feedCursorOrNull()
            }
        return listing(children, cursor)
    }

    private suspend fun popularListing(
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?,
    ): Listing {
        val variables: Map<String, Any?> =
            buildVariables(
                "sort" to sort.toPostFeedSort(),
                "time" to timeSorting?.toFeedRange(),
                "after" to after,
            )
        val response: PopularFeedResponse =
            parse(
                popularFeedAdapter,
                client.execute(RedditGraphQlOperations.PopularFeedSdui, variables),
            )
        val data: PopularFeedData = requireData(response.data, response.errors)
        val feed: GraphQlFeedRoot =
            requireField(data.popularV3, response.errors, "Missing popular feed")
        val elements: GraphQlFeedElements =
            requireField(feed.elements, response.errors, "Missing feed elements")
        return postsListing(elements, useHasNextPage = false)
    }

    private suspend fun postsByIds(ids: List<String>): List<GraphQlPostNode> {
        if (ids.isEmpty()) return emptyList()
        val response: PostsByIdsResponse =
            parse(
                postsByIdsAdapter,
                client.execute(
                    RedditGraphQlOperations.PostsByIds,
                    buildVariables(
                        "ids" to ids,
                        "includeExtendedVideoAsset" to true,
                        "includePlaybackMp4s" to true,
                    ),
                ),
            )
        val data = requireData(response.data, response.errors)
        return requireField(data.postsInfoByIds, response.errors, "Missing posts").mapNotNull { post: GraphQlPostNode? ->
            post?.takeIf { it.isRenderablePost() }
        }
    }

    private fun listing(children: List<Child>, cursor: String? = null): Listing =
        Listing(
            LISTING_KIND,
            ListingData(
                null,
                children.size,
                children,
                cursor,
                null,
            ),
        )

    private fun GraphQlPageInfo.feedCursorOrNull(): String? =
        endCursor?.takeIf { it.isNotBlank() }

    private fun GraphQlPageInfo.nextPageCursorOrNull(): String? =
        if (hasNextPage) {
            endCursor?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    private fun MoreChildren.withCursorAndSortType(
        cursor: String,
        sortType: String,
    ): MoreChildren {
        val things: List<Child> =
            json.data.things.map { child: Child ->
                if (child !is MoreChild) return@map child
                val data: MoreData = child.data
                MoreChild(
                    MoreData(
                        count = data.count,
                        name = data.name,
                        id = data.cursor ?: cursor,
                        depth = data.depth,
                        parentId = data.parentId,
                        children = data.children,
                        cursor = data.cursor ?: cursor,
                        sortType = data.sortType ?: sortType,
                    ),
                )
            }
        return MoreChildren(JsonMore(Data(things)))
    }

    private fun postIdFromPermalink(permalink: String): String {
        val segments: List<String> = extractPath(permalink).split('/').filter { it.isNotEmpty() }
        val id: String? =
            when {
                segments.size >= 4 &&
                    segments[0] in POST_OWNER_SEGMENTS &&
                    segments[2] in POST_ROUTE_SEGMENTS &&
                    REDDIT_ID_REGEX.matches(segments[3]) -> segments[3]
                segments.size >= 2 &&
                    segments[0] in POST_ROUTE_SEGMENTS &&
                    REDDIT_ID_REGEX.matches(segments[1]) -> segments[1]
                else ->
                    segments.singleOrNull()?.takeIf { segment: String ->
                        REDDIT_ID_REGEX.matches(segment.removePrefix(POST_ID_PREFIX))
                    }
            }
        val resolvedId: String = id ?: throw IOException("Could not parse post id from permalink")
        return if (resolvedId.startsWith(POST_ID_PREFIX)) {
            resolvedId
        } else {
            POST_ID_PREFIX + resolvedId
        }
    }

    private fun extractPath(permalink: String): String {
        val normalized: String = permalink.substringBefore('?').substringBefore('#').trim()
        if (!normalized.contains("://")) return normalized
        return normalized.substringAfter("://").substringAfter('/', missingDelimiterValue = "")
    }

    private fun postPermalink(postId: String): String {
        val id: String = postId.removePrefix(POST_ID_PREFIX)
        return if (id.isBlank()) "" else "https://www.reddit.com/comments/$id/"
    }

    private fun GraphQlPostNode.isRenderablePost(): Boolean =
        (typename == null || typename in RENDERABLE_POST_TYPENAMES) && !id.isNullOrBlank()

    private fun buildVariables(vararg variables: Pair<String, Any?>): Map<String, Any?> =
        variables.filter { it.second != null }.toMap()

    private fun <T> parse(adapter: JsonAdapter<T>, response: String): T =
        try {
            adapter.fromJson(response) ?: throw IOException(EMPTY_RESPONSE)
        } catch (exception: JsonDataException) {
            throw IOException("Malformed GraphQL response: ${exception.message}", exception)
        }

    private fun <T> requireData(data: T?, errors: List<GraphQlError>?): T =
        requireField(data, errors, EMPTY_RESPONSE)

    private fun <T> requireField(
        value: T?,
        errors: List<GraphQlError>?,
        missingMessage: String,
    ): T = value ?: throw IOException(graphQlErrorMessage(errors, missingMessage))

    private fun graphQlErrorMessage(errors: List<GraphQlError>?, fallback: String): String =
        errors?.joinToString("; ") { it.message }?.takeIf { it.isNotBlank() } ?: fallback

    private fun Sort.toPostFeedSort(): String? =
        when (this) {
            Sort.BEST -> "BEST"
            Sort.HOT -> "HOT"
            Sort.NEW -> "NEW"
            Sort.TOP -> "TOP"
            Sort.RISING -> "RISING"
            Sort.CONTROVERSIAL -> "CONTROVERSIAL"
            else -> null
        }

    private fun Sort.toProfileFeedSort(): String? =
        when (this) {
            Sort.HOT -> "HOT"
            Sort.NEW -> "NEW"
            Sort.TOP -> "TOP"
            Sort.CONTROVERSIAL -> "CONTROVERSIAL"
            else -> null
        }

    private fun Sort.toCommentSort(): String =
        when (this) {
            Sort.BEST -> "CONFIDENCE"
            Sort.TOP -> "TOP"
            Sort.NEW -> "NEW"
            Sort.OLD -> "OLD"
            Sort.CONTROVERSIAL -> "CONTROVERSIAL"
            Sort.QA -> "QA"
            else -> "CONFIDENCE"
        }

    private fun Sort?.toSearchSort(): String? =
        when (this) {
            null -> null
            Sort.RELEVANCE -> "RELEVANCE"
            Sort.HOT -> "HOT"
            Sort.NEW -> "NEW"
            Sort.TOP -> "TOP"
            Sort.COMMENTS -> "COMMENTS"
            else -> null
        }

    private fun Sort?.toRestSearchSort(): String? = toSearchSort()?.lowercase(Locale.US)

    private fun TimeSorting.toFeedRange(): String =
        when (this) {
            TimeSorting.HOUR -> "HOUR"
            TimeSorting.DAY -> "DAY"
            TimeSorting.WEEK -> "WEEK"
            TimeSorting.MONTH -> "MONTH"
            TimeSorting.YEAR -> "YEAR"
            TimeSorting.ALL -> "ALL"
        }

    private companion object {
        private const val LISTING_KIND: String = "Listing"
        private const val POPULAR_FEED: String = "popular"
        private const val POST_ID_PREFIX: String = "t3_"
        private const val SEARCH_PRODUCT_SURFACE: String = "SEARCH"
        private const val EMPTY_RESPONSE: String = "Empty GraphQL response"
        private const val SUBREDDIT_TYPENAME: String = "Subreddit"
        private const val UNAVAILABLE_REDDITOR_TYPENAME: String = "UnavailableRedditor"
        private const val DELETED_REDDITOR_TYPENAME: String = "DeletedRedditor"
        private val RENDERABLE_POST_TYPENAMES: Set<String> =
            setOf("SubredditPost", "ProfilePost")
        private val POST_OWNER_SEGMENTS: Set<String> = setOf("r", "u", "user")
        private val POST_ROUTE_SEGMENTS: Set<String> = setOf("comments", "gallery")
        private val REDDIT_ID_REGEX: Regex = Regex("[a-z0-9]+")
    }
}
