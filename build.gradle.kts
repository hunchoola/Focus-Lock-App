// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // ADD THIS LINE BELOW
    id("com.google.gms.google-services") version "4.4.4" apply false
}