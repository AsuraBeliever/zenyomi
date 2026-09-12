package eu.kanade.tachiyomi.ui.animeplayer

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the player needs to open one video.
 *
 * A source almost never hands back a url that plays on its own. Video hosts answer a bare
 * request with 403 unless the Referer and User-Agent the source used travel with it, and an
 * anime stream is routinely Japanese audio with the subtitles in a separate file, so a url by
 * itself plays a show the viewer cannot follow. Both used to be resolved correctly and then
 * dropped on the way to mpv, because what crossed into the player was a bare string.
 */
@Serializable
data class PlaybackRequest(
    val url: String,
    /** Header name to value, handed to mpv as `http-header-fields`. */
    val headers: Map<String, String> = emptyMap(),
    /** Subtitle files that live outside the container and have to be added by hand. */
    val subtitleTracks: List<Track> = emptyList(),
    /** Audio files that live outside the container; dual-audio sources use these. */
    val audioTracks: List<Track> = emptyList(),
    /** Options the source asks mpv for, such as a stream-specific timeout or protocol. */
    val mpvArgs: List<Pair<String, String>> = emptyList(),
) {

    fun encode(): String = Json.encodeToString(this)

    companion object {
        fun decode(raw: String): PlaybackRequest? = runCatching {
            Json.decodeFromString<PlaybackRequest>(raw)
        }.getOrNull()

        /** A file already on disk: no host to satisfy and no side-car tracks. */
        fun local(url: String) = PlaybackRequest(url = url)

        fun from(video: Video) = PlaybackRequest(
            url = video.videoUrl,
            headers = video.headers?.let { headers ->
                headers.names().associateWith { headers[it].orEmpty() }
            }.orEmpty(),
            subtitleTracks = video.subtitleTracks,
            audioTracks = video.audioTracks,
            mpvArgs = video.mpvArgs,
        )
    }
}
