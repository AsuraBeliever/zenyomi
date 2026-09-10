package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.toBackupAnime
import eu.kanade.tachiyomi.data.backup.models.toBackupEpisode
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Writes the anime library into the same backup file as the manga one.
 *
 * Reuses [BackupOptions] rather than adding anime-specific switches: a user who asks for their
 * library, chapters and history in a backup means both libraries. Splitting the toggles would
 * make it possible to back up half an app by accident.
 */
@Inject
class AnimeBackupCreator(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getEpisode: GetEpisode,
    private val getAnimeHistory: GetAnimeHistory,
    private val sourceManager: AnimeSourceManager,
) {

    suspend operator fun invoke(anime: List<Anime>, options: BackupOptions): List<BackupAnime> =
        anime.map { backupAnime(it, options) }

    suspend fun sources(anime: List<BackupAnime>): List<BackupSource> = anime
        .map { it.source }
        .distinct()
        .map { sourceId ->
            val source = sourceManager.getOrStub(sourceId)
            BackupSource(name = source.name, sourceId = sourceId)
        }

    private suspend fun backupAnime(anime: Anime, options: BackupOptions): BackupAnime {
        val animeObject = anime.toBackupAnime()

        if (options.chapters) {
            getEpisodesByAnimeId.await(anime.id)
                .map { it.toBackupEpisode() }
                .takeUnless { it.isEmpty() }
                ?.let { animeObject.episodes = it }
        }

        if (options.history) {
            // History is keyed by episode id locally, but ids mean nothing on another device,
            // so each entry is resolved back to the episode url it belongs to.
            getAnimeHistory.await(anime.id)
                .mapNotNull { history ->
                    val episode = getEpisode.await(history.episodeId) ?: return@mapNotNull null
                    BackupAnimeHistory(episode.url, history.seenAt?.time ?: 0L)
                }
                .takeUnless { it.isEmpty() }
                ?.let { animeObject.history = it }
        }

        return animeObject
    }
}
