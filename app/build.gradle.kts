plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mattyai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mattyai.assistant"
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }

    testOptions { unitTests.isIncludeAndroidResources = false }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
