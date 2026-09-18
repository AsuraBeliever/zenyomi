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

    /** Why the viewer is being shown the question. The dialog says so in its own words. */
    enum class Reason {
        /** The first download ever, and the answer becomes the default. */
        FIRST_TIME,

        /** The saved quality is not among the ones these episodes have. */
        NOT_AVAILABLE,

        /** They asked for it, by holding the download button. Just this once. */
        ASKED,
    }

    /** What the caller has to do next. */
    sealed interface Outcome {
        /** Already queued; nothing to show. */
        data object Queued : Outcome

        /** The viewer has to choose before this can be queued. */
        data class Choose(
            val episodes: List<Episode>,
            val qualities: List<DownloadQuality>,
            val reason: Reason,
        ) : Outcome {
            /** Whether the choice also becomes the default. Only the unprompted question does. */
            val remember: Boolean get() = reason == Reason.FIRST_TIME
        }
    }

    /**
     * The qualities of the first episode stand for the batch. Asking per episode would mean a
     * round trip and a dialog each, for a set that in practice comes from one encoder with one
     * ladder.
     *
     * @param ask show the question whatever is saved, for a download where the usual quality
     * is not what is wanted — a long press on the download button. What is picked applies to
     * this download only: changing the default is what Settings is for, and a one-off grab of
     * something smaller should not quietly redefine every download after it.
     */
    suspend fun await(anime: Anime, episodes: List<Episode>, ask: Boolean = false): Outcome {
        if (episodes.isEmpty()) return Outcome.Queued

        if (ask) {
            val offered = qualities(anime, episodes, measure = true)
            // Nothing to choose between is not worth a dialog with one row in it; it is queued
            // at what there is, which is what picking the single row would have done.
            if (offered.size <= 1) {
                return queue(anime, episodes, offered.firstOrNull()?.height, offered.firstOrNull()?.estimatedBytes)
            }
            return Outcome.Choose(episodes, offered, Reason.ASKED)
        }

        val saved = downloadPreferences.quality.get()

        // Once a quality is settled, tapping download does no network at all: it queues, and
        // everything else happens in the background where nobody is waiting on it. Asking the
        // source what it had first meant three or four seconds of a button that looked broken,
        // on every download, to answer a question whose answer was already known.
        //
        // The cost is that an episode which does not have that exact quality is no longer
        // offered the ones it does have — the download takes the closest below it instead and
        // says which in the row. There is nobody to ask by then.
        if (saved != AnimeDownloadPreferences.UNSET) {
            return queue(anime, episodes, saved.takeIf { it > 0 }, null)
        }

        // The first download ever is the one time it is worth the wait: the numbers are about
        // to be read by somebody, and the answer is kept for good.
        val measured = qualities(anime, episodes, measure = true)
        if (measured.size <= 1) return queue(anime, episodes, null, measured.firstOrNull()?.estimatedBytes)
        return Outcome.Choose(episodes, measured, Reason.FIRST_TIME)
    }

    private suspend fun qualities(anime: Anime, episodes: List<Episode>, measure: Boolean) =
        runCatching { getDownloadQualities.await(anime.source, episodes.first(), measure) }
            .getOrNull()
            .orEmpty()

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
