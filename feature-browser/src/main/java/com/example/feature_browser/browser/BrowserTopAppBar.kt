package com.example.feature_browser.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

/**
 * ファイルブラウザ画面で使用するトップバー。
 * ナビゲーションドロワーを開くアイコンと画面タイトルを表示する。
 *
 * @param title 表示するタイトル。
 * @param onNavigationIconClick ナビゲーションアイコン（メニュー）がクリックされたときのコールバック。
 * @param modifier このコンポーザブルに適用するModifier。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTopAppBar(
    title: String,
    onNavigationIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis // タイトルが長い場合に省略記号(...)で表示
            )
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onNavigationIconClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "ナビゲーションメニューを開く" // アクセシビリティのため
                )
            }
        },
        // フェーズ2ではアクションアイコンはドロワーに移動したため、ここは空
        actions = {
            // 必要であれば将来ここにアイコンを追加できる
        },
        // Material 3のカラースキームに準拠した色設定
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun BrowserTopAppBarPreview() {
    MaterialTheme {
        BrowserTopAppBar(
            title = "Modular Stream Player",
            onNavigationIconClick = { /* Previewでは何もしない */ }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BrowserTopAppBarWithLongTitlePreview() {
    MaterialTheme {
        BrowserTopAppBar(
            title = "非常に長いフォルダ名が表示される場合のテストです",
            onNavigationIconClick = { }
        )
    }
}