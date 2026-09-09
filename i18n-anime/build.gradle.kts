import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n.anime"

        withHostTest { }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    dependencies {
        api(libs.moko.resources)
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// Anime-only strings. Kept apart from :i18n so that Mihon's catalogue stays byte
// identical and its Weblate translations keep merging cleanly.
// See docs/adr/0001-arbol-paralelo-anime.md
multiplatformResources {
    resourcesClassName.set("ANMR")
    resourcesPackage.set("tachiyomi.i18n.anime")
}
