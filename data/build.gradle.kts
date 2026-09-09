plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

android {
    namespace = "tachiyomi.data"

    sqldelight {
        databases {
            create("Database") {
                packageName.set("tachiyomi.data")
                dialect(libs.sqldelight.sqliteDialect338)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
                generateAsync.set(true)
            }
            // Anime lives in its own database, exactly as Aniyomi keeps it, so that
            // Mihon's manga schema is never touched. See docs/adr/0001-arbol-paralelo-anime.md
            create("AnimeDatabase") {
                packageName.set("tachiyomi.data.anime")
                dialect(libs.sqldelight.sqliteDialect338)
                schemaOutputDirectory.set(project.file("./src/main/sqldelightanime"))
                srcDirs.from(project.file("./src/main/sqldelightanime"))
                generateAsync.set(true)
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(libs.metro.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.injekt)

    implementation(libs.kotlinx.datetime)

    api(libs.bundles.sqldelight)
}
