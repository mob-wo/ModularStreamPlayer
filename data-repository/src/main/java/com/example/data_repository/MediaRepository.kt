package com.example.data_repository

import javax.inject.Inject
import com.example.core_model.FileItem
import com.example.data_source.MediaSource
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val localMediaSource: MediaSource  // DIで具象クラスを注入
) {
    suspend fun getItemsIn(folderPath: String?): List<FileItem> {
        // 現状はローカルのみだが、将来的にはここでメディアソースを切り替える
        return localMediaSource.getItemsIn(folderPath)
    }
}