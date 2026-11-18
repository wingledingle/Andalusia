import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

/**
 * Release configuration lives in keystore.properties (git-ignored), see docs/PUBLISHING.md:
 *   storeFile, storePassword, keyAlias, keyPassword   - your Play upload key
 *   admobAppId, admobBannerId                          - your AdMob IDs
 * Without it, builds fall back to the debug key and Google's AdMob test IDs.
 */
val releaseProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val admobAppId = releaseProps.getProperty("admobAppId") ?: "ca-app-pub-3940256099942544~3347511713"
val admobBannerId = releaseProps.getProperty("admobBannerId") ?: "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "com.silpa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.silpa.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 101
        versionName = "1.0.1"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
    }

    signingConfigs {
        if (releaseProps.getProperty("storeFile") != null) create("upload") {
            storeFile = rootProject.file(releaseProps.getProperty("storeFile"))
            storePassword = releaseProps.getProperty("storePassword")
            keyAlias = releaseProps.getProperty("keyAlias")
            keyPassword = releaseProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // Optimized ARM-only APK for installing directly on a test device (always debug-signed).
    buildTypes.create("sideload") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        matchingFallbacks += listOf("release")
        applicationIdSuffix = ""
    }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.maxHeapSize = "4g"; it.forkEvery = 1; it.systemProperty("robolectric.graphicsMode", "NATIVE") } } }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":feature:home"))
    implementation(project(":feature:studio"))
    implementation(project(":feature:bank"))
    implementation(project(":engine"))
    implementation(project(":ml"))
    implementation(project(":monetization"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.compose.material3.navigation.suite)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
