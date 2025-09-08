package com.example.feature_browser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.feature_browser.MainScreen
import com.example.feature_browser.R
import com.example.feature_browser.ScreenTitle
import com.example.feature_browser.settings.SettingsScreen
import com.example.feature_browser.player.PlayerFullScreen
import com.example.feature_browser.settings.NasConnectionEditorScreen
import com.example.feature_browser.settings.NasConnectionListScreen
import com.example.feature_browser.settings.SettingsViewModel

/**
 * アプリ全体のナビゲーションを管理するNavHostコンポーザブル。
 * 各画面へのルートと、そのルートに対応するコンポーザブルを定義する。
 *
 * @param navController NavHostのナビゲーションを制御するコントローラー。
 * @param modifier このNavHostに適用するModifier。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = ScreenTitle.Browser.name, // アプリ起動時の初期画面
        modifier = modifier
    ) {
        /**
         * メイン画面。ファイルブラウザ、ドロワー、ミニプレーヤーなどを含む。
         * この画面がアプリのホームとなる。
         */
        composable(route = ScreenTitle.Browser.name) {
            MainScreen(navController = navController)
        }

        /**
         * 全画面再生画面。
         * MainScreenから遷移してくる。
         */
        composable(route = ScreenTitle.Player.name) {
            PlayerFullScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }

        /**
         * 詳細設定画面。
         * ドロワーから遷移してくる。
         */
        composable(route = ScreenTitle.Settings.name) {
            SettingsScreen(
                navController = navController,
                onNavigateUp = { navController.navigateUp() }
            )
        }
        settingsGraph(navController)
    }
}


/**
 * NAS接続管理に関連する画面のナビゲーションを定義する拡張関数。
 * これにより、SettingsViewModelがこのグラフ内で共有される。
 */
fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    navigation(
        startDestination = ScreenTitle.NasConnectionList.name,
        route = ScreenTitle.SettingsGraph.name // ★ このグラフのエントリーポイント
    ) {
        /**
         * NAS接続一覧画面
         */
        composable(route = ScreenTitle.NasConnectionList.name) { navBackStackEntry ->
            // このグラフを親とするViewModelを取得
            val parentEntry = remember(navBackStackEntry) {
                navController.getBackStackEntry(ScreenTitle.SettingsGraph.name)
            }
            val viewModel: SettingsViewModel = hiltViewModel(parentEntry)

            NasConnectionListScreen(
                viewModel = viewModel,
                onNavigateToEditor = { connectionId ->
                    val route = "${ScreenTitle.NasConnectionEditor.name}?connectionId=${connectionId}"
                    navController.navigate(route)
                },
                onNavigateUp = { navController.navigateUp() }
            )
        }

        /**
         * NAS接続編集画面
         */
        composable(
            route = "${ScreenTitle.NasConnectionEditor.name}?connectionId={connectionId}",
            arguments = listOf(navArgument("connectionId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { navBackStackEntry ->
            // このグラフを親とするViewModelを取得
            val parentEntry = remember(navBackStackEntry) {
                navController.getBackStackEntry(ScreenTitle.SettingsGraph.name)
            }
            val viewModel: SettingsViewModel = hiltViewModel(parentEntry)
            val connectionId = navBackStackEntry.arguments?.getString("connectionId")

            NasConnectionEditorScreen(
                viewModel = viewModel,
                connectionId = connectionId,
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}
