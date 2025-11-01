plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.marcs"
    // FIX 1: Use the latest STABLE Android SDK version.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.marcs"
        minSdk = 26
        // FIX 1: Target a stable SDK version.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        // Use the standard Java version for maximum compatibility.
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    aaptOptions {
        noCompress("tflite")
    }
}

dependencies {
    // STABLE AndroidX and Compose dependencies
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Use the latest stable Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // For getting device location
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.8")
    implementation("io.coil-kt:coil-svg:2.6.0")



// For loading images like marker icons easily
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- FIX 2: ADD ALL REQUIRED TENSORFLOW LITE LIBRARIES ---
    // 1. The core TFLite library (was missing)
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    // 2. The support library for image processing (was missing)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // 3. The high-level Task Vision library (you already had this)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    // 4. (Optional) GPU delegate for acceleration
    implementation("org.tensorflow:tensorflow-lite-gpu:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    implementation("org.osmdroid:osmdroid-android:6.1.18")


    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

}
