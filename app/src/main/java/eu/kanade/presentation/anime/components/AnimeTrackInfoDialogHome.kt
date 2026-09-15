package eu.kanade.presentation.anime.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.track.TrackInfoItem
import eu.kanade.presentation.track.TrackInfoItemEmpty
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.animetrack.AnimeTrackItem
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

/**
 * The tracking sheet for an anime.
 *
 * Deliberately not a tracking sheet of its own: the rows are Mihon's [TrackInfoItem] and
 * [TrackInfoItemEmpty], and everything below is the adapter that turns an anime track into the
 * primitives they want. The sheet used to be a list of plain rows with a title, a status and an
 * unbind button, against Mihon's status / progress / score / dates / menu — so it was not a
 * styling difference, it was most of the screen missing.
 *
 * Episodes where Mihon says chapters, which is the only thing that reads differently.
 */
@Composable
fun AnimeTrackInfoDialogHome(
    trackItems: List<AnimeTrackItem>,
    dateFormat: DateTimeFormatter,
    onStatusClick: (AnimeTrackItem) -> Unit,
    onEpisodeClick: (AnimeTrackItem) -> Unit,
    onScoreClick: (AnimeTrackItem) -> Unit,
    onStartDateEdit: (AnimeTrackItem) -> Unit,
    onEndDateEdit: (AnimeTrackItem) -> Unit,
    onNewSearch: (AnimeTrackItem) -> Unit,
    onOpenInBrowser: (AnimeTrackItem) -> Unit,
    onRemoved: (AnimeTrackItem) -> Unit,
    onCopyLink: (AnimeTrackItem) -> Unit,
    onTogglePrivate: (AnimeTrackItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        trackItems.forEach { item ->
            val track = item.track
            val tracker = item.tracker as Tracker
            if (track != null) {
                val supportsScoring = tracker.getScoreList().isNotEmpty()
                val supportsDates = tracker.supportsReadingDates
                val supportsPrivate = tracker.supportsPrivateTracking
                TrackInfoItem(
                    title = track.title,
                    tracker = tracker,
                    // The anime labels, not the manga ones: status 1 is "Watching" here.
                    status = (item.tracker as AnimeTracker).getStatusForAnime(track.status),
                    onStatusClick = { onStatusClick(item) },
                    chapters = "${track.lastEpisodeSeen.toInt()}".let {
                        if (track.totalEpisodes > 0) "$it / ${track.totalEpisodes}" else it
                    },
                    onChaptersClick = { onEpisodeClick(item) },
                    score = item.displayScore.takeIf { supportsScoring && track.score != 0.0 },
                    onScoreClick = { onScoreClick(item) }.takeIf { supportsScoring },
                    startDate = remember(track.startDate) {
                        dateFormat.format(track.startDate.toLocalDate().toJavaLocalDate())
                    }.takeIf { supportsDates && track.startDate != 0L },
                    onStartDateClick = { onStartDateEdit(item) }.takeIf { supportsDates },
                    endDate = remember(track.finishDate) {
                        dateFormat.format(track.finishDate.toLocalDate().toJavaLocalDate())
                    }.takeIf { supportsDates && track.finishDate != 0L },
                    onEndDateClick = { onEndDateEdit(item) }.takeIf { supportsDates },
                    onNewSearch = { onNewSearch(item) },
                    onOpenInBrowser = { onOpenInBrowser(item) },
                    onRemoved = { onRemoved(item) },
                    onCopyLink = { onCopyLink(item) },
                    private = track.private,
                    onTogglePrivate = { onTogglePrivate(item) }.takeIf { supportsPrivate },
                )
            } else {
                TrackInfoItemEmpty(
                    tracker = tracker,
                    onNewSearch = { onNewSearch(item) },
                )
            }
        }
    }
}
