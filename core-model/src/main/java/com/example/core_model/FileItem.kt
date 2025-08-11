package com.example.core_model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// MediaItem.kt
sealed interface FileItem {
    val title: String
    val path: String
    val uri: String // 再生や識別に使うURI形式のID
}

@Parcelize
data class FolderItem(
    override val title: String,
    override val path: String,
    override val uri: String
) : FileItem, Parcelable

@Parcelize
data class TrackItem(
    override val title: String,
    override val path: String,
    override val uri: String,
    val artist: String?,
    val albumId: Long?, // これは後で使う
    val album: String?,
    val artworkUri: String?, // Coilで読み込む用のアートワークURI
    val durationMs: Long
) : FileItem, Parcelable