package com.example.data_repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton


/**
 * プレーヤーがアクティブかどうか（再生中または一時停止中）をアプリ全体で共有するための
 * シンプルな状態ホルダー。主にPlaybackServiceのライフサイクル管理に使用。
 */
@Singleton
class PlayerStateRepository @Inject constructor() {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun setPlaying(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    fun reset() {
        _isPlaying.value = false
    }
}