// djk2008/hybridlauncher2/hybridlauncher2-d3964e3f2f78b565caef8e21fdf7801cdcb29989/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.samsung.hybridlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samsung.hybridlauncher"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core and UI
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.google.android.material:material:1.13.0")

    // Dagger Hilt
    implementation("com.google.dagger:hilt-android:2.59.1")
    kapt("com.google.dagger:hilt-android-compiler:2.59.1")

    // Biometrics and Physics
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.1.0")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation("androidx.compose:compose-bom:2026.02.00")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")

    // Theming
    implementation("androidx.palette:palette-ktx:1.0.0")
}