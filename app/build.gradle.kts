import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) load(versionFile.inputStream())
}
val vMajor = (versionProps["VERSION_MAJOR"] as? String)?.toIntOrNull() ?: 0
val vMinor = (versionProps["VERSION_MINOR"] as? String)?.toIntOrNull() ?: 1
val vPatch = (versionProps["VERSION_PATCH"] as? String)?.toIntOrNull() ?: 0
val vBuild = (versionProps["VERSION_BUILD"] as? String)?.toIntOrNull() ?: 1

android {
    namespace = "com.bikearcade.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bikearcade.app"
        minSdk = 28
        targetSdk = 34
        versionCode = vBuild
        versionName = "$vMajor.$vMinor.$vPatch"
    }

    signingConfigs {
        create("release") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
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
        buildConfig = true
    }
}

tasks.register("bumpBuildNumber") {
    doLast {
        val next = vBuild + 1
        versionFile.writeText(
            "VERSION_MAJOR=$vMajor\nVERSION_MINOR=$vMinor\nVERSION_PATCH=$vPatch\nVERSION_BUILD=$next\n"
        )
        println("Version bumped: $vMajor.$vMinor.$vPatch build $vBuild -> $next")
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
        finalizedBy("bumpBuildNumber")
    }
}

dependencies {
    // Module dependencies
    implementation(project(":emulator"))
    implementation(project(":metrics"))
    implementation(project(":overlay"))
    implementation(project(":ui"))
    implementation(project(":media"))
    implementation(project(":settings"))

    // VeloLauncher fitness API (shared profile, workouts, ride logging)
    implementation(files("../metrics/libs/velofit.jar"))

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
