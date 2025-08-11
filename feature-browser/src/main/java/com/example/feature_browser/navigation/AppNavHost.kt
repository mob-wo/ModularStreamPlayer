package com.example.feature_browser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.feature_browser.MainScreen
import com.example.feature_browser.SettingsScreen
import com.example.feature_browser.ScreenTitle
import com.example.feature_browser.player.PlayerFullScreen


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
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}



