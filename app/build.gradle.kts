plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Parcelize
    alias(libs.plugins.kotlin.parcelize)
    // kpsプラグイン
    alias(libs.plugins.ksp)
    // Hiltプラグイン
    alias(libs.plugins.hilt.android)

}

android {
    namespace = "com.example.modularstreamplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.modularstreamplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ViewModelでHiltを使うための追加ライブラリ
    implementation(libs.androidx.hilt.navigation.compose)


    //Jetpack Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    //module
    implementation(project(":core-model"))
    implementation(project(":core-player"))
    implementation(project(":core-http"))           // LocalHttpServer が存在するモジュール

    implementation(project(":data-repository"))    // NasCredentialsRepository, SecurityModule, DispatchersModule が存在するモジュール
    implementation(project(":data-smb"))            // SmbMediaSource が存在するモジュール
    implementation(project(":data-media-repository")) // DataSourceModule, MediaRepository が存在するモジュール

    implementation(project(":feature-browser"))
    implementation(project(":theme"))



    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}