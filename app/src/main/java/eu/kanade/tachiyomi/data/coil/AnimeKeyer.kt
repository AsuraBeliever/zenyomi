package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.anime.model.Anime

/**
 * Cache key for an anime cover. Includes coverLastModified so a refreshed cover replaces the
 * cached one instead of being served stale for the life of the install.
 */
class AnimeKeyer : Keyer<Anime> {
    override fun key(data: Anime, options: Options): String {
        return "${data.thumbnailUrl};${data.coverLastModified}"
    }
}
