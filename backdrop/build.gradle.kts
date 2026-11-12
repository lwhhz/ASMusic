import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    android {
        minSdk = 21
        compileSdk = 37
        buildToolsVersion = "37.0.0"
        namespace = "com.kyant.backdrop"
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    applyDefaultHierarchyTemplate()

    jvm("desktop")

    js {
        browser()
    }
    wasmJs {
        browser()
    }

    macosArm64()
    iosArm64("iosArm64")
    iosSimulatorArm64("iosSimulatorArm64")

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.ui.graphics)
                implementation(libs.kyant.shapes)
                implementation("org.jetbrains:annotations:26.1.0")
            }
        }

        val skikoMain = create("skikoMain") {
            dependsOn(commonMain)
        }

        val desktopMain = getByName("desktopMain") {
            dependsOn(skikoMain)
        }

        val macosArm64Main = getByName("macosArm64Main") {
            dependsOn(skikoMain)
        }

        val iosMain = getByName("iosMain") {
            dependsOn(skikoMain)
        }

        val iosArm64Main = getByName("iosArm64Main") {
        }

        val iosSimulatorArm64Main = getByName("iosSimulatorArm64Main") {
        }

        val jsMain = getByName("jsMain") {
            dependsOn(skikoMain)
        }

        val wasmJsMain = getByName("wasmJsMain") {
            dependsOn(skikoMain)
        }
    }
}

// The catalog app consumes this module as a local :backdrop project dependency.
// Maven Central publishing was removed from this fork; upstream coordinates:
// io.github.kyant0:backdrop:2.0.1 (Apache-2.0, https://github.com/Kyant0/AndroidLiquidGlass)
