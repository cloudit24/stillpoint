plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from environment variables (set by CI from repository secrets).
// Without them, release builds are unsigned and debug builds use the local debug key.
val releaseKeystore: String? = System.getenv("STILLPOINT_KEYSTORE")

android {
    namespace = "com.cloudit24.stillpoint"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cloudit24.stillpoint"
        minSdk = 26
        targetSdk = 35
        // Bump both for every release. The GitHub tag must be "v" + versionName.
        versionCode = 54
        versionName = "0.40.0"
    }

    // github: in-app updates from GitHub Releases.
    // fdroid: no self-update code; F-Droid delivers updates.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") { dimension = "distribution" }
        create("fdroid") { dimension = "distribution" }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("STILLPOINT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STILLPOINT_KEY_ALIAS")
                keyPassword = System.getenv("STILLPOINT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Installs the start-up profile on sideloaded phones too (faster first open).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // QR codes for Tools (pure Java, open source, no network).
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    // The real org.json for checks that run on the computer (Android's copy only exists on phones).
    testImplementation("org.json:json:20240303")
}
