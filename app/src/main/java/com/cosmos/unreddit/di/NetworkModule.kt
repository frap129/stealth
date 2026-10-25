package com.cosmos.unreddit.di

import android.content.Context
import com.cosmos.unreddit.data.remote.api.gfycat.GfycatApi
import com.cosmos.unreddit.data.remote.api.imgur.ImgurApi
import com.cosmos.unreddit.data.remote.api.imgur.adapter.AlbumDataAdapter
import com.cosmos.unreddit.data.remote.api.reddit.RedditAuthInterceptor
import com.cosmos.unreddit.data.remote.api.reddit.adapter.EditedAdapter
import com.cosmos.unreddit.data.remote.api.reddit.adapter.MediaMetadataAdapter
import com.cosmos.unreddit.data.remote.api.reddit.adapter.NullToEmptyStringAdapter
import com.cosmos.unreddit.data.remote.api.reddit.adapter.RepliesAdapter
import com.cosmos.unreddit.data.remote.api.reddit.auth.RedditTlsFactory
import com.cosmos.unreddit.data.remote.api.reddit.graphql.GraphQlExecutor
import com.cosmos.unreddit.data.remote.api.reddit.graphql.RedditGraphQlClient
import com.cosmos.unreddit.data.remote.api.reddit.graphql.RedditGraphQlHeadersInterceptor
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutChild
import com.cosmos.unreddit.data.remote.api.reddit.model.AboutUserChild
import com.cosmos.unreddit.data.remote.api.reddit.model.Child
import com.cosmos.unreddit.data.remote.api.reddit.model.ChildType
import com.cosmos.unreddit.data.remote.api.reddit.model.CommentChild
import com.cosmos.unreddit.data.remote.api.reddit.model.MoreChild
import com.cosmos.unreddit.data.remote.api.reddit.model.PostChild
import com.cosmos.unreddit.data.remote.api.redgifs.RedgifsApi
import com.cosmos.unreddit.data.remote.api.streamable.StreamableApi
import com.cosmos.unreddit.di.DispatchersModule.IoDispatcher
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {
    private val TIMEOUT = 60L to TimeUnit.SECONDS

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class RedditMoshi

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class BasicMoshi

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class ImgurMoshi

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class GenericOkHttp

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class RedditGqlOkHttp

    @RedditMoshi
    @Provides
    @Singleton
    fun provideRedditMoshi(): Moshi =
        Moshi
            .Builder()
            .add(
                PolymorphicJsonAdapterFactory
                    .of(Child::class.java, "kind")
                    .withSubtype(CommentChild::class.java, ChildType.t1.name)
                    .withSubtype(AboutUserChild::class.java, ChildType.t2.name)
                    .withSubtype(PostChild::class.java, ChildType.t3.name)
                    .withSubtype(AboutChild::class.java, ChildType.t5.name)
                    .withSubtype(MoreChild::class.java, ChildType.more.name),
            ).add(MediaMetadataAdapter.Factory)
            .add(RepliesAdapter())
            .add(EditedAdapter())
            .add(NullToEmptyStringAdapter())
            .build()

    @BasicMoshi
    @Provides
    @Singleton
    fun provideBasicMoshi(): Moshi =
        Moshi
            .Builder()
            .build()

    @ImgurMoshi
    @Provides
    @Singleton
    fun provideImgurMoshi(): Moshi =
        Moshi
            .Builder()
            .add(AlbumDataAdapter())
            .build()

    @RedditGqlOkHttp
    @Provides
    @Singleton
    fun provideRedditGqlOkHttpClient(
        redditAuthInterceptor: RedditAuthInterceptor,
        graphQlHeadersInterceptor: RedditGraphQlHeadersInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .sslSocketFactory(RedditTlsFactory.socketFactory(), RedditTlsFactory.trustManager())
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(redditAuthInterceptor)
            .addInterceptor(graphQlHeadersInterceptor)
            .connectTimeout(TIMEOUT.first, TIMEOUT.second)
            .readTimeout(TIMEOUT.first, TIMEOUT.second)
            .writeTimeout(TIMEOUT.first, TIMEOUT.second)
            .build()

    @Provides
    @Singleton
    fun provideRedditGraphQlClient(
        @RedditGqlOkHttp okHttpClient: OkHttpClient,
        @RedditMoshi moshi: Moshi,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): RedditGraphQlClient = RedditGraphQlClient(okHttpClient, moshi, ioDispatcher)

    @Provides
    @Singleton
    fun provideGraphQlExecutor(client: RedditGraphQlClient): GraphQlExecutor = client

    @Provides
    @Singleton
    fun provideGraphQlHeadersInterceptor(
        @ApplicationContext context: Context,
    ): RedditGraphQlHeadersInterceptor =
        RedditGraphQlHeadersInterceptor(context, RedditGraphQlHeadersInterceptor.GRAPHQL_HOST)

    @GenericOkHttp
    @Provides
    @Singleton
    fun provideGenericOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(TIMEOUT.first, TIMEOUT.second)
            .readTimeout(TIMEOUT.first, TIMEOUT.second)
            .writeTimeout(TIMEOUT.first, TIMEOUT.second)
            .build()

    @Provides
    @Singleton
    fun provideImgurApi(
        @ImgurMoshi moshi: Moshi,
        @GenericOkHttp okHttpClient: OkHttpClient,
    ): ImgurApi =
        Retrofit
            .Builder()
            .baseUrl(ImgurApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(ImgurApi::class.java)

    @Provides
    @Singleton
    fun provideStreamableApi(
        @BasicMoshi moshi: Moshi,
        @GenericOkHttp okHttpClient: OkHttpClient,
    ): StreamableApi =
        Retrofit
            .Builder()
            .baseUrl(StreamableApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(StreamableApi::class.java)

    @Provides
    @Singleton
    fun provideGfycatApi(
        @BasicMoshi moshi: Moshi,
        @GenericOkHttp okHttpClient: OkHttpClient,
    ): GfycatApi =
        Retrofit
            .Builder()
            .baseUrl(GfycatApi.BASE_URL_GFYCAT)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(GfycatApi::class.java)

    @Provides
    @Singleton
    fun provideRedgifsApi(
        @BasicMoshi moshi: Moshi,
        @GenericOkHttp okHttpClient: OkHttpClient,
    ): RedgifsApi =
        Retrofit
            .Builder()
            .baseUrl(RedgifsApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(RedgifsApi::class.java)
}
