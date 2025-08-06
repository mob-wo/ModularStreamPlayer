package com.example.feature_browser

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core_model.FolderItem
import com.example.core_model.TrackItem
import com.example.core_model.MediaItem
import com.example.data_repository.LayoutMode
import com.example.data_repository.MediaRepository
import com.example.data_repository.PlaybackState
import com.example.data_repository.PlayerStateRepository
import com.example.data_repository.SettingsRepository
import com.example.data_repository.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class BrowserUiState(
    val rootPath: String = "",
    val currentPath: String = "",
    val isPathInitialized: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val drawerState: DrawerUiState = DrawerUiState(),
    val userMessage: String? = null,
    val playbackState: PlaybackState = PlaybackState()
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val playerStateRepository: PlayerStateRepository,
    //private val savedStateHandle: SavedStateHandle,
    //@ApplicationContext private val context: Context
) : ViewModel() {

    private val _internalUiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState>

    // ファイル読み込みジョブを管理
    private var loadItemsJob: Job? = null

    //private val argPath = context.getString(R.string.nav_arg_path)

    init {
        // SavedStateHandle から path の変更を監視し、uiState.currentPath を更新
//        savedStateHandle.getStateFlow<String?>(argPath, null) // 初期値 null
//            .onEach { newPath ->
//                _uiState.value = _uiState.value.copy(isLoading = true, currentPath = newPath)
//                //loadItems(newPath)
//            }
//            .launchIn(viewModelScope)

        Log.d("BrowserViewModel", "ViewModel instance: ${this.hashCode()} - init block STARTED")

        viewModelScope.launch {
            Log.d("BrowserViewModel", "_internalUiState latest: ${_internalUiState.value}")
        }
        viewModelScope.launch {
            settingsRepository.localDefaultPath.collect { path ->
                Log.d("BrowserViewModel", "localDefaultPath emitted: $path")
            }
        }
        viewModelScope.launch {
            settingsRepository.layoutMode.collect { mode ->
                Log.d("BrowserViewModel", "layoutMode emitted: $mode")
            }
        }
        viewModelScope.launch {
            settingsRepository.viewMode.collect { mode ->
                Log.d("BrowserViewModel", "viewMode emitted: $mode")
            }
        }
        viewModelScope.launch {
            settingsRepository.localFavoritePaths.collect { paths ->
                Log.d("BrowserViewModel", "localFavoritePaths emitted: $paths")
            }
        }
        viewModelScope.launch {
            playerStateRepository.playbackState.collect { state ->
                Log.d("BrowserViewModel", "playbackState emitted: $state")
            }
        }

        Log.d("BrowserViewModel", "ViewModel instance: ${this.hashCode()} - init block: About to define uiState combine")

        // ViewModel初期化時に、複数のFlowを結合して単一のuiStateを構築する
        uiState = combine(
            _internalUiState,
            settingsRepository.localDefaultPath,
            settingsRepository.layoutMode,
            settingsRepository.viewMode,
            settingsRepository.localFavoritePaths,
            playerStateRepository.playbackState
        ) { values ->
            Log.d("BrowserViewModel", "COMBINE BLOCK EXECUTED!: ${this.hashCode()}")

            val internalState = values[0] as BrowserUiState
            val localDefaultPath = values[1] as String
            val layoutMode = values[2] as LayoutMode
            val viewMode = values[3] as ViewMode
            val favoritePaths = values[4] as Set<String>
            val currentPlaybackState = values[5] as PlaybackState
            internalState.copy(
                drawerState = internalState.drawerState.copy(
                    layoutMode = layoutMode,
                    viewMode = viewMode,
                    favoritePaths = favoritePaths.toList().sorted()
                ),
                rootPath = localDefaultPath,
                playbackState = currentPlaybackState
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowserUiState()
        )
        Log.d("BrowserViewModel", "ViewModel instance: ${this.hashCode()} - init block FINISHED (after stateIn)")
        viewModelScope.launch {
            // settingsRepository.localDefaultPath から最初の値を取得
            val initialDefaultPath = settingsRepository.localDefaultPath.first()
            Log.d("BrowserViewModel", "Initial default path loaded: $initialDefaultPath")
            _internalUiState.update { currentState ->
                currentState.copy(
                    currentPath = currentState.currentPath.ifBlank { initialDefaultPath },
                    isPathInitialized = true // ★ 初期化完了フラグを立てる
                )
            }
        }
    }

    fun loadItemsForCurrentPath() {
        loadItemsJob?.cancel()
        Log.d("BrowserViewModel", "_internal_loadItemsForCurrentPath: ${_internalUiState.value.currentPath}")
        Log.d("BrowserViewModel", "uiState_loadItemsForCurrentPath: ${uiState.value.currentPath}")
        _internalUiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val currentPathToLoad = uiState.value.currentPath
                val items = repository.getItemsIn(currentPathToLoad)
                _internalUiState.update { it.copy(items = items, isLoading = false, userMessage = null) }
            } catch (e: Exception) {
                _internalUiState.update { it.copy(items = emptyList(), isLoading = false, userMessage = e.message) }
            }
        }
    }

    fun loadItems(path: String) {
        _internalUiState.update { it.copy(isLoading = true, currentPath = path) }
        loadItemsForCurrentPath()
    }

    /**
     * ドロワーUIから受け取ったイベントを処理する
     */
    fun onDrawerEvent(event: DrawerEvent) {
        viewModelScope.launch {
            when (event) {
                is DrawerEvent.OnLayoutModeChanged -> settingsRepository.updateLayoutMode(event.mode)
                is DrawerEvent.OnViewModeChanged -> settingsRepository.updateViewMode(event.mode)
                is DrawerEvent.OnFavoriteLongClicked -> {
                    settingsRepository.removeLocalFavoritePath(event.path)
                    showUserMessage("'${event.path.substringAfterLast('/')}'をお気に入りから削除しました")
                }
                is DrawerEvent.OnFavoriteClicked -> loadItems(event.path)
                is DrawerEvent.OnRefreshClicked -> loadItemsForCurrentPath()
                // 他のドロワーイベントの処理...
                DrawerEvent.OnFavoritesHeaderClicked -> {
                    // isFavoritesExpandedの状態を反転させる
                    val currentState = uiState.value
                    _internalUiState.update {
                        currentState.copy(
                            drawerState = currentState.drawerState.copy(
                                isFavoritesExpanded = !currentState.drawerState.isFavoritesExpanded
                            )
                        )
                    }
                }

                // TODO: 他のイベントハンドリングを実装
                else -> { /* Not implemented yet */ }
            }
        }
    }

    /**
     * ファイルブラウザUIから受け取ったイベントを処理する
     */
    fun onBrowserEvent(event: BrowserEvent) {
        viewModelScope.launch {
            when(event) {
                is BrowserEvent.OnFolderClicked -> loadItems(event.folder.path)
                is BrowserEvent.OnTrackClicked -> playerStateRepository.updateCurrentTrack(event.track)
                is BrowserEvent.OnFolderLongClicked -> {
                    settingsRepository.addLocalFavoritePath(event.folder.path)
                    showUserMessage("'${event.folder.path}'をお気に入りに追加しました")
                }

                // TODO: 他のイベントハンドリングを実装
            }
        }
    }
    /**
     * Snackbarなどで表示するユーザーメッセージを設定し、一定時間後にクリアする
     */
    private fun showUserMessage(message: String) {
        _internalUiState.update { it.copy(userMessage = message) }
        viewModelScope.launch {
            // メッセージをクリアするのを忘れないように
            // ここでは単純化のためクリア処理は省略
            // _internalState.update { it.copy(userMessage = null) }
        }
    }
}

/**
 * ファイルブラウザ画面で発生したユーザー操作を表現するイベント
 */
sealed interface BrowserEvent {
    data class OnFolderClicked(val folder: FolderItem) : BrowserEvent
    data class OnTrackClicked(val track: TrackItem) : BrowserEvent
    data class OnFolderLongClicked(val folder: FolderItem) : BrowserEvent
    // 他のイベント...
}
