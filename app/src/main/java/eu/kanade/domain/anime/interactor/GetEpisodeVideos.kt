package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
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

    /**
     * The first of [videos] that actually plays, in the order a viewer would want them.
     *
     * A video on the list is not necessarily playable. Sources written against
     * extensions-lib 16 and up may return a promise rather than a url — the real one costs a
     * request, and making it for every quality on the list is wasted work — so it is deferred
     * to whichever one is played. Others hand back a url that is literally the string "null",
     * which mpv dutifully opens and fails on.
     *
     * Candidates are tried in order because a source listing five mirrors usually has some of
     * them dead, and giving up on the first is how a working episode came to look broken.
     */
    suspend fun playable(sourceId: Long, videos: List<Video>): Video? = withIOContext {
        val source = sourceManager.get(sourceId)
        videos.inPreferredOrder().take(MAX_CANDIDATES).firstNotNullOfOrNull { video ->
            val resolved = when {
                video.isPlayable() -> video
                source !is AnimeHttpSource -> null
                else -> runCatching { withTimeout(TIMEOUT) { source.resolveVideo(video) } }
                    .onFailure { logcat(LogPriority.WARN, it) { "Could not resolve ${video.videoTitle}" } }
                    .getOrNull()
            }
            resolved?.takeIf { it.isPlayable() }
        }
    }

    /** The source's own preference first, then by resolution. */
    private fun List<Video>.inPreferredOrder(): List<Video> =
        sortedWith(compareByDescending<Video> { it.preferred }.thenByDescending { it.resolution ?: 0 })

    /** The video a player should open first: the source's own preference, else the best resolution. */
    fun List<Video>.best(): Video? = inPreferredOrder().firstOrNull()

    /**
     * Whether mpv could even attempt this url.
     *
     * Beyond the empty and the literal "null" a source may hand back, extensions produce
     * protocol-relative urls — Jkanime's first mirror is `//www.mediafire.com/...` — which mpv
     * cannot open at all. Treating one as playable stopped the search at a url that was never
     * going to work while four usable mirrors sat behind it.
     */
    private fun Video.isPlayable(): Boolean =
        videoUrl.isNotBlank() && videoUrl != "null" &&
            (videoUrl.startsWith("/") || SCHEME.containsMatchIn(videoUrl))

    companion object {
        val NO_HOSTER_LIST = Hoster.NO_HOSTER_LIST

        /** Long enough for a slow site, short enough that a hung one is not forever. */
        private val TIMEOUT = 60.seconds

        /**
         * How many mirrors are tried before giving up. Each one that needs resolving is a
         * request, and a source that lists thirty dead hosts should not hold the screen for
         * half an hour to prove it.
         */
        private const val MAX_CANDIDATES = 5

        /**
         * A url mpv can open names its protocol; `content://` and `file://` count, and so does
         * a plain absolute path, which is what a downloaded episode is.
         */
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    }
}
