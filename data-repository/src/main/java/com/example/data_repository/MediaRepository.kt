package com.example.data_repository

import com.example.core_model.FileItem
// import com.example.data_local.LocalMediaSource // これは@LocalSourceで MediaSource として注入されるので、直接参照は不要かもしれません
import com.example.data_repository.di.LocalSource
import com.example.data_smb.SmbAccessException
import com.example.data_smb.SmbMediaSource
import com.example.data_source.MediaSource
import kotlinx.coroutines.Dispatchers // 追加
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext // 追加
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @LocalSource private val localMediaSource: MediaSource,
    private val settingsRepository: SettingsRepository,
    private val nasCredentialsRepository: NasCredentialsRepository
) {
    /**
     * 現在アクティブなデータソースから、指定されたパスのアイテムリストを取得する。
     *
     * @param folderPath 閲覧するフォルダのパス。
     * @return FileItemのリスト。
     * @throws IllegalStateException SMBが選択されているが、有効な接続情報が見つからない場合。
     * @throws SmbAccessException SMBサーバーへのアクセスでエラーが発生した場合。
     */
    suspend fun getItemsIn(folderPath: String?): List<FileItem> {
        // 現在アクティブなデータソース設定を取得
        val activeDataSource = settingsRepository.activeDataSource.first()

        // ネットワーク操作の可能性がある処理をIOスレッドで実行
        return withContext(Dispatchers.IO) { // 変更箇所：withContextでラップ
            val currentSource: MediaSource = when (activeDataSource) {
                is ActiveDataSource.Local -> {
                    // ローカルストレージが選択されている場合
                    localMediaSource
                }
                is ActiveDataSource.Smb -> {
                    // SMB (NAS) が選択されている場合
                    // 1. 接続IDから接続情報を取得
                    val connection = nasCredentialsRepository.getConnectionById(activeDataSource.connectionId)
                        ?: throw IllegalStateException("SMB connection with ID ${activeDataSource.connectionId} not found.")

                    // 2. 接続情報を使ってSmbMediaSourceを動的に生成
                    SmbMediaSource(connection) // このコンストラクタ自体がネットワーク処理をしていなくても、
                                               // 後続のgetItemsInがネットワーク処理をするため、全体をIOスレッドに移すのが安全
                }
            }

            // 決定したデータソースのgetItemsInを呼び出す
            currentSource.getItemsIn(folderPath) // この呼び出しもIOスレッドで実行される
        }
    }
}
