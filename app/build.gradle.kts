plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.autosynced.lyrics"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autosynced.lyrics"
        minSdk = 26            // Android 8.0: needed for the floating-window type used here
        targetSdk = 35
        // On GitHub, every build counts up (so Android sees each one as newer); locally it's 2.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(1) ?: 2
        versionName = "1.0"
    }

    // One fixed signing key (debug.keystore, committed on purpose) so every build from GitHub
    // installs over the previous one as an update instead of needing an uninstall first.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true        // the version shown on the About page
    }
    packaging {
        resources {
            // licence / metadata files that several libraries (Claude SDK, Jackson, OkHttp) all ship
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES",
                "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Claude, for translating lyric lines no music site has translated
    implementation("com.anthropic:anthropic-java:3.14.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // Android's org.json, for tests on the PC
}
