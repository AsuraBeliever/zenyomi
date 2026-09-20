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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where an episode's opening and ending are, according to AniSkip.
 *
 * AniSkip is a community database of the exact seconds an opening and an ending occupy in each
 * episode of each anime. It is keyed by MyAnimeList id, so the first job here is to put a MAL
 * id on the anime being watched. Three ways, in order: the MyAnimeList track it already has,
 * the AniList track it already has — AniList knows the MAL id of everything it holds and says
 * so without an account — and failing both, its title, looked up in AniList's catalogue.
 *
 * The title is the one that matters in practice: almost nobody tracks what they are about to
 * watch, and without it the button falls back to a fixed jump for every anime in the library.
 * A title is a guess where an id is a fact, so it is only believed on an exact match against a
 * single entry; see [malIdForTitle].
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
    private val getAnime: GetAnime,
    private val getAnimeTracks: GetAnimeTracks,
    private val networkHelper: NetworkHelper,
) {

    /**
     * One lookup per anime for the life of the app: a MAL id does not change.
     *
     * [UNKNOWN] is remembered too. An anime whose title matches nothing — a fan title, a
     * Spanish source, something AniList does not have — would otherwise be searched again at
     * every episode, and the answer would be the same every time. A lookup that *failed*
     * rather than answered is not remembered, so a moment without network does not cost the
     * rest of the session.
     */
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
                ?.also { logcat(LogPriority.DEBUG) { "Skip times for $malId episode $episode: $it" } }
        }

    private suspend fun malIdOf(animeId: Long): Long? {
        malIds[animeId]?.let { return it.takeIf { cached -> cached != UNKNOWN } }
        val tracks = getAnimeTracks.await(animeId)
        val tracked = tracks.firstOrNull { it.trackerId == MYANIMELIST_ID }?.remoteId
            ?: tracks.firstOrNull { it.trackerId == ANILIST_ID }?.let { malIdFromAnilist(it.remoteId) }
        if (tracked != null) return tracked.also { malIds[animeId] = it }

        val title = getAnime.await(animeId)?.title ?: return null
        val searched = searchMalId(title)
        val found = searched.getOrNull()
        // Only an answer is remembered. A search that threw says nothing about the anime.
        if (searched.isSuccess) malIds[animeId] = found ?: UNKNOWN
        logcat(LogPriority.DEBUG) { "MyAnimeList id for \"$title\": ${found ?: "none"}" }
        return found
    }

    /**
     * The MAL id of the anime AniList calls by this exact title, or null.
     *
     * AniList's search is fuzzy — it is built to help someone typing — and this is not: the
     * answer moves the episode by a minute and a half, so the nearest result is the wrong
     * thing to take. What comes back is filtered down to entries whose own title, in any of
     * the languages it carries, *is* the one being played, and only an unambiguous single
     * survivor is used.
     */
    private suspend fun searchMalId(title: String): Result<Long?> = runCatching {
        search(searchTermFor(title), title)
            ?: openingWordsOf(title)
                ?.let { shorter -> search(shorter, title) }
    }
        .onFailure { logcat(LogPriority.DEBUG, it) { "No MAL id for the title $title" } }

    /** One search, matched against [title] however long the term that found it was. */
    private suspend fun search(term: String, title: String): Long? {
        val query = "query(${'$'}search:String){Page(perPage:$SEARCH_RESULTS)" +
            "{media(search:${'$'}search,type:ANIME){idMal format title{romaji english native} synonyms}}}"
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") { put("search", term) }
        }
        val request = POST(
            url = ANILIST_URL,
            body = payload.toString().toRequestBody("application/json".toMediaType()),
        )
        return networkHelper.client.newCall(request).await().use { response ->
            if (!response.isSuccessful) null else malIdForTitle(title, response.body.string())
        }
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
        skipTimes(malId, episode, askedLength = episodeLengthSeconds, ourLength = episodeLengthSeconds)
            ?: skipTimes(malId, episode, askedLength = 0, ourLength = episodeLengthSeconds)

    private suspend fun skipTimes(malId: Long, episode: Int, askedLength: Int, ourLength: Int): Intervals? {
        val url = "$ANISKIP_URL/v2/skip-times/$malId/$episode" +
            "?types=op&types=ed&episodeLength=$askedLength"
        return networkHelper.client.newCall(GET(url)).await().use { response ->
            // 404 is the ordinary answer for an episode nobody has timed, not a failure.
            if (!response.isSuccessful) return null
            parseSkipTimes(response.body.string(), ourLength)
        }
    }

    /**
     * @param openingDrift how far [opening] may be from where it really is. See [driftFor].
     */
    data class Intervals(
        val opening: IntRange?,
        val ending: IntRange?,
        val openingDrift: Int = 0,
    )

    private companion object {
        const val ANISKIP_URL = "https://api.aniskip.com"
        const val ANILIST_URL = "https://graphql.anilist.co"

        /** The ids [eu.kanade.tachiyomi.data.track.TrackerManager] hands its trackers. */
        const val MYANIMELIST_ID = 1L
        const val ANILIST_ID = 2L

        /** Remembered for an anime AniList has no exact entry for, so it is asked about once. */
        const val UNKNOWN = -1L

        /**
         * How many search results are looked at.
         *
         * Enough that an exact title buried under the sequels and recaps that share its name
         * is still seen, and few enough that this stays one small request.
         */
        const val SEARCH_RESULTS = 10
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
internal fun parseSkipTimes(body: String, ourLengthSeconds: Int = 0): GetSkipIntervals.Intervals? = runCatching {
    val response = aniskipJson.decodeFromString<SkipTimesResponse>(body)
    if (!response.found) return null
    val opening = response.results.firstOrNull { it.skipType == "op" }
    GetSkipIntervals.Intervals(
        opening = opening?.interval?.toRange(),
        ending = response.results.firstOrNull { it.skipType == "ed" }?.interval?.toRange(),
        openingDrift = driftFor(opening?.episodeLength, ourLengthSeconds),
    ).takeIf { it.opening != null || it.ending != null }
}.getOrNull()

/**
 * How far these times may be from where the opening really is, in seconds.
 *
 * They were measured on somebody else's copy of the episode. A stream that carries a few
 * seconds of logo the timed copy did not, or a release cut a moment differently, shifts every
 * second of the answer — the interval is the right length, in the wrong place. The answer
 * carries the length of the copy it was measured on, and how far that is from this one is the
 * only handle there is on how far apart the two are.
 *
 * Capped, because past a point the difference is somewhere else in the episode — a preview the
 * stream does not have, credits cut differently — and says nothing about the opening.
 *
 * The player subtracts this on top of the margin it always leaves, so that the seconds it
 * lands short are counted from where the opening really ends and not from where a stranger's
 * copy says it does.
 */
internal fun driftFor(timedLengthSeconds: Double?, ourLengthSeconds: Int): Int {
    if (timedLengthSeconds == null || timedLengthSeconds <= 0 || ourLengthSeconds <= 0) return 0
    return abs(timedLengthSeconds - ourLengthSeconds).roundToInt().coerceAtMost(MAXIMUM_DRIFT_SECONDS)
}

/** Past this the copies are not versions of the same file, and the difference is not the opening's. */
private const val MAXIMUM_DRIFT_SECONDS = 7

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
private data class SkipResult(
    val interval: Interval,
    val skipType: String,
    /** How long the copy these times were measured on was. Absent in older answers. */
    val episodeLength: Double? = null,
)

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

/**
 * The MAL id of the single AniList entry that is called this, or null.
 *
 * Separate from the request so it can be tested against real payloads, and because this is
 * the part that decides whether a minute and a half of somebody's episode disappears. Two
 * titles are the same name when they are made of the same words — see [titleKey] — and that
 * is the whole rule. It is an equality, not a similarity: a word more or a word less is a
 * different anime, so "One Piece" never answers for "One Piece Film: Red".
 *
 * The match is against every name AniList holds for the entry — romaji, English, native and
 * synonyms — which is where most of the agreement between a source and a catalogue lives.
 *
 * Two entries answering is normally no answer at all, and when it is not, see [pickOne] for
 * the two things allowed to settle it.
 *
 * Finding nothing is a fine outcome: no times, and therefore no button.
 */
internal fun malIdForTitle(title: String, body: String): Long? = runCatching {
    val wanted = titleKey(title).takeIf { it.isNotEmpty() } ?: return@runCatching null
    val tight = tightTitleKey(title)
    aniskipJson.decodeFromString<AnilistSearchResponse>(body).data.page.media
        .filter { media -> media.names().any { titleKey(it) == wanted || tightTitleKey(it) == tight } }
        .pickOne(title)
}.getOrNull()

/**
 * The one entry this title means, out of everything that answers to it.
 *
 * One is the ordinary case and needs no deciding. When several answer, two things are allowed
 * to settle it, in this order, and nothing else is:
 *
 * 1. **Being called exactly this, character for character.** Gintama's seasons are "Gintama",
 *    "Gintama'", "Gintama°" and "Gintama." — an apostrophe and a full stop are the whole
 *    difference between them, and they are the first thing a comparison of words throws away.
 *
 * 2. **Being the series rather than something around it.** A special, an OVA or a recap
 *    routinely carries the series' name as a synonym: "Assassination Classroom" is both a
 *    show and a ten minute thing from a 2013 convention. A source listing episodes means the
 *    show. This is only ever reached among entries that already agree on the name.
 *
 * Still tied after both, or tied among two series, and there is no answer — which costs a
 * button and never costs episode.
 */
private fun List<AnilistSearchMedia>.pickOne(title: String): Long? {
    val ids = mapNotNull { it.idMal }.distinct()
    if (ids.size <= 1) return ids.singleOrNull()

    val raw = title.trim().lowercase()
    val named = filter { media -> media.names().any { it.trim().lowercase() == raw } }
    val narrowed = named.ifEmpty { this }
    narrowed.mapNotNull { it.idMal }.distinct().singleOrNull()?.let { return it }

    return narrowed.filter { it.format in SERIES_FORMATS }
        .mapNotNull { it.idMal }
        .distinct()
        .singleOrNull()
}

/** What a source is listing episodes of. Anything else is around the series, not the series. */
private val SERIES_FORMATS = setOf("TV", "TV_SHORT", "ONA")

/**
 * The first words of a title, for asking again when the whole of it found nothing.
 *
 * AniList's search is the part of this that is *not* strict, and it gives up on the titles a
 * light novel brings: forty characters of subtitle after a colon and it answers with nothing
 * at all. The name is at the front, before the first colon or comma, so that is what gets
 * asked the second time — and whatever comes back is still matched against the full title,
 * word for word. A shorter question, not a lower bar.
 *
 * Null when it would be the same question twice.
 */
internal fun openingWordsOf(title: String): String? {
    val front = searchTermFor(title).split(*TITLE_BREAKS).first().trim()
    val shorter = front.split(' ').filter { it.isNotEmpty() }.take(SEARCH_WORDS).joinToString(" ")
    return shorter.takeIf { it.isNotEmpty() && !it.equals(searchTermFor(title), ignoreCase = true) }
}

/**
 * Where a title stops being a name and starts being a sentence about the plot.
 *
 * Not the hyphen: romaji is full of them inside single words —"Tenkou-saki", "Bouken-roku"—
 * and breaking there asks the catalogue about half a word.
 */
private val TITLE_BREAKS = charArrayOf(':', ',', '~', '?', '!', '(', '"', '\u2014', '\u2013')

/** Enough of a name to find it, few enough that the search has something to work with. */
private const val SEARCH_WORDS = 6

/**
 * A title reduced to the words it is made of, so that two spellings of one name compare equal.
 *
 * Case, punctuation and the labels a source hangs off a name go first — "Fate/Zero", "Fate
 * Zero" and "FATE ZERO" are one anime. The order of the words stays: it is the difference
 * between a name and the same words in a bag, and two catalogues naming one anime have never
 * disagreed about it.
 *
 * Then the part that carries meaning and is spelt six ways: which season this is. A source
 * saying "4th Season", a catalogue saying "Season 4" and another saying plain "4" are all the
 * fourth one, so ordinals become digits, a trailing roman numeral becomes a digit, and the
 * word for "season" itself is dropped — it is punctuation between the name and the number.
 * The number stays, always: dropping *that* is what would make a season answer for the first
 * one, and skipping into the wrong episode of the wrong year is the failure this whole file
 * is built to avoid.
 */
internal fun titleKey(title: String): List<String> = canonicalWords(title)

/** The words of a title once the season is spelt the one way. See [titleKey]. */
private fun canonicalWords(title: String): List<String> {
    val words = normalizeAnimeTitle(title).split(' ').filter { it.isNotEmpty() }
    return words
        .mapIndexed { index, word ->
            when {
                word in ORDINALS -> ORDINALS.getValue(word)
                // Only at the end, and never as the whole name: "V" is a season, "V" alone is
                // a series called V.
                word in ROMAN_NUMERALS && index == words.lastIndex && words.size > 1 ->
                    ROMAN_NUMERALS.getValue(word)
                SEASON_SHORTHAND.matches(word) -> word.drop(1)
                else -> word
            }
        }
        .filterNot { it in SEASON_WORDS }
}

/**
 * The same title with the spacing rubbed out: letters and digits, in order, nothing between.
 *
 * Romaji is written by hand and hyphenated by taste. One catalogue has "Bouken-roku" where a
 * source has "Boukenroku", "Hai Settei" where it has "Haisettei", "Gin Tama" where it has
 * "Gintama". As words those are different titles; as letters they are the same one, and the
 * order still has to match, so this is far from a similarity — it is the same equality with
 * one fewer thing to disagree about.
 */
internal fun tightTitleKey(title: String): String = canonicalWords(title).joinToString("")

/** "3rd" and "3" are the same season, in every source that has ever named one. */
private val ORDINALS = mapOf(
    "1st" to "1", "2nd" to "2", "3rd" to "3", "4th" to "4", "5th" to "5",
    "6th" to "6", "7th" to "7", "8th" to "8", "9th" to "9", "10th" to "10",
    "first" to "1", "second" to "2", "third" to "3", "fourth" to "4", "fifth" to "5",
    "primera" to "1", "segunda" to "2", "tercera" to "3", "cuarta" to "4", "quinta" to "5",
)

/** Up to ten, which is further than any anime has ever counted its seasons in numerals. */
private val ROMAN_NUMERALS = mapOf(
    "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
    "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10",
)

/** "S2", the way a release names a season when it is in a hurry. */
private val SEASON_SHORTHAND = Regex("""s\d{1,2}""")

/** The word between the name and the season number, in the languages a source might use it. */
private val SEASON_WORDS = setOf(
    "season", "seasons", "temporada", "saison", "staffel", "stagione", "cour", "part", "parte",
)

/**
 * What is sent to AniList's search, which is the title with the decorations taken off.
 *
 * Sources label their entries with things that are not part of the name — "(Dub)", "[1080p]",
 * a "(TV)" to tell a series from its film. Left in they push the fuzzy search away from the
 * anime; the match itself is made against the untouched title afterwards.
 */
internal fun searchTermFor(title: String): String =
    title.replace(TITLE_DECORATION, " ").trim().ifEmpty { title }

/**
 * A title reduced to what two people naming the same anime would agree on.
 *
 * Case, punctuation and spacing go — "Fate/Zero", "Fate Zero" and "FATE ZERO" are one anime —
 * along with the source's own labels. Nothing that carries meaning is touched: a number, a
 * season, an "Final" or a "2nd" all survive, because those are exactly what separate one
 * entry from the next.
 */
internal fun normalizeAnimeTitle(title: String): String = title
    .replace(TITLE_DECORATION, " ")
    .lowercase()
    .replace(NON_TITLE, " ")
    .trim()

/** The labels a source hangs off a title, in either kind of bracket. */
private val TITLE_DECORATION = Regex(
    """[(\[]\s*(dub|dubbed|sub|subbed|subtitled|dual audio|tv|bd|blu-?ray|dvd|web|\d{3,4}p)\s*[)\]]""",
    RegexOption.IGNORE_CASE,
)

/**
 * Everything that is not a letter or a number, which is everything two spellings differ in.
 *
 * Whole runs at a time, so punctuation between words leaves one space and not three.
 */
private val NON_TITLE = Regex("""[^\p{L}\p{N}]+""")

@Serializable
private data class AnilistSearchResponse(val data: AnilistSearchData = AnilistSearchData())

@Serializable
private data class AnilistSearchData(
    @SerialName("Page") val page: AnilistPage = AnilistPage(),
)

@Serializable
private data class AnilistPage(val media: List<AnilistSearchMedia> = emptyList())

@Serializable
private data class AnilistSearchMedia(
    val idMal: Long? = null,
    /** TV, ONA, SPECIAL, MOVIE… — what kind of thing this entry is. */
    val format: String? = null,
    val title: AnilistTitles = AnilistTitles(),
    val synonyms: List<String> = emptyList(),
) {
    fun names(): List<String> = listOfNotNull(title.romaji, title.english, title.native) + synonyms
}

@Serializable
private data class AnilistTitles(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)
