package eu.kanade.tachiyomi.animeextension.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.animesource.AnimeSource
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.domain.source.anime.model.StubAnimeSource

sealed class AnimeExtension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val pkgFactory: String?,
        val sources: List<AnimeSource>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,
        val store: ExtensionStore? = null,
    ) : AnimeExtension()

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val sources: List<Source>,
        val apkUrl: String,
        val iconUrl: String,
        val store: ExtensionStore,
    ) : AnimeExtension() {

        data class Source(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
        ) {
            fun toStubAnimeSource(): StubAnimeSource {
                return StubAnimeSource(
                    id = this.id,
                    lang = this.lang,
                    name = this.name,
                )
            }
        }
    }

    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
    ) : AnimeExtension()
}

/**
 * Repositories flag an abandoned source by putting "(Dead)" in its name, which is the only
 * signal there is: nothing in the index says whether a source still works. Reading it is
 * better than a list of our own, which would be stale within a week.
 */
val AnimeExtension.isDead: Boolean
    get() = name.contains("(Dead)", ignoreCase = true)

/** The name without the repository's "Aniyomi: " prefix or its dead marker. */
val AnimeExtension.displayName: String
    get() = name
        .removePrefix("Aniyomi: ")
        .replace("(Dead)", "", ignoreCase = true)
        .trim()
