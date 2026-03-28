import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-kapt")
    id("com.google.gms.google-services")
    alias(libs.plugins.google.firebase.crashlytics)
}

// Load local.properties for secure API key storage
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API key loaded from local.properties (never committed to VCS)
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${localProperties.getProperty("OPENROUTER_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"${localProperties.getProperty("GROQ_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "HF_API_KEY",
            "\"${localProperties.getProperty("HF_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "ELEVENLABS_API_KEY",
            "\"${localProperties.getProperty("ELEVENLABS_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "VERITE_SERVER_URL",
            "\"${localProperties.getProperty("VERITE_SERVER_URL", "http://10.0.2.2:8000")}\""
        )
        buildConfigField(
            "String",
            "VERITE_API_KEY",
            "\"${localProperties.getProperty("VERITE_API_KEY", "dev-verite-tmr-key-2024")}\""
        )
        // Psychologist server — separate from TMR if on different port/host
        // Defaults to VERITE_SERVER_URL if not set
        buildConfigField(
            "String",
            "VERITE_PSYCH_URL",
            "\"${localProperties.getProperty("VERITE_PSYCH_URL", localProperties.getProperty("VERITE_SERVER_URL", "http://10.0.2.2:8000"))}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
        mlModelBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // 16KB page size support — fixes ANR on Android 15+ devices
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.firebase.crashlytics)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.ui.text.google.fonts)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Lifecycle ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    // 16KB Page Size Compatibility fix
    implementation("androidx.graphics:graphics-path:1.0.1")

    // Coil for Image Loading
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // NOTE: SceneView removed — it ships Filament native libs that are not 16KB aligned
    // and was not used anywhere in the codebase. Re-add only if needed.

    // TensorFlow Lite — updated to 2.16.1 for 16KB page alignment support
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") {
        exclude(group = "com.google.flatbuffers", module = "flatbuffers-java")
    }
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4") {
        exclude(group = "com.google.flatbuffers", module = "flatbuffers-java")
    }

    // Real-time Graphing
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Media3 for Audio Streaming
    val media3Version = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Secure key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // PDF text extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // DOCX text extraction
    implementation("org.apache.poi:poi-ooxml:5.2.5")
}
