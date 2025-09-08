plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // kpsプラグイン
    alias(libs.plugins.ksp)
    // Hiltプラグイン
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.feature_browser"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {

      implementation(libs.androidx.core.ktx)
//      implementation(libs.androidx.appcompat)
//      implementation(libs.material)

    //accompanist-permissions
    implementation(libs.accompanist.permissions)


//    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.kotlinx.coroutines.guava)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ViewModelでHiltを使うための追加ライブラリ
    implementation(libs.androidx.hilt.navigation.compose)

    // NanoHTTPD
    implementation(libs.nanohttpd.webserver)

    //Jetpack Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    //Icons
    implementation(libs.androidx.compose.material.icons.extended)

    //Coil
    implementation(libs.coil.compose)

    //module
    implementation(project(":core-model"))
    implementation(project(":core-player"))
    implementation(project(":data-smb"))
    implementation(project(":data-repository"))
    implementation(project(":theme"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}