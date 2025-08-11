package com.example.core_player.mapper

import android.net.Uri
import androidx.core.os.bundleOf
import com.example.core_model.TrackItem
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata


/**
 * Media3のMediaItemを、アプリのドメインモデルであるTrackItemに変換する拡張関数。
 * PlaybackService内でプレーヤーからのイベントを処理する際に使用される。
 */
fun MediaItem.toTrackItem(): TrackItem? {
    // mediaId (URI) がないと識別できないため、nullを返す
    if (this.mediaId.isEmpty()) {
        return null
    }

    // MediaMetadataから各プロパティを取得
    val metadata = this.mediaMetadata

    return TrackItem(
        // ★必須項目
        uri = this.mediaId,
        path = metadata.extras?.getString(METADATA_KEY_PATH) ?: this.mediaId,
        title = metadata.title?.toString() ?: "不明な曲",

        // ★オプション項目
        artist = metadata.artist?.toString(),
        album = metadata.albumTitle?.toString(),
        artworkUri = metadata.artworkUri?.toString(),
        albumId = null,
        durationMs = 0L
    )
}

/**
 * アプリのドメインモデルであるTrackItemを、Media3が扱えるMediaItemに変換する拡張関数。
 * UI側(ViewModel)からPlaybackServiceに再生を指示する際に使用される。
 */
fun TrackItem.toMediaItem(): MediaItem {
    // MediaItemに格納する追加情報（元のファイルパスなど）
    val extras = bundleOf(
        METADATA_KEY_PATH to this.path
    )

    val metadata = MediaMetadata.Builder()
        .setTitle(this.title)
        .setArtist(this.artist)
        .setAlbumTitle(this.album)
        .setArtworkUri(this.artworkUri?.let { Uri.parse(it) })
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(this.uri) // URIを一意なIDとして設定
        .setMediaMetadata(metadata)
        .build()
}

// MediaMetadataのextrasにデータを格納するためのキー
const val METADATA_KEY_PATH = "com.example.modularstreamplayer.METADATA_KEY_PATH"