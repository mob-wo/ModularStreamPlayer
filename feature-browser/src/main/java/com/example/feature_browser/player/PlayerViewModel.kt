package com.example.feature_browser

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.core_model.TrackItem
import com.example.core_model.MediaItem
import com.example.core_player.PlaybackService
import com.example.core_player.mapper.toMediaItem
import com.example.data_repository.PlaybackRequestRepository
import com.example.data_repository.PlayerStateRepository
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// PlayerScreenが直接使えるようにマッピングされたUI状態
data class PlayerUiState(
    val trackName: String = "",
    val artistName: String = "",
    val artworkUri: String? = null,
    val currentPositionFormatted: String = "00:00",
    val totalDurationFormatted: String = "00:00",
    val progress: Float = 0f,
    val isPlaying: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackRequestRepository: PlaybackRequestRepository,
    playerStateRepository: PlayerStateRepository
) : ViewModel() {

    private val _mediaController = mutableStateOf<MediaController?>(null)
    val mediaController: State<MediaController?> = _mediaController

    // PlayerStateRepositoryからの生の状態を、UIに最適化されたPlayerUiStateに変換
    val uiState: StateFlow<PlayerUiState> = playerStateRepository.playbackState
        .map { playbackState ->
            PlayerUiState(
                trackName = playbackState.currentTrack?.title ?: "曲が選択されていません",
                artistName = playbackState.currentTrack?.artist ?: "Unknown Artist",
                artworkUri = playbackState.currentTrack?.artworkUri,
                currentPositionFormatted = formatDuration(playbackState.currentPositionMs),
                totalDurationFormatted = formatDuration(playbackState.totalDurationMs),
                progress = if (playbackState.totalDurationMs > 0) {
                    playbackState.currentPositionMs.toFloat() / playbackState.totalDurationMs
                } else 0f,
                isPlaying = playbackState.isPlaying
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerUiState()
        )

    init {
        initializeMediaController()

        // ★★★ 再生要求を監視する ★★★
        viewModelScope.launch {
            playbackRequestRepository.playbackRequest.collect { request ->
                // 新しい要求があり(nullでなく)、まだ処理されていなければ処理を開始
                if (request != null) {
                    startPlayback(
                        path = request.path,
                        itemList = request.itemList,
                        currentItem = request.currentItem
                    )
                    // ★★★ 要求を消費して、再処理を防ぐ ★★★
                    playbackRequestRepository.consumePlaybackRequest()
                }
            }
        }
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            { _mediaController.value = controllerFuture.get() },
            MoreExecutors.directExecutor()
        )
    }

    // --- UIイベントハンドラ ---
    fun onPlayPauseClick() {
        if (uiState.value.isPlaying) {
            _mediaController.value?.pause()
        } else {
            _mediaController.value?.play()
        }
    }

    fun onNextClick() {
        _mediaController.value?.seekToNextMediaItem()
    }

    fun onPreviousClick() {
        _mediaController.value?.seekToPreviousMediaItem()
    }

    fun onSeek(position: Float) {
        val seekPositionMs = (uiState.value.totalDurationFormatted.toMillis() * position).toLong()
        _mediaController.value?.seekTo(seekPositionMs)
    }




    /**
     * 指定されたアイテムリストから再生を開始する。
     * リストにはフォルダが含まれている可能性があるため、トラックのみを選別してプレイリストを構築する。
     *
     * @param path 現在のフォルダパス。プレイリストの識別に利用。
     * @param itemList ブラウザに表示されている全アイテム（フォルダとトラックの混合リスト）。
     * @param currentItem ユーザーが実際にタップしたアイテム（必ずTrackItemである想定）。
     */
    fun startPlayback(path: String, itemList: List<MediaItem>, currentItem: TrackItem) {
        // 1. 渡されたアイテムリストからTrackItemのみを選別してプレイリストを作成
        val playlist = itemList.filterIsInstance<TrackItem>()

        if (playlist.isEmpty()) return

        // 2. プレイリスト内で、タップされたトラックが何番目にあるかを探す
        val startIndex = playlist.indexOf(currentItem)

        // 念のため、見つからなかった場合は再生しない
        if (startIndex == -1) return

        // 3. 抽出したプレイリストをMediaItemに変換
        val mediaItems = playlist.map { it.toMediaItem() }

        // 4. MediaControllerを通じてPlaybackServiceに再生を指示
        _mediaController.value?.let { controller ->
            controller.setMediaItems(mediaItems, startIndex, 0)
            controller.prepare()
            controller.play()
        }
    }


    override fun onCleared() {
        _mediaController.value?.release()
        super.onCleared()
    }
}

// --- ヘルパー関数 ---
private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
private fun String.toMillis(): Long {
    val parts = this.split(":")
    if (parts.size != 2) return 0L
    return (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
}
