package com.example.modularstreamplayer.di
/*
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // LocalMediaSourceの提供はDataSourceModuleに移動＆@Bindsになったので、このメソッドは不要になった
    /*
    @Provides
    @Singleton
    fun provideLocalMediaSource(
        @ApplicationContext context: Context
    ): MediaSource {
        return LocalMediaSource(context.contentResolver)
    }
    */

    // ExoPlayerは外部ライブラリのクラスなので、@Providesで提供する必要がある。
    // これはそのまま残す。
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context
    ): Player {
        return ExoPlayer.Builder(context).build()
    }

    // ViewModelに注入するPlayerインスタンスを@ViewModelScopedで提供する場合などもここに記述できる
}

 */