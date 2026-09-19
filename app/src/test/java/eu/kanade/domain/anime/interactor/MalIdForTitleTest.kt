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
    ): String {
        val titles = """{"romaji":${romaji.json()},"english":${english.json()},"native":null}"""
        val alternatives = synonyms.joinToString(",") { it.json() }
        return """{"idMal":${idMal ?: "null"},"title":$titles,"synonyms":[$alternatives]}"""
    }

    private fun String?.json() = if (this == null) "null" else "\"$this\""
}
