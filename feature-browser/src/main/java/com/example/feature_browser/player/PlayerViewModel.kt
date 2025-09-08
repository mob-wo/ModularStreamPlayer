package com.example.feature_browser.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.core_model.TrackItem
import com.example.core_player.LocalHttpServer
import com.example.core_player.PlaybackService
import com.example.data_repository.ActiveDataSource
import com.example.data_repository.PlaybackRequest
import com.example.data_repository.PlaybackRequestRepository
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject


data class PlayerUiState(
    val trackName: String = "",
    val artistName: String = "",
    val artworkData: ByteArray? = null, // ★★★ UriからByteArray?に変更 ★★★
    val currentPositionFormatted: String = "00:00",
    val totalDurationFormatted: String = "00:00",
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
    val hasPreviousMediaItem: Boolean = false,
    val hasNextMediaItem: Boolean = false,

) {
    // ★★★ ByteArrayの比較は参照ではなく内容で行うためのequals/hashCode ★★★
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerUiState

        if (trackName != other.trackName) return false
        if (artistName != other.artistName) return false
        if (artworkData != null) {
            if (other.artworkData == null) return false
            if (!artworkData.contentEquals(other.artworkData)) return false
        } else if (other.artworkData != null) return false
        if (currentPositionFormatted != other.currentPositionFormatted) return false
        if (totalDurationFormatted != other.totalDurationFormatted) return false
        if (progress != other.progress) return false
        if (isPlaying != other.isPlaying) return false
        if (hasPreviousMediaItem != other.hasPreviousMediaItem) return false
        if (hasNextMediaItem != other.hasNextMediaItem) return false

        return true
    }

    override fun hashCode(): Int {
        var result = trackName.hashCode()
        result = 31 * result + artistName.hashCode()
        result = 31 * result + (artworkData?.contentHashCode() ?: 0)
        result = 31 * result + currentPositionFormatted.hashCode()
        result = 31 * result + totalDurationFormatted.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + hasPreviousMediaItem.hashCode()
        result = 31 * result + hasNextMediaItem.hashCode()
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackRequestRepository: PlaybackRequestRepository,
    private val localHttpServer: LocalHttpServer
    // PlayerStateRepositoryは直接は不要になった
) : ViewModel() {

    private var mediaController: MediaController? = null
    private val _isControllerReady = MutableStateFlow(false)
    val isControllerReady: StateFlow<Boolean> = _isControllerReady.asStateFlow()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var positionUpdateJob: Job? = null

    init {
        try {
            if (!localHttpServer.isAlive) {
                localHttpServer.start()
                Log.d("PlayerViewModel", "LocalHttpServer started.")
            }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Failed to start LocalHttpServer", e)
        }

        initializeMediaController()

        viewModelScope.launch {
            _isControllerReady
                .filter { it }
                .flatMapLatest { playbackRequestRepository.playbackRequest }
                .collect { request ->
                    if (request != null) {
                        startPlayback(request)
                        playbackRequestRepository.consumePlaybackRequest()
                    }
                }
        }
    }

    private fun initializeMediaController() {
        viewModelScope.launch {
            try {
                val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val controllerFuture: ListenableFuture<MediaController> =
                    MediaController.Builder(context, sessionToken).buildAsync()

                val controller = controllerFuture.await()
                mediaController = controller
                _isControllerReady.value = true
                Log.d("PlayerViewModel", "MediaController initialized successfully.")
                // ★★★ コントローラーのリスナーを設定 ★★★
                addControllerListener(controller)
                // ★★★ 初期状態を更新 ★★★
                _uiState.update {
                    it.copy(
                        isPlaying = controller.isPlaying,
                        hasPreviousMediaItem = controller.hasPreviousMediaItem(),
                        hasNextMediaItem = controller.hasNextMediaItem()
                    )
                }

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to initialize MediaController", e)
            }
        }
    }

    private fun addControllerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            // ★★★ 1. メタデータ（曲名、アートワークなど）の変更 ★★★
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                _uiState.update {
                    it.copy(
                        trackName = mediaMetadata.title?.toString() ?: "No Title",
                        artistName = mediaMetadata.artist?.toString() ?: "Unknown Artist",
                        artworkData = mediaMetadata.artworkData
                    )
                }
            }

            // ★★★ 2. 再生/一時停止状態の変更 ★★★
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            // ★★★ 3. 再生状態（準備完了など）や曲の長さの変更 ★★★
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _uiState.update {
                        it.copy(
                            totalDurationFormatted = formatDuration(controller.duration.coerceAtLeast(0))
                        )
                    }
                }
            }

            // ★★★ 4. プレイリスト内の位置（曲遷移など）の変更 ★★★
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _uiState.update {
                    it.copy(
                        hasPreviousMediaItem = controller.hasPreviousMediaItem(),
                        hasNextMediaItem = controller.hasNextMediaItem()
                    )
                }
            }
        })
    }


    private fun startPositionUpdates() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                val player = mediaController ?: break
                _uiState.update {
                    it.copy(
                        currentPositionFormatted = formatDuration(player.currentPosition),
                        progress = calculateProgress(player.currentPosition, player.duration)
                    )
                }
                delay(500L) // UIの滑らかさのため少し間隔を短くする
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // --- UIイベントハンドラ ---
    fun onPlayPauseClick() = mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    fun onNextClick() = mediaController?.seekToNextMediaItem()
    fun onPreviousClick() = mediaController?.seekToPreviousMediaItem()

    fun onSeek(position: Float) {
        mediaController?.let {
            val seekPositionMs = (it.duration.coerceAtLeast(0) * position).toLong()
            it.seekTo(seekPositionMs)
        }
    }

    // isFirstMusic/isLastMusic は uiState に統合

    fun startPlayback(request: PlaybackRequest) {
        val playlist = request.itemList.filterIsInstance<TrackItem>()
        if (playlist.isEmpty()) return

        val startIndex = playlist.indexOf(request.currentItem)
        if (startIndex == -1) return

        // MediaItemを再ビルドしてクリーンなリストを渡す
        val mediaItems = playlist.map { track ->
            val uri = when (request.dataSource) {
                is ActiveDataSource.Local -> {
                    // ローカルファイルの場合はそのままURIを使用
                    track.uri
                }
                is ActiveDataSource.Smb -> {
                    // SMBファイルの場合は、LocalHttpServer経由のURLに変換
                    localHttpServer.getStreamingUrl(
                        smbPath = track.path,
                        connectionId = (request.dataSource as ActiveDataSource.Smb).connectionId
                    )
                }
            }
            // MediaItemをURIから生成
            MediaItem.fromUri(uri)
        }

        mediaController?.let { controller ->
            controller.setMediaItems(mediaItems, startIndex, 0)
            controller.prepare()
            controller.play()
        }
    }

    override fun onCleared() {
        // ★ サーバーの停止
        if (localHttpServer.isAlive) {
            localHttpServer.stop()
            Log.d("PlayerViewModel", "LocalHttpServer stopped.")
        }
        stopPositionUpdates()
        mediaController?.release()
        super.onCleared()
    }
}

// --- ヘルパー関数 ---
private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
private fun calculateProgress(current: Long, total: Long): Float {
    return if (total > 0) current.toFloat() / total else 0f
}