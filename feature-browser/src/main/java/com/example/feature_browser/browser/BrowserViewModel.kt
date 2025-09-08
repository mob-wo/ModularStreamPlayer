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
import com.example.data_repository.MediaRepository
import com.example.data_repository.NasCredentialsRepository
import com.example.data_repository.PlaybackRequestRepository
import com.example.data_repository.PlayerStateRepository
import com.example.data_repository.SettingsRepository
import com.example.data_repository.ViewMode
import com.example.feature_browser.DataSourceItem
import com.example.feature_browser.DrawerEvent
import com.example.feature_browser.DrawerUiState
// --- ここから追加 ---
import com.example.data_smb.SmbAccessException
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

        // 永続化された設定に基づいて_internalUiStateを初期化します。
        viewModelScope.launch {
            val initialDefaultPath = settingsRepository.localDefaultPath.first()
            val initialActiveDataSourceSetting = settingsRepository.activeDataSource.first()
            Log.d("BrowserViewModel", "初期設定読み込み: initialDefaultPath=$initialDefaultPath, initialActiveDataSourceSetting=$initialActiveDataSourceSetting")

            val initialRootPath: String
            val initialCurrentPath: String
            val initialCurrentDataSource: DataSourceItem // 新しく追加

            if (initialActiveDataSourceSetting is ActiveDataSource.Smb) {
                val smbConnection = nasCredentialsRepository.getConnectionById(initialActiveDataSourceSetting.connectionId)
                initialCurrentDataSource = smbConnection?.let { DataSourceItem.Smb(it) } ?: DataSourceItem.Local // 見つからなければLocalにフォールバック
                initialRootPath = smbConnection?.let { "smb://${it.hostname}/${it.path}".trimEnd('/') + "/" } ?: "SMB" // フォールバック
                initialCurrentPath = "" // SMBのルート
                Log.d("BrowserViewModel", "初期データソースはSMBです。rootPath=$initialRootPath, currentPath=$initialCurrentPath")
            } else { // ローカルの場合
                initialCurrentDataSource = DataSourceItem.Local
                initialRootPath = initialDefaultPath
                initialCurrentPath = initialDefaultPath
                Log.d("BrowserViewModel", "初期データソースはローカルです。rootPath=$initialRootPath, currentPath=$initialCurrentPath")
            }

            _internalUiState.update { currentState ->
                currentState.copy(
                    rootPath = initialRootPath,
                    currentPath = initialCurrentPath,
                    isPathInitialized = true, // 初期化完了としてマーク
                    drawerState = currentState.drawerState.copy( // ドロワーの状態もここで初期化
                        currentDataSource = initialCurrentDataSource,
                        // availableDataSourcesはまだ完全ではないが、combineで構築されるためここでは最低限でOK
                        availableDataSources = if (initialCurrentDataSource is DataSourceItem.Local) listOf(DataSourceItem.Local) else listOf(DataSourceItem.Local, initialCurrentDataSource) // 仮の初期リスト
                    )
                )
            }
            Log.d("BrowserViewModel", "初期_internalUiState更新後: ${_internalUiState.value}")
        }

        Log.d("BrowserViewModel", "ViewModelインスタンス: ${this.hashCode()} - initブロック: uiState combine定義前")

        // combineブロックは、_internalUiStateと他のFlowsから最終的なuiStateを生成するのみにする
        uiState = combine(
            _internalUiState, // これがViewModelの内部状態の真実の源
            settingsRepository.localDefaultPath,
            settingsRepository.layoutMode,
            settingsRepository.viewMode,
            settingsRepository.localFavoritePaths,
            nasCredentialsRepository.connections,
            settingsRepository.activeDataSource // データソース変更のトリガーだが、_internalUiStateが先に更新されるべき
        ) { values ->
            Log.d("BrowserViewModel", "COMBINEブロック実行!: ${this.hashCode()}")

            val internalState = values[0] as BrowserUiState // _internalUiStateの最新値を取得
            val localDefaultPath = values[1] as String
            val layoutMode = values[2] as LayoutMode
            val viewMode = values[3] as ViewMode
            val favoritePaths = values[4] as Set<String>
            val nasConnections = values[5] as List<NasConnection>
            val activeDataSourceSetting = values[6] as ActiveDataSource // これは、_internalUiStateのcurrentDataSourceが更新された後の確認用として機能する

            // availableDataSourcesはここで常に最新のリストを構築
            val availableDataSources = mutableListOf<DataSourceItem>(DataSourceItem.Local)
            availableDataSources.addAll(nasConnections.map { DataSourceItem.Smb(it) })

            // currentDataSourceは、すでに_internalUiState.drawerState.currentDataSourceが最新の値を持っているはず
            // combineはそれをDrawerUiStateに再度組み込むだけ
            val currentDataSource = internalState.drawerState.currentDataSource

            Log.d("BrowserViewModel", "Combine: activeDataSourceSetting=$activeDataSourceSetting, internalState.drawerState.currentDataSource=$currentDataSource")

            val newRootPath: String = if (currentDataSource is DataSourceItem.Smb) {
                currentDataSource.connection.let { "smb://${it.hostname}/${it.path}".trimEnd('/') + "/" }
            } else { // ローカルの場合
                localDefaultPath
            }
            Log.d("BrowserViewModel", "Combine: newRootPath決定: $newRootPath")

            // --- currentPath決定ロジック修正 ---
            // _internalUiStateのdrawerState.currentDataSourceと、前回 combine が発行した状態 (internalState) の
            // drawerState.currentDataSource を比較して変更を検出
            val previousEffectiveDataSourceId = internalState.drawerState.currentDataSource?.id // internalStateは_internalUiStateの過去の値
            val newEffectiveDataSourceId = currentDataSource.id // currentDataSourceは_internalUiStateの最新値から取得

            val dataSourceActuallyChanged = previousEffectiveDataSourceId != newEffectiveDataSourceId

            // ... (currentPath決定ロジックは変更なし。dataSourceActuallyChangedの定義が重要) ...
            var newCurrentPath: String
            if (!internalState.isPathInitialized) {
                newCurrentPath = if (currentDataSource is DataSourceItem.Smb) "" else localDefaultPath
            } else {
                if (dataSourceActuallyChanged) {
                    newCurrentPath = if (currentDataSource is DataSourceItem.Smb) "" else localDefaultPath
                } else {
                    newCurrentPath = internalState.currentPath
                }
            }
            if (currentDataSource is DataSourceItem.Local && newCurrentPath.isBlank() && localDefaultPath.isNotBlank()) {
                newCurrentPath = localDefaultPath
            }
            // --- currentPath決定ロジックここまで ---

            Log.d("BrowserViewModel", "Combine (before return): currentDataSource.id = ${currentDataSource.id}")
            availableDataSources.forEach { source ->
                Log.d("BrowserViewModel", "Combine (before return): availableDataSource.id = ${source.id}, isSelected = ${currentDataSource.id == source.id}")
            }
            Log.d("BrowserViewModel", "Combine (before return): internalState.drawerState.currentDataSource = ${internalState.drawerState.currentDataSource}")
            Log.d("BrowserViewModel", "Combine (before return): internalState.isLoading = ${internalState.isLoading}")

            // combineブロックから最終的に返却する新しいUI状態
            // ここでは_internalUiStateからの最新のデータを中心に、他の設定を組み合わせる
            internalState.copy( // _internalUiStateの最新のコピーをベースにする
                drawerState = internalState.drawerState.copy(
                    layoutMode = layoutMode,
                    viewMode = viewMode,
                    favoritePaths = favoritePaths.toList().sorted(),
                    availableDataSources = availableDataSources,
                    currentDataSource = currentDataSource // _internalUiStateの最新値を使用
                ),
                rootPath = newRootPath, // currentDataSourceに基づいて計算
                currentPath = newCurrentPath, // currentDataSourceとinternalState.currentPathに基づいて計算
                isPathInitialized = internalState.isPathInitialized,
                // アイテムリスト等のクリアは、_internalUiStateの更新時に行うのが望ましい。
                // combineはあくまで「派生」ロジックに集中。
                // ただし、データソース変更時にitemsとisLoadingをリセットするロジックはここで維持するのもアリ。
                // _internalUiState.updateでitems = emptyList(), isLoading = true にすれば、ここは単純に internalState.items, internalState.isLoading で良い
                // 今回は_internalUiState.updateで既にitems, isLoadingを更新するので、ここではinternalStateの値をそのまま使う
                items = internalState.items,
                isLoading = internalState.isLoading,
                currentIndex = internalState.currentIndex,
                userMessage = internalState.userMessage
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _internalUiState.value // 更新された可能性のある_internalUiStateを使用
        )
        Log.d("BrowserViewModel", "ViewModelインスタンス: ${this.hashCode()} - initブロック終了 (after stateIn)")
    }

    fun loadItemsForCurrentPath() {
        loadItemsJob?.cancel()
        Log.d(
            "BrowserViewModel",
            "loadItemsForCurrentPath: 開始。現在のuiState.value.currentPath = ${uiState.value.currentPath}, rootPath = ${uiState.value.rootPath}"
        )
        // 読み込み試行前にisPathInitializedがtrueであることを確認します。
        // isPathInitializedがtrueになれば、combineロジックがパスを正しく保証するはずです。
        if (!uiState.value.isPathInitialized) {
            Log.w("BrowserViewModel", "loadItemsForCurrentPath: パス初期化前に呼び出されたため中止します。")
            _internalUiState.update { it.copy(isLoading = false, userMessage = "初期化中です...") } // ユーザーに通知
            return
        }

        _internalUiState.update { 
            Log.d("BrowserViewModel", "loadItemsForCurrentPath: isLoading = true に設定")
            it.copy(isLoading = true) 
        }

        loadItemsJob = viewModelScope.launch { // Jobを再代入
            Log.d("BrowserViewModel", "loadItemsForCurrentPath: viewModelScope.launch開始")
            try {
                val currentPathToLoad = uiState.value.currentPath // 確定したパスを使用
                Log.d("BrowserViewModel", "loadItemsForCurrentPath: mediaRepository.getItemsIn('$currentPathToLoad') 呼び出し開始")
                val items = mediaRepository.getItemsIn(currentPathToLoad)
                Log.d("BrowserViewModel", "loadItemsForCurrentPath: mediaRepository.getItemsIn完了。アイテム数: ${items.size}")

                try {
                    _internalUiState.update {
                        Log.d("BrowserViewModel", "loadItemsForCurrentPath: _internalUiState.update開始。isLoading = false, items.size = ${items.size}")
                        it.copy(
                            items = items,
                            isLoading = false,
                            userMessage = null
                        )
                    }
                    Log.d("BrowserViewModel", "loadItemsForCurrentPath: _internalUiState.update完了。")
                } catch (e: Exception) {
                    Log.e("BrowserViewModel", "loadItemsForCurrentPath: _internalUiState.update中にエラー発生", e)
                    // isLoading=true のままになってしまう可能性を避けるため、エラー時もisLoadingをfalseにする
                     _internalUiState.update {
                        it.copy(
                            items = emptyList(), // エラーなのでアイテムは空にする
                            isLoading = false,
                            userMessage = "UI状態の更新に失敗しました: ${e.localizedMessage}"
                        )
                    }
                }
            }
            // --- ここから既存の例外処理 ---
            catch (e: SmbAuthenticationException) {
                Log.e("BrowserViewModel", "アイテム読み込みエラー: SMB認証失敗", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = e.message ?: "NASの認証に失敗しました。")
                }
            } catch (e: SmbHostNotFoundException) {
                Log.e("BrowserViewModel", "アイテム読み込みエラー: SMBホスト未検出", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = e.message ?: "NASサーバーが見つかりません。")
                }
            } catch (e: SmbShareNotFoundException) {
                Log.e("BrowserViewModel", "アイテム読み込みエラー: SMB共有未検出", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = e.message ?: "共有フォルダまたはパスが見つかりません。")
                }
            } catch (e: SmbPermissionException) {
                Log.e("BrowserViewModel", "アイテム読み込みエラー: SMB権限拒否", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = e.message ?: "アクセス権がありません。")
                }
            } catch (e: SmbNetworkException) {
                Log.e("BrowserViewModel", "アイテム読み込みエラー: SMBネットワークエラー", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = e.message ?: "NASとの通信でネットワークエラーが発生しました。")
                }
            } catch (e: SmbAccessException) { // その他のSMB関連エラー
                Log.e("BrowserViewModel", "アイテム読み込みエラー: SMBアクセス例外", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = e.message ?: "NASアクセス中にエラーが発生しました。")
                }
            }
            // --- ここまで既存の例外処理 ---
            catch (e: java.net.UnknownHostException) { // ローカルファイルアクセス時など、SMB以外でのホスト不明エラー
                Log.e("BrowserViewModel", "アイテム読み込みエラー: ホスト未検出 (非SMB)", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = "サーバーまたはホストが見つかりません。")
                }
            } catch (e: java.io.IOException) { // SMB以外のI/Oエラー
                Log.e("BrowserViewModel", "アイテム読み込みエラー: IOException (非SMB)", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = "ファイルの読み込みに失敗しました: ${e.localizedMessage}")
                }
            } catch (e: Exception) { // その他の予期せぬエラー (UI更新中のエラー以外)
                Log.e("BrowserViewModel", "アイテム読み込みエラー: 不明な例外 (UI更新以外)", e)
                _internalUiState.update {
                    it.copy(items = emptyList(), isLoading = false, userMessage = "不明なエラーが発生しました: ${e.message}")
                }
            } finally {
                Log.d("BrowserViewModel", "loadItemsForCurrentPath: viewModelScope.launch終了 (finallyブロック)")
                // 万が一isLoadingがtrueのままなら、ここでfalseにするか検討 (ただし、上記catchでほぼカバーされるはず)
                if (_internalUiState.value.isLoading) {
                     Log.w("BrowserViewModel", "loadItemsForCurrentPath: finallyブロックでisLoadingがまだtrueでした。強制的にfalseにします。")
                    _internalUiState.update { it.copy(isLoading = false, userMessage = it.userMessage ?: "読み込みが完了しましたが、問題が発生した可能性があります。") }
                }
            }
        }
    }

    fun loadItems(path: String) {
        Log.d("BrowserViewModel", "loadItems呼び出し path: $path. 現在のuiState.isPathInitialized=${uiState.value.isPathInitialized}")
        // パスの初期化はメインのcombineロジックで処理されるべきです。
        // この関数は、現在のデータソース内の*新しい*パスに移動するためのものです。
        if (!uiState.value.isPathInitialized) {
            Log.w("BrowserViewModel", "loadItems: パス初期化未完了。パス更新が遅延または不正確になる可能性があります。")
            // メッセージ表示または待機を検討。現時点ではログ記録して続行。
        }
         _internalUiState.update {
             // currentPathのみ更新。rootPathは現在のデータソースに対して安定しているべき。
             // isLoadingはloadItemsForCurrentPathによって設定されます。
             it.copy(currentPath = path)
         }
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
                is DrawerEvent.OnDataSourceSelected -> { 
                    Log.d("BrowserViewModel", "OnDataSourceSelected: ${event.source}")
                    val newActiveDataSource = when (event.source) {
                        is DataSourceItem.Local -> ActiveDataSource.Local
                        is DataSourceItem.Smb -> ActiveDataSource.Smb(event.source.connection.id)
                    }
                    settingsRepository.updateActiveDataSource(newActiveDataSource)
                    // ★ ViewModelの内部状態 _internalUiState を直接更新 ★
                    _internalUiState.update { currentState ->
                        currentState.copy(
                            drawerState = currentState.drawerState.copy(
                                currentDataSource = event.source // 選択されたデータソースで更新
                            ),
                            // データソースが変更されたので、アイテムリストをクリアし、ロード中状態にする
                            items = emptyList(),
                            isLoading = true,
                            currentIndex = -1,
                            userMessage = null // メッセージをクリア
                        )
                    }
                    // _internalUiStateの更新がcombineをトリガーし、uiStateが更新される。
                    // その後、loadItemsForCurrentPath()を呼び出して新しいデータソース/パスでアイテムをロードする。
                    loadItemsForCurrentPath()
                }

                is DrawerEvent.OnFavoriteLongClicked -> {
                    settingsRepository.removeLocalFavoritePath(event.path)
                    showUserMessage("'${event.path.substringAfterLast('/')}'をお気に入りから削除しました")
                }

                is DrawerEvent.OnFavoriteClicked -> loadItems(event.path) // このパスに移動
                is DrawerEvent.OnRefreshClicked -> loadItemsForCurrentPath()
                DrawerEvent.OnFavoritesHeaderClicked -> {
                    // _internalUiState.drawerState の isFavoritesExpanded を直接更新
                    _internalUiState.update { currentState ->
                        currentState.copy(
                            drawerState = currentState.drawerState.copy(
                                isFavoritesExpanded = !currentState.drawerState.isFavoritesExpanded
                            )
                        )
                    }
                }
                else -> { /* 未実装 */
                }
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
                is BrowserEvent.OnTrackClicked -> {
                    // 再生リクエストにはuiState.valueを使用して決定的な現在の状態を取得
                    val currentUiState = uiState.value
                    val activeDataSourceForPlayback = when (currentUiState.drawerState.currentDataSource) {
                        is DataSourceItem.Local -> ActiveDataSource.Local
                        is DataSourceItem.Smb -> ActiveDataSource.Smb((currentUiState.drawerState.currentDataSource as DataSourceItem.Smb).connection.id)
                        null -> settingsRepository.activeDataSource.first() // フォールバック、ドロワーがカレントを持つべきだが
                    }
                    playbackRequestRepository.requestPlayback(
                        path = currentUiState.currentPath,
                        itemList = currentUiState.items,
                        currentItem = event.track,
                        dataSource = activeDataSourceForPlayback
                    )
                }

                is BrowserEvent.OnFolderLongClicked -> {
                    settingsRepository.addLocalFavoritePath(event.folder.path)
                    showUserMessage("'${event.folder.path}'をお気に入りに追加しました")
                }
                else -> { /* 未実装 */
                }
            }
        }
    }

    /**
     * Snackbarなどで表示するユーザーメッセージが処理されたことを通知する
     */
    fun onUserMessageShown() {
        _internalUiState.update { it.copy(userMessage = null) }
    }

    /**
     * Snackbarなどで表示するユーザーメッセージを設定する
     */
    private fun showUserMessage(message: String) {
        _internalUiState.update { it.copy(userMessage = message) }
    }

    fun onPathChanged(newPath: String?) {
        Log.d("BrowserViewModel", "onPathChanged受信 newPath: $newPath")
        if (newPath != null) {
            // これは通常ナビゲーション引数から呼び出されます。
            // _internalUiStateのcurrentPathを更新。combineがそれを拾います。
            // isLoadingはloadItemsForCurrentPathによって処理されます。
            _internalUiState.update { it.copy(currentPath = newPath) }
            loadItemsForCurrentPath()
        } else {
            Log.w("BrowserViewModel", "onPathChanged受信 null path.")
            // 動作を決定: 現在のデータソースのルートに行く？エラー表示？
            // 現時点では、現在のルートに基づいて読み込みを試みます。
             val currentRoot = uiState.value.rootPath // これは既にデータソースに応じたルートのはず
             _internalUiState.update { it.copy(currentPath = currentRoot) }
             loadItemsForCurrentPath()
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
