package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Where an episode's opening and ending are, according to AniSkip.
 *
 * AniSkip is a community database of the exact seconds an opening and an ending occupy in each
 * episode of each anime. It is keyed by MyAnimeList id, which is why this only answers for an
 * anime that is tracked: with MyAnimeList directly, or with AniList, which knows the MAL id of
 * what it holds and is asked for it once.
 *
 * Everything here is best-effort. An anime nobody has submitted times for, an episode numbered
 * with a half, a service that is down — all of them answer "no idea", and the player falls
 * back to jumping a fixed length. The one thing it must never do is guess: a wrong interval
 * skips a minute and a half of the episode.
 *
 * See `docs/adr/0007-de-donde-salen-los-tiempos-del-opening.md` for why this is the third of
 * three sources and what leaves the device.
 */
@Inject
@SingleIn(AppScope::class)
class GetSkipIntervals(
    private val getAnimeTracks: GetAnimeTracks,
    private val networkHelper: NetworkHelper,
) {

    /** One lookup per anime for the life of the app: a MAL id does not change. */
    private val malIds = ConcurrentHashMap<Long, Long>()

    suspend fun await(animeId: Long, episodeNumber: Double, episodeLengthSeconds: Int): Intervals? =
        withIOContext {
            // The api takes an episode number, not an episode: specials numbered 5.5 are not in
            // it, and rounding one to 5 would hand back the wrong episode's opening.
            val episode = episodeNumber.takeIf { it > 0 && it == it.roundToInt().toDouble() }
                ?.roundToInt()
                ?: return@withIOContext null
            val malId = malIdOf(animeId) ?: return@withIOContext null
            runCatching { fetch(malId, episode, episodeLengthSeconds) }
                .onFailure { logcat(LogPriority.DEBUG, it) { "No skip times for $malId episode $episode" } }
                .getOrNull()
        }

    private suspend fun malIdOf(animeId: Long): Long? {
        malIds[animeId]?.let { return it }
        val tracks = getAnimeTracks.await(animeId)
        val resolved = tracks.firstOrNull { it.trackerId == MYANIMELIST_ID }?.remoteId
            ?: tracks.firstOrNull { it.trackerId == ANILIST_ID }?.let { malIdFromAnilist(it.remoteId) }
        return resolved?.also { malIds[animeId] = it }
    }

    /** AniList holds the MyAnimeList id of everything it knows, and says so without an account. */
    private suspend fun malIdFromAnilist(anilistId: Long): Long? {
        val graphql = "query(${'$'}id:Int){Media(id:${'$'}id,type:ANIME){idMal}}"
        val query = """{"query":"$graphql","variables":{"id":$anilistId}}"""
        val request = POST(
            url = ANILIST_URL,
            body = query.toRequestBody("application/json".toMediaType()),
        )
        return runCatching {
            networkHelper.client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return null
                aniskipJson.decodeFromString<AnilistResponse>(response.body.string()).data.media?.idMal
            }
        }
            .onFailure { logcat(LogPriority.DEBUG, it) { "No MAL id for AniList $anilistId" } }
            .getOrNull()
    }

    /**
     * The length is sent because a series with several cuts has times for each of them, and
     * sending it is how the right one is picked. A length that matches nothing answers 404
     * rather than falling back on its own, so the question is asked again without it: the
     * episode's own times are a better answer than none, and a release a few seconds longer
     * than the one somebody timed is the ordinary case.
     */
    private suspend fun fetch(malId: Long, episode: Int, episodeLengthSeconds: Int): Intervals? =
        skipTimes(malId, episode, episodeLengthSeconds) ?: skipTimes(malId, episode, 0)

    private suspend fun skipTimes(malId: Long, episode: Int, episodeLengthSeconds: Int): Intervals? {
        val url = "$ANISKIP_URL/v2/skip-times/$malId/$episode" +
            "?types=op&types=ed&episodeLength=$episodeLengthSeconds"
        return networkHelper.client.newCall(GET(url)).await().use { response ->
            // 404 is the ordinary answer for an episode nobody has timed, not a failure.
            if (!response.isSuccessful) return null
            parseSkipTimes(response.body.string())
        }
    }

    data class Intervals(val opening: IntRange?, val ending: IntRange?)

    private companion object {
        const val ANISKIP_URL = "https://api.aniskip.com"
        const val ANILIST_URL = "https://graphql.anilist.co"

        /** The ids [eu.kanade.tachiyomi.data.track.TrackerManager] hands its trackers. */
        const val MYANIMELIST_ID = 1L
        const val ANILIST_ID = 2L
    }
}

/**
 * What AniSkip answered, or null for anything that is not a usable pair of times.
 *
 * Separate from the request so it can be tested against real payloads: "found" false, an
 * episode with only an ending, an interval of zero length, and a body that is not what this
 * expects at all — all of which mean the same thing to the player, which is to fall back to
 * the fixed jump rather than to seek somewhere invented.
 */
internal fun parseSkipTimes(body: String): GetSkipIntervals.Intervals? = runCatching {
    val response = aniskipJson.decodeFromString<SkipTimesResponse>(body)
    if (!response.found) return null
    GetSkipIntervals.Intervals(
        opening = response.results.firstOrNull { it.skipType == "op" }?.interval?.toRange(),
        ending = response.results.firstOrNull { it.skipType == "ed" }?.interval?.toRange(),
    ).takeIf { it.opening != null || it.ending != null }
}.getOrNull()

/** Both ends in whole seconds, which is the resolution the seek bar works in anyway. */
private fun Interval.toRange(): IntRange? {
    val start = startTime.toInt()
    val end = endTime.toInt()
    return (start..end).takeIf { end > start }
}

private val aniskipJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class SkipTimesResponse(
    val found: Boolean = false,
    val results: List<SkipResult> = emptyList(),
)

@Serializable
private data class SkipResult(val interval: Interval, val skipType: String)

@Serializable
private data class Interval(val startTime: Double, val endTime: Double)

@Serializable
private data class AnilistResponse(val data: AnilistData = AnilistData())

@Serializable
private data class AnilistData(
    // The field is capitalised in AniList's schema, and this is what it is called there.
    @SerialName("Media") val media: AnilistMedia? = null,
)

@Serializable
private data class AnilistMedia(val idMal: Long? = null)
