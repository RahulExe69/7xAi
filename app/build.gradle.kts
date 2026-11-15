plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val configuredAbis = providers.gradleProperty("sevenxAbis").orNull ?: "arm64-v8a"
val buildAbis = configuredAbis.split(",").map { it.trim() }.filter { it.isNotEmpty() }
require(buildAbis.isNotEmpty() && buildAbis.all { it in setOf("arm64-v8a", "x86_64") }) {
    "sevenxAbis must be arm64-v8a and/or x86_64"
}

android {
    namespace = "com.sevenx.ai"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.sevenx.ai"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += buildAbis
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        animationsDisabled = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("debug") {
            // The Node 24.22.1-0 library is a debug-only POC. It must never be
            // included in the shipping APK; release builds are blocked below.
            jniLibs.srcDir(rootProject.file("runtime/poc/lite"))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyNodeRuntimeForRelease by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine(
        "node",
        "scripts/validate-runtime-manifest.mjs",
        "runtime/node-runtime-lite.json",
        "--for-release",
    )
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyNodeRuntimeForRelease)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
