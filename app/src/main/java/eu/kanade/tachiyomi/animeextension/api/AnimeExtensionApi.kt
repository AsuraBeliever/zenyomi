package eu.kanade.tachiyomi.animeextension.api

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.animeextension.model.AnimeLoadResult
import eu.kanade.tachiyomi.animeextension.util.AnimeExtensionLoader
import mihon.domain.animeextension.repository.AnimeExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

/**
 * Remote catalogue of anime extensions, read from the stores the user has added.
 *
 * Mirrors [eu.kanade.tachiyomi.extension.api.ExtensionApi] against the anime store
 * repository, so an anime repository URL behaves the same way a manga one does.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeExtensionApi(
    private val repository: AnimeExtensionStoreRepository,
) {

    suspend fun findExtensions(): List<AnimeExtension.Available> {
        return withIOContext { repository.fetchExtensions() }
    }

    /**
     * Installed extensions that a store offers a newer build of.
     *
     * Returns them rather than notifying: the anime side has no update notifier yet, and
     * whoever calls this can decide what to do with the list.
     */
    suspend fun checkForUpdates(context: Context): List<AnimeExtension.Installed> {
        repository.refreshAll()
        val available = findExtensions()

        return AnimeExtensionLoader.loadExtensions(context)
            .filterIsInstance<AnimeLoadResult.Success>()
            .map { it.extension }
            .filter { installed ->
                val remote = available.find { it.pkgName == installed.pkgName } ?: return@filter false
                remote.versionCode > installed.versionCode || remote.libVersion > installed.libVersion
            }
    }
}
