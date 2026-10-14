plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.s23cp5054.posewithme"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.s23cp5054.posewithme"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-phase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // MediaPipe ships ~14 MB of native code per ABI; 64-bit ARM covers the target phone.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // Lets the release build be installed on a test phone for measurement. Not for store upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        // The .task bundle is already a stored zip; leaving it uncompressed costs nothing.
        noCompress += "task"
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.recyclerview)
    implementation(libs.exifinterface)
    testImplementation(libs.junit)
    // Android's built-in org.json is stubbed out in local unit tests.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
