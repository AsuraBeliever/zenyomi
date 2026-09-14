package eu.kanade.tachiyomi.ui.animestats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.stats.components.StatsItem
import eu.kanade.presentation.more.stats.components.StatsOverviewItem
import eu.kanade.presentation.util.Screen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import mihon.icons.materialsymbols.rounded.LocalLibrary
import mihon.icons.materialsymbols.rounded.Schedule
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.Locale
import java.util.concurrent.TimeUnit

/** What the anime library holds, counted up. */
class AnimeStatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<AnimeStatsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_stats),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val current = state) {
                AnimeStatsState.Loading -> LoadingScreen(Modifier.padding(contentPadding))
                is AnimeStatsState.Success -> LazyColumn(contentPadding = contentPadding) {
                    animeStatsSections(current)
                }
            }
        }
    }
}

/**
 * The anime figures, as rows in somebody else's list.
 *
 * Pulled out of the screen so the Statistics entry in More can show these under a Both tab
 * beside the manga ones, rather than sending you to a second screen to see half the picture.
 */
fun LazyListScope.animeStatsSections(current: AnimeStatsState.Success) {
    item {
        SectionCard(MR.strings.label_overview_section) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                StatsOverviewItem(
                    title = current.libraryCount.toString(),
                    subtitle = stringResource(MR.strings.in_library),
                    icon = MaterialSymbols.Rounded.CollectionsBookmark,
                )
                StatsOverviewItem(
                    title = formatDuration(current.watchedSeconds),
                    subtitle = stringResource(ANMR.strings.stats_time_watched),
                    icon = MaterialSymbols.Rounded.Schedule,
                )
                StatsOverviewItem(
                    title = current.completedCount.toString(),
                    subtitle = stringResource(MR.strings.label_completed_titles),
                    icon = MaterialSymbols.Rounded.LocalLibrary,
                )
            }
        }
    }
    item {
        SectionCard(MR.strings.label_titles_section) {
            Row {
                StatsItem(
                    current.inGlobalUpdate.toString(),
                    stringResource(MR.strings.label_titles_in_global_update),
                )
                StatsItem(current.startedCount.toString(), stringResource(MR.strings.label_started))
                StatsItem(current.localCount.toString(), stringResource(MR.strings.label_local))
            }
        }
    }
    item {
        SectionCard(ANMR.strings.stats_episodes_section) {
            Row {
                StatsItem(current.totalEpisodes.toString(), stringResource(ANMR.strings.stats_total_episodes))
                StatsItem(current.seenEpisodes.toString(), stringResource(ANMR.strings.stats_seen_episodes))
                StatsItem(current.downloadedEpisodes.toString(), stringResource(MR.strings.label_downloaded))
            }
        }
    }
    item {
        SectionCard(MR.strings.label_tracker_section) {
            Row {
                StatsItem(current.trackedCount.toString(), stringResource(MR.strings.label_tracked_titles))
                StatsItem(
                    if (current.meanScore > 0) {
                        String.format(Locale.US, "%.2f", current.meanScore)
                    } else {
                        stringResource(MR.strings.not_applicable)
                    },
                    stringResource(MR.strings.label_mean_score),
                )
                StatsItem(current.loggedInTrackerCount.toString(), stringResource(MR.strings.label_used))
            }
        }
    }
}

@Composable
internal fun StatsSection(title: String, entries: List<Pair<String, String>>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        entries.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
    HorizontalDivider()
}

/** Hours and minutes; seconds are noise once you are counting a library's worth. */
internal fun formatDuration(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "-"
    }
}
