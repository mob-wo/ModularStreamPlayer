package com.example.feature_browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data_repository.LayoutMode
import com.example.data_repository.ViewMode
import com.example.core_model.NasConnection
import java.io.File

// --- ViewModelと連携するためのStateとEventを定義 ---

/**
 * ドロワーに表示するデータソースの選択肢を表現するモデル。
 */
sealed class DataSourceItem(val displayName: String, val id: String) {
    data object Local : DataSourceItem("ローカルストレージ", "local_storage")
    data class Smb(val connection: NasConnection) : DataSourceItem(
        displayName = connection.nickname, // ニックネームを表示名とする
        id = connection.id                 // 一意なIDとしてNasConnectionのIDを使用
    )
}

/**
 * ナビゲーションドロワーが表示に必要な状態をすべて保持するデータクラス
 */
data class DrawerUiState(
    // 将来の拡張性のため、Enumでデータソース種別を定義
    val currentDataSource: DataSourceItem = DataSourceItem.Local,
    val availableDataSources: List<DataSourceItem> = listOf(DataSourceItem.Local),
    val layoutMode: LayoutMode = LayoutMode.LIST,
    val viewMode: ViewMode = ViewMode.SINGLE,
    val favoritePaths: List<String> = emptyList(),
    val isFavoritesExpanded: Boolean = true
)

/**
 * ドロワー内で発生したユーザー操作を表現するイベント
 */
sealed interface DrawerEvent {
    object OnRefreshClicked : DrawerEvent
    data class OnDataSourceSelected(val source: DataSourceItem) : DrawerEvent
    data class OnLayoutModeChanged(val mode: LayoutMode) : DrawerEvent
    data class OnViewModeChanged(val mode: ViewMode) : DrawerEvent
    object OnFavoritesHeaderClicked : DrawerEvent
    data class OnFavoriteClicked(val path: String) : DrawerEvent
    data class OnFavoriteLongClicked(val path: String) : DrawerEvent
    object OnSettingsClicked : DrawerEvent
}


// --- UIコンポーネントの実装 ---

/**
 * アプリのナビゲーションドロワー本体
 * @param state ドロワーの表示内容を決定する状態
 * @param onEvent ユーザー操作を通知するためのコールバック
 */
@Composable
fun AppDrawer(
    state: DrawerUiState,
    onEvent: (DrawerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            item { DrawerHeader() }

            item {
                DrawerItem(
                    text = "データソースの更新",
                    icon = Icons.Default.Refresh,
                    onClick = { onEvent(DrawerEvent.OnRefreshClicked) }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { DrawerSectionHeader("データソース") } // ヘッダーは単独のitemに

            // ★ ここを修正: LazyColumn の items(list) を使用する
            items(state.availableDataSources, key = { it.id }) { source ->
                SelectableItem(
                    text = source.displayName,
                    isSelected = state.currentDataSource.id == source.id,
                    onClick = { onEvent(DrawerEvent.OnDataSourceSelected(source)) }
                )
            }
            /*item {
                DrawerSectionHeader("データソース")
                state.availableDataSources.forEach { source ->
                    SelectableItem(
                        text = source.displayName,
                        // ★ idで選択状態を比較
                        isSelected = state.currentDataSource.id == source.id,
                        onClick = { onEvent(DrawerEvent.OnDataSourceSelected(source)) }
                    )
                }
            }*/

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                DrawerSectionHeader("表示設定")
                // 画面モード設定
                Text("画面モード", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SegmentedButtonItem(text = "1画面", isSelected = state.viewMode == ViewMode.SINGLE) {
                        onEvent(DrawerEvent.OnViewModeChanged(ViewMode.SINGLE))
                    }
                    SegmentedButtonItem(text = "2画面", isSelected = state.viewMode == ViewMode.DUAL) {
                        onEvent(DrawerEvent.OnViewModeChanged(ViewMode.DUAL))
                    }
                }
                // 表示密度設定
                Text("表示密度", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SegmentedButtonItem(text = "リスト", isSelected = state.layoutMode == LayoutMode.LIST) {
                        onEvent(DrawerEvent.OnLayoutModeChanged(LayoutMode.LIST))
                    }
                    SegmentedButtonItem(text = "中", isSelected = state.layoutMode == LayoutMode.GRID_MEDIUM) {
                        onEvent(DrawerEvent.OnLayoutModeChanged(LayoutMode.GRID_MEDIUM))
                    }
                    SegmentedButtonItem(text = "小", isSelected = state.layoutMode == LayoutMode.GRID_SMALL) {
                        onEvent(DrawerEvent.OnLayoutModeChanged(LayoutMode.GRID_SMALL))
                    }
                }
            }


            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // お気に入りセクション
            item {
                ExpandableDrawerSectionHeader(
                    title = "お気に入り (${state.currentDataSource.displayName})",
                    isExpanded = state.isFavoritesExpanded,
                    onClick = { onEvent(DrawerEvent.OnFavoritesHeaderClicked) }
                )
            }
            if (state.isFavoritesExpanded) {
                if (state.favoritePaths.isEmpty()) {
                    item {
                        Text(
                            text = "（フォルダ長押しで追加）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(state.favoritePaths) { path ->
                        FavoriteItem(
                            path = path,
                            onClick = { onEvent(DrawerEvent.OnFavoriteClicked(path)) },
                            onLongClick = { onEvent(DrawerEvent.OnFavoriteLongClicked(path)) }
                        )
                    }
                }
            }

            // フッター（画面下部に配置するためのSpacer）
            item { Spacer(modifier = Modifier.weight(1f)) }

            item {
                Column {
                    HorizontalDivider()
                    DrawerItem(
                        text = "詳細設定",
                        icon = Icons.Default.Settings,
                        onClick = { onEvent(DrawerEvent.OnSettingsClicked) }
                    )
                }
            }
        }
    }
}

// --- 個別のUI部品 ---

@Composable
private fun DrawerHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "Modular Stream Player",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ExpandableDrawerSectionHeader(
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = if (isExpanded) "閉じる" else "展開する"
        )
    }
}

@Composable
private fun DrawerItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable
private fun DrawerItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DrawerItem(text = text, icon = { Icon(icon, contentDescription = text) }, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteItem(
    path: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // File.separatorを使って、パスの最後の部分（フォルダ名）のみを抽出
    val folderName = path.substringAfterLast(File.separator)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, contentDescription = "お気に入り", tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Text(folderName, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SelectableItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SegmentedButtonItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Text(
        text = text,
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    )
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun AppDrawerPreview() {
    val previewState = DrawerUiState(
        currentDataSource = DataSourceItem.Local,
        availableDataSources = listOf(DataSourceItem.Local, DataSourceItem.Local),
        layoutMode = LayoutMode.GRID_MEDIUM,
        viewMode = ViewMode.DUAL,
        favoritePaths = listOf(
            "/storage/emulated/0/Music/Rock",
            "/storage/emulated/0/Music/J-Pop/AwesomeBand"
        ),
        isFavoritesExpanded = true
    )
    MaterialTheme {
        AppDrawer(
            state = previewState,
            onEvent = {} // Previewでは何もしない
        )
    }
}