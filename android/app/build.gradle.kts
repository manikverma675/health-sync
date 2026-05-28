plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.healthsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.healthsync"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        getByName("debug") {
            // Used by GitHub Actions — file placed by build-apk.yml before build runs
            val localKeystore = file("debug.keystore")
            if (localKeystore.exists()) {
                storeFile = localKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
            // Falls back to ~/.android/debug.keystore if file not present
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Lifecycle + coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // HTTP client for Google Drive REST API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

}
