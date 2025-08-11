package com.example.feature_browser.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data_repository.ViewMode
import com.example.feature_browser.R
import com.example.theme.AppTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerFullScreen(
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
                            contentDescription = stringResource(R.string.back)
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
        PlayerScreen(
            modifier = modifier,
            paddingValues = paddingValues,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewMode: ViewMode = ViewMode.SINGLE,
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isControllerReady by viewModel.isControllerReady.collectAsStateWithLifecycle()
    if (!isControllerReady) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // ローディング中の表示
            CircularProgressIndicator()
        }
    } else {
        PlayerScreenContent(
            modifier = modifier,
            viewMode = viewMode,
            uiState = uiState,
            onSeek = { newPosition ->
                viewModel.onSeek(newPosition)
            },
            onPreviousClick = {
                viewModel.onPreviousClick()
            },
            onPlayPauseClick = {
                viewModel.onPlayPauseClick()
            },
            onNextClick = {
                viewModel.onNextClick()
            },
            paddingValues = paddingValues,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreenContent(
    modifier: Modifier = Modifier,
    viewMode: ViewMode = ViewMode.SINGLE,
    uiState: PlayerUiState = PlayerUiState(),
    paddingValues: PaddingValues = PaddingValues(),
    onSeek: (Float) -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
) {
    // 表示モードに応じてレイアウトを調整
    val artworkModifier: Modifier
    val mainContentSpacerHeight: Dp
    val controlsSpacerWeight: Float

    if (viewMode == ViewMode.DUAL) {
        artworkModifier = Modifier
            .fillMaxWidth(0.3f) // コンパクトモードでは横幅を少し狭く
            .aspectRatio(1f)
        mainContentSpacerHeight = 8.dp // コンパクトモードではメインコンテンツとアートワークの間を狭く
        controlsSpacerWeight = 0.0f     // コンパクトモードでは下のスペーサーを小さく
    } else { // FULL モード
        artworkModifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
        mainContentSpacerHeight = 48.dp
        controlsSpacerWeight = 1.0f
    }

    Column(
        modifier = modifier
            //.fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Spacer to push content down
        if (viewMode == ViewMode.SINGLE) {
            Spacer(modifier = Modifier.weight(controlsSpacerWeight))
        } else {
            Spacer(modifier = Modifier.height(mainContentSpacerHeight))
        }


        // Album Art
        Card(
            modifier = artworkModifier,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.artworkData) // ★★★ UriではなくByteArrayを直接渡す ★★★
                    .crossfade(true)
                    .placeholder(R.drawable.ic_default_music_art)
                    .error(R.drawable.ic_default_music_art)
                    .build(),
                contentDescription = "Album Art",
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

        Spacer(modifier = Modifier.height(mainContentSpacerHeight))

        // Seek Bar
        PlayerSeekBar(
            progress = uiState.progress,
            currentPositionFormatted = uiState.currentPositionFormatted,
            totalDurationFormatted = uiState.totalDurationFormatted,
            onSeek = { newPosition ->
                onSeek(newPosition)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Player Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Button (フェーズ4で有効化)
            IconButton(
                onClick = { onPreviousClick() },
                enabled = uiState.hasPreviousMediaItem
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(40.dp)
                )
            }

            // Play/Pause Button
            IconButton(
                onClick = { onPlayPauseClick() },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Next Button (フェーズ4で有効化)
            IconButton(
                onClick = { onNextClick() },
                enabled = uiState.hasNextMediaItem
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        if (viewMode == ViewMode.SINGLE) {
            Spacer(modifier = Modifier.weight(controlsSpacerWeight))
        } else {
            Spacer(modifier = Modifier.height(mainContentSpacerHeight))
        }
    }
}

val previewItem = PlayerUiState(
    trackName = "Another Brick in the Wall, Pt. 2",
    artistName = "Pink Floyd",
    artworkData = null,
    currentPositionFormatted = "00:00",
    totalDurationFormatted = "00:00",
    progress = 0f,
    isPlaying = false,
    hasPreviousMediaItem = false,
    hasNextMediaItem = false
)

@Preview(showBackground = true, name = "PlayerScreenContent - Compact")
@Composable
fun PlayerScreenContentCompactPreview() {
    AppTheme {
        PlayerScreenContent(
            viewMode = ViewMode.DUAL,
            uiState = previewItem)
    }
}

@Preview(showBackground = true, name = "PlayerScreenContent - Full")
@Composable
fun PlayerScreenContentFullPreview() {
    AppTheme {
        PlayerScreenContent(
            viewMode = ViewMode.SINGLE,
            uiState = previewItem)
    }
}