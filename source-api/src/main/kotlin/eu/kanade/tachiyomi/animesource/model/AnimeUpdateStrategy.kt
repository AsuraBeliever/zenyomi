package eu.kanade.tachiyomi.animesource.model

/**
 * Define the update strategy for a single SAnime.
 * The strategy used will only take effect on the library update.
 *
 * Anime-side counterpart of [eu.kanade.tachiyomi.source.model.UpdateStrategy]. The two
 * are kept separate on purpose so that the manga source API stays exactly as Mihon
 * ships it; see docs/adr/0001-arbol-paralelo-anime.md.
 *
 * @since extensions-lib 1.4
 */
@Suppress("UNUSED")
enum class AnimeUpdateStrategy {
    /**
     * Series marked as always update will be included in the library
     * update if they aren't excluded by additional restrictions.
     */
    ALWAYS_UPDATE,

    /**
     * Series marked as only fetch once will be automatically skipped
     * during library updates. Useful for series already known to be finished.
     */
    ONLY_FETCH_ONCE,
}
