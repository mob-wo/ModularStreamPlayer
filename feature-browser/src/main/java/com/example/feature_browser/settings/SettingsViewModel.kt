package com.example.feature_browser.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core_model.NasConnection
// SmbMediaSource とそのファクトリ、関連する例外をインポート
import com.example.data_smb.SmbMediaSource
import com.example.data_smb.SmbAccessException
import com.example.data_smb.SmbAuthenticationException
import com.example.data_smb.SmbHostNotFoundException
import com.example.data_smb.SmbShareNotFoundException
import com.example.data_smb.SmbPermissionException
import com.example.data_smb.SmbNetworkException
import com.example.data_repository.NasCredentialsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers // ★★★ Dispatchers.IO をインポート ★★★
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // ★★★ withContext をインポート ★★★
import javax.inject.Inject

/**
 * NAS接続一覧画面の状態
 */
data class NasConnectionListUiState(
    val connections: List<NasConnection> = emptyList()
)

/**
 * NAS接続編集画面の状態
 */
data class NasEditorUiState(
    val id: String? = null,
    val nickname: String = "",
    val hostname: String = "",
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val userMessage: String? = null,
    val isTestingConnection: Boolean = false // 接続テスト中の状態
)

// ★★★ 接続テストの結果を保持するための小さなデータクラス（オプション） ★★★
data class TestResult(val userMessage: String?, val isTestingConnection: Boolean)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val nasRepository: NasCredentialsRepository,
    private val smbMediaSourceFactory: SmbMediaSource.Factory // SmbMediaSource.Factoryを注入
) : ViewModel() {

    // --- 一覧画面用の状態 ---
    // (変更なし)
    val listUiState: StateFlow<NasConnectionListUiState> =
        nasRepository.connections
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
            .combine(MutableStateFlow(0)) { connections, _ -> // ダミーと結合して再評価
                NasConnectionListUiState(connections)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NasConnectionListUiState()
            )


    // --- 編集画面用の状態 ---
    private val _editorUiState = MutableStateFlow(NasEditorUiState())
    val editorUiState: StateFlow<NasEditorUiState> = _editorUiState.asStateFlow()

    /**
     * 指定されたIDの接続情報を削除する
     */
    fun deleteConnection(id: String) {
        viewModelScope.launch {
            nasRepository.deleteConnection(id)
        }
    }

    /**
     * 編集画面の初期データをロードする。IDがnullなら新規作成モード。
     */
    fun loadConnectionForEdit(connectionId: String?) {
        _editorUiState.value = NasEditorUiState() // 状態をリセット
        if (connectionId == null) {
            // 新規作成モード (初期状態のまま)
        } else {
            // 編集モード
            val connection = nasRepository.getConnectionById(connectionId)
            if (connection != null) {
                _editorUiState.value = NasEditorUiState(
                    id = connection.id,
                    nickname = connection.nickname,
                    hostname = connection.hostname,
                    path = connection.path,
                    username = connection.username ?: "",
                    password = connection.password ?: ""
                )
            } else {
                // IDが見つからない場合はエラーメッセージを設定
                _editorUiState.update { it.copy(userMessage = "接続情報が見つかりません") }
            }
        }
    }

    /**
     * 編集画面の入力値を更新する
     */
    fun updateEditorState(
        nickname: String = _editorUiState.value.nickname,
        hostname: String = _editorUiState.value.hostname,
        path: String = _editorUiState.value.path,
        username: String = _editorUiState.value.username,
        password: String = _editorUiState.value.password
    ) {
        _editorUiState.update {
            it.copy(
                nickname = nickname,
                hostname = hostname,
                path = path,
                username = username,
                password = password,
                saveSuccess = false // 入力変更時は保存成功フラグをリセット
            )
        }
    }

    /**
     * 入力された接続情報を保存する
     */
    fun saveConnection() {
        viewModelScope.launch {
            val state = _editorUiState.value
            if (state.nickname.isBlank() || state.hostname.isBlank() || state.path.isBlank()) {
                _editorUiState.update { it.copy(userMessage = "ニックネーム、ホスト名、共有フォルダ名は必須です。") }
                return@launch
            }

            _editorUiState.update { it.copy(isSaving = true) }

            val connection = NasConnection(
                id = state.id ?: java.util.UUID.randomUUID().toString(),
                nickname = state.nickname,
                hostname = state.hostname,
                path = state.path,
                username = state.username.takeIf { it.isNotBlank() },
                password = state.password.takeIf { it.isNotBlank() }
            )
            nasRepository.saveConnection(connection)
            _editorUiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    /**
     * NAS接続テストを実行する
     */
    fun testNasConnection() {
        viewModelScope.launch { // UI関連の状態更新はメインスレッドで開始
            val state = _editorUiState.value
            if (state.hostname.isBlank() || state.path.isBlank()) {
                _editorUiState.update { it.copy(userMessage = "ホスト名と共有フォルダ名は必須です。") }
                return@launch
            }

            _editorUiState.update { it.copy(isTestingConnection = true, userMessage = null) }

            // ★★★ ここからIOディスパッチャで実行 ★★★
            val result = withContext(Dispatchers.IO) {
                // テスト用の一時的なNasConnectionオブジェクトを作成
                val testConnection = NasConnection(
                    id = "test-${java.util.UUID.randomUUID()}", // テスト用ID
                    nickname = "テスト接続", // テスト用ニックネーム
                    hostname = state.hostname,
                    path = state.path,
                    username = state.username.takeIf { it.isNotBlank() },
                    password = state.password.takeIf { it.isNotBlank() }
                )

                try {
                    val smbSource = smbMediaSourceFactory.create(testConnection)
                    // ルートパスのアイテムを1つだけ取得しようと試みる
                    smbSource.getItemsIn(null).firstOrNull() // nullまたは空文字列でルートを示す
                    // 成功メッセージをTestResultとして返す
                    TestResult(userMessage = "接続に成功しました。", isTestingConnection = false)
                } catch (e: SmbAuthenticationException) {
                    TestResult(userMessage = "認証に失敗しました。ユーザー名またはパスワードを確認してください。", isTestingConnection = false)
                } catch (e: SmbHostNotFoundException) {
                    TestResult(userMessage = "ホストが見つかりません。ホスト名またはIPアドレスを確認してください。", isTestingConnection = false)
                } catch (e: SmbShareNotFoundException) {
                    TestResult(userMessage = "共有フォルダが見つかりません。パスを確認してください。", isTestingConnection = false)
                } catch (e: SmbPermissionException) {
                    TestResult(userMessage = "アクセス権がありません。接続設定を確認してください。", isTestingConnection = false)
                } catch (e: SmbNetworkException) {
                    TestResult(userMessage = "ネットワークエラーが発生しました。NASまたはネットワークの状態を確認してください。", isTestingConnection = false)
                } catch (e: SmbAccessException) {
                    TestResult(userMessage = "NASへのアクセス中にエラーが発生しました: ${e.message}", isTestingConnection = false)
                } catch (e: Exception) {
                    // 予期せぬその他のエラー
                    e.printStackTrace() // ログにスタックトレースを出力
                    TestResult(userMessage = "接続テスト中に不明なエラーが発生しました: ${e.message}", isTestingConnection = false)
                }
            }
            // ★★★ IOディスパッチャでの処理結果をUI状態に反映 ★★★
            _editorUiState.update {
                it.copy(
                    userMessage = result.userMessage,
                    isTestingConnection = result.isTestingConnection
                )
            }
        }
    }

    /**
     * ユーザーメッセージ表示後に状態をリセットする
     */
    fun userMessageShown() {
        _editorUiState.update { it.copy(userMessage = null) }
    }
}