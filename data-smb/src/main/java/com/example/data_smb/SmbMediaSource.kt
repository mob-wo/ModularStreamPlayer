package com.example.data_smb

import com.example.core_model.FileItem
import com.example.core_model.FolderItem
import com.example.core_model.NasConnection
import com.example.core_model.TrackItem
import com.example.data_source.MediaSource
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.NtStatus // 正しいインポート jcifs.smb.NtStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override suspend fun getItemsIn(folderPath: String?): List<FileItem> = withContext(Dispatchers.IO) {
        // 修正箇所: folderPathがnullまたは空文字列の場合、ベースURLを構築する
        val currentSmbPath = if (folderPath.isNullOrEmpty()) {
            buildSmbUrl(nasConnection.path)
        } else {
            folderPath
        }

        try {
            val file = SmbFile(currentSmbPath, authContext)
            if (!file.exists() || !file.isDirectory) {
                return@withContext emptyList()
            }
            val items = mutableListOf<FileItem>()
            val rootPathUrl = buildSmbUrl(nasConnection.path)
            // SMBルート自身でない場合のみ「..」を追加
            if (file.canonicalPath.trimEnd('/') != rootPathUrl.trimEnd('/')) {
                val parentPath = file.parent // 親パスを取得
                // 親パスがnullでなく、かつSMBルートより深い階層にある場合のみ「..」を追加
                // SmbFile.getParent() は "smb://host/share/" の場合 "smb://host/" を返すことがあるため、
                // 単純なnullチェックだけでは不十分な場合がある。ここでは、SMBルートとの比較で代替。
                if (parentPath != null && parentPath.length < file.canonicalPath.length && parentPath.startsWith("smb://")) {
                     items.add(FolderItem(title = "..", path = parentPath, uri = parentPath))
                }
            }
            val files = file.listFiles().sortedBy { it.name.lowercase() }
            val folders = mutableListOf<FolderItem>()
            val tracks = mutableListOf<TrackItem>()

            files.forEach { smbFile ->
                val fileName = smbFile.name.trimEnd('/')
                val smbUri = smbFile.canonicalPath
                when {
                    smbFile.isDirectory -> {
                        folders.add(FolderItem(title = fileName, path = smbUri, uri = smbUri))
                    }
                    fileName.endsWith(".mp3", ignoreCase = true) -> {
                        tracks.add(TrackItem(
                            title = fileName.substringBeforeLast('.'),
                            path = smbUri,
                            uri = smbUri,
                            artist = null, albumId = null, album = null, artworkUri = null, durationMs = 0L
                        ))
                    }
                }
            }
            items.addAll(folders)
            items.addAll(tracks)
            items
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
    }

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
