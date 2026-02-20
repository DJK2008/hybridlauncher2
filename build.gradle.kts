// project-level build.gradle.kts
buildscript {
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.50")
    }
}

plugins {
    // Upgrade Kotlin to 2.3.10 for Gradle 9.2.1 compatibility
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.android.application") version "8.2.2" apply false
}