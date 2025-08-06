package com.example.data_repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// アプリ全体で共有するDataStoreインスタンス
// Hiltモジュールで提供する代わりに、Contextの拡張プロパティとして定義するのが一般的
 val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * アプリの表示設定やユーザー設定を永続化するためのEnumクラス
 */
enum class LayoutMode {
    LIST,
    GRID_MEDIUM,
    GRID_SMALL
}

enum class ViewMode {
    SINGLE, // ブラウザのみの1画面モード
    DUAL    // ブラウザ＋ミニプレーヤーの2画面モード
}

/**
 * アプリの設定情報を管理するリポジトリ。
 * Jetpack DataStoreを使用して、UI設定やユーザーのお気に入りパスなどを永続化する。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // DataStoreで使用するキーを定義する内部オブジェクト
    private object PreferencesKeys {
        // 表示密度 (リスト, グリッド中, グリッド小)
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        // 画面モード (1画面, 2画面)
        val VIEW_MODE = stringPreferencesKey("view_mode")
        // お気に入りパスのセット (ローカルストレージ用)
        val LOCAL_FAVORITE_PATHS = stringSetPreferencesKey("local_favorite_paths")
        // デフォルトパス (ローカルストレージ用)
        val LOCAL_DEFAULT_PATH = stringPreferencesKey("local_default_path")
    }

    /**
     * 現在の表示密度設定をFlowとして公開する。
     * 設定が未定義の場合はLISTをデフォルト値とする。
     */
    val layoutMode: Flow<LayoutMode> = context.dataStore.data.map { preferences ->
        // 保存された文字列からEnumを復元。失敗した場合はデフォルト値を返す。
        try {
            LayoutMode.valueOf(preferences[PreferencesKeys.LAYOUT_MODE] ?: LayoutMode.LIST.name)
        } catch (e: IllegalArgumentException) {
            LayoutMode.LIST
        }
    }

    /**
     * 現在の画面モード設定をFlowとして公開する。
     * 設定が未定義の場合はSINGLEをデフォルト値とする。
     */
    val viewMode: Flow<ViewMode> = context.dataStore.data.map { preferences ->
        try {
            ViewMode.valueOf(preferences[PreferencesKeys.VIEW_MODE] ?: ViewMode.SINGLE.name)
        } catch (e: IllegalArgumentException) {
            ViewMode.SINGLE
        }
    }

    /**
     * ローカルストレージのお気に入りパス一覧をFlowとして公開する。
     */
    val localFavoritePaths: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOCAL_FAVORITE_PATHS] ?: emptySet()
    }

    /**
     * ローカルストレージのデフォルトパスをFlowとして公開する。
     * 設定が未定義の場合は、要件定義に基づいた初期値を返す。
     */
    val localDefaultPath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOCAL_DEFAULT_PATH] ?: "/storage/emulated/0/Music"
    }

    /**
     * 表示密度設定を更新する。
     */
    suspend fun updateLayoutMode(mode: LayoutMode) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.LAYOUT_MODE] = mode.name
        }
    }

    /**
     * 画面モード設定を更新する。
     */
    suspend fun updateViewMode(mode: ViewMode) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.VIEW_MODE] = mode.name
        }
    }

    /**
     * ローカルストレージのお気に入りに新しいパスを追加する。
     * 既に存在する場合は何もしない。
     */
    suspend fun addLocalFavoritePath(path: String) {
        context.dataStore.edit { settings ->
            val currentFavorites = settings[PreferencesKeys.LOCAL_FAVORITE_PATHS] ?: emptySet()
            settings[PreferencesKeys.LOCAL_FAVORITE_PATHS] = currentFavorites + path
        }
    }

    /**
     * ローカルストレージのお気に入りから指定したパスを削除する。
     */
    suspend fun removeLocalFavoritePath(path: String) {
        context.dataStore.edit { settings ->
            val currentFavorites = settings[PreferencesKeys.LOCAL_FAVORITE_PATHS] ?: emptySet()
            settings[PreferencesKeys.LOCAL_FAVORITE_PATHS] = currentFavorites - path
        }
    }

    /**
     * ローカルストレージのデフォルトパスを更新する。
     */
    suspend fun updateLocalDefaultPath(path: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.LOCAL_DEFAULT_PATH] = path
        }
    }
}