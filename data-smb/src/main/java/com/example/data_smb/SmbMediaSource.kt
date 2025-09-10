package com.example.data_smb

import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.core_model.FileItem
import com.example.core_model.FolderItem
import com.example.core_model.NasConnection
import com.example.core_model.TrackItem
import com.example.core_http.LocalHttpServer
import com.example.data_source.MediaSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtStatus
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Properties
import kotlinx.coroutines.CancellationException // ★★★ CancellationExceptionをインポート ★★★


// カスタム例外クラス (変更なし)
open class SmbAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SmbAuthenticationException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbHostNotFoundException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbShareNotFoundException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbPermissionException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbNetworkException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)

class SmbMediaSource @AssistedInject constructor(
    private val localHttpServer: LocalHttpServer, // Hiltが提供する依存関係
    @Assisted private val nasConnection: NasConnection // 実行時に外部から提供される依存関係
) : MediaSource {

    private val authContext: CIFSContext

    init {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
        }
        val config = PropertyConfiguration(properties)
        val baseContext = BaseContext(config)
        val auth = if (nasConnection.username.isNullOrEmpty()) {
            null
        } else {
            NtlmPasswordAuthenticator(null, nasConnection.username, nasConnection.password)
        }
        authContext = if (auth != null) {
            baseContext.withCredentials(auth)
        } else {
            baseContext
        }
    }

    override fun getItemsIn(folderPath: String?): Flow<FileItem> = flow {
        // folderPathがnullまたは空文字列の場合、ベースURLを構築する
        val currentSmbPath = if (folderPath.isNullOrEmpty()) {
            buildSmbUrl(nasConnection.path)
        } else {
            folderPath
        }

        try {
            val file = SmbFile(currentSmbPath, authContext)
            if (!file.exists() || !file.isDirectory) {
                return@flow // Flowを完了させる
            }

            // 1. まずは「..」フォルダを即座に発行
            val rootPathUrl = buildSmbUrl(nasConnection.path)
            if (file.canonicalPath.trimEnd('/') != rootPathUrl.trimEnd('/')) {
                val parentPath = file.parent
                if (parentPath != null && parentPath.length < file.canonicalPath.length && parentPath.startsWith("smb://")) {
                    emit(FolderItem(title = "..", path = parentPath, uri = parentPath))
                }
            }

            val files = file.listFiles().sortedBy { it.name.lowercase() }

            // 2. 次にフォルダリストをすべて発行
            files.forEach { smbFile ->
                if (smbFile.isDirectory) {
                    val fileName = smbFile.name.trimEnd('/')
                    val smbUri = smbFile.canonicalPath
                    emit(FolderItem(title = fileName, path = smbUri, uri = smbUri))
                }
            }

            // 3. 最後にトラックファイルを一つずつ発行
            files.forEach { smbFile ->
                val fileName = smbFile.name.trimEnd('/')
                if (fileName.endsWith(".mp3", ignoreCase = true)) {
                    val smbUri = smbFile.canonicalPath
                    val placeholderTrack = TrackItem(
                        title = fileName.substringBeforeLast('.'),
                        path = smbUri,
                        uri = smbUri,
                        artist = null,
                        albumId = null, album = null, artworkUri = null, durationMs = 0L
                    )
                    emit(placeholderTrack)
                }
            }
        } catch (e: SmbException) {
            e.printStackTrace()
            val ntStatus: Int = e.ntStatus
            when (ntStatus) {
                NtStatus.NT_STATUS_LOGON_FAILURE,
                NtStatus.NT_STATUS_WRONG_PASSWORD,
                NtStatus.NT_STATUS_ACCOUNT_DISABLED,
                NtStatus.NT_STATUS_PASSWORD_EXPIRED,
                NtStatus.NT_STATUS_ACCOUNT_RESTRICTION,
                NtStatus.NT_STATUS_ACCOUNT_LOCKED_OUT
                -> throw SmbAuthenticationException("NASへの認証に失敗しました。ユーザー名、パスワード、またはアカウントの状態を確認してください。", e)

                NtStatus.NT_STATUS_NO_SUCH_DEVICE,
                NtStatus.NT_STATUS_CONNECTION_REFUSED
                -> throw SmbHostNotFoundException("NASサーバーが見つからないか、到達できません。ホスト名、IPアドレス、ポート、またはプロトコルを確認してください。", e)

                NtStatus.NT_STATUS_BAD_NETWORK_NAME,
                NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND,
                NtStatus.NT_STATUS_NO_SUCH_FILE,
                NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,
                NtStatus.NT_STATUS_OBJECT_NAME_INVALID
                -> throw SmbShareNotFoundException("共有フォルダまたは指定されたパスが見つかりません。", e)

                NtStatus.NT_STATUS_ACCESS_DENIED
                -> throw SmbPermissionException("指定されたファイルまたはフォルダへのアクセス権がありません。", e)

                NtStatus.NT_STATUS_PIPE_NOT_AVAILABLE,
                NtStatus.NT_STATUS_PIPE_DISCONNECTED,
                NtStatus.NT_STATUS_PIPE_BROKEN,
                NtStatus.NT_STATUS_SHARING_VIOLATION
                -> throw SmbNetworkException("NASとの通信中にネットワークエラーが発生しました。", e)
                
                else -> {
                    val messageByCode = SmbException.getMessageByCode(ntStatus)
                    val displayMessage = if (!messageByCode.isNullOrEmpty() && messageByCode != "0x${Integer.toHexString(ntStatus)}") {
                        messageByCode
                    } else {
                        "NTステータスコード 0x${Integer.toHexString(ntStatus)} に対応するエラーが発生しました。"
                    }
                    System.err.println("Unhandled SmbException NT Status: 0x${Integer.toHexString(ntStatus)} - $displayMessage")
                    throw SmbAccessException("NASアクセスエラー: $displayMessage", e)
                }
            }
        } catch (e: CancellationException) { // ★★★ CancellationExceptionをキャッチして再スロー ★★★
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            throw SmbAccessException("予期せぬエラーが発生しました: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getTrackDetails(trackItem: TrackItem): TrackItem = withContext(Dispatchers.IO) {
        val smbFile = SmbFile(trackItem.path, authContext)
        val detailedTrack = parseTrackMetadata(smbFile)
        return@withContext detailedTrack
    }

    /**
     * MediaMetadataRetrieverを使ってSMBファイルのメタデータを解析する。
     * @param smbFile メタデータを解析する対象のSmbFile。
     * @return メタデータが設定されたTrackItem。取得に失敗した場合は部分的な情報のみ。
     */
    private fun parseTrackMetadata(smbFile: SmbFile): TrackItem {
        val fileName = smbFile.name.trimEnd('/')
        val smbUri = smbFile.canonicalPath
        Log.d("SmbMediaSource", "parseTrackMetadata: 開始 smbUri: $smbUri")
        val retriever = MediaMetadataRetriever()

        try {
            // LocalHttpServerの起動を保証
            localHttpServer.ensureStarted()
            Log.d("SmbMediaSource", "parseTrackMetadata: LocalHttpServer起動確認済み (getStreamingUrl直前)")

            val httpUrl = localHttpServer.getStreamingUrl(smbUri, nasConnection.id)
            Log.d("SmbMediaSource", "parseTrackMetadata: 生成されたhttpUrl: $httpUrl (smbUri: $smbUri)")

            Log.d("SmbMediaSource", "parseTrackMetadata: MediaMetadataRetrieverにデータソース設定開始 httpUrl: $httpUrl")
            retriever.setDataSource(httpUrl, HashMap<String, String>())
            Log.d("SmbMediaSource", "parseTrackMetadata: MediaMetadataRetrieverにデータソース設定成功 httpUrl: $httpUrl")

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            Log.d("SmbMediaSource", "parseTrackMetadata: メタデータ抽出完了 ($smbUri) -> Title: '$title', Artist: '$artist', Album: '$album', Duration: $durationMs ms")

            return TrackItem(
                title = title?.ifBlank { null } ?: fileName.substringBeforeLast('.'),
                path = smbUri,
                uri = smbUri,
                artist = artist?.ifBlank { null },
                album = album?.ifBlank { null },
                durationMs = durationMs,
                albumId = null,
                artworkUri = null
            )
        } catch (e: CancellationException) { // ★★★ CancellationExceptionをキャッチして再スロー ★★★
            throw e
        } catch (e: Exception) {
            Log.e("SmbMediaSource", "parseTrackMetadata: メタデータ取得失敗 smbUri: ${smbFile.path}. 例外: ${e.javaClass.simpleName} - ${e.message}", e)
            return TrackItem(
                title = fileName.substringBeforeLast('.'),
                path = smbUri, uri = smbUri,
                artist = null, albumId = null, album = null, artworkUri = null, durationMs = 0L
            )
        } finally {
            Log.d("SmbMediaSource", "parseTrackMetadata: MediaMetadataRetriever解放処理 smbUri: $smbUri")
            retriever.release()
        }
    }

    /**
     * NAS接続情報に基づいて、SMB共有へのベースURLを構築する。
     * @param sharePath NasConnectionで設定された共有パス (例: "music" や "Multimedia/Audio")。
     * @return 整形されたSMB URL (例: "smb://hostname/music/")。
     */
    private fun buildSmbUrl(sharePath: String): String {
        val normalizedPathPart = sharePath
            .trim('/')
            .replace(Regex("/{2,}"), "/")

        var smbUrl = "smb://${nasConnection.hostname}"
        if (normalizedPathPart.isNotEmpty()) {
            smbUrl += "/$normalizedPathPart"
        }
        if (!smbUrl.endsWith("/")) {
            smbUrl += "/"
        }
        return smbUrl
    }

    /**
     * SmbMediaSourceを生成するためのAssistedFactory。
     */
    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(nasConnection: NasConnection): SmbMediaSource
    }
}
