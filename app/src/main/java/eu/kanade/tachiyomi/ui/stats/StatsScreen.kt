package eu.kanade.tachiyomi.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.stats.StatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.mangaStatsSections
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animestats.AnimeStatsState
import eu.kanade.tachiyomi.ui.animestats.AnimeStatsViewModel
import eu.kanade.tachiyomi.ui.animestats.animeStatsSections
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class StatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = metroViewModel<StatsViewModel>()
        val state by viewModel.state.collectAsState()

        // Zenyomi: the anime figures live here too. They used to be a separate screen behind
        // the anime library's overflow menu, which now carries the same three items the manga
        // library's does — and half your library's numbers being in a different place from the
        // other half was never a good answer anyway.
        val animeViewModel = metroViewModel<AnimeStatsViewModel>()
        val animeState by animeViewModel.state.collectAsState()
        var half by rememberSaveable { mutableStateOf(StatsHalf.MANGA) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_stats),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                PrimaryTabRow(selectedTabIndex = half.ordinal) {
                    StatsHalf.entries.forEach { entry ->
                        Tab(
                            selected = half == entry,
                            onClick = { half = entry },
                            text = { Text(stringResource(entry.label)) },
                        )
                    }
                }

                val mangaReady = state as? StatsScreenState.Success
                val animeReady = animeState as? AnimeStatsState.Success
                val waiting = when (half) {
                    StatsHalf.MANGA -> mangaReady == null
                    StatsHalf.ANIME -> animeReady == null
                    StatsHalf.BOTH -> mangaReady == null || animeReady == null
                }
                if (waiting) {
                    LoadingScreen()
                    return@Column
                }

                val rest = PaddingValues(bottom = paddingValues.calculateBottomPadding())
                when (half) {
                    StatsHalf.MANGA -> StatsScreenContent(mangaReady!!, rest)
                    StatsHalf.ANIME -> LazyColumn(contentPadding = rest) {
                        animeStatsSections(animeReady!!)
                    }
                    // Stacked rather than added together: a chapter and an episode are not the
                    // same unit, and a single number mixing them would be a worse answer than
                    // two honest ones.
                    StatsHalf.BOTH -> LazyColumn(contentPadding = rest) {
                        item { SectionHeader(stringResource(ANMR.strings.label_manga_library)) }
                        mangaStatsSections(mangaReady!!)
                        item { SectionHeader(stringResource(ANMR.strings.label_anime_library)) }
                        animeStatsSections(animeReady!!)
                    }
                }
            }
        }
    }
}

/** Which half of the library the figures are about. */
enum class StatsHalf(val label: dev.icerock.moko.resources.StringResource) {
    MANGA(ANMR.strings.label_manga_library),
    ANIME(ANMR.strings.label_anime_library),
    BOTH(ANMR.strings.label_stats_both),
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
