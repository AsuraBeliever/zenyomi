package eu.kanade.domain.anime.interactor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Which anime the player decides it is watching, when nothing has tracked it.
 *
 * This is the part that turns a title into a MyAnimeList id, and a wrong answer here is a
 * minute and a half of somebody's episode gone. So: an exact match against one entry, or no
 * answer at all — which costs nothing, because the button falls back to the fixed jump.
 */
class MalIdForTitleTest {

    @Test
    fun `the anime AniList calls by that name`() {
        assertEquals(21L, malIdForTitle("One Piece", search(entry(21, romaji = "ONE PIECE"))))
    }

    @Test
    fun `the labels a source hangs off a title do not stop it matching`() {
        val body = search(entry(21, romaji = "ONE PIECE"))
        assertEquals(21L, malIdForTitle("One Piece (Dub)", body))
        assertEquals(21L, malIdForTitle("One Piece [1080p]", body))
    }

    @Test
    fun `punctuation and case are not part of a name`() {
        assertEquals(10087L, malIdForTitle("FATE ZERO", search(entry(10087, romaji = "Fate/Zero"))))
    }

    @Test
    fun `an english title or a synonym counts as its name`() {
        val body = search(
            entry(1535, romaji = "DEATH NOTE", english = "Death Note", synonyms = listOf("Desu Noto")),
        )
        assertEquals(1535L, malIdForTitle("Desu Noto", body))
        assertEquals(1535L, malIdForTitle("Death Note", body))
    }

    @Test
    fun `a season spelt six ways is the same season`() {
        // Measured against the real catalogue: sources say "4th Season", AniList says "4",
        // its English title says "Season 4", and all three are the fourth one.
        val body =
            search(
                entry(
                    60310,
                    romaji = "Mairimashita! Iruma-kun 4",
                    english = "Welcome to Demon School! Iruma-kun Season 4",
                ),
            )
        assertEquals(60310L, malIdForTitle("Mairimashita! Iruma-kun 4th Season", body))
        assertEquals(60310L, malIdForTitle("Mairimashita! Iruma-kun S4", body))
        assertEquals(60310L, malIdForTitle("Welcome to Demon School! Iruma-kun 4", body))
    }

    @Test
    fun `a trailing roman numeral is a season number`() {
        val body = search(entry(37430, romaji = "Youjo Senki II"))
        assertEquals(37430L, malIdForTitle("Youjo Senki 2", body))
        assertEquals(37430L, malIdForTitle("Youjo Senki Season 2", body))
    }

    @Test
    fun `dropping the season number is not the same anime`() {
        val body = search(
            entry(48549, romaji = "Dr. STONE: NEW WORLD"),
            entry(55644, romaji = "Dr. STONE: NEW WORLD Part 2"),
        )
        assertEquals(48549L, malIdForTitle("Dr. Stone New World", body))
        assertEquals(55644L, malIdForTitle("Dr. Stone New World Part 2", body))
    }

    @Test
    fun `an apostrophe can be the whole difference between two seasons`() {
        // Gintama's seasons are told apart by punctuation, which comparing words throws
        // away. Four entries answer to "gintama"; only one is called exactly that.
        val body = search(
            entry(918, romaji = "Gintama", english = "Gintama"),
            entry(9969, romaji = "Gintama'", english = "Gintama Season 2"),
            entry(28977, romaji = "Gintama°", english = "Gintama Season 3"),
            entry(34096, romaji = "Gintama.", english = "Gintama Season 4"),
        )
        assertEquals(918L, malIdForTitle("Gintama", body))
        assertEquals(9969L, malIdForTitle("Gintama'", body))
    }

    @Test
    fun `a later season is not the first one`() {
        val body = search(
            entry(30276, romaji = "One Punch Man", english = "One-Punch Man"),
            entry(34134, romaji = "One Punch Man 2nd Season"),
        )
        assertEquals(34134L, malIdForTitle("One-Punch Man 2nd Season", body))
        assertEquals(30276L, malIdForTitle("One Punch Man", body))
    }

    @Test
    fun `the nearest result is not good enough`() {
        val body = search(entry(21, romaji = "ONE PIECE", english = "ONE PIECE"))
        assertNull(malIdForTitle("One Piece Film: Red", body))
    }

    @Test
    fun `two entries of the same name are no answer`() {
        val body = search(
            entry(1, romaji = "Bleach", english = "Bleach"),
            entry(2, romaji = "Bleach", english = "Bleach"),
        )
        assertNull(malIdForTitle("Bleach", body))
    }

    @Test
    fun `an entry with no MyAnimeList id is no answer`() {
        assertNull(malIdForTitle("Some Anime", search(entry(null, romaji = "Some Anime"))))
    }

