package com.cosmos.unreddit.di

import com.cosmos.unreddit.data.remote.api.reddit.source.BaseRedditSource
import com.cosmos.unreddit.data.remote.api.reddit.source.RedditSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {

    @Binds
    @Singleton
    abstract fun bindBaseRedditSource(redditSource: RedditSource): BaseRedditSource
}
