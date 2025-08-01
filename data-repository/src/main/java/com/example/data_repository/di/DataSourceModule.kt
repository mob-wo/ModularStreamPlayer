package com.example.data_repository.di

import com.example.data_local.LocalMediaSource
import com.example.data_source.MediaSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    abstract fun bindMediaSource(
        localMediaSource: LocalMediaSource
    ): MediaSource
}