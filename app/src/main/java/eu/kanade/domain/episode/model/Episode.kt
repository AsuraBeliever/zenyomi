package eu.kanade.domain.episode.model

import eu.kanade.tachiyomi.animesource.model.SEpisode
import tachiyomi.domain.episode.model.Episode

/**
 * Anime counterpart of [eu.kanade.domain.chapter.model.toSChapter].
 */
fun Episode.toSEpisode(): SEpisode = SEpisode.create().also {
    it.url = url
    it.name = name
    it.episode_number = episodeNumber.toFloat()
    it.scanlator = scanlator
    it.date_upload = dateUpload
    it.fillermark = fillermark
    it.summary = summary
    it.preview_url = previewUrl
}

/**
 * Overwrites the fields a source owns, leaving watch state alone.
 */
fun Episode.copyFromSEpisode(sEpisode: SEpisode): Episode = copy(
    name = sEpisode.name,
    url = sEpisode.url,
    dateUpload = sEpisode.date_upload,
    episodeNumber = sEpisode.episode_number.toDouble(),
    scanlator = sEpisode.scanlator?.ifBlank { null },
    summary = sEpisode.summary,
    previewUrl = sEpisode.preview_url,
    fillermark = sEpisode.fillermark,
)
