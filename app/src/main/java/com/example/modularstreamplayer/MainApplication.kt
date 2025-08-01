package com.example.modularstreamplayer


import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Hiltを使うアプリの起点であることを示すアノテーション
@HiltAndroidApp
class MainApplication : Application() {
    // 通常、このクラスの中身は空でOK
}