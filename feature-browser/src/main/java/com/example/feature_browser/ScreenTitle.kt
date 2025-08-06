package com.example.feature_browser

import androidx.annotation.StringRes

enum class ScreenTitle (@StringRes val titleResId: Int) {
    Browser(titleResId = R.string.screen_title_browser),
    Player(titleResId = R.string.screen_title_player),
    Settings(titleResId = R.string.screen_title_settings);
}