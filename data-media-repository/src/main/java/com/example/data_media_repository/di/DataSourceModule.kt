package com.example.data_media_repository.di

import com.example.core_model.NasConnection
import com.example.core_http.LocalHttpServer
import com.example.data_local.LocalMediaSource
import com.example.data_smb.SmbMediaSource
import com.example.data_source.MediaSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

// LocalMediaSourceを特定するためのQualifierアノテーション
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalSource

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    /**
     * LocalMediaSourceをMediaSourceインターフェースとして提供する。
     * @LocalSourceアノテーションにより、他のMediaSource実装と区別できるようにする。
     */
    @Provides
    @Singleton
    @LocalSource
    fun provideLocalMediaSource(
        // LocalMediaSourceは@Inject constructorを持つため、Hiltがインスタンス化できる
        localMediaSource: LocalMediaSource
    ): MediaSource {
        return localMediaSource
    }

    /**
     * SmbMediaSourceを生成するためのファクトリを提供する。
     * SmbMediaSourceは実行時にNasConnectionが必要なため、直接@Singletonとしては提供できない。
     * そのため、「NasConnectionを受け取ったらSmbMediaSourceを返す関数（ファクトリ）」をHiltに提供する。
     */
    /*
    @Provides
    @Singleton // ファクトリ自体はシングルトンとして管理する
    fun provideSmbMediaSourceFactory(
        localHttpServer: LocalHttpServer // LocalHttpServerは@SingletonなのでHiltが注入できる
    ): (NasConnection) -> SmbMediaSource {
        return { nasConnection ->
            SmbMediaSource(
                localHttpServer = localHttpServer,
                nasConnection = nasConnection
            )
        }
    }*/
}