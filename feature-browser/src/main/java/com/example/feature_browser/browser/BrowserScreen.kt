package com.example.feature_browser.browser

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState // 追加
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState // 追加
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState // 追加
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState // 追加
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow // 追加
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.core_model.FolderItem
import com.example.core_model.FileItem
import com.example.core_model.TrackItem
import com.example.data_repository.LayoutMode
import com.example.theme.AppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    uiState: BrowserUiState,
    // loadItems: () -> Unit, // ViewModel内で初期読み込みを行うため削除
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    loadMetadataForVisibleItems: (List<FileItem>) -> Unit // ViewModelから渡される関数
) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    // 初期アイテム読み込みのトリガーはViewModel内で行う
    // LaunchedEffect(uiState.currentPath, permissionState.status.isGranted) {
    //     if (permissionState.status.isGranted) {
    //         Log.d("BrowserScreen", "LaunchedEffect: Initial items load for ${uiState.currentPath}")
    //         // loadItems() // 削除
    //     }
    // }

    if (permissionState.status.isGranted) {
        BrowserScreenContent(
            modifier = modifier,
            uiState = uiState,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            loadMetadataForVisibleItems = loadMetadataForVisibleItems // ViewModelの関数を渡す
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ストレージへのアクセス権限が必要です",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BrowserScreenContent(
    modifier: Modifier = Modifier,
    uiState: BrowserUiState,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    loadMetadataForVisibleItems: (List<FileItem>) -> Unit // ViewModelの関数を受け取る
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isLoading && uiState.items.isEmpty()) { // 初期ロード中のみプログレス表示
            CircularProgressIndicator()
        } else if (uiState.items.isEmpty()) {
            Text(
                text = "このフォルダは空です",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            MediaList(
                fileItems = uiState.items,
                layoutMode = uiState.drawerState.layoutMode,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                loadMetadataForVisibleItems = loadMetadataForVisibleItems // ViewModelの関数を渡す
            )
        }
    }
}


@Composable
private fun MediaList(
    fileItems: List<FileItem>,
    layoutMode: LayoutMode,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    loadMetadataForVisibleItems: (List<FileItem>) -> Unit, // ViewModelの関数を受け取る
    modifier: Modifier = Modifier
) {
    if (fileItems.isEmpty()) return // アイテムがない場合は何も表示しない

    if (layoutMode == LayoutMode.LIST) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState, // 状態を渡す
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = fileItems,
                key = { it.uri }
            ) { item ->
                _root_ide_package_.com.example.feature_browser.MediaListItem(
                    item = item,
                    layoutMode = LayoutMode.LIST,
                    onClick = onItemClick,
                    onLongClick = onItemLongClick
                )
            }
        }
        // 表示アイテムのメタデータ読み込み
        LoadVisibleItemsMetadata(listState = listState, fileItems = fileItems, loadMetadata = loadMetadataForVisibleItems)

    } else {
        val gridState = rememberLazyGridState()
        val columns = if (layoutMode == LayoutMode.GRID_MEDIUM) 2 else 3
        LazyVerticalGrid(
            state = gridState, // 状態を渡す
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = fileItems,
                key = { it.uri }
            ) { item ->
                _root_ide_package_.com.example.feature_browser.MediaListItem(
                    item = item,
                    layoutMode = layoutMode,
                    onClick = onItemClick,
                    onLongClick = onItemLongClick
                )
            }
        }
        // 表示アイテムのメタデータ読み込み
        LoadVisibleItemsMetadata(gridState = gridState, fileItems = fileItems, loadMetadata = loadMetadataForVisibleItems)
    }
}

// LazyList用
@Composable
private fun LoadVisibleItemsMetadata(
    listState: LazyListState,
    fileItems: List<FileItem>,
    loadMetadata: (List<FileItem>) -> Unit
) {
    LaunchedEffect(listState, fileItems) { // fileItemsもキーに含める
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .distinctUntilChanged()
            .mapNotNull { visibleItemsInfo ->
                if (visibleItemsInfo.isEmpty() || fileItems.isEmpty()) {
                    null
                } else {
                    visibleItemsInfo.mapNotNull { info ->
                        if (info.index < fileItems.size) fileItems[info.index] else null
                    }
                }
            }
            .filter { it.isNotEmpty() }
            .collect { visibleFileItems ->
                Log.d("BrowserScreen", "Visible items changed (List), loading metadata for ${visibleFileItems.size} items.")
                loadMetadata(visibleFileItems)
            }
    }
}

