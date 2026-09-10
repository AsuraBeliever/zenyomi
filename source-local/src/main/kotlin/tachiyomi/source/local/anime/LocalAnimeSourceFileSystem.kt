package tachiyomi.source.local.anime

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.domain.storage.service.StorageManager

/**
 * Anime counterpart of [tachiyomi.source.local.io.LocalSourceFileSystem].
 */
@Inject
@SingleIn(AppScope::class)
class LocalAnimeSourceFileSystem(
    private val storageManager: StorageManager,
) {

    fun getBaseDirectory(): UniFile? = storageManager.getLocalAnimeSourceDirectory()

    fun getFilesInBaseDirectory(): List<UniFile> =
        getBaseDirectory()?.listFiles().orEmpty().toList()

    fun getAnimeDirectory(name: String): UniFile? =
        getBaseDirectory()?.findFile(name)?.takeIf { it.isDirectory }

    fun getFilesInAnimeDirectory(name: String): List<UniFile> =
        getAnimeDirectory(name)?.listFiles().orEmpty().toList()
}
