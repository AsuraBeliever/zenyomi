import zenyomi.gradle.tasks.FetchTorrServerTask
import java.io.FileInputStream
import java.util.Properties
import kotlin.io.encoding.Base64

// The torrent add-on: its own APK, carrying TorrServer (GPL-3.0). Zenyomi installs it on
// demand and talks to it over a bound service and localhost HTTP, never linking any of it.
// See docs/adr/0008-torrent-como-complemento.md. This module is GPL-3.0 (see its LICENSE).

plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.spotless)
}

val torrServerVersion = "MatriX.145.1"

val keystorePropertiesFile = rootProject.file("keystore.properties")

// Shared with app/, which compares the installed add-on against it.
val addonVersion = file("addon.properties").inputStream().use { Properties().apply { load(it) } }

android {
    namespace = "app.zenyomi.torrent"

    defaultConfig {
        applicationId = "app.zenyomi.torrent"

        versionCode = addonVersion.getProperty("versionCode").toInt()
        versionName = addonVersion.getProperty("versionName")

        buildConfigField("String", "TORRSERVER_VERSION", "\"$torrServerVersion\"")
    }

    // Same key as the app: Zenyomi only binds to an add-on signed like itself, and the
    // permission that guards the service is signature-level.
    if (System.getenv("MIHON_GITHUB_RELEASE").toBoolean()) {
        val tempStoreFile = file(System.getenv("RUNNER_TEMP")).resolve("torrent.keystore")
        tempStoreFile.outputStream().use { it.write(Base64.decode(System.getenv("storeFileBase64"))) }

        signingConfigs {
            create("release") {
                storeFile = tempStoreFile
                storePassword = System.getenv("storePassword")
                keyAlias = System.getenv("keyAlias")
                keyPassword = System.getenv("keyPassword")
            }
        }
    } else if (keystorePropertiesFile.exists()) {
        val keystoreProperties = FileInputStream(keystorePropertiesFile).use { Properties().apply { load(it) } }

        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        val debug = getByName("debug") {
            // Pairs with app.zenyomi.dev, which is signed with the debug key too.
            applicationIdSuffix = ".dev"
        }
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release") ?: debug.signingConfig
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    packaging {
        jniLibs {
            // Unlike the app, compressed: a 66 MB executable is ~23 MB in the download, and
            // only whoever turns torrent on pays for the extracted copy.
            useLegacyPackaging = true
            // A Go executable, not a library: stripping it would only risk breaking it.
            keepDebugSymbols += "**/libtorrserver.so"
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

val fetchTorrServer = tasks.register<FetchTorrServerTask>("fetchTorrServer") {
    version.set(torrServerVersion)
    assets.set(
        mapOf(
            "arm64-v8a" to "TorrServer-android-arm64:2cdd665e2d3b89741c6121b6d22dde45fe64254ee71b9e154a54cc400994ebff",
            "armeabi-v7a" to "TorrServer-android-arm7:1cc9238f787e3a1273b0d57767b9ac8781aa17f840136227142a55ff51a7dc0f",
            "x86_64" to "TorrServer-android-amd64:590ffb9b3b1f90ca93a8ab8c9dd3f38423073ed597762abc69365cb73e779844",
            "x86" to "TorrServer-android-386:3dd7ec77b3fd7de0ed50f45556e6a5115862c62c39eb650814c4683d151a492d",
        ),
    )
    cacheDir.set(gradle.gradleUserHomeDir.resolve("caches/zenyomi-torrserver"))
    outputDir.set(layout.buildDirectory.dir("generated/torrserver/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(fetchTorrServer) { it.outputDir }
    }
}
