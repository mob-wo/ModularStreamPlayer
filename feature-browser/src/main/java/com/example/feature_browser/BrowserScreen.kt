package com.example.feature_browser

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core_model.TrackItem
import com.example.core_model.MediaItem
import com.example.core_model.FolderItem
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    onFolderClick: (String) -> Unit,
    onTrackClick: (TrackItem) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Android 13以上はREAD_MEDIA_AUDIO、それ未満はREAD_EXTERNAL_STORAGE
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

//    LaunchedEffect(Unit) {
//        if (!permissionState.status.isGranted) {
//            permissionState.launchPermissionRequest()
//        }
//    }

    LaunchedEffect(uiState.currentPath) {
        if (permissionState.status.isGranted) {
            viewModel.loadItemsForCurrentPath()
        } else {
            // パーミッションがない場合、アイテムリストをクリアしたり、
            // エラーメッセージを表示するなどの処理をViewModel経由で行うこともできる
            // viewModel.clearItemsOrSetPermissionError()
        }
    }

    Scaffold(
        topBar = {
            // パス表示などをここに追加すると良い
            TopAppBar(title = { Text(text = uiState.currentPath ?: "/") })
        }
    ) { padding ->
        if (permissionState.status.isGranted) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(uiState.items, key = {it.path}) { item ->
                        MediaListItem(
                            item = item,
                            onClick = { clickedItem ->
                                when (clickedItem) {
                                    is FolderItem -> onFolderClick(clickedItem.path)
                                    is TrackItem -> onTrackClick(clickedItem)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("ストレージへのアクセス権限が必要です。")
            }
        }
    }
}

@Composable
fun MediaListItem(
    item: MediaItem,
    onClick: (MediaItem) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick(item) },
        headlineContent = { Text(item.title) },
        leadingContent = {
            Icon(
                imageVector = if (item is FolderItem) Icons.Default.Folder else Icons.Default.Audiotrack,
                contentDescription = null
            )
        }
    )
}

