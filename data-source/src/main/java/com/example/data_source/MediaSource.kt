package com.example.data_source

import coil.ImageLoader
import com.example.core_model.MediaItem

interface MediaSource {
    /** 指定されたパス（フォルダURI）直下のアイテムリストを取得する */
    suspend fun getItemsIn(folderPath: String?): List<MediaItem>

    /** Coilでアートワークを読み込むためのカスタムFetcherを登録する */
    //fun registerCoilExtensions(coilRegistry: ImageLoader.Builder)
}