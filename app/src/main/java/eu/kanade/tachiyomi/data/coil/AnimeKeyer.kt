package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeCover

/**
 * Cache key for an anime cover. Includes coverLastModified so a refreshed cover replaces the
 * cached one instead of being served stale for the life of the install.
 */
class AnimeKeyer : Keyer<Anime> {
    override fun key(data: Anime, options: Options): String {
        return "${data.thumbnailUrl};${data.coverLastModified}"
    }
}

/**
 * Cache key for a cover held as an [AnimeCover] rather than a whole [tachiyomi.domain.anime.model.Anime].
 */
class AnimeCoverKeyer : Keyer<AnimeCover> {
    override fun key(data: AnimeCover, options: Options): String {
        return "${data.url};${data.lastModified}"
    }
}