// LazyGrid用
@Composable
private fun LoadVisibleItemsMetadata(
    gridState: LazyGridState,
    fileItems: List<FileItem>,
    loadMetadata: (List<FileItem>) -> Unit
) {
    LaunchedEffect(gridState, fileItems) { // fileItemsもキーに含める
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
            .distinctUntilChanged()
            .mapNotNull { visibleItemsInfo ->
                if (visibleItemsInfo.isEmpty() || fileItems.isEmpty()) {
                    null
                } else {
                    visibleItemsInfo.mapNotNull { info ->
                        if (info.index < fileItems.size) fileItems[info.index] else null
                    }
                }
            }
            .filter { it.isNotEmpty() }
            .collect { visibleFileItems ->
                Log.d("BrowserScreen", "Visible items changed (Grid), loading metadata for ${visibleFileItems.size} items.")
                loadMetadata(visibleFileItems)
            }
    }
}


// --- Preview ---

val previewItems: List<FileItem> = listOf(
    FolderItem("Rock Classics", "/Music/Rock", "uri_folder_1"),
    TrackItem("Bohemian Rhapsody", "/Music/Queen/song1.mp3", "uri_track_1", "Queen", null, "A Night at the Opera", null, 482000),
    TrackItem("Stairway to Heaven", "/Music/LedZep/song2.mp3", "uri_track_2", "Led Zeppelin", null, "Led Zeppelin IV", null, 482000),
    FolderItem("J-Pop", "/Music/J-Pop", "uri_folder_2"),
)

@Preview(name = "List Mode", showBackground = true)
@Composable
fun BrowserScreenListPreview() {
    AppTheme {
        BrowserScreenContent(
            uiState = BrowserUiState(
                currentPath = "/Music",
                items = previewItems,
                isLoading = false,
                drawerState = _root_ide_package_.com.example.feature_browser.DrawerUiState()
            ),
            onItemClick = { },
            onItemLongClick = { },
            loadMetadataForVisibleItems = { Log.d("Preview", "Load metadata for items: $it") } // ダミー実装
        )
    }
}

@Preview(name = "Grid Medium Mode", showBackground = true)
@Composable
fun BrowserScreenGridMediumPreview() {
    AppTheme {
        BrowserScreenContent(
            uiState = BrowserUiState(
                currentPath = "/Music",
                items = previewItems,
                isLoading = false,
                drawerState = _root_ide_package_.com.example.feature_browser.DrawerUiState(
                    layoutMode = LayoutMode.GRID_MEDIUM
                )
            ),
            onItemClick = { },
            onItemLongClick = { },
            loadMetadataForVisibleItems = { Log.d("Preview", "Load metadata for items: $it") } // ダミー実装
        )
    }
}

@Preview(name = "Loading State", showBackground = true)
@Composable
fun BrowserScreenLoadingPreview() {
    AppTheme {
        BrowserScreenContent(
            uiState = BrowserUiState( // 初期ロード中のisLoadingをtrueにする
                currentPath = "/Music",
                items = emptyList(), // 初期ロード中はアイテムは空
                isLoading = true,
                drawerState = _root_ide_package_.com.example.feature_browser.DrawerUiState()
            ),
            onItemClick = { },
            onItemLongClick = { },
            loadMetadataForVisibleItems = { Log.d("Preview", "Load metadata for items: $it") } // ダミー実装
        )
    }
}

@Preview(name = "Empty State", showBackground = true)
@Composable
fun BrowserScreenEmptyPreview() {
    AppTheme {
        BrowserScreenContent(
            uiState = BrowserUiState(
                currentPath = "/Music",
                items = emptyList(), // 空の状態
                isLoading = false,
                drawerState = _root_ide_package_.com.example.feature_browser.DrawerUiState()
            ),
            onItemClick = { },
            onItemLongClick = { },
            loadMetadataForVisibleItems = { Log.d("Preview", "Load metadata for items: $it") } // ダミー実装
        )
    }
}
