package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode

/**
 * Queues episodes at the quality the viewer wants, asking first if there is a reason to ask.
 *
 * Shared rather than living in one screen, which is the mistake this was written to correct:
 * the gate was wired into the entry screen only, so downloading from Updates or from the
 * library — which is what you actually do when new episodes appear — went straight past it.
 * No question, no quality applied, and no size for the progress bar to be a fraction of.
 *
 * Every download in the app goes through here now, and a screen's only job is to show the
 * dialog when it is handed one.
 */
@Inject
class StartAnimeDownload(
    private val downloadManager: AnimeDownloadManager,
    private val downloadPreferences: AnimeDownloadPreferences,
    private val getDownloadQualities: GetDownloadQualities,
) {

    /** What the caller has to do next. */
    sealed interface Outcome {
        /** Already queued; nothing to show. */
        data object Queued : Outcome

        /**
         * The viewer has to choose before this can be queued.
         *
         * @param remember whether the choice also becomes the default. True the first time
         * anything is downloaded, which is the only time the question is asked unprompted;
         * false when the saved default is simply not on offer for these episodes.
         */
        data class Choose(
            val episodes: List<Episode>,
            val qualities: List<DownloadQuality>,
            val remember: Boolean,
        ) : Outcome
    }

    /**
     * The qualities of the first episode stand for the batch. Asking per episode would mean a
     * round trip and a dialog each, for a set that in practice comes from one encoder with one
     * ladder.
     */
    suspend fun await(anime: Anime, episodes: List<Episode>): Outcome {
        if (episodes.isEmpty()) return Outcome.Queued
        val qualities = runCatching { getDownloadQualities.await(anime.source, episodes.first()) }
            .getOrNull()
            .orEmpty()
        val saved = downloadPreferences.quality.get()
        return when {
            // Nothing to choose between. Queue it and say nothing: a dialog with one option in
            // it is a dialog that should not have opened.
            qualities.size <= 1 -> queue(anime, episodes, null, qualities.firstOrNull()?.estimatedBytes)
            saved == AnimeDownloadPreferences.UNSET -> Outcome.Choose(episodes, qualities, remember = true)
            saved == AnimeDownloadPreferences.BEST ->
                queue(anime, episodes, null, qualities.firstOrNull()?.estimatedBytes)
            qualities.any { it.height == saved } ->
                queue(anime, episodes, saved, qualities.first { it.height == saved }.estimatedBytes)
            // The usual quality is not on offer here, so the viewer sees what is. That choice
            // is for these episodes only; the default stays as it was.
            else -> Outcome.Choose(episodes, qualities, remember = false)
        }
    }

    /** Called once the viewer has picked from an [Outcome.Choose]. */
    fun confirm(anime: Anime, choice: Outcome.Choose, height: Int?) {
        if (choice.remember) {
            downloadPreferences.quality.set(height ?: AnimeDownloadPreferences.BEST)
        }
        queue(anime, choice.episodes, height, choice.qualities.firstOrNull { it.height == height }?.estimatedBytes)
    }

    private fun queue(anime: Anime, episodes: List<Episode>, quality: Int?, estimatedBytes: Long?): Outcome {
        downloadManager.enqueue(anime, episodes, quality, estimatedBytes)
        return Outcome.Queued
    }
}
