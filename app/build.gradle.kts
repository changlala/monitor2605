plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.monitor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.monitor.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 19
        versionName = "1.3.6"
        buildConfigField("String", "DEFAULT_CONFIG_SOURCE_URLS",
            "\"https://cdn.jsdelivr.net/gh/changlala/monitor2605@main/config.json5;" +
            "https://gitee.com/changhao24/monitor2605/raw/master/config.json5;" +
            "https://raw.githubusercontent.com/changlala/monitor2605/main/config.json5\""
        )
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("monitor-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "monitor2605"
            keyAlias = "monitor"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "monitor2605"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Enable BuildConfig generation
android.buildFeatures.buildConfig = true

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.activity:activity-ktx:1.8.2")
}

kapt {
    correctErrorTypes = true
}
