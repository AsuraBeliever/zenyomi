package eu.kanade.tachiyomi.ui.animeupdates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.animeplayer.AnimePlayerActivity
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Episodes the anime library has gained, grouped by the day they arrived.
 *
 * Reached from the anime library rather than from Mihon's Updates tab: that tab renders its
 * own scaffold and app bar, so hosting both there would mean restructuring a screen that has
 * to keep merging from upstream. Moving it up is a later call, not a missing piece.
 */
class AnimeUpdatesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<AnimeUpdatesViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
        val downloadQueue by viewModel.downloadQueue.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        // A tap that resolves to nothing is indistinguishable from a tap that missed, so
        // whatever the source said is shown instead of silence.
        LaunchedEffect(state.playbackError) {
            state.playbackError?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearPlaybackError()
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(ANMR.strings.label_anime_updates),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isEmpty -> EmptyScreen(
                    stringRes = ANMR.strings.anime_updates_empty,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> LazyColumn(contentPadding = contentPadding) {
                    items(
                        count = state.items.size,
                        key = { index ->
                            when (val item = state.items[index]) {
                                is AnimeUpdatesUiItem.Header -> "header-${item.dateFetch}"
                                is AnimeUpdatesUiItem.Item -> "episode-${item.update.episodeId}"
                            }
                        },
                    ) { index ->
                        when (val item = state.items[index]) {
                            is AnimeUpdatesUiItem.Header -> Text(
                                text = relativeDateText(item.dateFetch),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            is AnimeUpdatesUiItem.Item -> UpdateRow(
                                item = item,
                                isResolving = state.resolvingEpisodeId == item.update.episodeId,
                                resolvingAny = state.resolvingEpisodeId != null,
                                isDownloaded = item.update.episodeId in state.downloadedEpisodeIds,
                                canDownload = item.update.episodeId in state.downloadableEpisodeIds,
                                downloadPercent = downloadProgress[item.update.episodeId],
                                isQueued = downloadQueue.any { it.episodeId == item.update.episodeId },
                                onOpenAnime = { navigator.push(AnimeDetailsScreen(item.update.animeId)) },
                                onToggleSeen = { viewModel.toggleSeen(item.update) },
                                onDownload = { viewModel.downloadEpisode(item.update) },
                                onDeleteDownload = { viewModel.deleteDownload(item.update) },
                                onPlay = {
                                    viewModel.resolveVideo(item.update) { url, headers ->
                                        if (url != null) {
                                            context.startActivity(
                                                AnimePlayerActivity.newIntent(
                                                    context,
                                                    url,
                                                    item.update.episodeName,
                                                    item.update.episodeId,
                                                    headers,
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun UpdateRow(
    item: AnimeUpdatesUiItem.Item,
    isResolving: Boolean,
    resolvingAny: Boolean,
    isDownloaded: Boolean,
    canDownload: Boolean,
    downloadPercent: Int?,
    isQueued: Boolean,
    onOpenAnime: () -> Unit,
    onToggleSeen: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onPlay: () -> Unit,
) {
    val update = item.update
    // A seen episode stays in the list — it is still news that it arrived — but reads as
    // already dealt with.
    val contentColor = if (update.seen) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    ListItem(
        colors = ListItemDefaults.colors(
            headlineColor = contentColor,
            supportingColor = contentColor,
        ),
        leadingContent = {
            AsyncImage(
                model = update.coverData,
                contentDescription = update.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 40.dp, height = 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenAnime),
            )
        },
        headlineContent = {
            Text(update.animeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = update.episodeName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onToggleSeen) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Check,
                        contentDescription = stringResource(
                            if (update.seen) {
                                ANMR.strings.anime_action_mark_unseen
                            } else {
                                ANMR.strings.anime_action_mark_seen
                            },
                        ),
                        tint = contentColor,
                    )
                }
                when {
                    isResolving -> CircularProgressIndicator(Modifier.size(20.dp))
                    downloadPercent != null -> CircularProgressIndicator(
                        progress = { downloadPercent / 100f },
                        modifier = Modifier.size(20.dp),
                    )
                    // Queued but not started: an indeterminate spinner would claim work is
                    // happening, so the row just shows it is waiting.
                    isQueued -> CircularProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.size(20.dp),
                    )
                    isDownloaded -> IconButton(onClick = onDeleteDownload) {
                        Icon(
                            imageVector = MaterialSymbols.RoundedFilled.CheckCircle,
                            contentDescription = stringResource(ANMR.strings.anime_action_delete_download),
                        )
                    }
                    !canDownload -> Unit
                    else -> IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Download,
                            contentDescription = stringResource(MR.strings.action_download),
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(enabled = !resolvingAny, onClick = onPlay),
    )
}
