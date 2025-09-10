package com.example.core_model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
    override val path: String, // equals/hashCodeはデフォルトの動作に任せる
    override val uri: String,
    val artist: String?,
    val albumId: Long?,
    val album: String?,
    val artworkUri: String?,
    val durationMs: Long
) : FileItem, Parcelable
// equals と hashCode のオーバーライドを削除