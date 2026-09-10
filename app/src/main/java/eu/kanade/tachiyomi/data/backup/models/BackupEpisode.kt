package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.episode.model.Episode

/**
 * An episode inside a backup.
 *
 * Numbered from 1 independently of [BackupChapter]: the two never share a message, so reusing
 * low numbers costs nothing and keeps the anime fields readable next to the manga ones.
 */
@Serializable
class BackupEpisode(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var seen: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    @ProtoNumber(6) var lastSecondSeen: Long = 0,
    @ProtoNumber(7) var totalSeconds: Long = 0,
    @ProtoNumber(8) var dateFetch: Long = 0,
    @ProtoNumber(9) var dateUpload: Long = 0,
    @ProtoNumber(10) var episodeNumber: Double = 0.0,
    @ProtoNumber(11) var sourceOrder: Long = 0,
    @ProtoNumber(12) var lastModifiedAt: Long = 0,
    @ProtoNumber(13) var version: Long = 0,
    @ProtoNumber(14) var fillermark: Boolean = false,
    @ProtoNumber(15) var summary: String? = null,
    @ProtoNumber(16) var previewUrl: String? = null,
) {
    fun toEpisodeImpl(): Episode = Episode.create().copy(
        url = url,
        name = name,
        episodeNumber = episodeNumber,
        scanlator = scanlator,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = sourceOrder,
        summary = summary,
        previewUrl = previewUrl,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}

fun Episode.toBackupEpisode() = BackupEpisode(
    url = url,
    name = name,
    scanlator = scanlator,
    seen = seen,
    bookmark = bookmark,
    lastSecondSeen = lastSecondSeen,
    totalSeconds = totalSeconds,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    episodeNumber = episodeNumber,
    sourceOrder = sourceOrder,
    lastModifiedAt = lastModifiedAt,
    version = version,
    fillermark = fillermark,
    summary = summary,
    previewUrl = previewUrl,
)
