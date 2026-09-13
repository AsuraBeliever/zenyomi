package eu.kanade.tachiyomi.ui.animeplayer

/**
 * Turns whatever a source calls a language into the code mpv matches on.
 *
 * mpv's `alang` and `slang` compare against a track's language tag, and sources do not hand
 * back tags: KickAssAnime labels its subtitles "Spanish (spa)", AnimeOnsen says "Español" and
 * "Español (Spain)", others say "Latino" or "日本語". None of those equal "spa", so setting a
 * preferred subtitle language selected nothing at all and the player fell back to whichever
 * track happened to come first — English, every time.
 *
 * The table is deliberately short. It covers the languages the sources this app can reach
 * actually ship, and anything unrecognised is passed through untouched: a label mpv cannot
 * match is no worse than the label we started with, and inventing a mapping for a language
 * nobody here speaks would be guessing.
 */
object TrackLanguage {

    /** ISO 639-2/B, which is what mpv's own matching normalises towards. */
    private val BY_NAME = mapOf(
        "english" to "eng", "inglés" to "eng", "ingles" to "eng",
        "spanish" to "spa", "español" to "spa", "espanol" to "spa",
        "castellano" to "spa", "castilian" to "spa", "latino" to "spa",
        "japanese" to "jpn", "japonés" to "jpn", "japones" to "jpn", "日本語" to "jpn",
        "portuguese" to "por", "português" to "por", "portugues" to "por",
        "french" to "fre", "français" to "fre", "francais" to "fre",
        "german" to "ger", "deutsch" to "ger", "alemán" to "ger",
        "italian" to "ita", "italiano" to "ita",
        "russian" to "rus", "русский" to "rus",
        "arabic" to "ara", "العربية" to "ara",
        "korean" to "kor", "한국어" to "kor",
        "chinese" to "chi", "中文" to "chi",
        "thai" to "tha", "ไทย" to "tha",
        "turkish" to "tur", "türkçe" to "tur",
        "polish" to "pol", "indonesian" to "ind", "malay" to "may",
        "vietnamese" to "vie", "tagalog" to "tgl",
    )

    /** The two-letter forms a person is likely to type into the preference. */
    private val BY_SHORT_CODE = mapOf(
        "en" to "eng", "es" to "spa", "ja" to "jpn", "jp" to "jpn", "pt" to "por",
        "fr" to "fre", "de" to "ger", "it" to "ita", "ru" to "rus", "ar" to "ara",
        "ko" to "kor", "zh" to "chi", "th" to "tha", "tr" to "tur", "pl" to "pol",
        "id" to "ind", "ms" to "may", "vi" to "vie", "tl" to "tgl",
    )

    /**
     * The code for one label.
     *
     * A parenthesised code wins when there is one — "Spanish (spa)" says it outright — then the
     * name, then the leading word, so "Español (Spain)" and "Português (Brasil)" resolve
     * without an entry each for every region.
     */
    fun of(label: String): String {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return trimmed

        PARENTHESISED.find(trimmed)?.groupValues?.get(1)?.let { return it.lowercase() }

        val lower = trimmed.lowercase()
        BY_NAME[lower]?.let { return it }
        BY_SHORT_CODE[lower]?.let { return it }
        if (lower.length == 3 && lower.all { it.isLetter() }) return lower

        val head = lower.substringBefore(' ').substringBefore('(').trim()
        return BY_NAME[head] ?: BY_SHORT_CODE[head] ?: trimmed
    }

    /** The same, for the comma separated list the language preferences hold. */
    fun ofList(preference: String): String = preference
        .split(',')
        .map { of(it) }
        .filter { it.isNotEmpty() }
        .joinToString(",")

    private val PARENTHESISED = Regex("""\(([a-zA-Z]{3})\)""")
}
