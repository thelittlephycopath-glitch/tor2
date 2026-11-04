import com.android.build.api.variant.FilterConfiguration.FilterType

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.jlleitschuh.ktlint)
    alias(libs.plugins.androidx.safeargs)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        )
    }
}

android {
    compileSdk = 37
    namespace = "org.torproject.vpn"

    defaultConfig {
        applicationId = "org.torproject.vpn.tor2beta.dev"
        minSdk = 24
        // Android 16 — https://developer.android.com/about/versions/16/behavior-changes-16
        targetSdk = 36
        // Base version code; ABI-specific offsets keep split APK version codes unique.
        versionCode = 24
        versionName = "1.9.1Beta-tor2"

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
        register("staging") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // We temporarily removed the support for x86 builds until
    // https://gitlab.torproject.org/tpo/core/onionmasq/-/work_items/191 gets fixed
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += listOf(
            "MissingTranslation",
            "VectorPath",
            "UnusedResources",
            "IconLocation",
            "ContentDescription",
            "RtlSymmetry",
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "DiscouragedApi",
            "Typos",
            "OldTargetApi", // https://gitlab.torproject.org/tpo/applications/vpn/-/work_items/506
            // This personal build intentionally targets arm64-v8a only; x86_64 is out of scope.
            "ChromeOsAbiSupport"
        )
    }
}

// https://developer.android.com/build/configure-apk-splits#configure-APK-versions
androidComponents {
    onVariants(selector().all()) { variant ->
        val abiCodes = mapOf("arm64-v8a" to 2)
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterType.ABI }?.identifier
            val baseAbiCode = abiCodes[abi] ?: 0
            output.versionCode.set((android.defaultConfig.versionCode ?: 0) * 10 + baseAbiCode)
        }
    }
}

dependencies {
    // Kotlin
    implementation(libs.bundles.retrofit)
    implementation(libs.bundles.ui)
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.commonTest)
    androidTestImplementation(libs.bundles.androidTest)
    androidTestImplementation(libs.bundles.kotlin)

    testImplementation(libs.bundles.commonTest)
    testImplementation(libs.bundles.test)

    debugImplementation(libs.bundles.debug)
    "stagingImplementation"(libs.bundles.staging)
    releaseImplementation(libs.bundles.release)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.composeDebug)
    "stagingImplementation"(libs.bundles.composeDebug)

    // Navigation3
    implementation(libs.bundles.navigation3)

    // Room
    ksp(libs.androidx.room3.compiler)
    implementation(libs.bundles.room3)
    implementation(libs.bundles.paging3)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
