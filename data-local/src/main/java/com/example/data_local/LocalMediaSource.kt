package com.example.data_local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.core_model.FolderItem
import com.example.core_model.MediaItem
import com.example.core_model.TrackItem
import com.example.data_source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class LocalMediaSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaSource {

    private val contentResolver: ContentResolver = context.contentResolver

    //private val rootPath = "/storage/emulated/0"
    private val rootPath = "/storage/emulated/0/Music"
    override suspend fun getItemsIn(folderPath: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val currentPath = folderPath ?: rootPath
        val parentPath = File(currentPath).parent
        val items = mutableListOf<MediaItem>()

        // 1. 親フォルダへのナビゲーションを追加
        if (currentPath != rootPath && parentPath != null) {
            items.add(FolderItem(title = "..", path = parentPath, uri = "folder://$parentPath"))
        }

        // 2. ContentResolverを使用してMP3ファイルを取得
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA, // file path
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )

        // 指定されたフォルダパスでフィルタリング
        val selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$currentPath/%")

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        val subFolders = mutableSetOf<String>()

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val path = it.getString(pathColumn)
                // MP3ファイルのみを対象
                if (!path.endsWith(".mp3", ignoreCase = true)) continue

                val remainingPath = path.removePrefix("$currentPath/") // 例: "song1.mp3" または "AlbumA/song2.mp3"

                if (remainingPath.contains('/')) {
                    // remainingPath にスラッシュが含まれる => ファイルはサブフォルダ内にある
                    // 例: remainingPath = "AlbumA/song2.mp3"
                    val subFolderName = remainingPath.substringBefore('/') // "AlbumA"
                    subFolders.add("$currentPath/$subFolderName")
                } else {
                    // remainingPath にスラッシュが含まれない => ファイルは currentPath 直下にある
                    // 例: remainingPath = "song1.mp3"
                    // ... TrackItem を作成する処理 ...
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn)
                    val artist = it.getString(artistColumn)
                    val album = it.getString(albumColumn)
                    val albumId = it.getLong(albumIdColumn)
                    val duration = it.getLong(durationColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

                    items.add(TrackItem(
                        title = title,
                        path = path,
                        uri = uri.toString(),
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        artworkUri = artworkUri.toString(),
                        durationMs = duration
                    ))
                }
            }
        }

        // 3. サブフォルダを追加
        subFolders.sorted().forEach { subFolderPath ->
            val folderName = File(subFolderPath).name
            items.add(FolderItem(title = folderName, path = subFolderPath, uri = "folder://$subFolderPath"))
        }

        items
    }
}