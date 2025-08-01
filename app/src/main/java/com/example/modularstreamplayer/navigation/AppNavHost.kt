package com.example.modularstreamplayer.navigation

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.core_model.TrackItem
import com.example.feature_browser.BrowserScreen
import com.example.feature_browser.PlayerScreen
import com.example.feature_browser.PlayerViewModel
import com.example.feature_browser.R
import com.example.modularstreamplayer.ui.ScreenTitle
import com.example.feature_browser.R as feature_browser_R

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(UnstableApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val argPath = context.getString(feature_browser_R.string.nav_arg_path)
    val argTrackItem = context.getString(feature_browser_R.string.nav_arg_track_item)

    NavHost(
        navController = navController,
        startDestination = "${ScreenTitle.Browser.name}?$argPath={${argPath}}", // スタート地点を定義
        modifier = modifier
    ) {
        // --- ファイルブラウザ画面 ---
        composable(
            route = "${ScreenTitle.Browser.name}?$argPath={${argPath}}",
            arguments = listOf(
                navArgument(argPath) {
                    type = NavType.StringType
                    nullable = true // ルートフォルダの場合はnull
                }
            )
        ) { backStackEntry ->
            // ViewModelはHiltによって自動的に生成される
            BrowserScreen(
                onFolderClick = { folderPath ->
                    navController.navigate("${ScreenTitle.Browser.name}?$argPath=$folderPath")
                },
                onTrackClick = { trackItem ->
                    // ParcelableオブジェクトをBackStackEntryに保存して渡す
                    backStackEntry.savedStateHandle[argTrackItem] = trackItem
                    navController.navigate(ScreenTitle.Player.name)
                }
            )
        }

        // --- 再生画面 ---
        composable(
            route = ScreenTitle.Player.name
        ) { backStackEntry ->
            // 前の画面からParcelableオブジェクトを取得
            //val parentEntry = remember(backStackEntry) {
            //    navController.getBackStackEntry(ScreenTitle.Browser.name)
            //}
            val parentEntry = navController.previousBackStackEntry
            val trackItem = parentEntry?.savedStateHandle?.get<TrackItem>(argTrackItem)
            if (trackItem != null) {
                Log.d("AppNavHost", "SavedStateHandle keys: ${backStackEntry.savedStateHandle?.keys()}")
                PlayerScreen(
                     trackItem = trackItem,
                     onBack = { navController.popBackStack() }
                )
            } else {
                // trackItemが取得できないエラーケース。ブラウザに戻る。
                navController.popBackStack()
            }
        }
    }
}

/*
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier
) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = ScreenTitle.valueOf(
        backStackEntry?.destination?.route ?: ScreenTitle.Browser.name
    )
//    Scaffold(
//        topBar = {
//            AppBar(
//                currentScreen = currentScreen,
//                canNavigateBack = navController.previousBackStackEntry != null,
//                navigateUp = { navController.navigateUp() },
//            )
//        }
//    ) { innerPadding ->
    Surface(
        modifier = Modifier
            //.padding(innerPadding)
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = ScreenTitle.Browser.name
        ) {
            composable(
                "browser?path={path}",
                arguments = listOf(navArgument("path") { nullable = true })
            ) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path")
                BrowserScreen(
                    onFolderClick = { folderPath ->
                        navController.navigate("browser?path=$folderPath")
                    },
                    onTrackClick = { trackUri ->
                        // URIをエンコードして渡す
                        val encodedUri = URLEncoder.encode(trackUri, "UTF-8")
                        navController.navigate("player/$encodedUri")
                    }
                )
//                    Button(
//                        onClick = {
//                            navController.navigate("player/trackUri")
//                        },
//                        content = {
//                            Text("Navigate to Player")
//                        }
//                    )
            }
            composable(
                "player/{trackUri}",
                arguments = listOf(navArgument("trackUri") { type = NavType.StringType })
            ) {
                //PlayerScreen(onBack = { navController.popBackStack() })
                Text("Player Screen")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    currentScreen: ScreenTitle,
    canNavigateBack: Boolean = false,
    navigateUp: () -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(currentScreen.titleResId),
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            }
        }
    )
}

 */



