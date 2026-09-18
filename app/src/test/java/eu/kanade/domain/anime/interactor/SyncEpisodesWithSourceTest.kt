package eu.kanade.domain.anime.interactor

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.NoEpisodesException
import tachiyomi.domain.episode.repository.EpisodeRepository

/**
 * Episodes are matched to the database by url, so anything that changes the url a source
 * hands out — a domain move, a mirror, a token in the link — deletes every row and adds it
 * back. What must not go with it is the watch state, and above all the resume position.
 *
 * The other half is the empty answer. A source that momentarily returns nothing looks
 * identical to an entry whose episodes were all pulled, and obeying it deletes everything.
 */
class SyncEpisodesWithSourceTest {

    private val repository = mockk<EpisodeRepository>(relaxed = true)
    private val sync = SyncEpisodesWithSource(repository)

    private val anime = Anime.create().copy(id = 7L, source = 99L, title = "Entry", url = "/entry")

    private fun source(episodes: List<SEpisode>, id: Long = 99L): AnimeSource {
        val source = mockk<AnimeSource>()
        coEvery { source.id } returns id
        coEvery { source.getEpisodeList(any<SAnime>()) } returns episodes
        return source
    }

    private fun sEpisode(url: String, name: String, number: Float) = SEpisode.create().apply {
        this.url = url
        this.name = name
        this.episode_number = number
    }

    private fun stored(
        id: Long,
        url: String,
        name: String,
        number: Double,
        seen: Boolean = false,
        bookmark: Boolean = false,
        lastSecondSeen: Long = 0,
        totalSeconds: Long = 0,
        dateFetch: Long = 1_000L,
    ) = Episode.create().copy(
        id = id,
        animeId = anime.id,
        url = url,
        name = name,
        episodeNumber = number,
        seen = seen,
        bookmark = bookmark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
    )

    private suspend fun inserted(): List<Episode> {
        val captured = slot<List<Episode>>()
        coVerify { repository.addAllEpisodes(capture(captured)) }
        return captured.captured
    }

    @Test
    fun `an episode re-listed under a new url keeps its position`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/old/ep1", "Episode 1", 1.0, lastSecondSeen = 754, totalSeconds = 1440),
        )

        sync.await(anime, source(listOf(sEpisode("/new/ep1", "Episode 1", 1f))))

        val new = inserted().single()
        assertEquals("/new/ep1", new.url)
        assertEquals(754L, new.lastSecondSeen)
        assertEquals(1440L, new.totalSeconds)
    }

    @Test
    fun `and its seen flag, its bookmark and its original fetch date`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/old/ep1", "Episode 1", 1.0, seen = true, bookmark = true, dateFetch = 4_242L),
        )

        sync.await(anime, source(listOf(sEpisode("/new/ep1", "Episode 1", 1f))))

        val new = inserted().single()
        assertTrue(new.seen)
        assertTrue(new.bookmark)
        // A re-added episode is not news, so it must not resurface in the Updates tab.
        assertEquals(4_242L, new.dateFetch)
    }

    @Test
    fun `the old row is deleted, so the entry does not end up holding both`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/old/ep1", "Episode 1", 1.0),
        )

        sync.await(anime, source(listOf(sEpisode("/new/ep1", "Episode 1", 1f))))

        coVerify { repository.removeEpisodesWithIds(listOf(1L)) }
    }

    @Test
    fun `a genuinely new episode is not handed someone else's position`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/ep1", "Episode 1", 1.0, lastSecondSeen = 900),
        )

        sync.await(
            anime,
            source(listOf(sEpisode("/ep1", "Episode 1", 1f), sEpisode("/ep2", "Episode 2", 2f))),
        )

        val new = inserted().single()
        assertEquals("/ep2", new.url)
        assertEquals(0L, new.lastSecondSeen)
    }

    @Test
    fun `an episode whose number could not be recognised carries nothing`() = runBlocking {
        // episodeNumber below zero is what Episode.create leaves behind and what
        // isRecognizedNumber rejects; matching on it would pair unrelated episodes.
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/old/special", "Special", -1.0, lastSecondSeen = 600),
        )

        sync.await(anime, source(listOf(sEpisode("/new/special", "Special", -1f))))

        assertEquals(0L, inserted().single().lastSecondSeen)
    }

    @Test
    fun `where a number has several rows the most watched one wins`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/old/ep1-mirror", "Episode 1", 1.0, lastSecondSeen = 0),
            stored(2L, "/old/ep1", "Episode 1", 1.0, lastSecondSeen = 1500),
        )

        sync.await(anime, source(listOf(sEpisode("/new/ep1", "Episode 1", 1f))))

        assertEquals(1500L, inserted().single().lastSecondSeen)
    }

    @Test
    fun `an episode still listed under the same url is left alone`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/ep1", "Episode 1", 1.0, seen = true, lastSecondSeen = 300),
        )

        sync.await(anime, source(listOf(sEpisode("/ep1", "Episode 1", 1f))))

        coVerify(exactly = 0) { repository.removeEpisodesWithIds(any()) }
        coVerify(exactly = 0) { repository.addAllEpisodes(any()) }
    }

    @Test
    fun `a source that answers with nothing is refused, not obeyed`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/ep1", "Episode 1", 1.0, lastSecondSeen = 900),
        )

        assertThrows(NoEpisodesException::class.java) {
            runBlocking { sync.await(anime, source(emptyList())) }
        }
        // The point of refusing: nothing was deleted on the way out.
        coVerify(exactly = 0) { repository.removeEpisodesWithIds(any()) }
    }

    @Test
    fun `the local source is exempt, where an empty folder really is empty`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns listOf(
            stored(1L, "/ep1", "Episode 1", 1.0),
        )

        sync.await(anime.copy(source = 0L), source(emptyList(), id = 0L))

        coVerify { repository.removeEpisodesWithIds(listOf(1L)) }
    }

    @Test
    fun `an entry with no episodes yet takes what the source lists`() = runBlocking {
        coEvery { repository.getEpisodeByAnimeId(anime.id) } returns emptyList()

        sync.await(anime, source(listOf(sEpisode("/ep1", "Episode 1", 1f))))

        assertEquals(1, inserted().size)
        coVerify(exactly = 0) { repository.removeEpisodesWithIds(any()) }
    }
}
