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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core_model.TrackItem
import com.example.theme.AppTheme
import java.util.concurrent.TimeUnit

@Composable
fun PlayerScreen(
    trackItem: TrackItem,
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    LaunchedEffect(trackItem) {
        viewModel.setTrack(trackItem)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlayerScreenContent(
        uiState = uiState,
        onBack = onBack,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo
        // フェーズ4で次へ/前への関数を渡す
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreenContent(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* タイトルはなし */ },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Spacer to push content down
            Spacer(modifier = Modifier.weight(1f))

            // Album Art
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                AsyncImage(
                    model = uiState.currentTrack?.artworkUri,
                    contentDescription = "Album Art",
                    // フェーズ1ではローカルのリソースを指定
                    placeholder = painterResource(id = R.drawable.ic_default_music_art),
                    error = painterResource(id = R.drawable.ic_default_music_art),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Track Info
            Text(
                text = uiState.currentTrack?.title ?: "Unknown Track",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uiState.currentTrack?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Seek Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = uiState.currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..uiState.totalDuration.toFloat().coerceAtLeast(0f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(uiState.currentPosition), style = MaterialTheme.typography.labelSmall)
                    Text(text = formatDuration(uiState.totalDuration), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Player Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Button (フェーズ4で有効化)
                IconButton(onClick = { /* TODO */ }, enabled = false) {
                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
                }

                // Play/Pause Button
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Next Button (フェーズ4で有効化)
                IconButton(onClick = { /* TODO */ }, enabled = false) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
                }
            }

            // Spacer to push content up
            Spacer(modifier = Modifier.weight(1.5f))
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun PlayerScreenPreview() {
    val previewTrack = TrackItem(
        title = "Another Brick in the Wall, Pt. 2",
        path = "",
        uri = "",
        artist = "Pink Floyd",
        albumId = null,
        album = "The Wall",
        artworkUri = null,
        durationMs = 239000 // 3:59
    )

    AppTheme {
        PlayerScreenContent(
            uiState = PlayerUiState(
                currentTrack = previewTrack,
                isPlaying = true,
                currentPosition = 75000, // 1:15
                totalDuration = 239000
            ),
            onBack = {},
            onTogglePlayPause = {},
            onSeek = {}
        )
    }
}