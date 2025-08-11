package com.example.data_source

import com.example.core_model.FileItem

interface MediaSource {
    /** 指定されたパス（フォルダURI）直下のアイテムリストを取得する */
    suspend fun getItemsIn(folderPath: String?): List<FileItem>

    /** Coilでアートワークを読み込むためのカスタムFetcherを登録する */
    //fun registerCoilExtensions(coilRegistry: ImageLoader.Builder)
}