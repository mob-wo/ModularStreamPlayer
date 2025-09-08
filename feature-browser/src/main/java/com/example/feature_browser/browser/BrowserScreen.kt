package com.example.feature_browser.browser

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    uiState: BrowserUiState,
    loadItems: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    ) {
    // Android 13以上はREAD_MEDIA_AUDIO、それ未満はREAD_EXTERNAL_STORAGE
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    /*
    LaunchedEffect(uiState.currentPath, permissionState.status) {
        if (permissionState.status.isGranted) {
            Log.d("BrowserScreen", "LaunchedEffect triggered: Path initialized and permission granted. Path: ${uiState.currentPath}")
            loadItems()
        } else { // Permission not granted
            Log.d("BrowserScreen", "LaunchedEffect skipped: Permission not granted.")
            // viewModel.clearItemsOrSetPermissionError()
        }
    }*/

    if (permissionState.status.isGranted){
        BrowserScreenContent(
            modifier = modifier,
            uiState = uiState,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick
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

    ) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        if (uiState.isLoading) {
            // ローディング中の表示
            CircularProgressIndicator()
        } else if (uiState.items.isEmpty()) {
            // アイテムが一つもない場合の表示
            Text(
                text = "このフォルダは空です",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // レイアウトモードに応じてリストまたはグリッドを表示
            MediaList(
                fileItems = uiState.items,
                layoutMode = uiState.drawerState.layoutMode,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick
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
    modifier: Modifier = Modifier
) {
    if (layoutMode == LayoutMode.LIST) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = fileItems,
                key = { it.uri } // パフォーマンス向上のため一意なキーを指定
            ) { item ->
                _root_ide_package_.com.example.feature_browser.MediaListItem(
                    item = item,
                    layoutMode = LayoutMode.LIST,
                    onClick = onItemClick,
                    onLongClick = onItemLongClick
                )
            }
        }
    } else {
        val columns = if (layoutMode == LayoutMode.GRID_MEDIUM) 2 else 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = fileItems,
                key = { it.uri } // パフォーマンス向上のため一意なキーを指定
            ) { item ->
                _root_ide_package_.com.example.feature_browser.MediaListItem(
                    item = item,
                    layoutMode = layoutMode,
                    onClick = onItemClick,
                    onLongClick = onItemLongClick
                )
            }
        }
    }
}




// --- Preview ---

val previewItems: List<FileItem> = listOf(
    FolderItem("Rock Classics", "/Music/Rock", "uri_folder_1"),
    TrackItem("Bohemian Rhapsody", "/Music/Queen/song1.mp3", "uri_track_1", "Queen", null,"A Night at the Opera", null, 482000),
    TrackItem("Stairway to Heaven", "/Music/LedZep/song2.mp3", "uri_track_2", "Led Zeppelin", null,"Led Zeppelin IV", null, 482000),
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
            onItemLongClick = { }
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
            onItemLongClick = { }
        )
    }
}

@Preview(name = "Loading State", showBackground = true)
@Composable
fun BrowserScreenLoadingPreview() {
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
            onItemLongClick = { }
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
                items = previewItems,
                isLoading = false,
                drawerState = _root_ide_package_.com.example.feature_browser.DrawerUiState(
                    layoutMode = LayoutMode.GRID_MEDIUM
                )
            ),
            onItemClick = { },
            onItemLongClick = { }
        )
    }
}
