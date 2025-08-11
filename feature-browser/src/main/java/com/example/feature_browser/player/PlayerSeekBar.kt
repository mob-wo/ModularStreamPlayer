package com.example.feature_browser.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.theme.AppTheme

/**
 * 再生画面のシークバーと時間表示を担当するコンポーネント。
 *
 * @param progress 現在の再生位置の進捗 (0.0f から 1.0f)。
 * @param currentPositionFormatted 現在の再生時間のフォーマット済み文字列 (例: "01:23")。
 * @param totalDurationFormatted 曲の総時間のフォーマット済み文字列 (例: "03:45")。
 * @param onSeek ユーザーがシーク操作を完了したときに呼び出されるコールバック。新しい位置(0.0f-1.0f)を渡す。
 */
@Composable
fun PlayerSeekBar(
    progress: Float,
    currentPositionFormatted: String,
    totalDurationFormatted: String,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // ユーザーがスライダーをドラッグしている間の状態を管理
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth(),
            //.padding(horizontal = 24.dp)
    ) {
        Slider(
            value = if (isSeeking) seekPosition else progress,
            onValueChange = { newValue ->
                // ドラッグ中の位置を更新
                isSeeking = true
                seekPosition = newValue
            },
            onValueChangeFinished = {
                // ドラッグが完了したら、ViewModelに最終的な位置を通知
                onSeek(seekPosition)
                isSeeking = false
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentPositionFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = totalDurationFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


// --- Preview ---

@Preview(showBackground = true, widthDp = 360)
@Composable
fun PlayerSeekBarPreview() {
    AppTheme {
        PlayerSeekBar(
            progress = 0.3f, // 30%の位置
            currentPositionFormatted = "01:10",
            totalDurationFormatted = "03:55",
            onSeek = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun PlayerSeekBarAtStartPreview() {
    AppTheme {
        PlayerSeekBar(
            progress = 0f,
            currentPositionFormatted = "00:00",
            totalDurationFormatted = "04:20",
            onSeek = {}
        )
    }
}