package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.anime.model.Anime

/**
 * An anime inside a backup.
 *
 * Carries only what cannot be fetched again from the source: what the user chose (favourite,
 * categories, flags) and what the user did (seen episodes, watch position, history). Metadata
 * is included so a restored library is readable before the sources have been contacted, but a
 * later refresh overwrites it — it is a convenience, not the record.
 */
@Serializable
class BackupAnime(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(10) var dateAdded: Long = 0,
    @ProtoNumber(11) var episodes: List<BackupEpisode> = emptyList(),
    @ProtoNumber(12) var categories: List<Long> = emptyList(),
    @ProtoNumber(13) var favorite: Boolean = true,
    @ProtoNumber(14) var episodeFlags: Int = 0,
    @ProtoNumber(15) var viewerFlags: Int = 0,
    @ProtoNumber(16) var history: List<BackupAnimeHistory> = emptyList(),
    @ProtoNumber(17) var updateStrategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(18) var lastModifiedAt: Long = 0,
    @ProtoNumber(19) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(20) var version: Long = 0,
    @ProtoNumber(21) var initialized: Boolean = false,
    @ProtoNumber(22) var backgroundUrl: String? = null,
) {
    fun toAnimeImpl(): Anime = Anime.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status.toLong(),
        thumbnailUrl = thumbnailUrl,
        backgroundUrl = backgroundUrl,
        favorite = favorite,
        source = source,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags.toLong(),
        episodeFlags = episodeFlags.toLong(),
        updateStrategy = updateStrategy,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        initialized = initialized,
    )
}

fun Anime.toBackupAnime() = BackupAnime(
    source = source,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genre = genre.orEmpty(),
    status = status.toInt(),
    thumbnailUrl = thumbnailUrl,
    backgroundUrl = backgroundUrl,
    dateAdded = dateAdded,
    favorite = favorite,
    episodeFlags = episodeFlags.toInt(),
    viewerFlags = viewerFlags.toInt(),
    updateStrategy = updateStrategy,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    initialized = initialized,
)
