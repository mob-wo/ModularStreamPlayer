package com.example.feature_browser

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.core_model.TrackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch



data class PlayerUiState(
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
)

@HiltViewModel
class PlayerViewModel
@OptIn(UnstableApi::class)
@Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    val player: Player, // HiltからExoPlayerインスタンスを注入
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var positionTrackingJob: Job? = null

    fun setTrack(track: TrackItem) {
        _uiState.update { it.copy(currentTrack = track, totalDuration = track.durationMs) }
        playTrack(track)
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionTracking()
                } else {
                    stopPositionTracking()
                }
            }

            //override fun onMediaItemTransition(mediaItem: Media3MediaItem?, @MediaItemTransitionReason reason: Int) {
                // 将来のプレイリスト対応用
                // ここで新しい曲の情報をUI Stateに反映させる
            //}
        })
    }

    private fun playTrack(track: TrackItem) {
        val mediaItem = Media3MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.uri) // Media IDも設定
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
        _uiState.update { it.copy(currentPosition = position) }
    }

    private fun startPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update { it.copy(currentPosition = player.currentPosition) }
                delay(1000L) // 1秒ごとに更新
            }
        }
    }

    private fun stopPositionTracking() {
        positionTrackingJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        player.clearMediaItems() // ViewModelが破棄されるときは再生リストをクリア
        stopPositionTracking()
        // Playerインスタンス自体の解放(release)はApplicationスコープで行う
    }
}