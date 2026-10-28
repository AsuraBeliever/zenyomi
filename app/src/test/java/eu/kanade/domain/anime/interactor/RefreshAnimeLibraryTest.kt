package eu.kanade.domain.anime.interactor

import eu.kanade.tachiyomi.animesource.AnimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.anime.interactor.EpisodeFetchInterval
import tachiyomi.domain.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * A pull to refresh asks for every series; only the periodic update skips the ones whose next
 * expected episode is still far off.
 */
class RefreshAnimeLibraryTest {

    private val source = mockk<AnimeSource>()
    private val sync = mockk<SyncEpisodesWithSource>()

    // Expected again in the far future: outside the release period.
    private val resting = Anime.create().copy(id = 1L, source = 9L, title = "Resting", nextUpdate = Long.MAX_VALUE)
    private val airing = Anime.create().copy(id = 2L, source = 9L, title = "Airing", nextUpdate = 0L)

    private val refresh = RefreshAnimeLibrary(
        getAnimeFavorites = mockk<GetAnimeFavorites> { coEvery { await() } returns listOf(resting, airing) },
        syncEpisodesWithSource = sync,
        sourceManager = mockk<AnimeSourceManager> { coEvery { get(9L) } returns source },
        updateAnime = mockk<UpdateAnime>(relaxed = true),
        fetchInterval = EpisodeFetchInterval(mockk(relaxed = true)),
        // Mihon's defaults, which include "outside release period".
        libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
    )

    init {
        coEvery { sync.await(any(), any()) } returns emptyList()
    }

    @Test
    fun `Manual refresh checks every series`() = runBlocking {
        refresh.await(isAutomatic = false)

        coVerify { sync.await(resting, source) }
        coVerify { sync.await(airing, source) }
    }

    @Test
    fun `Automatic update skips series outside their release period`() = runBlocking {
        refresh.await(isAutomatic = true)

        coVerify(exactly = 0) { sync.await(resting, source) }
        coVerify { sync.await(airing, source) }
    }
}
