## 技術設計書: Modular Stream Player (Overview)

### 1. はじめに

このドキュメントは、Android音楽プレーヤーアプリ「Modular Stream Player」の全体的な技術アーキテクチャと設計原則を定義するものです。各開発フェーズの個別技術設計書は、このドキュメントで定義された基盤の上に構築されます。

### 2. プロジェクト目標と開発思想

*   **技術的目標**: ローカルストレージおよびネットワーク上の共有フォルダ（SMB）からの音楽ストリーミングを可能にし、将来的な拡張性（新しいデータソース、メディア形式）と高いメンテナンス性を備えたアプリケーションを構築する。
*   **開発思想**:
    *   **クリーンアーキテクチャ**: 関心事の分離とテスト容易性を重視。
    *   **モジュール化**: 機能とレイヤーに基づいた明確なモジュール分割。
    *   **一方向データフロー**: UI → ViewModel → Repository → DataSource の原則。
    *   **DI (Hilt)**: 依存関係の疎結合化。
    *   **Kotlin First**: Coroutines, Flow を活用した現代的な非同期処理。
    *   **UI (Jetpack Compose)**: 宣言的なUI構築。

### 3. 全体アーキテクチャ

**3.1. レイヤー構造**

本アプリケーションは、クリーンアーキテクチャの原則に基づき、以下の主要なレイヤーで構成されます。

*   **UI Layer (Presentation Layer)**: ユーザーインタフェース（Compose画面）とViewModelを担当。ユーザー操作の受付と、状態に基づいたUIの表示を行う。
*   **Domain Layer**: アプリケーション固有のビジネスロジックとユースケース、コアとなるデータモデルを定義。UIやDataレイヤーの詳細には依存しない。
*   **Data Layer**: データソース（ローカル、リモート）へのアクセス、データ永続化、リポジトリパターンによるデータ抽象化を担当。

**依存関係のルール**: 依存の方向は常に内側（UI → Domain → Data）へ向かうことを原則とします。ただし、Dataレイヤー内のRepositoryがDomainレイヤーのモデルを利用したり、DomainレイヤーのインターフェースをDataレイヤーで実装する形は許容されます。Dataレイヤー内では、具体的なデータソース実装モジュール（例: `:data-local`, `:data-smb`）は、それらを抽象化するインターフェースモジュール（例: `:data-source`）やRepositoryモジュールに依存します。

**3.2. モジュール構成の基本方針**

アプリケーションは複数のGradleモジュールに分割され、各モジュールは特定の機能やレイヤーの責務を持ちます。主なモジュールカテゴリは以下の通りです。

*   **:app**: アプリケーションのエントリーポイント、全体的なDI設定、Activityなどを含むメインアプリケーションモジュール。
*   **:feature-\***: 各機能単位のUI（Compose画面）、ViewModel、およびその機能固有のナビゲーション定義を含むモジュール群。
*   **:core-\***: 複数の機能モジュールやデータモジュールから共通して利用されるコアな機能やモデルを提供するモジュール群。
    *   **:core-model**: アプリ全体で使われる基本的なデータクラス（`FileItem`, `TrackItem`, `NasConnection` など）。
    *   **:core-player**: メディア再生エンジンのコアロジック（`PlaybackService`など）。
    *   **:core-http**: ローカルHTTPサーバーなど、基盤となる通信機能。
*   **:data-\***: データアクセスと永続化に関連するモジュール群。
    *   **:data-source**: データソースの抽象インターフェース（`MediaSource`など）を定義。
    *   **:data-local**: ローカルストレージ（`MediaStore`など）へのアクセス実装。
    *   **:data-smb**: SMB/CIFSプロトコルでのネットワーク共有フォルダへのアクセス実装。
    *   **:data-repository**: Repositoryパターンの実装や、設定情報（`SettingsRepository`）、認証情報（`NasCredentialsRepository`）、再生リクエスト（`PlaybackRequestRepository`）、再生状態（`PlayerStateRepository`）の管理。
    *   **:data-media-repository**: `MediaRepository` など、メディアアイテム取得の主要な窓口となるRepository。
*   **:theme**: アプリケーションのデザイントークン（色、タイポグラフィ、シェイプ）を定義するUIテーマモジュール。

**(全体的なモジュール構成図 - 簡略版)**

