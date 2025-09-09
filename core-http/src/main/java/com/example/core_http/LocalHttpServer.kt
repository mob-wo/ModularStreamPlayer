package com.example.core_http

import android.util.Log
import com.example.core_model.NasConnection
import com.example.data_repository.NasCredentialsRepository
import fi.iki.elonen.NanoHTTPD
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalHttpServer @Inject constructor(
    private val nasCredentialsRepository: NasCredentialsRepository
) : NanoHTTPD(DEFAULT_PORT) {

    companion object {
        const val DEFAULT_PORT = 8080
        private const val TAG = "LocalHttpServer"
        private const val STREAM_PREFIX = "/stream/"
    }

    /**
     * SMBパスをエンコードして、このサーバーで再生可能なHTTP URLに変換する。
     *
     * @param smbPath `smb://...` 形式のフルパス。
     * @param connectionId このSMBパスにアクセスするための認証情報を持つNasConnectionのID。
     * @return `http://127.0.0.1:8080/stream/{connectionId}/{encoded_smb_path}` 形式のURL。
     */
    fun getStreamingUrl(smbPath: String, connectionId: String): String {
        // smb://プレフィックスを除去してエンコード
        val encodedPath = smbPath.removePrefix("smb://")
        return "http://127.0.0.1:$DEFAULT_PORT$STREAM_PREFIX$connectionId/$encodedPath"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "Received request for URI: $uri")

        // URIが `/stream/` から始まるリクエストのみを処理
        if (!uri.startsWith(STREAM_PREFIX)) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }

        try {
            // URIから接続IDとSMBパスを抽出
            val (connectionId, smbPath) = parseUri(uri)
            Log.d(TAG, "Parsed connectionId: $connectionId, smbPath: $smbPath")

            // 接続IDを使って認証情報を取得
            // runBlockingは慎重に使うべきだが、ここではサーバーのスレッドをブロックして
            // 同期的に認証情報を取得する必要があるため許容する。
            val connection = nasCredentialsRepository.getConnectionById(connectionId)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Connection info not found")

            // SMBファイルストリームを取得
            val (inputStream, fileSize) = getSmbInputStream(smbPath, connection)

            // HTTPレスポンスとしてストリームを返す
            // MIMEタイプは "audio/mpeg" に固定（MP3再生を想定）
            val mimeType = "audio/mpeg"
            Log.d(TAG, "Serving content of size: $fileSize, mime: $mimeType")
            return newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, fileSize)

        } catch (e: Exception) {
            Log.e(TAG, "Error serving request", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    /**
     * リクエストURIから接続IDとSMBパスをデコードして取り出す。
     * 例: /stream/{connectionId}/{hostname}/{share}/{...}/{file.mp3}
     */
    private fun parseUri(uri: String): Pair<String, String> {
        val pathPart = uri.substringAfter(STREAM_PREFIX)
        val connectionId = pathPart.substringBefore('/')
        val encodedSmbPath = pathPart.substringAfter('/')
        // URLデコードは不要。jcifs-ngが適切に処理してくれる
        val smbPath = "smb://$encodedSmbPath"
        return Pair(connectionId, smbPath)
    }

    /**
     * SMBパスと認証情報を使って、ファイルのInputStreamとファイルサイズを取得する。
     */
    private fun getSmbInputStream(smbPath: String, connection: NasConnection): Pair<InputStream, Long> {
        // SmbMediaSourceと同様のjcifs-ngコンテキスト設定
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val config = PropertyConfiguration(properties)
        val baseContext = BaseContext(config)

        val auth = if (connection.username.isNullOrEmpty()) null else {
            NtlmPasswordAuthenticator(null, connection.username, connection.password)
        }

        val authContext = auth?.let { baseContext.withCredentials(it) } ?: baseContext

        val smbFile = SmbFile(smbPath, authContext)
        val fileSize = smbFile.length()
        val inputStream = smbFile.inputStream

        return Pair(inputStream, fileSize)
    }
}