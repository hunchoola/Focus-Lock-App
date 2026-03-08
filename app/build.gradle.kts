import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.focuslockapp.myapplication"

    // STABLE SDK VERSION (Android 15)
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focuslockapp.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 16
        versionName = "1.5.7"

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
}

dependencies {
    // --- FIXED STABLE DEPENDENCIES (SDK 35 Compatible) ---

    // Core & Appcompat
    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.androidx.appcompat.v170)

    // Material Design
    implementation(libs.material.v1120)

    // Activity
    implementation(libs.androidx.activity.v193)

    // Layouts
    implementation(libs.androidx.constraintlayout.v220)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v121)
    androidTestImplementation(libs.androidx.espresso.core.v361)

    // AdMob
    implementation(libs.play.services.ads.v2360)

    // Gemini AI SDK
    implementation(libs.generativeai)

    // Coroutines (for background tasks)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)


}

apply(plugin = "com.google.gms.google-services")