package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import java.util.Date

/**
 * Restores the anime half of a backup.
 *
 * Merges rather than replaces: an anime already in the library keeps its row and only gains
 * what the backup knows, and an episode already present keeps whichever watch position is
 * further along. Restoring a backup onto a device that has since been used should never move
 * the user backwards.
 */
@Inject
class AnimeRestorer(
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val updateAnime: UpdateAnime,
    private val episodeRepository: EpisodeRepository,
    private val upsertAnimeHistory: UpsertAnimeHistory,
) {

    suspend fun restore(backupAnime: BackupAnime) {
        val anime = networkToLocalAnime.await(backupAnime.toAnimeImpl())

        // networkToLocalAnime returns the existing row untouched when the anime is already
        // known, so the backup's own values are applied explicitly.
        updateAnime.await(
            AnimeUpdate(
                id = anime.id,
                favorite = backupAnime.favorite || anime.favorite,
                dateAdded = leastNonZero(anime.dateAdded, backupAnime.dateAdded),
                episodeFlags = backupAnime.episodeFlags.toLong(),
                viewerFlags = backupAnime.viewerFlags.toLong(),
            ),
        )

        val restoredEpisodes = restoreEpisodes(anime, backupAnime.episodes)
        restoreHistory(backupAnime, restoredEpisodes)
    }

    private suspend fun restoreEpisodes(anime: Anime, backupEpisodes: List<BackupEpisode>): List<Episode> {
        if (backupEpisodes.isEmpty()) return emptyList()

        val existing = episodeRepository.getEpisodeByAnimeId(anime.id).associateBy { it.url }

        val toInsert = mutableListOf<Episode>()
        val toUpdate = mutableListOf<EpisodeUpdate>()

        backupEpisodes.forEach { backupEpisode ->
            val current = existing[backupEpisode.url]
            if (current == null) {
                toInsert += backupEpisode.toEpisodeImpl().copy(animeId = anime.id)
            } else {
                // Whichever side watched further wins, so restoring an older backup cannot
                // un-watch an episode.
                toUpdate += EpisodeUpdate(
                    id = current.id,
                    seen = current.seen || backupEpisode.seen,
                    bookmark = current.bookmark || backupEpisode.bookmark,
                    fillermark = current.fillermark || backupEpisode.fillermark,
                    lastSecondSeen = maxOf(current.lastSecondSeen, backupEpisode.lastSecondSeen),
                    totalSeconds = maxOf(current.totalSeconds, backupEpisode.totalSeconds),
                )
            }
        }

        if (toInsert.isNotEmpty()) episodeRepository.addAllEpisodes(toInsert)
        if (toUpdate.isNotEmpty()) episodeRepository.updateAllEpisodes(toUpdate)

        // Re-read instead of stitching together the inserted and pre-existing lists: history is
        // matched against these ids, and one read is both simpler and certain to reflect what is
        // actually in the table.
        return episodeRepository.getEpisodeByAnimeId(anime.id)
    }

    private suspend fun restoreHistory(backupAnime: BackupAnime, episodes: List<Episode>) {
        if (backupAnime.history.isEmpty()) return

        val byUrl = episodes.associateBy { it.url }
        backupAnime.history.forEach { entry ->
            val episode = byUrl[entry.url] ?: return@forEach
            if (entry.lastSeen <= 0) return@forEach
            upsertAnimeHistory.await(AnimeHistoryUpdate(episode.id, Date(entry.lastSeen)))
        }
    }

    /**
     * Keeps the earlier of two dates, treating 0 as "unknown" rather than as the epoch — a
     * library entry added before the backup was taken should keep its original date.
     */
    private fun leastNonZero(a: Long, b: Long): Long = when {
        a == 0L -> b
        b == 0L -> a
        else -> minOf(a, b)
    }
}
