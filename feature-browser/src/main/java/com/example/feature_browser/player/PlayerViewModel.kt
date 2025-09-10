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
import com.example.core_http.LocalHttpServer
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

/**
 * プレーヤー画面のUI状態を表すデータクラス。
 *
 * @property trackName 現在のトラック名。
 * @property artistName 現在のアーティスト名。
 * @property artworkData 現在のアートワークデータ（バイト配列）。
 * @property currentPositionFormatted フォーマットされた現在の再生位置。
 * @property totalDurationFormatted フォーマットされた総再生時間。
 * @property progress 再生進捗度 (0.0f - 1.0f)。
 * @property isPlaying 再生中かどうか。
 * @property hasPreviousMediaItem 前のメディアアイテムが存在するかどうか。
 * @property hasNextMediaItem 次のメディアアイテムが存在するかどうか。
 */
data class PlayerUiState(
    val trackName: String = "",
    val artistName: String = "",
    val artworkData: ByteArray? = null,
    val currentPositionFormatted: String = "00:00",
    val totalDurationFormatted: String = "00:00",
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
    val hasPreviousMediaItem: Boolean = false,
    val hasNextMediaItem: Boolean = false,
) {
    // equalsとhashCodeは内容比較のために重要なので変更なし
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

/**
 * プレーヤー画面のビジネスロジックを処理するViewModel。
 * MediaControllerの管理、再生状態のUIへの反映、ユーザー操作の処理などを行う。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackRequestRepository: PlaybackRequestRepository,
    private val localHttpServer: LocalHttpServer
) : ViewModel() {

    private var mediaController: MediaController? = null
    private val _isControllerReady = MutableStateFlow(false)
    /** MediaControllerの準備ができているかを示すStateFlow。 */
    val isControllerReady: StateFlow<Boolean> = _isControllerReady.asStateFlow()

    private val _uiState = MutableStateFlow(PlayerUiState())
    /** プレーヤー画面のUI状態。 */
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var positionUpdateJob: Job? = null

    init {
        // LocalHttpServerの起動を保証
        localHttpServer.ensureStarted()
        Log.d("PlayerViewModel", "PlayerViewModel初期化: LocalHttpServerの起動を確認しました。")

        initializeMediaController()

        viewModelScope.launch {
            _isControllerReady
                .filter { it }
                .flatMapLatest { playbackRequestRepository.playbackRequest }
                .collect { request ->
                    if (request != null) {
                        startPlayback(request)
                        playbackRequestRepository.consumePlaybackRequest() // リクエストを消費済みにする
                    }
                }
        }
    }

    /**
     * MediaControllerを非同期で初期化する。
     */
    private fun initializeMediaController() {
        viewModelScope.launch {
            try {
                val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val controllerFuture: ListenableFuture<MediaController> =
                    MediaController.Builder(context, sessionToken).buildAsync()

                val controller = controllerFuture.await()
                mediaController = controller
                _isControllerReady.value = true
                Log.d("PlayerViewModel", "MediaControllerの初期化に成功しました。")
                addControllerListener(controller) // コントローラーのリスナーを設定
                // 初期状態をUIに反映
                _uiState.update {
                    it.copy(
                        isPlaying = controller.isPlaying,
                        hasPreviousMediaItem = controller.hasPreviousMediaItem(),
                        hasNextMediaItem = controller.hasNextMediaItem()
                    )
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "MediaControllerの初期化に失敗しました。", e)
            }
        }
    }

    /**
     * MediaControllerにリスナーを追加し、再生状態の変更をUIに反映する。
     * @param controller リスナーを追加するMediaController。
     */
    private fun addControllerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                Log.d("PlayerViewModel", "onMediaMetadataChanged: ${mediaMetadata.title}")
                _uiState.update {
                    it.copy(
                        trackName = mediaMetadata.title?.toString() ?: "タイトルなし",
                        artistName = mediaMetadata.artist?.toString() ?: "不明なアーティスト",
                        artworkData = mediaMetadata.artworkData
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("PlayerViewModel", "onIsPlayingChanged: $isPlaying")
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("PlayerViewModel", "onPlaybackStateChanged: $playbackState")
                if (playbackState == Player.STATE_READY) {
                    _uiState.update {
                        it.copy(
                            totalDurationFormatted = formatDuration(controller.duration.coerceAtLeast(0))
                        )
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d("PlayerViewModel", "onPositionDiscontinuity: reason $reason")
                _uiState.update {
                    it.copy(
                        hasPreviousMediaItem = controller.hasPreviousMediaItem(),
                        hasNextMediaItem = controller.hasNextMediaItem()
                    )
                }
            }
        })
    }

    /**
     * 再生位置の定期的なUI更新を開始する。
     */
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
        Log.d("PlayerViewModel", "再生位置の更新を開始しました。")
    }

    /**
     * 再生位置の定期的なUI更新を停止する。
     */
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        Log.d("PlayerViewModel", "再生位置の更新を停止しました。")
    }

    /** 再生/一時停止ボタンがクリックされた時の処理。 */
    fun onPlayPauseClick() {
        mediaController?.let {
            if (it.isPlaying) {
                it.pause()
                Log.d("PlayerViewModel", "再生を一時停止しました。")
            } else {
                it.play()
                Log.d("PlayerViewModel", "再生を開始しました。")
            }
        }
    }

    /** 次の曲へボタンがクリックされた時の処理。 */
    fun onNextClick() {
        mediaController?.seekToNextMediaItem()
        Log.d("PlayerViewModel", "次の曲へ移動します。")
    }

    /** 前の曲へボタンがクリックされた時の処理。 */
    fun onPreviousClick() {
        mediaController?.seekToPreviousMediaItem()
        Log.d("PlayerViewModel", "前の曲へ移動します。")
    }

    /**
     * シークバーが操作された時の処理。
     * @param position 新しい再生位置の割合 (0.0f - 1.0f)。
     */
    fun onSeek(position: Float) {
        mediaController?.let {
            val seekPositionMs = (it.duration.coerceAtLeast(0) * position).toLong()
            it.seekTo(seekPositionMs)
            Log.d("PlayerViewModel", "再生位置を ${formatDuration(seekPositionMs)} に変更しました。")
        }
    }

    /**
     * 指定されたリクエストに基づいて再生を開始する。
     * @param request 再生リクエスト情報。
     */
    fun startPlayback(request: PlaybackRequest) {
        // 再生開始前にもLocalHttpServerの起動を保証
        localHttpServer.ensureStarted()
        Log.d("PlayerViewModel", "再生開始: LocalHttpServerの起動を確認。リクエスト: $request")

        val playlist = request.itemList.filterIsInstance<TrackItem>()
        if (playlist.isEmpty()) {
            Log.w("PlayerViewModel", "再生可能なトラックがプレイリストにありません。")
            return
        }

        val startIndex = playlist.indexOfFirst { it.path == request.currentItem.path }
        if (startIndex == -1) {
            Log.w("PlayerViewModel", "開始トラックがプレイリストに見つかりません。")
            Log.d("PlayerViewModel", "currentItem (path: ${request.currentItem.path}): ${request.currentItem}")
            playlist.forEachIndexed { index, track ->
                Log.d("PlayerViewModel", "playlist[$index] (path: ${track.path}): $track, path_equals_currentItem_path: ${track.path == request.currentItem.path}")
            }
            return
        }

        val mediaItems = playlist.map { track ->
            val uri = when (request.dataSource) {
                is ActiveDataSource.Local -> {
                    Log.d("PlayerViewModel", "ローカルデータソースを使用: ${track.uri}")
                    track.uri
                }
                is ActiveDataSource.Smb -> {
                    val httpUri = localHttpServer.getStreamingUrl(
                        smbPath = track.path,
                        connectionId = (request.dataSource as ActiveDataSource.Smb).connectionId
                    )
                    Log.d("PlayerViewModel", "SMBデータソースを使用: ${track.path} -> $httpUri")
                    httpUri
                }
            }
            MediaItem.fromUri(uri)
        }

        mediaController?.let { controller ->
            Log.d("PlayerViewModel", "MediaControllerにメディアアイテムを設定: ${mediaItems.size}曲, 開始インデックス: $startIndex")
            controller.setMediaItems(mediaItems, startIndex, 0) // 開始位置を0msに設定
            controller.prepare()
            controller.play()
            Log.i("PlayerViewModel", "再生を開始しました: ${request.currentItem.title}")
        }
    }

    override fun onCleared() {
        Log.d("PlayerViewModel", "ViewModel破棄 (onCleared)")
        // LocalHttpServerの停止処理はここでは行わない
        stopPositionUpdates()
        mediaController?.release() // MediaControllerのリソースを解放
        mediaController = null
        Log.d("PlayerViewModel", "MediaControllerを解放しました。")
        super.onCleared()
    }
}

/**
 * ミリ秒単位の時間を "mm:ss" 形式の文字列にフォーマットする。
 * @param ms フォーマットする時間（ミリ秒）。
 * @return フォーマットされた時間文字列。
 */
private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * 現在の再生位置と総再生時間から進捗度を計算する。
 * @param current 現在の再生位置（ミリ秒）。
 * @param total 総再生時間（ミリ秒）。
 * @return 進捗度 (0.0f - 1.0f)。
 */
private fun calculateProgress(current: Long, total: Long): Float {
    return if (total > 0) current.toFloat() / total else 0f
}
