package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.model.copyFromSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
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
 * lists, and never touch watch state on an episode that is still there.
 */
@Inject
class SyncEpisodesWithSource(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(anime: Anime, source: AnimeSource): List<Episode> {
        val sourceEpisodes = source.getEpisodeList(anime.toSAnime())
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
        val toDelete = dbEpisodes.filterNot { it.url in sourceUrls }.map { it.id }

        if (toDelete.isNotEmpty()) episodeRepository.removeEpisodesWithIds(toDelete)
        if (toUpdate.isNotEmpty()) {
            episodeRepository.updateAllEpisodes(toUpdate.map { it.toEpisodeUpdate() })
        }
        val inserted = if (toInsert.isNotEmpty()) {
            episodeRepository.addAllEpisodes(toInsert)
        } else {
            emptyList()
        }
        return inserted
    }
}
