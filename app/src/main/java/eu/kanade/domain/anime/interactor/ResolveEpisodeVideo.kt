package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.presentation.anime.AnimeSourceHealth
import eu.kanade.presentation.anime.NoVideoFoundException
import eu.kanade.presentation.anime.SourceOutdatedException
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.animeplayer.PlaybackRequest
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Turns an episode into something the player can open.
 *
 * The steps are not obvious and getting any of them wrong is visible to the viewer: a
 * downloaded copy has to win over the network, a url resolved a moment ago has to be reused
 * instead of paying the whole extractor chain again, and an episode that resolves to nothing
 * has to say *why* — a dead extension and an episode with no mirrors ask opposite things of
 * the person watching.
 *
 * All of that lived in [eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsViewModel] and was
 * copied, with drift, into Updates and History: the history copy had neither the cache nor the
 * reason. It moves here because the player needs it too — it is what lets an episode open the
 * next one without going back to a list — and a fourth copy was not worth writing.
 *
 * Reports failure rather than throwing: a source that cannot answer is a normal outcome.
 */
@Inject
@SingleIn(AppScope::class)
class ResolveEpisodeVideo(
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
    private val getEpisodeVideos: GetEpisodeVideos,
    private val sourceHealth: AnimeSourceHealth,
) {

    suspend fun await(anime: Anime, episode: Episode): Result {
        // A downloaded copy wins: it plays offline and costs the source nothing. Off the main
        // thread, because looking for the file is a round trip to the storage provider and
        // this is called from a tap.
        val source = sourceManager.get(anime.source)
        val local = source?.let {
            withIOContext { downloadManager.downloadedUri(anime, it, episode) }
        }
        if (local != null) return Result.Playable(PlaybackRequest.local(local))

        // Lo que este episodio resolvio hace un momento sirve tal cual. Salir del reproductor
        // y volver a entrar repetia toda la cadena de peticiones para acabar abriendo
        // exactamente el mismo video.
        getEpisodeVideos.cached(episode.id)?.let { known ->
            return Result.Playable(PlaybackRequest.from(known))
        }

        val result = runCatching { getEpisodeVideos.await(anime.source, episode) }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not resolve ${episode.name}" } }
        val video = result.getOrDefault(emptyList())
            .let { getEpisodeVideos.playable(anime.source, it) }
            ?: return Result.Failed(result.exceptionOrNull() ?: noVideoReason(anime.source))

        getEpisodeVideos.remember(episode.id, video)
        // The whole video travels, not just its url: the headers it was resolved with and any
        // side-car subtitle track are as much a part of playing it as the url is.
        return Result.Playable(PlaybackRequest.from(video))
    }

    /**
     * Why an episode produced no video: the episode, or the extension.
     *
     * Worth separating because the two ask opposite things of the user. Our last sweep of the
     * installed sources already knows which ones stopped working; saying "no video found for
     * this episode" about one of those sends people to try episode after episode of a source
     * that will never answer.
     */
    private fun noVideoReason(sourceId: Long): Throwable =
        if (sourceHealth.statusOf(sourceId) != null) {
            SourceOutdatedException()
        } else {
            NoVideoFoundException()
        }

    sealed interface Result {
        data class Playable(val request: PlaybackRequest) : Result

        /** The throwable travels, not a string: what the viewer is told is a presentation
         * decision, and [eu.kanade.presentation.anime.animeSourceErrorText] is where it is made. */
        data class Failed(val reason: Throwable) : Result
    }
}
