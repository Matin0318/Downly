import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

kotlin { jvmToolchain(17) }

// Release signing. The values come from keystore.properties (never committed) or from environment variables (CI).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(env)

android {
    namespace = "app.downly.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.downly.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        create("release") {
            val path = signingValue("storeFile", "DOWNLY_KEYSTORE")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = signingValue("storePassword", "DOWNLY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DOWNLY_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DOWNLY_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed only when a key is configured; otherwise the release APK is built unsigned (CI check).
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) signingConfig = releaseSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":core:engine"))
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.libtorrent4j)
    implementation(libs.libtorrent4j.arm)
    implementation(libs.libtorrent4j.arm64)
    implementation(libs.libtorrent4j.x64)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
