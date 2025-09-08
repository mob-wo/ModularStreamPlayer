package com.example.data_repository

import com.example.core_model.FileItem
import com.example.core_model.TrackItem
import com.example.data_repository.ActiveDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UIからの再生要求を表現するデータクラス。
 *
 * @param path 再生要求が発生したフォルダのパス。
 * @param itemList そのフォルダに表示されていた全アイテムのリスト。
 * @param currentItem ユーザーが実際にクリックしたトラック。
 * @param dataSource この再生要求がどのデータソースで発生したか。
 * @param requestId 要求を一意に識別するためのID。クリックのたびに更新される。
 */
data class PlaybackRequest(
    val path: String,
    val itemList: List<FileItem>,
    val currentItem: TrackItem,
    val dataSource: ActiveDataSource,
    val requestId: String = UUID.randomUUID().toString()
)

/**
 * UIからの再生要求を一時的に保持し、関係するViewModelに伝達するためのリポジトリ。
 * BrowserViewModelが書き込み、PlayerViewModelが読み取る。
 */
@Singleton
class PlaybackRequestRepository @Inject constructor() {

    // 初期値はnullとし、新しい要求がない状態を示す
    private val _playbackRequest = MutableStateFlow<PlaybackRequest?>(null)
    val playbackRequest: StateFlow<PlaybackRequest?> = _playbackRequest.asStateFlow()

    /**
     * 新しい再生要求を発行する。
     * BrowserViewModelから呼び出される。
     */
    fun requestPlayback(
        path: String,
        itemList: List<FileItem>,
        currentItem: TrackItem,
        dataSource: ActiveDataSource
    ) {
        // requestIdを更新するために新しいインスタンスを生成してセットする
        _playbackRequest.value = PlaybackRequest(
            path = path,
            itemList = itemList.map { item -> if (item is TrackItem) item.copy() else item }, // 安全のためTrackItemはコピー
            currentItem = currentItem.copy(),
            dataSource = dataSource
        )
    }

    /**
     * 現在の再生要求を処理済みとしてクリアする。
     * PlayerViewModelが再生処理を開始した後に呼び出す。
     * これにより、同じ要求が再度処理されるのを防ぐ。
     */
    fun consumePlaybackRequest() {
        _playbackRequest.value = null
    }
}