package com.example.feature_browser.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core_model.NasConnection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NasConnectionListScreen(
    viewModel: SettingsViewModel,
    onNavigateToEditor: (connectionId: String?) -> Unit,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.listUiState.collectAsState()
    var connectionToDelete by remember { mutableStateOf<NasConnection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NAS接続設定") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEditor(null) }) { // 新規作成
                        Icon(Icons.Default.Add, contentDescription = "新規追加")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            if (uiState.connections.isEmpty()) {
                Text(
                    text = "接続情報がありません。\n右上の「＋」から追加してください。",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn {
                    items(uiState.connections, key = { it.id }) { connection ->
                        ListItem(
                            modifier = Modifier.combinedClickable(
                                onClick = { onNavigateToEditor(connection.id) }, // 編集
                                onLongClick = { connectionToDelete = connection } // 削除確認
                            ),
                            headlineContent = { Text(connection.nickname) },
                            supportingContent = {
                                Text(
                                    text = "${connection.hostname}/${connection.path}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // 削除確認ダイアログ
    connectionToDelete?.let { connection ->
        AlertDialog(
            onDismissRequest = { connectionToDelete = null },
            title = { Text("接続の削除") },
            text = { Text("「${connection.nickname}」を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConnection(connection.id)
                        connectionToDelete = null
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}