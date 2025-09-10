package com.example.data_source

import com.example.core_model.FileItem
import com.example.core_model.TrackItem
import kotlinx.coroutines.flow.Flow

interface MediaSource {
    /** 指定されたパス（フォルダURI）直下のアイテムリストを取得する */
    fun getItemsIn(folderPath: String?): Flow<FileItem>

    suspend fun getTrackDetails(trackItem: TrackItem): TrackItem

    /** Coilでアートワークを読み込むためのカスタムFetcherを登録する */
    //fun registerCoilExtensions(coilRegistry: ImageLoader.Builder)
}