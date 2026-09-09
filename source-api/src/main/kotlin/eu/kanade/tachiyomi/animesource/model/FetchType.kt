package eu.kanade.tachiyomi.animesource.model

/**
 * Define what type of content an anime entry should fetch.
 *
 * The fetch type is decided when the entry is initialized and does not change
 * afterwards: an entry either exposes seasons or episodes, never both.
 *
 * @since extensions-lib 16
 */
enum class FetchType {
    /**
     * The entry only calls `getSeasonList`.
     */
    Seasons,

    /**
     * The entry only calls `getEpisodeList`.
     */
    Episodes,
}
