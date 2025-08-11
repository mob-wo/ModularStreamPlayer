package com.example.feature_browser

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core_model.TrackItem
import com.example.data_repository.ViewMode
import com.example.theme.AppTheme
import java.util.concurrent.TimeUnit


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* タイトルはなし */ },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f) // 背景を透過
                )
            )
        }
    ) { paddingValues ->
//    LaunchedEffect(uiState.currentTrack) {
//        viewModel.setTrack(uiState.currentTrack)
//    }
        PlayerScreenContent(
            modifier = modifier,
            paddingValues = paddingValues,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreenContent(
    modifier: Modifier = Modifier,
    viewMode: ViewMode = ViewMode.SINGLE,
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: PlayerViewModel = hiltViewModel()
) {
    // ViewModelからUI状態を収集
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()


    // 表示モードに応じてレイアウトを調整
    val artworkModifier: Modifier
    val mainContentSpacerHeight: Dp
    val controlsSpacerWeight: Float

    if (viewMode == ViewMode.DUAL) {
        artworkModifier = Modifier
            .fillMaxWidth(0.6f) // コンパクトモードでは横幅を少し狭く
            .aspectRatio(1f)
        mainContentSpacerHeight = 16.dp // コンパクトモードではメインコンテンツとアートワークの間を狭く
        controlsSpacerWeight = 0.5f     // コンパクトモードでは下のスペーサーを小さく
    } else { // FULL モード
        artworkModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
        mainContentSpacerHeight = 48.dp
        controlsSpacerWeight = 1.5f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Spacer to push content down
        if (viewMode == ViewMode.SINGLE) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Album Art
        Card(
            modifier = artworkModifier,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            AsyncImage(
                model = uiState.artworkUri,
                contentDescription = "Album Art",
                // フェーズ1ではローカルのリソースを指定
                placeholder = painterResource(id = R.drawable.ic_default_music_art),
                error = painterResource(id = R.drawable.ic_default_music_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(mainContentSpacerHeight))

        // Track Info
        Text(
            text = uiState.trackName,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = uiState.artistName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Seek Bar
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = uiState.progress,
                onValueChange = { viewModel.onSeek(it) },
                valueRange = 0f..uiState.totalDuration.toFloat().coerceAtLeast(0f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(uiState.currentPosition),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatDuration(uiState.totalDuration),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Player Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Button (フェーズ4で有効化)
            IconButton(onClick = { /* TODO */ }, enabled = false) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(40.dp)
                )
            }

            // Play/Pause Button
            IconButton(onClick = { viewModel.onPlayPauseClick() }, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Next Button (フェーズ4で有効化)
            IconButton(onClick = { /* TODO */ }, enabled = false) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Spacer to push content up
        Spacer(modifier = Modifier.weight(controlsSpacerWeight))
    }
}


private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true, name = "PlayerScreenContent - Compact")
@Composable
fun PlayerScreenContentCompactPreview() {
    AppTheme {
        PlayerScreenContent(viewMode = ViewMode.DUAL)
    }
}

@Preview(showBackground = true, name = "PlayerScreenContent - Full")
@Composable
fun PlayerScreenContentFullPreview() {
    AppTheme {
        PlayerScreenContent(viewMode = ViewMode.SINGLE)
    }
}
//@Preview(showBackground = true)
//@Composable
//fun PlayerScreenPreview() {
//    val previewTrack = TrackItem(
//        title = "Another Brick in the Wall, Pt. 2",
//        path = "",
//        uri = "",
//        artist = "Pink Floyd",
//        albumId = null,
//        album = "The Wall",
//        artworkUri = null,
//        durationMs = 239000 // 3:59
//    )
//
//    AppTheme {
//        PlayerScreenContent(
//
//        )
//    }
//}