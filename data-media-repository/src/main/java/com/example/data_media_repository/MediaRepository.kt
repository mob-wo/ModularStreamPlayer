package com.example.data_media_repository

import com.example.core_model.FileItem
import com.example.core_model.NasConnection
import com.example.core_model.TrackItem // TrackItem をインポート
import com.example.data_media_repository.di.LocalSource
import com.example.data_repository.ActiveDataSource
import com.example.data_repository.NasCredentialsRepository
import com.example.data_repository.SettingsRepository
import com.example.data_smb.SmbMediaSource
import com.example.data_source.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext // withContext をインポート
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @LocalSource private val localMediaSource: MediaSource,
    private val settingsRepository: SettingsRepository,
    private val nasCredentialsRepository: NasCredentialsRepository,
    private val smbMediaSourceFactory: SmbMediaSource.Factory
) {

    /**
     * 現在アクティブなデータソースの MediaSource インスタンスを取得する。
     * このメソッドは suspend 関数なので、コルーチン内から呼び出す必要がある。
     */
    private suspend fun getCurrentActiveMediaSource(): MediaSource {
        val activeDataSource = settingsRepository.activeDataSource.first()
        return when (activeDataSource) {
            is ActiveDataSource.Local -> {
                localMediaSource
            }
            is ActiveDataSource.Smb -> {
                val connection =
                    nasCredentialsRepository.getConnectionById(activeDataSource.connectionId)
                        ?: throw IllegalStateException("SMB connection with ID ${activeDataSource.connectionId} not found.")
                smbMediaSourceFactory.create(connection)
            }
        }
    }

    /**
     * 現在アクティブなデータソースから、指定されたパスのアイテムリストのFlowを取得する。
     *
     * @param folderPath 閲覧するフォルダのパス。
     * @return FileItemのFlow。
     * @throws IllegalStateException SMBが選択されているが、有効な接続情報が見つからない場合。
     *         Flowの収集時にこれらの例外が発生する可能性があります。
     */
    fun getItemsIn(folderPath: String?): Flow<FileItem> = flow {
        val currentSource = getCurrentActiveMediaSource()
        emitAll(currentSource.getItemsIn(folderPath))
    }.flowOn(Dispatchers.IO) // このFlowのアップストリーム処理(データソース決定と実際のI/O)をIOスレッドで実行

    /**
     * 現在アクティブなデータソースから、指定されたトラックアイテムの詳細情報を取得する。
     *
     * @param trackItem 詳細情報を取得したいトラックアイテム。
     * @return 詳細情報が設定されたTrackItem。
     * @throws IllegalStateException SMBが選択されているが、有効な接続情報が見つからない場合など。
     */
    suspend fun getTrackDetails(trackItem: TrackItem): TrackItem = withContext(Dispatchers.IO) {
        val currentSource = getCurrentActiveMediaSource()
        currentSource.getTrackDetails(trackItem)
    }
}