// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.legacy.kapt) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.jlleitschuh.ktlint) apply false
    alias(libs.plugins.androidx.safeargs) apply false
}
