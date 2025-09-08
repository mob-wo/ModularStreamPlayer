package com.example.feature_browser


import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.data_repository.ViewMode
import com.example.core_model.FolderItem
import com.example.core_model.TrackItem
import com.example.data_repository.LayoutMode
import com.example.feature_browser.browser.BrowserEvent
import com.example.feature_browser.browser.BrowserScreen
import com.example.feature_browser.browser.BrowserScreenContent
import com.example.feature_browser.browser.BrowserTopAppBar
import com.example.feature_browser.browser.BrowserUiState
import com.example.feature_browser.browser.BrowserViewModel
import com.example.feature_browser.browser.previewItems
import com.example.feature_browser.player.PlayerScreen
import com.example.feature_browser.player.PlayerScreenContent

import com.example.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * アプリの主要なUI骨格を定義する画面。
 * ナビゲーションドロワー、トップバー、メインコンテンツ（BrowserScreen）、
 * およびミニプレーヤーのレイアウトを管理する。
 *
 * @param navController アプリ全体のナビゲーションを管理するNavController。
 * @param viewModel この画面と関連するUIの状態を管理するViewModel。
 */
@OptIn(UnstableApi::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    // ViewModelからUI状態を収集
    Log.d("BrowserScreen", "ViewModel instance obtained: ${viewModel.hashCode()}")

    Log.d("BrowserScreen", "Before collecting uiState from ViewModel: ${viewModel.hashCode()}")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Log.d("BrowserScreen", "After collecting uiState")

    // ドロワーの状態と、それを開閉するためのコルーチンスコープ
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ViewModelからのユーザーメッセージを監視し、Snackbarを表示
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onUserMessageShown() // メッセージ表示後にクリア処理を呼び出す
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                state = uiState.drawerState,
                onEvent = { event ->
                    // ドロワー内でのイベントをハンドリング
                    when (event) {
                        is DrawerEvent.OnSettingsClicked -> {
                            // 設定画面へ遷移
                            navController.navigate(ScreenTitle.Settings.name)
                        }
                        // その他のイベントはViewModelに委譲
                        else -> viewModel.onDrawerEvent(event)
                    }
                    // イベント処理後、ドロワーを閉じる
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                BrowserTopAppBar(
                    title = uiState.currentPath.substringAfterLast('/')
                        .ifEmpty { "Modular Stream Player" },
                    onNavigationIconClick = {
                        // トップバーのメニューアイコンクリックでドロワーを開く
                        scope.launch { drawerState.open() }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                BrowserScreen(
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    loadItems = { viewModel.loadItemsForCurrentPath() },
                    onItemClick = { item ->
                        when (item) {
                            is TrackItem -> {
                                viewModel.onBrowserEvent(BrowserEvent.OnTrackClicked(item))
                                if (uiState.drawerState.viewMode == ViewMode.SINGLE) {
                                    navController.navigate(ScreenTitle.Player.name)
                                }
                            }
                            is FolderItem -> viewModel.onBrowserEvent(
                                BrowserEvent.OnFolderClicked(
                                    item
                                )
                            )
                        }
                    },
                    onItemLongClick = { item ->
                        if (item is FolderItem) {
                            viewModel.onBrowserEvent(BrowserEvent.OnFolderLongClicked(item))
                        }
                    }
                )

                // 2画面モードの時のみミニプレーヤーを表示
                if (uiState.drawerState.viewMode == ViewMode.DUAL) {
                    // TODO: MiniPlayerに必要な状態をuiStateから取得して渡す
                    HorizontalDivider()
                    PlayerScreen(
                        modifier = Modifier.weight(1f),
                        viewMode = ViewMode.DUAL,
                        paddingValues = PaddingValues(bottom = 0.dp)
                    )
                }
            }
        }
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun MainScreenPreview_GRID_MEDIUM() {
    // PreviewではHiltViewModelが使えないため、モックやダミーのViewModelを用意する必要がある。
    // ここでは単純化のため、UIの骨格のみを描画する。
    val navController = rememberNavController()

    AppTheme {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // ダミーのDrawerUiStateでプレビュー
                AppDrawer(state = DrawerUiState(), onEvent = {})
            }
        ) {
            Scaffold(
                topBar = {
                    BrowserTopAppBar(title = "Preview", onNavigationIconClick = {})
                }
            ) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    // ダミーデータでBrowserScreenをプレビュー
                    BrowserScreenContent(
                        modifier = Modifier.weight(1f),
                        uiState = BrowserUiState(
                            currentPath = "/Music",
                            items = previewItems,
                            isLoading = false,
                            drawerState = DrawerUiState(layoutMode = LayoutMode.GRID_MEDIUM)
                        ),
                        onItemClick = { },
                        onItemLongClick = { }
                    )
                    HorizontalDivider()
                    PlayerScreenContent(
                        modifier = Modifier.weight(1f),
                        viewMode = ViewMode.DUAL,
                        paddingValues = PaddingValues(bottom = 0.dp),
                        uiState = com.example.feature_browser.player.previewItem
                    ) // 2画面モードを想定したプレビュー
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview_LIST() {
    // PreviewではHiltViewModelが使えないため、モックやダミーのViewModelを用意する必要がある。
    // ここでは単純化のため、UIの骨格のみを描画する。
    val navController = rememberNavController()

    AppTheme {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // ダミーのDrawerUiStateでプレビュー
                AppDrawer(state = DrawerUiState(), onEvent = {})
            }
        ) {
            Scaffold(
                topBar = {
                    BrowserTopAppBar(title = "Preview", onNavigationIconClick = {})
                }
            ) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    // ダミーデータでBrowserScreenをプレビュー
                    BrowserScreenContent(
                        modifier = Modifier.weight(1f),
                        uiState = BrowserUiState(
                            currentPath = "/Music",
                            items = previewItems,
                            isLoading = false,
                            drawerState = DrawerUiState(layoutMode = LayoutMode.LIST)
                        ),
                        onItemClick = { },
                        onItemLongClick = { }
                    )
                    HorizontalDivider()
                    PlayerScreenContent(
                        modifier = Modifier.weight(1f),
                        viewMode = ViewMode.DUAL,
                        paddingValues = PaddingValues(bottom = 0.dp),
                        uiState = com.example.feature_browser.player.previewItem
                    ) // 2画面モードを想定したプレビュー
                }
            }
        }
    }
}
