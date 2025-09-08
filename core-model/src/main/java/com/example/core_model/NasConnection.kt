package com.example.core_model

import android.annotation.SuppressLint
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.UUID

 /**
 * ネットワーク上のNAS（SMB共有フォルダ）への接続情報を表現するデータクラス。
 * このオブジェクトは暗号化されたSharedPreferencesにJSON形式で保存されます。
 *
 * @property id 接続情報の一意な識別子。
 * @property nickname ユーザーが識別するためのニックネーム（例: "My-NAS"）。
 * @property hostname NASのホスト名またはIPアドレス（例: "192.168.1.10"）。
 * @property path 共有フォルダのパス（例: "Music" や "Share/Audio"）。
 * @property username 認証に使用するユーザー名。匿名アクセスの場合はnull。
 * @property password 認証に使用するパスワード。匿名アクセスの場合はnull。
 */

@Parcelize
@Serializable
data class NasConnection(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String,
    val hostname: String,
    val path: String,
    val username: String?,
    val password: String?
) : Parcelable