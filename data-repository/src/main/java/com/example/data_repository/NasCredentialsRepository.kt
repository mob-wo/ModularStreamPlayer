package com.example.data_repository

import android.content.SharedPreferences
import com.example.core_model.NasConnection
import com.example.data_repository.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NAS接続情報を安全に管理するためのリポジトリ。
 * EncryptedSharedPreferencesを使用して、接続情報リストをJSON形式で永続化する。
 * (kotlinx.serialization 版)
 */
@Singleton
class NasCredentialsRepository @Inject constructor(
    private val encryptedPrefs: SharedPreferences,
    // I/O処理用にデフォルトのディスパッチャを注入可能にする（テスト容易性のため）
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    // SharedPreferencesにデータを保存するためのキー
    private companion object {
        const val KEY_NAS_CONNECTIONS = "nas_connections_list"
    }

    // kotlinx.serializationのJsonインスタンス。設定はデフォルトのままで良い場合が多い。
    private val json = Json { ignoreUnknownKeys = true }

    // メモリ内にキャッシュし、UIに公開するためのStateFlow
    private val _connections = MutableStateFlow<List<NasConnection>>(emptyList())
    val connections: StateFlow<List<NasConnection>> = _connections.asStateFlow()

    init {
        // リポジトリ初期化時に、SharedPreferencesからデータを読み込んでFlowに反映させる
        CoroutineScope(ioDispatcher).launch {
            _connections.value = loadConnectionsFromPrefs()
        }
    }

    /**
     * 新しいNAS接続情報を追加、または既存のものを更新する。
     *
     * @param connection 保存する接続情報。
     */
    suspend fun saveConnection(connection: NasConnection) {
        val currentList = _connections.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == connection.id }

        if (existingIndex != -1) {
            // 既存のIDがあれば、その要素を置き換える（更新）
            currentList[existingIndex] = connection
        } else {
            // 既存のIDがなければ、リストの末尾に追加する（新規）
            currentList.add(connection)
        }

        // メモリと永続化層の両方を更新
        saveConnectionsToPrefs(currentList)
        _connections.value = currentList.toList() // 不変リストとして更新
    }

    /**
     * 指定されたIDのNAS接続情報を削除する。
     *
     * @param connectionId 削除する接続情報のID。
     */
    suspend fun deleteConnection(connectionId: String) {
        val updatedList = _connections.value.filterNot { it.id == connectionId }

        // メモリと永続化層の両方を更新
        saveConnectionsToPrefs(updatedList)
        _connections.value = updatedList
    }

    /**
     * 指定されたIDのNAS接続情報を取得する。
     *
     * @param connectionId 取得したい接続情報のID。
     * @return 見つかった場合はNasConnectionオブジェクト、見つからなければnull。
     */
    fun getConnectionById(connectionId: String): NasConnection? {
        return _connections.value.find { it.id == connectionId }
    }

    // --- Private Helper Functions ---

    /**
     * SharedPreferencesから接続情報リストを読み込む。
     */
    private suspend fun loadConnectionsFromPrefs(): List<NasConnection> = withContext(ioDispatcher) {
        val jsonString = encryptedPrefs.getString(KEY_NAS_CONNECTIONS, null)
        if (jsonString.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                // JSON文字列をList<NasConnection>に変換
                json.decodeFromString<List<NasConnection>>(jsonString)
            } catch (e: Exception) {
                // JSONパースに失敗した場合は空リストを返す
                // TODO: エラーロギング
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * 接続情報リストをJSONに変換してSharedPreferencesに書き込む。
     */
    private suspend fun saveConnectionsToPrefs(connections: List<NasConnection>) = withContext(ioDispatcher) {
        // List<NasConnection>をJSON文字列に変換
        val jsonString = json.encodeToString(connections)
        encryptedPrefs.edit().putString(KEY_NAS_CONNECTIONS, jsonString).apply()
    }
}