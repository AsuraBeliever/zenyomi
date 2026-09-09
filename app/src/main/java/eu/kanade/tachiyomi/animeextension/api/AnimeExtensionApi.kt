package eu.kanade.tachiyomi.animeextension.api

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension

/**
 * Remote catalogue of anime extensions.
 *
 * Zenyomi has no anime extension stores yet: Mihon's store chain
 * ([mihon.domain.extension.repository.ExtensionStoreRepository] and the network models
 * behind it) is typed against the manga [eu.kanade.tachiyomi.extension.model.Extension],
 * so an anime counterpart has to be ported before this can return anything.
 *
 * Until then there genuinely are no available anime extensions to list, and an empty
 * result is the honest answer rather than a placeholder. Anime extensions that are
 * already installed on the device as APKs are unaffected: those are discovered by
 * [eu.kanade.tachiyomi.animeextension.util.AnimeExtensionLoader], which does not go
 * through here.
 *
 * See docs/PORTING_LOG.md, "Pendientes conocidos".
 */
@Inject
@SingleIn(AppScope::class)
class AnimeExtensionApi {

    suspend fun findExtensions(): List<AnimeExtension.Available> = emptyList()

    suspend fun checkForUpdates(context: Context): List<AnimeExtension.Installed>? = null
}
