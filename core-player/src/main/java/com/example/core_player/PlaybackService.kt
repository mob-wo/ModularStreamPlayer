package com.example.core_player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.data_repository.PlayerStateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var player: ExoPlayer
    @Inject lateinit var playerStateRepository: PlayerStateRepository

    private lateinit var mediaSession: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        val callback = object : MediaLibrarySession.Callback {}
        // MediaSessionのコールバックは、もしカスタムコマンドがなければ空でも良い
        mediaSession = MediaLibrarySession.Builder(this, player, callback).build()

        // プレーヤーの状態変更をリポジトリに通知するリスナー
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerStateRepository.setPlaying(isPlaying)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    // アプリがスワイプで閉じられた際、再生中でなければサービスも終了する
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // サービス終了時にリポジトリの状態をリセット
        playerStateRepository.reset()
        player.release()
        mediaSession.release()
        super.onDestroy()
    }
}