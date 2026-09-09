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
