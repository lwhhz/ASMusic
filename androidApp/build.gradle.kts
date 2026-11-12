import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kyant.backdrop.catalog"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.asmusic.app"
        minSdk = 23
        targetSdk = 37
        versionCode = 58
        versionName = "1.3.45"
        androidResources.localeFilters += arrayOf("en")
    }

    // Signing material is NOT part of the repository: put the keystore in
    // keys/appstore-release.jks (gitignored) and the passwords in local.properties
    // (also gitignored) or in the matching environment variables.
    signingConfigs {
        create("release") {
            val localProps = Properties().apply {
                val file = rootProject.file("local.properties")
                if (file.exists()) file.inputStream().use { load(it) }
            }
            val store = rootProject.file("keys/appstore-release.jks")
            if (store.exists()) {
                storeFile = store
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                    ?: System.getenv("RELEASE_STORE_PASSWORD").orEmpty()
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                    ?: System.getenv("RELEASE_KEY_ALIAS") ?: "appstore"
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                    ?: System.getenv("RELEASE_KEY_PASSWORD").orEmpty()
            }
        }
    }

    buildTypes {
        release {
            // Rhino is protected by explicit keep rules in proguard-rules.pro; R8
            // removes the unused App Store demo/catalog and shrinks resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (rootProject.file("keys/appstore-release.jks").exists()) {
                signingConfigs.getByName("release")
            } else null
            vcsInfo.include = false
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += arrayOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "kotlin/**",
                "META-INF/*.version",
                "META-INF/**/LICENSE.txt"
            )
        }
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(project(":app"))

    // Netease API proxy client + QR/Cookie login
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Local QR encoding for direct logins (no server-side qrcode module needed)
    implementation("com.google.zxing:core:3.5.3")
    // Real audio playback (media3-common comes transitively via exoplayer)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-datasource:1.5.1")
    implementation("androidx.media3:media3-database:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Importable JS music-source plugins
    implementation("org.mozilla:rhino:1.7.15")
}
