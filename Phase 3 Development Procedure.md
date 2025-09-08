## **フェーズ3 開発手順書: SMB対応**

### **目的**
このフェーズのゴールは、ローカルストレージに加えて、ネットワーク上のNAS（SMB共有フォルダ）を新たなメディアソースとして追加し、ファイルの閲覧とストリーミング再生を可能にすることです。

### **主要技術スタック**
*   **SMB通信**: `jcifs-ng`
*   **認証情報保存**: `Jetpack Security (EncryptedSharedPreferences)`
*   **ストリーミング中継**: `NanoHTTPD`

---

### **ステップ1: 依存関係の追加**
まず、各モジュールの`build.gradle.kts`ファイルに必要なライブラリを追加します。

*   **対象モジュール**: `:data-smb` (新規作成)
    *   **ファイル**: `data-smb/build.gradle.kts`
    *   **追加ライブラリ**: `org.codelibs:jcifs-ng`

*   **対象モジュール**: `:data-repository`
    *   **ファイル**: `data-repository/build.gradle.kts`
    *   **追加ライブラリ**: `androidx.security:security-crypto`

*   **対象モジュール**: `:core-player`
    *   **ファイル**: `core-player/build.gradle.kts`
    *   **追加ライブラリ**: `org.nanohttpd:nanohttpd`

---

### **ステップ2: NAS接続情報の永続化層の実装**
**目的**: ユーザーが入力したNASの接続情報（ホスト名、認証情報など）を、デバイス内に安全に保存・管理する仕組みを構築します。

1.  **接続情報モデルの定義**
    *   **ファイル**: `:core-model/src/main/java/com/example/core_model/NasConnection.kt`
    *   **内容**: NAS接続情報を表すデータクラスを定義します（ID, ニックネーム, ホスト名, パス, ユーザー名, パスワードなど）。

2.  **暗号化されたSharedPreferencesの提供 (DI)**
    *   **ファイル**: `:data-repository/src/main/java/com/example/data_repository/di/SecurityModule.kt` (新規)
    *   **内容**: `EncryptedSharedPreferences`のインスタンスをHiltで提供するモジュールを作成します。

3.  **NAS接続情報リポジトリの実装**
    *   **ファイル**: `:data-repository/src/main/java/com/example/data_repository/NasCredentialsRepository.kt` (新規)
    *   **内容**: `EncryptedSharedPreferences`を使用して、`NasConnection`オブジェクトのリストをJSON形式で保存・読み込み・削除するロジックを実装します。CRUD操作メソッドと、接続情報リストを公開する`Flow`を定義します。

---

### **ステップ3: SMBデータソースの実装**
**目的**: SMBサーバー上のファイル/フォルダを閲覧するための`MediaSource`を実装します。

1.  **新規モジュールの作成**
    *   **モジュール名**: `:data-smb`
    *   **依存関係**: `:core-model`, `:data-source`, `jcifs-ng`

2.  **SmbMediaSourceの実装**
    *   **ファイル**: `:data-smb/src/main/java/com/example/data_smb/SmbMediaSource.kt`
    *   **内容**: `MediaSource`インターフェースを実装します。`jcifs-ng`を使い、指定されたSMBパスのファイルとフォルダの一覧を取得し、`FileItem`のリストに変換して返すロジックを実装します。

---

### **ステップ4: データソース切り替えの仕組みを実装**
**目的**: `MediaRepository`が、現在選択されているデータソース（ローカル or SMB）に応じて、適切な`MediaSource`（`LocalMediaSource` or `SmbMediaSource`）に処理を委譲できるようにします。

1.  **アクティブなデータソース状態の管理**
    *   **ファイル**: `:data-repository/src/main/java/com/example/data_repository/SettingsRepository.kt` (修正)
    *   **内容**: 現在選択されているデータソースの種類とID（SMBの場合は`NasConnection`のID）を`DataStore`に保存する仕組みを追加します。

2.  **MediaRepositoryの拡張**
    *   **ファイル**: `:data-repository/src/main/java/com/example/data_repository/MediaRepository.kt` (修正)
    *   **内容**: `SettingsRepository`から現在のアクティブなデータソースを監視します。`getItemsIn`が呼び出された際に、ローカルが選択されていれば`LocalMediaSource`を、SMBが選択されていれば`NasCredentialsRepository`から該当の接続情報を取得し、それを使って`SmbMediaSource`を動的に生成して処理を委譲します。

---

### **ステップ5: SMBストリーミング再生の基盤実装**
**目的**: `ExoPlayer`でSMB上のファイルを再生するため、ストリームを中継するローカルHTTPサーバーを実装します。

1.  **ローカルHTTPサーバーの実装**
    *   **ファイル**: `:core-player/src/main/java/com/example/core_player/LocalHttpServer.kt` (新規)
    *   **内容**: `NanoHTTPD`を継承したシングルトンクラスを実装します。HTTPリクエストのパスからSMBのパスをデコードし、`jcifs-ng`経由で取得した`InputStream`をHTTPレスポンスとして返すロジックを記述します。

2.  **DI設定の追加**
    *   **ファイル**: `:core-player/src/main/java/com/example/core_player/di/PlayerModule.kt` (修正)
    *   **内容**: `LocalHttpServer`のインスタンスをシングルトンとしてHiltで提供する設定を追加します。

3.  **PlayerViewModelの修正**
    *   **ファイル**: `:feature-browser/src/main/java/com/example/feature_browser/player/PlayerViewModel.kt` (修正)
    *   **内容**: 再生要求(`startPlayback`)時に、トラックがSMB由来であるかを判断するロジックを追加します。SMBの場合は、`track.path`を`LocalHttpServer`が解釈できる`http://127.0.0.1:port/stream/...`形式のURLに変換し、`MediaItem`を生成するように修正します。

---

### **ステップ6: UIの実装**
**目的**: ユーザーがNAS接続情報を管理し、データソースを切り替えるためのUIを実装します。

1.  **NAS接続設定関連の画面作成**
    *   **ファイル**: `:feature-browser/src/main/java/com/example/feature_browser/settings/SettingsViewModel.kt` (新規)
    *   **ファイル**: `:feature-browser/src/main/java/com/example/feature_browser/settings/NasConnectionListScreen.kt` (新規)
    *   **ファイル**: `:feature-browser/src/main/java/com/example/feature_browser/settings/NasConnectionEditorScreen.kt` (新規)
    *   **内容**: UI/UX設計書に基づき、NAS接続の一覧表示、追加、編集、削除を行う画面と、それに対応するViewModelを実装します。ViewModelは`NasCredentialsRepository`を利用します。

2.  **ナビゲーションの更新**
    *   **ファイル**: `:feature-browser/src/main/java/com/example/feature_browser/navigation/AppNavHost.kt` (修正)
    *   **内容**: 「詳細設定」から「NAS接続一覧」、「NAS接続エディタ」への画面遷移ルートを追加します。

3.  **ナビゲーションドロワーの更新**
    *   **ファイル**: `:feature-browser/src/main/java/com/example/feature_browser/AppDrawer.kt` (修正)
    *   **ファイル**: `:feature-browser/src/main/java/com/example/feature_browser/browser/BrowserViewModel.kt` (修正)
    *   **内容**: `NasCredentialsRepository`から取得したNAS接続リストをデータソースの選択肢としてドロワーに表示します。ユーザーがNASを選択した際に、`SettingsRepository`にアクティブなデータソースとして保存するイベントを発行するようにします。