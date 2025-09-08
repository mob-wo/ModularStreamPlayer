package com.example.data_smb

import android.util.Log
import com.example.core_model.FileItem
import com.example.core_model.FolderItem
import com.example.core_model.NasConnection
import com.example.core_model.TrackItem
import com.example.data_source.MediaSource
import com.mpatric.mp3agic.Mp3File
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtStatus
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.io.InputStream
import java.util.Properties


// カスタム例外クラス (変更なし)
open class SmbAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SmbAuthenticationException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbHostNotFoundException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbShareNotFoundException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbPermissionException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)
class SmbNetworkException(message: String, cause: Throwable? = null) : SmbAccessException(message, cause)

class SmbMediaSource(
    private val nasConnection: NasConnection
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
        // 修正箇所: folderPathがnullまたは空文字列の場合、ベースURLを構築する
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
            // SMBルート自身でない場合のみ「..」を追加
            if (file.canonicalPath.trimEnd('/') != rootPathUrl.trimEnd('/')) {
                val parentPath = file.parent // 親パスを取得
                // 親パスがnullでなく、かつSMBルートより深い階層にある場合のみ「..」を追加
                // SmbFile.getParent() は "smb://host/share/" の場合 "smb://host/" を返すことがあるため、
                // 単純なnullチェックだけでは不十分な場合がある。ここでは、SMBルートとの比較で代替。
                if (parentPath != null && parentPath.length < file.canonicalPath.length && parentPath.startsWith("smb://")) {
                    emit(FolderItem(title = "..", path = parentPath, uri = parentPath))
                }
            }

            val files = file.listFiles().sortedBy { it.name.lowercase() }

            // 2. 次にフォルダリストをすべて発行（メタデータ不要なので高速）
            files.forEach { smbFile ->
                if (smbFile.isDirectory) {
                    val fileName = smbFile.name.trimEnd('/')
                    val smbUri = smbFile.canonicalPath
                    emit(FolderItem(title = fileName, path = smbUri, uri = smbUri))
                }
            }

            // 3. 最後にトラックファイルを一つずつ解析して発行（ここが時間のかかる処理）
            files.forEach { smbFile ->
                val fileName = smbFile.name.trimEnd('/')
                if (fileName.endsWith(".mp3", ignoreCase = true)) {
                    val smbUri = smbFile.canonicalPath
                    // まずはファイル名だけのプレースホルダを即座に発行
                    val placeholderTrack = TrackItem(
                        title = fileName.substringBeforeLast('.'),
                        path = smbUri,
                        uri = smbUri,
                        artist = "読み込み中...", // プレースホルダーテキスト
                        albumId = null, album = null, artworkUri = null, durationMs = 0L
                    )
                    emit(placeholderTrack)

                    // ↓↓↓ ここからが本来のメタデータ解析処理 ↓↓↓
                    // ただし、このままだとUIが待たされるので、次のセクションで改善します
                }
            }
        } catch (e: SmbException) {
            e.printStackTrace()
            val ntStatus: Int = e.ntStatus
            // jcifs-ng 2.1.7 の jcifs.smb.NtStatus で定義されている定数のみを使用
            when (ntStatus) {
                // Authentication Errors
                NtStatus.NT_STATUS_LOGON_FAILURE,
                NtStatus.NT_STATUS_WRONG_PASSWORD,
                NtStatus.NT_STATUS_ACCOUNT_DISABLED,
                NtStatus.NT_STATUS_PASSWORD_EXPIRED,
                NtStatus.NT_STATUS_ACCOUNT_RESTRICTION, // '''S''' がつくのが正しい
                NtStatus.NT_STATUS_ACCOUNT_LOCKED_OUT    // Javadoc 2.1.7 に定義あり
                -> throw SmbAuthenticationException("NASへの認証に失敗しました。ユーザー名、パスワード、またはアカウントの状態を確認してください。", e)

                // Host Not Found / Unreachable Errors
                NtStatus.NT_STATUS_NO_SUCH_DEVICE,         // 0xC000000E (ホスト名間違いの可能性含む)
                NtStatus.NT_STATUS_CONNECTION_REFUSED      // Javadoc 2.1.7 に定義あり
                -> throw SmbHostNotFoundException("NASサーバーが見つからないか、到達できません。ホスト名、IPアドレス、ポート、またはプロトコルを確認してください。", e)

                // Share or Path Not Found Errors
                NtStatus.NT_STATUS_BAD_NETWORK_NAME,
                NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND,
                NtStatus.NT_STATUS_NO_SUCH_FILE,           // Javadoc 2.1.7 に定義あり
                NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,  // Javadoc 2.1.7 に定義あり
                NtStatus.NT_STATUS_OBJECT_NAME_INVALID     // Javadoc 2.1.7 に定義あり (パス形式不正など)
                -> throw SmbShareNotFoundException("共有フォルダまたは指定されたパスが見つかりません。", e)

                // Permission Errors
                NtStatus.NT_STATUS_ACCESS_DENIED
                -> throw SmbPermissionException("指定されたファイルまたはフォルダへのアクセス権がありません。", e)

                // Specific Network Issues
                NtStatus.NT_STATUS_PIPE_NOT_AVAILABLE,     // 0xC00000AE
                NtStatus.NT_STATUS_PIPE_DISCONNECTED,      // 0xC00000B1
                NtStatus.NT_STATUS_PIPE_BROKEN,            // 0xC00000B0
                NtStatus.NT_STATUS_SHARING_VIOLATION,      // Javadoc 2.1.7 に定義あり (ファイル使用中など)
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
        } catch (e: Exception) {
            e.printStackTrace()
            throw SmbAccessException("予期せぬエラーが発生しました: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO) // Flow全体の処理をIOスレッドで実行

    // メタデータ解析のヘルパー関数
    /*private fun parseTrackMetadata(smbFile: SmbFile): TrackItem {
        val fileName = smbFile.name.trimEnd('/')
        val smbUri = smbFile.canonicalPath
        var inputStream: InputStream? = null

        try {
            inputStream = SmbFileInputStream(smbFile)

            // Mp3FileのコンストラクタにInputStreamとファイル長を渡す
            // 第2引数にファイル長を渡すと、ストリームの末尾にあるID3v1タグも読み取ろうと試みる
            val mp3file = Mp3File(inputStream, smbFile.length())

            val title: String?
            val artist: String?
            val album: String?

            if (mp3file.hasId3v2Tag()) {
                val id3v2Tag = mp3file.id3v2Tag
                title = id3v2Tag.title
                artist = id3v2Tag.artist
                album = id3v2Tag.album
            } else if (mp3file.hasId3v1Tag()) {
                val id3v1Tag = mp3file.id3v1Tag
                title = id3v1Tag.title
                artist = id3v1Tag.artist
                album = id3v1Tag.album
            } else {
                title = null
                artist = null
                album = null
            }

            return TrackItem(
                title = title?.ifBlank { null } ?: fileName.substringBeforeLast('.'),
                path = smbUri,
                uri = smbUri,
                artist = artist?.ifBlank { null },
                album = album?.ifBlank { null },
                durationMs = mp3file.lengthInMilliseconds,
                albumId = null,
                artworkUri = null
            )

        } catch (e: Exception) {
            // com.mpatric.mp3agic.InvalidDataException などもここでキャッチ
            Log.e("SmbMediaSource", "Failed to parse metadata for: ${smbFile.path}", e)
            return TrackItem(
                title = fileName.substringBeforeLast('.'),
                path = smbUri, uri = smbUri,
                artist = null, albumId = null, album = null, artworkUri = null, durationMs = 0L
            )
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                Log.e("SmbMediaSource", "Failed to close input stream", e)
            }
        }
    }*/

    private fun buildSmbUrl(sharePath: String): String {
        // sharePath (nasConnection.pathから渡される) を正規化:
        // 先頭/末尾のスラッシュを除去し、内部の連続するスラッシュを1つにまとめる
        val normalizedPathPart = sharePath
            .trim('/')                         // 例: "myshare/sub" や "myshare"
            .replace(Regex("/{2,}"), "/")      // 例: "myshare/sub" や "myshare"

        var smbUrl = "smb://${nasConnection.hostname}"

        // 正規化されたパス部分が空でなければ、ホスト名に続けて追加
        if (normalizedPathPart.isNotEmpty()) {
            smbUrl += "/$normalizedPathPart"  // 例: "smb://server/myshare/sub" または "smb://server/myshare"
        }

        // URLが必ず末尾スラッシュで終わるようにする
        if (!smbUrl.endsWith("/")) {
            smbUrl += "/"                     // 例: "smb://server/myshare/sub/" または "smb://server/myshare/"
        }
        return smbUrl
    }
}