    @Test
    fun `nothing found, and answers that are not answers`() {
        assertNull(malIdForTitle("One Piece", search()))
        assertNull(malIdForTitle("One Piece", "<html>502 Bad Gateway</html>"))
        assertNull(malIdForTitle("One Piece", ""))
        assertNull(malIdForTitle("", search(entry(21, romaji = "ONE PIECE"))))
    }

    @Test
    fun `what is searched for is the name without the labels`() {
        assertEquals("One Piece", searchTermFor("One Piece (Dub)"))
        assertEquals("One Piece", searchTermFor("One Piece [1080p]"))
        // The label was the whole title: searching for nothing finds nothing, so it goes as is.
        assertEquals("(Dub)", searchTermFor("(Dub)"))
    }

    /** The shape AniList answers a search in, trimmed to the fields that are asked for. */
    private fun search(vararg entries: String) =
        """{"data":{"Page":{"media":[${entries.joinToString(",")}]}}}"""

    private fun entry(
        idMal: Long?,
        romaji: String,
        english: String? = null,
        synonyms: List<String> = emptyList(),
        format: String = "TV",
    ): String {
        val titles = """{"romaji":${romaji.json()},"english":${english.json()},"native":null}"""
        val alternatives = synonyms.joinToString(",") { it.json() }
        return """{"idMal":${idMal ?: "null"},"format":"$format","title":$titles,"synonyms":[$alternatives]}"""
    }

    private fun String?.json() = if (this == null) "null" else "\"$this\""

    @Test
    fun `romaji hyphenated one way and another is one name`() {
        // Measured against the real catalogue: sources and AniList disagree on where the
        // hyphens and spaces go, and on nothing else.
        val body =
            search(
                entry(
                    1,
                    romaji = "Rakudai Kenja no Gakuin Musou: Nidome no Tensei, S-Rank Cheat Majutsushi Bouken-roku",
                ),
            )
        assertEquals(
            1L,
            malIdForTitle(
                "Rakudai Kenja no Gakuin Musou: Nidome no Tensei, S-Rank Cheat Majutsushi Boukenroku",
                body,
            ),
        )
        assertEquals(
            1L,
            malIdForTitle(
                "Let's Go Kaiki-gumi",
                search(entry(2, romaji = "Let's Go Kaikigumi")).replace("\"idMal\":2", "\"idMal\":1"),
            ),
        )
    }

    @Test
    fun `rubbing out the spacing is not a licence to match anything`() {
        val body = search(entry(21, romaji = "ONE PIECE"))
        assertNull(malIdForTitle("One Piece Film: Red", body))
        assertNull(malIdForTitle("Piece One", body))
    }

    @Test
    fun `asking again with the front of the title, when the whole of it is too long`() {
        assertEquals(
            "Rakudai Kenja no Gakuin Musou",
            openingWordsOf("Rakudai Kenja no Gakuin Musou: Nidome no Tensei, S-Rank Cheat Majutsushi Boukenroku"),
        )
        assertEquals(
            "Hell Mode",
            openingWordsOf("Hell Mode: Yarikomizuki no Gamer wa Hai Settei no Isekai de Musou suru"),
        )
        // Six words is the most it ever asks for.
        assertEquals(
            "Tenkou-saki no Seiso Karen na Bishoujo",
            openingWordsOf("Tenkou-saki no Seiso Karen na Bishoujo ga, Mukashi Danshi to Omotte Issho ni Asonda"),
        )
    }

    @Test
    fun `a title that is already short is not asked for twice`() {
        assertNull(openingWordsOf("One Piece"))
        assertNull(openingWordsOf("Dandadan"))
    }

    @Test
    fun `the series, not the ten minute thing that borrowed its name`() {
        // Both of these really do answer to "Assassination Classroom" in the catalogue.
        val body = search(
            entry(24833, romaji = "Ansatsu Kyoushitsu", english = "Assassination Classroom"),
            entry(
                19759,
                romaji = "Ansatsu Kyoushitsu: Jump Festa 2013 Special",
                synonyms = listOf("Assassination Classroom"),
                format = "SPECIAL",
            ),
        )
        assertEquals(24833L, malIdForTitle("Assassination Classroom", body))
    }

    @Test
    fun `two series of the same name are still no answer`() {
        val body = search(
            entry(1, romaji = "Bleach", english = "Bleach"),
            entry(2, romaji = "Bleach", english = "Bleach"),
        )
        assertNull(malIdForTitle("Bleach", body))
    }

    @Test
    fun `being called exactly that still beats being a series`() {
        // "Gintama'" is the second season and a series; the first one is what was asked for.
        val body = search(
            entry(918, romaji = "Gintama", english = "Gintama"),
            entry(9969, romaji = "Gintama'", english = "Gintama Season 2"),
        )
        assertEquals(918L, malIdForTitle("Gintama", body))
    }
}
