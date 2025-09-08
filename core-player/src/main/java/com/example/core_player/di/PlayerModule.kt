package com.example.core_player.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
object PlayerModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes
    ): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(audioAttributes, true)
        setHandleAudioBecomingNoisy(true) // イヤホンが抜かれたら一時停止
    }

    // ★★★ 以下のprovideLocalHttpServerメソッドはLocalHttpServerクラスに@Injectコンストラクタと@Singletonを付けたため、明示的な@Providesは不要になりました ★★★
    // Hiltは@Injectコンストラクタを持つクラスのインスタンスを自動的に生成・提供できます。
    // LocalHttpServer.ktのクラス宣言が `class LocalHttpServer @Inject constructor(...)` となっていれば、
    // このモジュールに追記は不要です。

    // 【参考】もしLocalHttpServerに@Injectコンストラクタが付けられない事情がある場合は、
    // 以下のように記述します。
    /*
    @Provides
    @Singleton
    fun provideLocalHttpServer(
        nasCredentialsRepository: com.example.data_repository.NasCredentialsRepository
    ): LocalHttpServer {
        return LocalHttpServer(nasCredentialsRepository)
    }
    */
}