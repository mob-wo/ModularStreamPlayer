plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

    // kpsプラグイン
    alias(libs.plugins.ksp)
    // Hiltプラグイン
    alias(libs.plugins.hilt.android)

}

android {
    namespace = "com.example.core_player"
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
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    //Jetpack Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    //module
    implementation(project(":core-model"))
    implementation(project(":data-repository"))

    // NanoHTTPD
    implementation(libs.nanohttpd.webserver)

    // jcifs-ng
    //implementation(libs.jcifs.ng)
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.7")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}