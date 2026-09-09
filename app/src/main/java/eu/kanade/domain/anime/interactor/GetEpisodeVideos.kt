package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Resolves an episode into the videos a source can actually play.
 *
 * The anime source API offers two routes and a source implements one of them: the
 * current one lists hosters and then videos per hoster, the older one returns videos for
 * the episode directly. Both throw from their default implementation, so the only way to
 * tell them apart is to try the current route and fall back.
 *
 * A hoster may carry its videos already, or be marked lazy and need a second call.
 */
@Inject
class GetEpisodeVideos(
    private val sourceManager: AnimeSourceManager,
) {

    suspend fun await(sourceId: Long, episode: Episode): List<Video> = withIOContext {
        val source = sourceManager.get(sourceId) ?: return@withIOContext emptyList()
        val sEpisode = episode.toSEpisode()

        val hosters = try {
            source.getHosterList(sEpisode)
        } catch (e: IllegalStateException) {
            // Source predates hosters; ask it for videos directly.
            return@withIOContext runCatching { source.getVideoList(sEpisode) }.getOrDefault(emptyList())
        } catch (e: UnsupportedOperationException) {
            return@withIOContext runCatching { source.getVideoList(sEpisode) }.getOrDefault(emptyList())
        }

        hosters.flatMap { hoster ->
            hoster.videoList ?: runCatching { source.getVideoList(hoster) }.getOrDefault(emptyList())
        }
    }

    /** The video a player should open first: the source's own preference, else the best resolution. */
    fun List<Video>.best(): Video? =
        firstOrNull { it.preferred } ?: maxByOrNull { it.resolution ?: 0 }

    companion object {
        val NO_HOSTER_LIST = Hoster.NO_HOSTER_LIST
    }
}
