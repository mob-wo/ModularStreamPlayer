package com.example.core_http

import android.util.Log
import com.example.core_model.NasConnection
import com.example.data_repository.NasCredentialsRepository
import fi.iki.elonen.NanoHTTPD
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalHttpServer @Inject constructor(
    private val nasCredentialsRepository: NasCredentialsRepository
) : NanoHTTPD(DEFAULT_PORT) {

    companion object {
        /**
         * HTTPサーバーのデフォルトポート番号。
         */
        const val DEFAULT_PORT = 8080
        private const val TAG = "LocalHttpServer"
        private const val STREAM_PREFIX = "/stream/"
    }

    init {
        Log.d(TAG, "LocalHttpServer: 初期化中... インスタンス ${this.hashCode()}")
        if (!isAlive()) {
            try {
                start(SOCKET_READ_TIMEOUT, false) // デーモンスレッドではない
                Log.i(TAG, "LocalHttpServer: ポート $listeningPort で正常に起動しました。isAlive: ${isAlive()}")
            } catch (e: IOException) {
                Log.e(TAG, "LocalHttpServer: init中にポート $listeningPort でサーバーを起動できませんでした。", e)
            }
        } else {
            Log.i(TAG, "LocalHttpServer: ポート $listeningPort で既に実行中です。")
        }
    }

    /**
     * サーバーが起動していることを保証する。
     * サーバーが起動していなければ、起動を試みる。
     */
    fun ensureStarted() {
        if (!isAlive()) {
            Log.i(TAG, "LocalHttpServer: ensureStarted() が呼ばれましたが、サーバー (インスタンス ${this.hashCode()}) は起動していませんでした。起動を試みます。")
            try {
                start(SOCKET_READ_TIMEOUT, false)
                Log.i(TAG, "LocalHttpServer: ensureStarted() によりポート $listeningPort で正常に起動しました。isAlive: ${isAlive()}")
            } catch (e: IOException) {
                Log.e(TAG, "LocalHttpServer: ensureStarted() によりポート $listeningPort でサーバーを起動できませんでした。", e)
            }
        } else {
            Log.d(TAG, "LocalHttpServer: ensureStarted() が呼ばれました。サーバー (インスタンス ${this.hashCode()}) はポート $listeningPort で既に起動しています。")
        }
    }

    /**
     * SMBパスをエンコードして、このサーバーで再生可能なHTTP URLに変換する。
     *
     * @param smbPath `smb://...` 形式のフルパス。
     * @param connectionId このSMBパスにアクセスするための認証情報を持つNasConnectionのID。
     * @return `http://127.0.0.1:{port}/stream/{connectionId}/{encoded_smb_path}` 形式のURL。
     */
    fun getStreamingUrl(smbPath: String, connectionId: String): String {
        ensureStarted() // 利用前にサーバーの起動を保証
        val encodedPath = smbPath.removePrefix("smb://")
        // listeningPortが0 (未起動など) の場合はDEFAULT_PORTをフォールバックとして使用する
        val currentPort = if (listeningPort > 0) listeningPort else DEFAULT_PORT
        return "http://127.0.0.1:$currentPort$STREAM_PREFIX$connectionId/$encodedPath"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val headers = session.headers
        Log.d(TAG, "serve: リクエスト受信 -> URI: '$uri', Method: $method, Headers: $headers (インスタンス: ${this.hashCode()})")

        if (!uri.startsWith(STREAM_PREFIX)) {
            Log.w(TAG, "serve: URI '$uri' がSTREAM_PREFIXで始まっていません。404 Not Foundを返します。")
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found: URI must start with $STREAM_PREFIX")
        }

        Log.d(TAG, "serve: ストリームリクエストを処理中 URI: '$uri'")
        try {
            val (connectionId, smbPath) = parseUri(uri)
            Log.i(TAG, "serve: パースされたURI -> ConnectionId: '$connectionId', SmbPath: '$smbPath'")

            val connection = nasCredentialsRepository.getConnectionById(connectionId)
            if (connection == null) {
                Log.w(TAG, "serve: ID '$connectionId' のNasConnectionが見つかりません。404 Not Foundを返します。")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Connection info not found for ID: $connectionId")
            }
            Log.d(TAG, "serve: ID '$connectionId' のNasConnectionを正常に取得しました (ホスト: ${connection.hostname})")

            val (inputStream, fileSize) = getSmbInputStream(smbPath, connection)
            Log.i(TAG, "serve: SMB InputStreamを正常に取得しました。Path: '$smbPath', FileSize: $fileSize bytes")

            val mimeType = "audio/mpeg"
            Log.i(TAG, "serve: OKレスポンスを準備中。MimeType: '$mimeType', FileSize: $fileSize bytes for URI: '$uri'")
            return newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, fileSize)

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "serve: URI '$uri' の形式が無効です。例外: ${e.javaClass.simpleName} - ${e.message}", e)
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request: Invalid URI format. ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "serve: URI '$uri' のリクエスト処理中にエラーが発生しました。例外: ${e.javaClass.simpleName} - ${e.message}", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error: ${e.message}")
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
        val smbPath = "smb://$encodedSmbPath"
        return Pair(connectionId, smbPath)
    }

    /**
     * SMBパスと認証情報を使って、ファイルのInputStreamとファイルサイズを取得する。
     */
    private fun getSmbInputStream(smbPath: String, connection: NasConnection): Pair<InputStream, Long> {
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

    /**
     * サーバーの現在のステータスをログに出力する。
     * @param contextTag このメソッドが呼び出されたコンテキストを識別するためのタグ。
     */
    fun logServerStatus(contextTag: String) {
        Log.d(TAG, "$contextTag: LocalHttpServer ステータス (インスタンス ${this.hashCode()}) - isAlive: ${isAlive()}, port: $listeningPort")
    }
}
