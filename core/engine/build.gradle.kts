plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.okio)
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        // Code that is identical on Android and Desktop JVM (FTP client, proxy support).
        androidMain.get().kotlin.srcDir("src/jvmShared/kotlin")
        jvmMain.get().kotlin.srcDir("src/jvmShared/kotlin")
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

android {
    namespace = "app.downly.core.engine"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
