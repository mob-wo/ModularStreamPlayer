package com.example.data_repository.di

import com.example.data_local.LocalMediaSource
import com.example.data_source.MediaSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

// ★★★ LocalMediaSourceを特定するためのQualifierアノテーションを定義 ★★★
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalSource

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    // ★★★ 修正: @Bindsではなく@ProvidesでLocalMediaSourceを提供する ★★★
    // これにより、@LocalSourceアノテーションを付与できる
    @Provides
    @Singleton
    @LocalSource // このQualifierを付けることで、他のMediaSource実装と区別する
    fun provideLocalMediaSource(
        localMediaSource: LocalMediaSource // HiltがLocalMediaSourceの作り方を知っているので注入可能
    ): MediaSource {
        return localMediaSource
    }
}


