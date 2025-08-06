package com.example.data_repository

import com.example.core_model.TrackItem
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * プレーヤーの再生状態を定義するEnum
 */
enum class RepeatMode {
    OFF,    // リピートなし
    ONE,    // 1曲リピート
    ALL     // 全曲リピート
}

/**
 * アプリ全体で共有される現在の再生状態を保持するデータクラス。
 * このクラスのインスタンスがPlayerStateRepositoryのStateFlowを通じて公開される。
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: TrackItem? = null,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleEnabled: Boolean = false
)

/**
 * 再生状態をアプリ全体で共有するためのリポジトリ（単一の真実の源）。
 * このリポジトリはシングルトンとして提供され、どのViewModelからでも同じインスタンスを参照できる。
 *
 * 【重要】
 * - 書き込み操作（updateメソッド群）は、信頼できる唯一の情報源である `PlaybackService` からのみ呼び出すべき。
 * - 読み取り（playbackState Flow）は、UIの状態を更新する必要がある `BrowserViewModel` や `PlayerViewModel` などから行う。
 */
@Singleton
class PlayerStateRepository @Inject constructor() {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /**
     * 新しいトラックが再生開始（または準備）されたときに呼び出す。
     * 再生位置をリセットし、トラック情報と総再生時間を更新する。
     *
     * @param track 新しく再生するトラック。
     */
    fun updateCurrentTrack(track: TrackItem) {
        _playbackState.update {
            it.copy(
                currentTrack = track,
                totalDurationMs = track.durationMs,
                currentPositionMs = 0L
            )
        }
    }

    /**
     * 再生/一時停止の状態を更新する。
     */
    fun updateIsPlaying(isPlaying: Boolean) {
        _playbackState.update { it.copy(isPlaying = isPlaying) }
    }

    /**
     * 再生位置の進捗を更新する。
     * PlaybackServiceから定期的に呼び出されることを想定。
     */
    fun updatePosition(positionMs: Long) {
        // 現在の再生時間と異なる場合のみ更新して不要な再描画を防ぐ
        if (_playbackState.value.currentPositionMs != positionMs) {
            _playbackState.update { it.copy(currentPositionMs = positionMs) }
        }
    }

    /**
     * リピートモードを更新する。
     */
    fun updateRepeatMode(repeatMode: RepeatMode) {
        _playbackState.update { it.copy(repeatMode = repeatMode) }
    }

    /**
     * シャッフルモードを更新する。
     */
    fun updateShuffleMode(isEnabled: Boolean) {
        _playbackState.update { it.copy(isShuffleEnabled = isEnabled) }
    }

    /**
     * プレーヤーの状態を完全に初期状態にリセットする。
     * 再生が完全に停止したときや、アプリが終了するときに呼び出す。
     */
    fun reset() {
        _playbackState.value = PlaybackState()
    }
}