```mermaid
graph TD
    subgraph "Presentation Layer (UI & ViewModel)"
        app
        feature_browser["feature-browser"]
        theme["theme"]
    end

    subgraph "Domain Layer (Core Logic & Models)"
        core_model["core-model"]
        core_player["core-player"]
    end

    subgraph "Data Layer (Repositories, DataSources, Network)"
        data_media_repository["data-media-repository (MediaRepository)"]
        data_repository["data-repository (Settings, Credentials etc.)"]
        data_source["data-source (MediaSource Interface)"]
        data_local["data-local (LocalMediaSource)"]
        data_smb["data-smb (SmbMediaSource)"]
        core_http["core-http (LocalHttpServer)"]
    end

    %% Application Layer Dependencies
    app --> feature_browser
    app --> theme
    app --> core_player %% PlaybackServiceの起動等
    app --> core_http %% LocalHttpServerの起動等

    %% Feature Layer Dependencies
    feature_browser --> core_model
    feature_browser --> data_media_repository
    feature_browser --> data_repository
    feature_browser --> core_player %% MediaController経由
    feature_browser --> core_http   %% PlayerViewModelがLocalHttpServerを利用
    feature_browser --> theme
    feature_browser --> data_smb %% ViewModelでSmbExceptionをハンドルするため

    %% Domain Layer Dependencies
    core_player --> core_model

    %% Data Layer Dependencies
    data_media_repository --> core_model
    data_media_repository --> data_source
    data_media_repository --> data_local
    data_media_repository --> data_smb
    data_media_repository --> data_repository %% SettingsRepository, NasCredentialsRepository
    data_media_repository --> core_http       %% SmbMediaSourceFactoryがLocalHttpServerを必要とするため

    data_smb --> core_model
    data_smb --> data_source
    data_smb --> core_http

    data_local --> core_model
    data_local --> data_source

    core_http --> core_model
    core_http --> data_repository %% LocalHttpServerがNasCredentialsRepositoryを利用

    data_repository --> core_model
    data_source --> core_model
```

### 4. 主要技術スタック

*   **言語**: Kotlin
*   **UI**: Jetpack Compose
*   **アーキテクチャコンポーネント**: ViewModel, StateFlow, Navigation Component
*   **非同期処理**: Kotlin Coroutines (Flow, suspend functions)
*   **DI (依存性の注入)**: Hilt
*   **メディア再生**: Jetpack Media3 (ExoPlayer, MediaSession, MediaLibraryService)
*   **画像読み込み**: Coil
*   **ネットワーク通信 (SMB)**: JCIFS-NG
*   **ローカルHTTPサーバー**: NanoHTTPD
*   **データ永続化**:
    *   **設定**: Jetpack DataStore (Preferences)
    *   **認証情報**: Jetpack Security (EncryptedSharedPreferences)
*   **シリアライズ**: kotlinx.serialization
*   **テスト**: JUnit5, MockK, Turbine, Espresso, Robolectric (必要に応じて)

### 5. データフローの基本パターン

アプリケーション内のデータは、主に以下の流れで処理されます。

1.  **UI (Compose Screen)**: ユーザー操作を検知し、`ViewModel` の関数を呼び出す。`ViewModel` から `StateFlow` などで公開されるUI状態を購読し、画面を更新する。
2.  **ViewModel**: UIからのイベントを処理し、必要に応じて `Repository` にデータアクセスを要求する。`Repository` から受け取ったデータを加工し、UIが購読可能な状態として公開する。UIロジックを含む。
3.  **Repository**: `ViewModel` からの要求に基づき、1つまたは複数の `DataSource` からデータを取得・加工する。データのキャッシュ戦略や、どの `DataSource` を使用するかの判断も担う。ドメインオブジェクトやDTOを返す。
4.  **DataSource**: 特定のデータソース（ローカルDB, ネットワークAPI, ファイルシステムなど）との具体的なやり取りを担当する。

### 6. 主要な横断的関心事

*   **エラーハンドリング**:
    *   `DataSource` や `Repository` で発生した例外は、適切にキャッチされ、必要に応じてドメイン固有の例外にラップされるか、UIが解釈可能なエラー状態として `ViewModel` に伝えられる。
    *   UIは、エラー状態に応じてユーザーフレンドリーなメッセージや表示を行う。
*   **スレッド管理**:
    *   I/Oバウンドな処理（ファイルアクセス、ネットワーク通信、DBクエリなど）は、`Dispatchers.IO` を使用してバックグラウンドスレッドで実行される。
    *   CPUバウンドな処理（大規模なリストのソートやフィルタリングなど）は `Dispatchers.Default` を使用。
    *   UIの更新は常にメインスレッド (`Dispatchers.Main`) で行われる。`ViewModel` は `viewModelScope` を利用する。
*   **設定管理**:
    *   ユーザー設定（UIモード、デフォルトパスなど）は `SettingsRepository` を介して永続化され、アプリ全体からアクセス可能。
*   **認証情報管理**:
    *   NASのパスワードなどの機密情報は `NasCredentialsRepository` を介して暗号化されて保存される。

### 7. テスト戦略

*   **単体テスト (Unit Tests)**: `ViewModel`, `Repository`, `DataSource`, ユーティリティクラスなど、個々のコンポーネントのロジックをテストする。JUnit, MockK, Turbine (Flowテスト用) を使用。
*   **結合テスト (Integration Tests)**: 複数のコンポーネントが連携して動作することを確認する（例: ViewModelとRepositoryの連携）。
*   **UIテスト (UI Tests)**: Jetpack ComposeのテストフレームワークとEspressoを使用して、画面の表示やユーザー操作のテストを行う。
*   各モジュールは `test` および `androidTest` ソースセットを持ち、対応するテストコードを配置する。

