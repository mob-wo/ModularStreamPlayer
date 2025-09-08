package com.example.data_repository.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    // 暗号化された設定を保存するファイル名
    private const val NAS_CREDENTIALS_FILE_NAME = "nas_credentials_prefs"

    /**
     * NASの接続情報を保存するための暗号化されたSharedPreferencesのインスタンスを提供する。
     * @param context アプリケーションコンテキスト。Hiltによって自動的に注入される。
     * @return SharedPreferencesのインスタンス。
     */
    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        // 1. マスターキーの生成
        // Android Keystoreを使用して、SharedPreferencesを暗号化/復号するためのキーを安全に生成・管理する。
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // 2. EncryptedSharedPreferencesのインスタンスを生成して返す
        return EncryptedSharedPreferences.create(
            context,
            NAS_CREDENTIALS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}