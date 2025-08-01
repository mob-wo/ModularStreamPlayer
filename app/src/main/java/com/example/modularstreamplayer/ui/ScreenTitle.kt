package com.example.modularstreamplayer.ui

import androidx.annotation.StringRes
import com.example.modularstreamplayer.R

enum class ScreenTitle (@StringRes val titleResId: Int) {
    Browser(titleResId = R.string.screen_title_browser),
    Player(titleResId = R.string.screen_title_player);
}