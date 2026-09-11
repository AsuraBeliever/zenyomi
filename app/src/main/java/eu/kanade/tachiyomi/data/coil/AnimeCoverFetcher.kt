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
 */
class AnimeCoverFetcher(
    private val anime: Anime,
    private val sourceManager: AnimeSourceManager,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val url = anime.thumbnailUrl
        if (url.isNullOrBlank()) throw IllegalStateException("No cover for ${anime.title}")

        val source = sourceManager.get(anime.source) as? AnimeHttpSource
        val client = source?.client ?: callFactoryLazy.value

        val request = Request.Builder()
            .url(url)
            .apply { source?.headers?.let { headers(it) } }
            .build()

        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} fetching the cover for ${anime.title}")
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

    class Factory(
        private val sourceManager: AnimeSourceManager,
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Anime> {
        override fun create(data: Anime, options: Options, imageLoader: ImageLoader): Fetcher {
            return AnimeCoverFetcher(data, sourceManager, callFactoryLazy, options)
        }
    }
}
