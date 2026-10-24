plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:engine"))
            api(project(":core:data"))
            implementation(libs.kotlinx.coroutines.core)
            api(compose.runtime)
            api(compose.foundation)
            implementation(compose.material3)
        }
    }
}

android {
    namespace = "app.downly.ui"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
