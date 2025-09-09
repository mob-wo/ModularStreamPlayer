package com.example.data_media_repository

import com.example.core_model.FileItem
import com.example.core_model.NasConnection
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
     * 現在アクティブなデータソースから、指定されたパスのアイテムリストのFlowを取得する。
     *
     * @param folderPath 閲覧するフォルダのパス。
     * @return FileItemのFlow。
     * @throws IllegalStateException SMBが選択されているが、有効な接続情報が見つからない場合。
     *         Flowの収集時にこれらの例外が発生する可能性があります。
     */
    fun getItemsIn(folderPath: String?): Flow<FileItem> = flow { // suspendを外し、戻り値をFlow<FileItem>に変更
        // 現在アクティブなデータソース設定を取得 (これはsuspendなのでflowブロック内で行う)
        val activeDataSource = settingsRepository.activeDataSource.first()

        val currentSource: MediaSource = when (activeDataSource) {
            is ActiveDataSource.Local -> {
                // ローカルストレージが選択されている場合
                localMediaSource
            }

            is ActiveDataSource.Smb -> {
                // SMB (NAS) が選択されている場合
                // 1. 接続IDから接続情報を取得 (これもsuspend)
                val connection =
                    nasCredentialsRepository.getConnectionById(activeDataSource.connectionId)
                        ?: throw IllegalStateException("SMB connection with ID ${activeDataSource.connectionId} not found.")

                // 2. 接続情報を使ってSmbMediaSourceを動的に生成
                smbMediaSourceFactory.create(connection)
            }
        }

        // 決定したデータソースのgetItemsIn (Flow<FileItem>を返すと期待) から全てemitする
        emitAll(currentSource.getItemsIn(folderPath))
    }.flowOn(Dispatchers.IO) // このFlowのアップストリーム処理(データソース決定と実際のI/O)をIOスレッドで実行
}