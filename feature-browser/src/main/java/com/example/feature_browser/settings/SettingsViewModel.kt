package com.example.feature_browser.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core_model.NasConnection
import com.example.data_repository.NasCredentialsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val userMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val nasRepository: NasCredentialsRepository
) : ViewModel() {

    // --- 一覧画面用の状態 ---
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
        if (connectionId == null) {
            // 新規作成モード
            _editorUiState.value = NasEditorUiState()
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
                password = password
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
                _editorUiState.update { it.copy(userMessage = "必須項目を入力してください") }
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
     * ユーザーメッセージ表示後に状態をリセットする
     */
    fun userMessageShown() {
        _editorUiState.update { it.copy(userMessage = null) }
    }
}