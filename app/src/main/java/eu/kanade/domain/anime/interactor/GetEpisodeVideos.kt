package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves an episode into the videos a source can actually play.
 *
 * The anime source API offers two routes and a source implements one of them: the
 * current one lists hosters and then videos per hoster, the older one returns videos for
 * the episode directly. Both throw from their default implementation.
 *
 * Which route a source implements is settled by looking, not by trying: the default
 * hoster route fetches `baseUrl + episode.url` and only then throws while parsing, so a
 * source that does not implement it was costing a whole round trip per episode whose
 * response went straight in the bin. Measured on KickAssAnime: 513 ms, every time.
 *
 * A hoster may carry its videos already, or be marked lazy and need a second call.
 *
 * Scoped to the app because both of the things it remembers are worthless otherwise. Without
 * the scope Metro hands a fresh instance to every injection point, so each entry screen got
 * its own: leaving an entry threw away what its episodes had resolved to, the player's
 * `forget` reached a different instance than the one holding the url, and the hoster memo
 * below — "once per extension class, for the life of the app" — was in truth rebuilt every
 * time anybody opened anything.
 */
@Inject
@SingleIn(AppScope::class)
class GetEpisodeVideos(
    private val sourceManager: AnimeSourceManager,
) {

    /** Una vez por clase de extension; la respuesta no cambia mientras la app viva. */
    private val hosterSupport = ConcurrentHashMap<String, Boolean>()

    /**
     * What each episode last resolved to, for as long as it is worth reusing.
     *
     * Resolving is not one request: it is the hoster list, then a video list per hoster,
     * then usually an extractor chain per mirror, and often against a host that rate-limits.
     * None of it was remembered, so leaving the player and opening the same episode again
     * paid the whole bill a second time — and paid it slower than the first, because the
     * first pass had just spent the rate limiter's budget.
     *
     * Reusing a url is no bolder than what already happens: one resolved at the start of an
     * episode has to stay good for the twenty minutes it plays, so minutes-old is well
     * inside what every source already guarantees. [TTL] keeps it to that.
     */
    private val resolved = object : LinkedHashMap<Long, Resolved>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Long, Resolved>) = size > MAX_REMEMBERED
    }

    private class Resolved(val video: Video, val at: Long)

    /**
     * The video [episodeId] last resolved to, or null if it was never resolved or the entry
     * has gone stale. A hit skips the whole network path below.
     */
    fun cached(episodeId: Long): Video? = synchronized(resolved) {
        val hit = resolved[episodeId] ?: return null
        if (System.currentTimeMillis() - hit.at > TTL.inWholeMilliseconds) {
            resolved.remove(episodeId)
            return null
        }
        hit.video
    }

    /** Remembers what an episode resolved to, so opening it again costs nothing. */
    fun remember(episodeId: Long, video: Video) = synchronized(resolved) {
        resolved[episodeId] = Resolved(video, System.currentTimeMillis())
        Unit
    }

    /**
     * Drops what an episode resolved to.
     *
     * Called when a url turns out not to play: without it the retry would be handed the same
     * dead url until the entry expired on its own, which reads as an episode that is simply
     * broken.
     */
    fun forget(episodeId: Long) = synchronized(resolved) {
        resolved.remove(episodeId)
        Unit
    }

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
        val hosters = if (source.implementsHosters()) {
            runCatching { source.getHosterList(sEpisode) }
                .onFailure { logcat(LogPriority.DEBUG, it) { "No hoster list for ${episode.name}" } }
                .getOrNull()
        } else {
            // Ni se intenta: pedirlo solo sirve para tirar la respuesta.
            null
        }

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
     * Whether this source actually implements the hoster route.
     *
     * Walks the class chain from the extension down to our own base classes looking for an
     * override of any hoster method. Our bases declare them too — [AnimeHttpSource] to throw,
     * [ParsedAnimeHttpSource] to delegate to a selector that throws — so only a class *below*
     * them counts, and that class can only be the extension's.
     *
     * Reflection by name is safe under R8: `source-api/consumer-proguard.pro` keeps the public
     * and protected members of `animesource.online.**`, which is also what makes the overrides
     * dispatch at all.
     *
     * Anything that is not an [AnimeHttpSource] keeps the old behaviour — try and fall back —
     * because this only knows how to read that hierarchy, and guessing about the rest would be
     * how a source that works today stops working. The try/catch below stays for the same
     * reason: if this is ever wrong, the result is what it has always been.
     */
    private fun Any.implementsHosters(): Boolean {
        if (this !is AnimeHttpSource) return true
        return hosterSupport.getOrPut(javaClass.name) { declaresHosterOverride(javaClass) }
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
         * How long a resolved url is reused. Long enough to cover stepping out of the player
         * and back in, short enough that a link a host signs for a single short window is not
         * handed out after it has died.
         */
        private val TTL = 5.minutes

        /** Enough for the episode being watched and the ones around it, and no more. */
        private const val MAX_REMEMBERED = 16

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

/**
 * Whether [type] — an extension's source class — overrides any of the hoster methods.
 *
 * Internal and top level so it can be tested without a real extension: none of the published
 * ones implements this route, so the only positive case available is a made-up one, and a
 * detection that silently answers "no" would quietly cost a source its videos.
 */
internal fun declaresHosterOverride(type: Class<*>): Boolean {
    val ours = setOf(
        AnimeHttpSource::class.java.name,
        ParsedAnimeHttpSource::class.java.name,
    )
    return generateSequence<Class<*>>(type) { it.superclass }
        .takeWhile { it.name !in ours && it != Any::class.java }
        .any { cls -> cls.declaredMethods.any { it.name in HOSTER_METHODS } }
}

private val HOSTER_METHODS = setOf(
    "getHosterList",
    "hosterListParse",
    "hosterListSelector",
    "hosterFromElement",
)
