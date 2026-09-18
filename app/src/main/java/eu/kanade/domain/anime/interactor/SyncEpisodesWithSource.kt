package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.model.copyFromSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.NoEpisodesException
import tachiyomi.domain.episode.model.toEpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.episode.service.EpisodeRecognition
import kotlin.time.Clock

/**
 * Brings an entry's episodes in line with what its source reports.
 *
 * Narrower than Mihon's SyncChaptersWithSource, which also drives download cleanup and
 * excluded scanlators. Neither exists on the anime side yet, so this does the part that
 * matters now: add what is new, update what changed, drop what the source no longer
 * lists, and never lose watch state — not on an episode that is still there, and not on
 * one the source re-lists under a different url.
 */
@Inject
class SyncEpisodesWithSource(
    private val episodeRepository: EpisodeRepository,
) {

    /**
     * Runs on IO: asking a source for its episode list is a network call, and extensions do
     * not dispatch it themselves. Called from a ViewModel's default scope it threw
     * NetworkOnMainThreadException, which was swallowed and showed up as "0 episodes" —
     * indistinguishable from a source that genuinely had none.
     */
    suspend fun await(anime: Anime, source: AnimeSource): List<Episode> = withIOContext {
        val sourceEpisodes = source.getEpisodeList(anime.toSAnime())

        // A source that answers with nothing is almost never an entry that lost every
        // episode — it is a blocked request, an expired session, a mirror that went down.
        // Taken at face value the pass below would delete every row and every watch
        // position with them, so it is refused outright, as Mihon refuses it for chapters.
        // The local source is exempt: an empty folder there really is empty.
        if (sourceEpisodes.isEmpty() && source.id != LOCAL_ANIME_SOURCE_ID) {
            throw NoEpisodesException()
        }

        val dbEpisodes = episodeRepository.getEpisodeByAnimeId(anime.id)
        val byUrl = dbEpisodes.associateBy { it.url }
        val now = Clock.System.now().toEpochMilliseconds()

        val toInsert = mutableListOf<Episode>()
        val toUpdate = mutableListOf<Episode>()

        sourceEpisodes.forEachIndexed { index, sEpisode ->
            val number = EpisodeRecognition.parseEpisodeNumber(
                anime.title,
                sEpisode.name,
                sEpisode.episode_number.toDouble(),
            )
            val existing = byUrl[sEpisode.url]
            if (existing == null) {
                toInsert += Episode.create()
                    .copyFromSEpisode(sEpisode)
                    .copy(
                        animeId = anime.id,
                        episodeNumber = number,
                        sourceOrder = index.toLong(),
                        dateFetch = now,
                    )
            } else {
                val updated = existing
                    .copyFromSEpisode(sEpisode)
                    .copy(episodeNumber = number, sourceOrder = index.toLong())
                if (updated != existing) toUpdate += updated
            }
        }

        val sourceUrls = sourceEpisodes.map { it.url }.toSet()
        val removed = dbEpisodes.filterNot { it.url in sourceUrls }
        val toDelete = removed.map { it.id }

        // An episode matched by url alone disappears the moment its source changes the url
        // it hands out — a domain move, a mirror, a token baked into the link — and comes
        // straight back as a brand new row. Watch state went with it: an episode stopped at
        // 12:34 reappeared at zero, which is the one thing a resume point must not do.
        //
        // Mihon's SyncChaptersWithSource carries `read` and `bookmark` across that gap by
        // recognized chapter number, and the same trick works here. The position comes too,
        // which Mihon has no need of: a re-added chapter it considered read needs no page to
        // return to, but a half-watched episode does.
        val carried = removed
            .filter { it.isRecognizedNumber }
            .groupBy { it.episodeNumber }
            // Most-watched wins where a number has several rows, so a duplicate that was
            // never opened cannot bury the one that was.
            .mapValues { (_, episodes) -> episodes.maxBy { it.lastSecondSeen } }

        val restored = toInsert.map { episode ->
            val previous = carried[episode.episodeNumber]
                ?.takeIf { episode.isRecognizedNumber }
                ?: return@map episode
            episode.copy(
                seen = previous.seen,
                bookmark = previous.bookmark,
                lastSecondSeen = previous.lastSecondSeen,
                totalSeconds = previous.totalSeconds,
                // Keeping the original fetch date keeps a re-added episode out of the
                // Updates tab, where it is not news.
                dateFetch = previous.dateFetch,
            )
        }

        if (toDelete.isNotEmpty()) episodeRepository.removeEpisodesWithIds(toDelete)
        if (toUpdate.isNotEmpty()) {
            episodeRepository.updateAllEpisodes(toUpdate.map { it.toEpisodeUpdate() })
        }
        val inserted = if (restored.isNotEmpty()) {
            episodeRepository.addAllEpisodes(restored)
        } else {
            emptyList()
        }
        inserted
    }

    private companion object {
        /** Matches tachiyomi.source.local.anime.LocalAnimeSource.ID without depending on it. */
        const val LOCAL_ANIME_SOURCE_ID = 0L
    }
}
