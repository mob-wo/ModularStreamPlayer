package com.example.data_local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.core_model.FileItem
import com.example.core_model.FolderItem
import com.example.core_model.TrackItem
import com.example.data_source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.withContext // 不要になるためコメントアウトまたは削除
import kotlinx.coroutines.flow.Flow // 追加
import kotlinx.coroutines.flow.flow // 追加
import kotlinx.coroutines.flow.flowOn // 追加
import java.io.File
import javax.inject.Inject

class LocalMediaSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaSource {

    private val contentResolver: ContentResolver = context.contentResolver

    //private val rootPath = "/storage/emulated/0"
    private val rootPath = "/storage/emulated/0/Music"

    override fun getItemsIn(folderPath: String?): Flow<FileItem> = flow { // suspendを削除し、戻り値をFlow<FileItem>に変更
        val currentPath = folderPath ?: rootPath
        val parentPath = File(currentPath).parent
        // val items = mutableListOf<FileItem>() // MutableListは不要に

        // 1. 親フォルダへのナビゲーションをemit
        if (currentPath != rootPath && parentPath != null) {
            emit(FolderItem(title = "..", path = parentPath, uri = "folder://$parentPath"))
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
        // 注意: selectionArgsは currentPath の直下のみを検索するようにするべき
        // 元のコード "$currentPath/%" はフォルダ内のサブフォルダも検索対象に含めてしまう可能性がある
        // MediaStoreはフラットなリストを返すため、サブフォルダは別途処理する
        val directChildrenSelectionArgs = arrayOf("$currentPath/%")
        // サブフォルダ内のファイルを除外するためのより正確なクエリ (ただし、今回は元のロジックを維持し、後でトラックとフォルダを分ける)
        // val directChildrenSelectionArgs = arrayOf("$currentPath/%", "$currentPath/%/%_UP")
        // val selection = "${MediaStore.Audio.Media.DATA} LIKE ? AND ${MediaStore.Audio.Media.DATA} NOT LIKE ?"


        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection, // ここは元の "$currentPath/%" を使う
            directChildrenSelectionArgs,
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
                // MP3ファイルのみを対象 (これはここで良い)
                if (!path.endsWith(".mp3", ignoreCase = true)) continue

                val relativePath = path.removePrefix("$currentPath/").trimStart('/') // "song1.mp3" or "AlbumA/song2.mp3"

                if (relativePath.contains('/')) {
                    // remainingPath にスラッシュが含まれる => ファイルはサブフォルダ内にある
                    // 例: relativePath = "AlbumA/song2.mp3"
                    val subFolderName = relativePath.substringBefore('/') // "AlbumA"
                    // フルパスでサブフォルダを記録
                    subFolders.add("$currentPath/$subFolderName")
                } else {
                    // remainingPath にスラッシュが含まれない => ファイルは currentPath 直下にある
                    // 例: relativePath = "song1.mp3"
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn)
                    val artist = it.getString(artistColumn)
                    val album = it.getString(albumColumn)
                    val albumId = it.getLong(albumIdColumn)
                    val duration = it.getLong(durationColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

                    emit(TrackItem( // TrackItemをemit
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

        // 3. サブフォルダをemit (重複を除いたものをソートして)
        subFolders.sorted().forEach { subFolderPath ->
            val folderName = File(subFolderPath).name
            emit(FolderItem(title = folderName, path = subFolderPath, uri = "folder://$subFolderPath"))
        }

        // itemsリストを返す代わりに、emitで各アイテムを発行したので、ここでのreturnは不要
    }.flowOn(Dispatchers.IO) // このFlowの処理(ContentResolverクエリなど)をIOスレッドで実行

    /**
     * LocalMediaSourceでは、getItemsInの時点で既に全詳細情報が取得済み。
     * そのため、この関数は渡されたアイテムをそのまま返すだけで良い。
     */
    override suspend fun getTrackDetails(trackItem: TrackItem): TrackItem {
        // 何もする必要がないので、引数をそのまま返す
        return trackItem
    }
}