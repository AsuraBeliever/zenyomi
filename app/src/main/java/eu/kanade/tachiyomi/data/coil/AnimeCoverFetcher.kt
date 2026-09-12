package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.await
import okhttp3.Call
import okhttp3.Request
import okio.buffer
import okio.source
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.io.IOException

/**
 * Loads an anime cover through the source that published it.
 *
 * Fetching the thumbnail URL directly is what Coil does by default, and plenty of sites answer
 * that with a 403: they want the Referer and User-Agent the source sends with every other
 * request. The result was a library and a catalogue full of blank covers.
 *
 * Deliberately thinner than [MangaCoverFetcher], which also keeps a cover cache on disk for
 * custom covers and offline use. This only fixes the fetch; that cache can come later.
 *
 * Takes the url and source id rather than an [Anime] so that screens holding only an
 * [AnimeCover] — history, updates — get the headers too instead of falling back to Coil's
 * plain fetch and showing blanks.
 */
class AnimeCoverFetcher(
    private val url: String?,
    private val sourceId: Long,
    private val describedAs: String,
    private val sourceManager: AnimeSourceManager,
    private val callFactoryLazy: Lazy<Call.Factory>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        if (url.isNullOrBlank()) throw IllegalStateException("No cover for $describedAs")

        val source = sourceManager.get(sourceId) as? AnimeHttpSource
        val client = source?.client ?: callFactoryLazy.value

        val request = Request.Builder()
            .url(url)
            .apply { source?.headers?.let { headers(it) } }
            .build()

        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} fetching the cover for $describedAs")
        }

        return SourceFetchResult(
            source = ImageSource(
                source = response.body.byteStream().source().buffer(),
                fileSystem = okio.FileSystem.SYSTEM,
            ),
            mimeType = response.body.contentType()?.toString(),
            dataSource = DataSource.NETWORK,
        )
    }

    class AnimeFactory(
        private val sourceManager: AnimeSourceManager,
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Anime> {
        override fun create(data: Anime, options: Options, imageLoader: ImageLoader): Fetcher {
            return AnimeCoverFetcher(
                url = data.thumbnailUrl,
                sourceId = data.source,
                describedAs = data.title,
                sourceManager = sourceManager,
                callFactoryLazy = callFactoryLazy,
            )
        }
    }

    class AnimeCoverFactory(
        private val sourceManager: AnimeSourceManager,
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<AnimeCover> {
        override fun create(data: AnimeCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return AnimeCoverFetcher(
                url = data.url,
                sourceId = data.sourceId,
                describedAs = "anime ${data.animeId}",
                sourceManager = sourceManager,
                callFactoryLazy = callFactoryLazy,
            )
        }
    }
}
