package com.example.feature_browser.browser

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core_model.FileItem
import com.example.core_model.FolderItem
import com.example.core_model.NasConnection
import com.example.core_model.TrackItem
import com.example.data_repository.ActiveDataSource
import com.example.data_repository.LayoutMode
import com.example.data_media_repository.MediaRepository
import com.example.data_repository.NasCredentialsRepository
import com.example.data_repository.PlaybackRequestRepository
import com.example.data_repository.PlayerStateRepository
import com.example.data_repository.SettingsRepository
import com.example.data_repository.ViewMode
import com.example.feature_browser.DataSourceItem
import com.example.feature_browser.DrawerEvent
import com.example.feature_browser.DrawerUiState
// --- ここから追加 ---
import com.example.data_smb.SmbAccessException // 明示的なキャッチのため確認
import com.example.data_smb.SmbAuthenticationException
import com.example.data_smb.SmbHostNotFoundException
import com.example.data_smb.SmbNetworkException
import com.example.data_smb.SmbPermissionException
import com.example.data_smb.SmbShareNotFoundException
// --- ここまで追加 ---
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class BrowserUiState(
    val rootPath: String = "",
    val currentPath: String = "",
    val items: List<FileItem> = emptyList(),
    val currentIndex: Int = -1,
    val isLoading: Boolean = true,
    val drawerState: DrawerUiState = DrawerUiState(),
    val userMessage: String? = null,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val playerStateRepository: PlayerStateRepository,
    private val playbackRequestRepository: PlaybackRequestRepository,
    private val nasCredentialsRepository: NasCredentialsRepository
    //private val savedStateHandle: SavedStateHandle,
    //@ApplicationContext private val context: Context
) : ViewModel() {

    private val _internalUiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState>

    // ファイル読み込みジョブを管理
    private var loadItemsJob: Job? = null

    init {
        Log.d("BrowserViewModel", "ViewModelインスタンス: ${this.hashCode()} - initブロック開始")

        // 1. 初期状態を設定する
        initializeState()

        // 2. uiStateを構築する
        // combineは、_internalUiStateと外部設定をマージする役割に徹する
        uiState = combine(
            _internalUiState,
            settingsRepository.layoutMode,
            settingsRepository.viewMode,
            settingsRepository.localFavoritePaths,
            nasCredentialsRepository.connections
        ) { values ->
            val internalState = values[0] as BrowserUiState
            val layoutMode = values[1] as LayoutMode
            val viewMode = values[2] as ViewMode
            val favoritePaths = values[3] as Set<String>
            val nasConnections = values[4] as List<NasConnection>

            val availableDataSources = buildList {
                add(DataSourceItem.Local)
                addAll(nasConnections.map { DataSourceItem.Smb(it) })
            }

            // internalStateをベースに、外部設定をマージして最終的なUI状態を返す
            internalState.copy(
                drawerState = internalState.drawerState.copy(
                    layoutMode = layoutMode,
                    viewMode = viewMode,
                    favoritePaths = favoritePaths.toList().sorted(),
                    availableDataSources = availableDataSources
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _internalUiState.value
        )
        Log.d("BrowserViewModel", "ViewModelインスタンス: ${this.hashCode()} - initブロック終了")
    }

    // --- イベント処理 ---
    /**
     * ドロワーUIから受け取ったイベントを処理する
     */
    fun onDrawerEvent(event: DrawerEvent) {
        when (event) {
            is DrawerEvent.OnDataSourceSelected -> selectDataSource(event.source)
            is DrawerEvent.OnLayoutModeChanged -> viewModelScope.launch { settingsRepository.updateLayoutMode(event.mode) }
            is DrawerEvent.OnViewModeChanged -> viewModelScope.launch { settingsRepository.updateViewMode(event.mode) }
            is DrawerEvent.OnFavoriteClicked -> navigateToPath(event.path)
            is DrawerEvent.OnFavoriteLongClicked -> removeFavorite(event.path)
            DrawerEvent.OnFavoritesHeaderClicked -> toggleFavoriteExpansion()
            DrawerEvent.OnRefreshClicked -> {
                // ★ ここでisLoadingをtrueに設定
                _internalUiState.update { it.copy(isLoading = true) }
                loadItemsForCurrentPath()
            }
            else -> { /* OnSettingsClickedなど未実装のイベント */ }
        }
    }

    /**
     * ファイルブラウザUIから受け取ったイベントを処理する
     */
    fun onBrowserEvent(event: BrowserEvent) {
        when (event) {
            is BrowserEvent.OnFolderClicked -> navigateToPath(event.folder.path)
            is BrowserEvent.OnFolderLongClicked -> addFavorite(event.folder)
            is BrowserEvent.OnTrackClicked -> playTrack(event.track)
        }
    }

    fun onPathChanged(newPath: String?) {
        if (newPath != null) {
            navigateToPath(newPath)
        }
    }

    fun onUserMessageShown() {
        _internalUiState.update { it.copy(userMessage = null) }
    }

    // --- プライベートなサブルーチン ---
    /**
     * ViewModelの初期状態を非同期で設定する
     */
    private fun initializeState() {
        viewModelScope.launch {
            val initialActiveDataSourceSetting = settingsRepository.activeDataSource.first()
            val smbConnection = if (initialActiveDataSourceSetting is ActiveDataSource.Smb) {
                nasCredentialsRepository.getConnectionById(initialActiveDataSourceSetting.connectionId)
            } else null

            val dataSource = smbConnection?.let { DataSourceItem.Smb(it) } ?: DataSourceItem.Local
            updatePathsForNewDataSource(dataSource) // 初期パスを設定
        }
    }

    /**
     * 指定されたデータソースを選択し、関連する状態を更新する
     */
    private fun selectDataSource(dataSource: DataSourceItem) {
        viewModelScope.launch {
            Log.d("BrowserViewModel", "selectDataSource: $dataSource")

            // 永続化する設定を更新
            val newActiveDataSource = when (dataSource) {
                is DataSourceItem.Local -> ActiveDataSource.Local
                is DataSourceItem.Smb -> ActiveDataSource.Smb(dataSource.connection.id)
            }
            settingsRepository.updateActiveDataSource(newActiveDataSource)

            // パスを更新し、アイテムを再読み込み
            updatePathsForNewDataSource(dataSource)
        }
    }

    /**
     * 新しいデータソースに基づいてrootPathとcurrentPathを更新し、アイテムの読み込みを開始する
     */
    private fun updatePathsForNewDataSource(dataSource: DataSourceItem) {
        viewModelScope.launch {
            val localDefaultPath = settingsRepository.localDefaultPath.first()

            val newRootPath: String
            val newCurrentPath: String

            if (dataSource is DataSourceItem.Smb) {
                newRootPath = "smb://${dataSource.connection.hostname}/${dataSource.connection.path}".trimEnd('/') + "/"
                newCurrentPath = "" // SMBのルート
            } else { // Local
                newRootPath = localDefaultPath
                newCurrentPath = localDefaultPath
            }

            _internalUiState.update {
                it.copy(
                    rootPath = newRootPath,
                    currentPath = newCurrentPath,
                    drawerState = it.drawerState.copy(currentDataSource = dataSource),
                    items = emptyList(),
                    isLoading = true,
                    currentIndex = -1
                )
            }
            loadItemsForCurrentPath()
        }
    }

    /**
     * 指定されたパスに移動し、アイテムの読み込みを開始する
     */
    private fun navigateToPath(path: String) {
        Log.d("BrowserViewModel", "navigateToPath: $path")
        _internalUiState.update { it.copy(currentPath = path, isLoading = true) }
        loadItemsForCurrentPath()
    }

    /**
     * 現在のパスにあるアイテムを非同期で読み込む
     */
    fun loadItemsForCurrentPath() {
        loadItemsJob?.cancel()
        // アイテムリストをクリアし、isLoadingをtrueに設定してロード中であることを示す
        _internalUiState.update { it.copy(isLoading = true, items = emptyList()) }
        loadItemsJob = viewModelScope.launch {

            val pathToLoad = _internalUiState.value.currentPath
            Log.d("BrowserViewModel", "loadItemsForCurrentPath: Loading items for '$pathToLoad'")

            try {
                mediaRepository.getItemsIn(pathToLoad)
                    .onStart {
                        Log.d("BrowserViewModel", "Flow collection started for path: $pathToLoad")
                    }
                    .onCompletion { cause ->
                        // Flowの完了時またはエラー時にisLoadingをfalseに設定
                        _internalUiState.update { it.copy(isLoading = false) }
                        if (cause != null) {
                            Log.d("BrowserViewModel", "Flow collection completed with error for path: $pathToLoad", cause)
                            throw cause // エラーを再スローしてcatchブロックで処理
                        } else {
                            Log.d("BrowserViewModel", "Flow collection completed successfully for path: $pathToLoad")
                        }
                    }
                    .collect { newItem ->
                        // Flowから新しいアイテムが届くたびにUI状態を更新
                        _internalUiState.update { currentState ->
                            val updatedList = currentState.items + newItem
                            currentState.copy(items = updatedList) // isLoadingはonCompletionで管理
                        }
                    }
            } catch (e: Exception) {
                Log.e("BrowserViewModel", "アイテム読み込みエラー: path='$pathToLoad'", e)
                val errorMessage = when (e) {
                    is SmbAuthenticationException -> "NASの認証に失敗しました。"
                    is SmbHostNotFoundException -> "NASサーバーが見つかりません。"
                    is SmbShareNotFoundException -> "共有フォルダまたはパスが見つかりません。"
                    is SmbPermissionException -> "NASへのアクセス権がありません。" // SMB固有の権限エラー
                    is SmbNetworkException -> "NASとの通信でネットワークエラーが発生しました。"
                    is SmbAccessException -> "NASへのアクセス中にエラーが発生しました: ${e.localizedMessage}" // その他のSMB関連エラー
                    is SecurityException -> "メディアへのアクセス許可がありません。アプリの権限設定を確認してください。" // ローカルメディアアクセス時の権限エラー
                    is IllegalStateException -> "処理中に予期しない状態になりました: ${e.localizedMessage}" // 内部的な状態エラー
                    // 必要に応じて他の具体的な例外をここに追加できます (例: java.io.IOExceptionなど)
                    else -> "ファイルの読み込み中にエラーが発生しました: ${e.localizedMessage}" // その他の一般的なエラー
                }
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = errorMessage)
                }
            }
        }
    }

    /**
     * 指定されたトラックの再生をリクエストする
     */
    private fun playTrack(track: TrackItem) {
        viewModelScope.launch {
            val currentState = _internalUiState.value
            val activeDataSource = when (val ds = currentState.drawerState.currentDataSource) {
                is DataSourceItem.Local -> ActiveDataSource.Local
                is DataSourceItem.Smb -> ActiveDataSource.Smb(ds.connection.id)
                null -> { // currentDataSourceがnullの場合のフォールバック (基本的には発生しないはず)
                    Log.w("BrowserViewModel", "playTrack: currentDataSource is null, falling back to persisted ActiveDataSource.")
                    settingsRepository.activeDataSource.first() // 永続化された設定から取得試行
                }
            }
            playbackRequestRepository.requestPlayback(
                path = currentState.currentPath,
                itemList = currentState.items,
                currentItem = track,
                dataSource = activeDataSource
            )
        }
    }

    /**
     * お気に入りセクションの開閉状態を切り替える
     */
    private fun toggleFavoriteExpansion() {
        _internalUiState.update {
            it.copy(drawerState = it.drawerState.copy(isFavoritesExpanded = !it.drawerState.isFavoritesExpanded))
        }
    }

    /**
     * フォルダをお気に入りに追加する
     */
    private fun addFavorite(folder: FolderItem) {
        viewModelScope.launch {
            settingsRepository.addLocalFavoritePath(folder.path)
            showUserMessage("'${folder.title}'をお気に入りに追加しました")
        }
    }

    /**
     * お気に入りからパスを削除する
     */
    private fun removeFavorite(path: String) {
        viewModelScope.launch {
            settingsRepository.removeLocalFavoritePath(path)
            val folderName = path.substringAfterLast('/')
            showUserMessage("'$folderName'をお気に入りから削除しました")
        }
    }

    /**
     * Snackbarなどで表示するユーザーメッセージを設定する
     */
    private fun showUserMessage(message: String) {
        _internalUiState.update { it.copy(userMessage = message) }
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
