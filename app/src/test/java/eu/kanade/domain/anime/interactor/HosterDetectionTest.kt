package eu.kanade.domain.anime.interactor

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The hoster route is skipped for sources that do not implement it, which saves a round trip
 * per episode on every published extension — none of them implements it.
 *
 * That is also why this test exists. With no extension available that takes the hoster route,
 * the only positive case is a made-up one, and the failure mode is silent: a detection that
 * wrongly answers "no" would quietly cost a real source its videos with nothing in the log.
 */
class HosterDetectionTest {

    /** What every published extension looks like: the old route only. */
    private class LegacySource : AnimeHttpSource() {
        override val baseUrl = "https://example.invalid"
        override val lang = "en"
        override val name = "Legacy"
        override val supportsLatest = false
        override fun animeDetailsParse(response: Response) = SAnime.create()
        override fun episodeListParse(response: Response) = emptyList<SEpisode>()
        override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
        override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
        override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
        override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()
        override fun searchAnimeParse(response: Response) = throw UnsupportedOperationException()
        override fun searchAnimeRequest(
            page: Int,
            query: String,
            filters: eu.kanade.tachiyomi.animesource.model.AnimeFilterList,
        ) =
            throw UnsupportedOperationException()
        override fun videoListParse(response: Response) = emptyList<eu.kanade.tachiyomi.animesource.model.Video>()
    }

    /** A source that took the newer route, by overriding the parse step. */
    private class HosterSource : AnimeHttpSource() {
        override val baseUrl = "https://example.invalid"
        override val lang = "en"
        override val name = "Hoster"
        override val supportsLatest = false
        override fun animeDetailsParse(response: Response) = SAnime.create()
        override fun episodeListParse(response: Response) = emptyList<SEpisode>()
        override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
        override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
        override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
        override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()
        override fun searchAnimeParse(response: Response) = throw UnsupportedOperationException()
        override fun searchAnimeRequest(
            page: Int,
            query: String,
            filters: eu.kanade.tachiyomi.animesource.model.AnimeFilterList,
        ) =
            throw UnsupportedOperationException()
        override fun videoListParse(response: Response) = emptyList<eu.kanade.tachiyomi.animesource.model.Video>()

        @Suppress("OVERRIDE_DEPRECATION")
        override fun hosterListParse(response: Response): List<Hoster> = emptyList()
    }

    /** And one that reached it through a subclass, which is how a real extension is layered. */
    private open class Middle : AnimeHttpSource() {
        override val baseUrl = "https://example.invalid"
        override val lang = "en"
        override val name = "Middle"
        override val supportsLatest = false
        override fun animeDetailsParse(response: Response) = SAnime.create()
        override fun episodeListParse(response: Response) = emptyList<SEpisode>()
        override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
        override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
        override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
        override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()
        override fun searchAnimeParse(response: Response) = throw UnsupportedOperationException()
        override fun searchAnimeRequest(
            page: Int,
            query: String,
            filters: eu.kanade.tachiyomi.animesource.model.AnimeFilterList,
        ) =
            throw UnsupportedOperationException()
        override fun videoListParse(response: Response) = emptyList<eu.kanade.tachiyomi.animesource.model.Video>()
    }

    private class DeepHosterSource : Middle() {
        @Suppress("OVERRIDE_DEPRECATION")
        override fun hosterListRequest(episode: SEpisode): Request = throw UnsupportedOperationException()

        @Suppress("OVERRIDE_DEPRECATION")
        override fun hosterListParse(response: Response): List<Hoster> = emptyList()
    }

    @Test
    fun `a source with only the old route is not asked for hosters`() {
        assertFalse(declaresHosterOverride(LegacySource::class.java))
    }

    @Test
    fun `a source that overrides the hoster parse is`() {
        assertTrue(declaresHosterOverride(HosterSource::class.java))
    }

    @Test
    fun `and so is one that does it from a subclass`() {
        assertTrue(declaresHosterOverride(DeepHosterSource::class.java))
    }
}
