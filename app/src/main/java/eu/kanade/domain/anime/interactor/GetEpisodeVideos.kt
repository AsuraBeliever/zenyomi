package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import kotlin.time.Duration.Companion.seconds

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

    /**
     * @throws kotlinx.coroutines.TimeoutCancellationException if the source takes longer than
     * [TIMEOUT]. Extensions run third-party code against sites that can stall indefinitely, and
     * one that never returns used to leave the episode row disabled for the life of the screen
     * with nothing on screen to explain it.
     */
    suspend fun await(sourceId: Long, episode: Episode): List<Video> = withIOContext {
        withTimeout(TIMEOUT) { resolve(sourceId, episode) }
    }

    private suspend fun resolve(sourceId: Long, episode: Episode): List<Video> {
        val source = sourceManager.get(sourceId) ?: return emptyList()
        val sEpisode = episode.toSEpisode()

        // Almost every published anime extension is still built against extensions-lib 14,
        // which knows nothing about hosters: it implements videoListRequest and leaves
        // hosterListRequest at the default, which fabricates `baseUrl + episode.url`. For a
        // source whose episode url is a bare id that produces a nonsense hostname and an
        // IOException. Only two exception types used to fall back to the old path, so anything
        // else — including that IOException — simply gave up and the episode refused to open.
        val hosters = runCatching { source.getHosterList(sEpisode) }
            .onFailure { logcat(LogPriority.DEBUG, it) { "No hoster list for ${episode.name}" } }
            .getOrNull()

        if (hosters.isNullOrEmpty()) {
            return runCatching { source.getVideoList(sEpisode) }
                .onFailure { logcat(LogPriority.WARN, it) { "No videos for ${episode.name}" } }
                .getOrDefault(emptyList())
                .also { videos ->
                    logcat(LogPriority.DEBUG) {
                        "Resolved ${videos.size} video(s), legacy path; " +
                            "headers=${videos.firstOrNull()?.headers?.size ?: 0}"
                    }
                }
        }

        logcat(LogPriority.DEBUG) { "Got ${hosters.size} hoster(s) for ${episode.name}" }

        val fromHosters = hosters.flatMap { hoster ->
            hoster.videoList ?: runCatching { source.getVideoList(hoster) }
                .onFailure {
                    // Swallowing this left the player doing nothing at all when a hoster
                    // refused, which is indistinguishable from a tap that missed.
                    logcat(LogPriority.WARN, it) { "Hoster ${hoster.hosterName} gave no videos" }
                }
                .getOrDefault(emptyList())
        }

        // Hosters that resolve to nothing are as useless as no hosters at all.
        return fromHosters.ifEmpty {
            runCatching { source.getVideoList(sEpisode) }.getOrDefault(emptyList())
        }.also { videos ->
            logcat(LogPriority.DEBUG) {
                "Resolved ${videos.size} video(s) for ${episode.name}; " +
                    "headers=${videos.firstOrNull()?.headers?.size ?: 0}"
            }
        }
    }

    /** The video a player should open first: the source's own preference, else the best resolution. */
    fun List<Video>.best(): Video? =
        firstOrNull { it.preferred } ?: maxByOrNull { it.resolution ?: 0 }

    companion object {
        val NO_HOSTER_LIST = Hoster.NO_HOSTER_LIST

        /** Long enough for a slow site, short enough that a hung one is not forever. */
        private val TIMEOUT = 60.seconds
    }
}
