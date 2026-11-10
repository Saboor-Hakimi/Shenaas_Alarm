plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shenaas.alarm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shenaas.alarm"
        // Android 12+. Primary targets: Pixel 7, Pixel 8 and Pixel 10 Pro on their latest Android release.
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // Pixel 7 and newer only run 64-bit apps; dropping other ABIs keeps MediaPipe's native libs small.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so it installs for testing; use a real upload key for Play.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates the Test Lab.
        buildConfig = true
    }
    androidResources {
        // MediaPipe memory-maps the detector model, so it must be stored uncompressed.
        noCompress += "tflite"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mlkit.image.labeling)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
