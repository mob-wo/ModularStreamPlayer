package com.example.feature_browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data_repository.LayoutMode
import com.example.core_model.FolderItem
import com.example.core_model.MediaItem
import com.example.core_model.TrackItem

/**
 * 表示密度(LayoutMode)に応じて、適切なリストアイテムコンポーザブルを呼び出す。
 *
 * @param item 表示するMediaItem (FolderItem または TrackItem)
 * @param layoutMode 表示密度 (LIST, GRID_MEDIUM, GRID_SMALL)
 * @param onClick アイテムがクリックされたときのコールバック
 * @param onLongClick アイテムが長押しされたときのコールバック（主にフォルダ用）
 * @param modifier このコンポーザブルに適用するModifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListItem(
    item: MediaItem,
    layoutMode: LayoutMode,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemModifier = modifier.combinedClickable(
        onClick = { onClick(item) },
        onLongClick = {
            // フォルダアイテムのみ長押しをハンドリング
            if (item is FolderItem) {
                onLongClick(item)
            }
        }
    )

    when (layoutMode) {
        LayoutMode.LIST -> MediaListItemLarge(item, itemModifier)
        LayoutMode.GRID_MEDIUM -> MediaListItemMedium(item, itemModifier)
        LayoutMode.GRID_SMALL -> MediaListItemSmall(item, itemModifier)
    }
}

// --- 表示密度ごとの実装 ---

/**
 * 表示密度: 大（リスト形式）
 */
@Composable
private fun MediaListItemLarge(item: MediaItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(
            artworkUri = (item as? TrackItem)?.artworkUri,
            isFolder = item is FolderItem,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item is TrackItem) {
                Text(
                    text = item.artist ?: "不明なアーティスト",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 表示密度: 中（2列グリッド形式）
 */
@Composable
private fun MediaListItemMedium(item: MediaItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AlbumArtwork(
                artworkUri = (item as? TrackItem)?.artworkUri,
                isFolder = item is FolderItem,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 表示密度: 小（3列グリッド形式）
 */
@Composable
private fun MediaListItemSmall(item: MediaItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AlbumArtwork(
            artworkUri = (item as? TrackItem)?.artworkUri,
            isFolder = item is FolderItem,
            modifier = Modifier.fillMaxSize()
        )
    }
}


// --- 共通コンポーネント ---

/**
 * アートワークまたはフォルダアイコンを表示する共通コンポーネント
 */
@Composable
private fun AlbumArtwork(
    artworkUri: String?,
    isFolder: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        if (isFolder) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(R.drawable.ic_default_music_art),
                error = painterResource(R.drawable.ic_default_music_art),
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


// --- Preview ---

@Preview(name = "Large - Track", showBackground = true)
@Composable
fun MediaListItemLargeTrackPreview() {
    val track = TrackItem("Another Brick in the Wall", "/path/to/song.mp3", "uri1", "Pink Floyd", null, null, null, 239000)
    MaterialTheme {
        MediaListItemLarge(item = track)
    }
}

@Preview(name = "Large - Folder", showBackground = true)
@Composable
fun MediaListItemLargeFolderPreview() {
    val folder = FolderItem("The Wall", "/path/to/folder", "uri2")
    MaterialTheme {
        MediaListItemLarge(item = folder)
    }
}

@Preview(name = "Medium - Track")
@Composable
fun MediaListItemMediumTrackPreview() {
    val track = TrackItem("Stairway to Heaven", "/path/to/song.mp3", "uri3", "Led Zeppelin", null, null, null, 482000)
    MaterialTheme {
        Box(Modifier.size(200.dp)) {
            MediaListItemMedium(item = track)
        }
    }
}

@Preview(name = "Medium - Folder")
@Composable
fun MediaListItemMediumFolderPreview() {
    val folder = FolderItem("Led Zeppelin IV", "/path/to/folder", "uri4")
    MaterialTheme {
        Box(Modifier.size(200.dp)) {
            MediaListItemMedium(item = folder)
        }
    }
}

@Preview(name = "Small - Track")
@Composable
fun MediaListItemSmallTrackPreview() {
    val track = TrackItem("Bohemian Rhapsody", "/path/to/song.mp3", "uri5", "Queen", null, null, null, 355000)
    MaterialTheme {
        Box(Modifier.size(120.dp)) {
            MediaListItemSmall(item = track)
        }
    }
}

@Preview(name = "Small - Folder")
@Composable
fun MediaListItemSmallFolderPreview() {
    val folder = FolderItem("A Night at the Opera", "/path/to/folder", "uri6")
    MaterialTheme {
        Box(Modifier.size(120.dp)) {
            MediaListItemSmall(item = folder)
        }
    }
}