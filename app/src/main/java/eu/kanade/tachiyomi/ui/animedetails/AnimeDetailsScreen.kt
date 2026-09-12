package eu.kanade.tachiyomi.ui.animedetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.anime.animeSourceErrorText
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animebrowse.globalsearch.AnimeGlobalSearchScreen
import eu.kanade.tachiyomi.ui.animecategory.AnimeCategoryScreen
import eu.kanade.tachiyomi.ui.animeplayer.AnimePlayerActivity
import eu.kanade.tachiyomi.ui.animetrack.AnimeTrackScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.Sync
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import mihon.icons.materialsymbols.roundedfilled.Favorite
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * One anime entry: cover, description and the list of episodes.
 *
 * An episode row resolves its video and opens the player, shows download state and offers
 * tracking once the anime is in the library.
 */
class AnimeDetailsScreen(private val animeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = assistedMetroViewModel<AnimeDetailsViewModel, AnimeDetailsViewModel.Factory> {
            create(animeId = animeId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
        val downloadQueue by viewModel.downloadQueue.collectAsStateWithLifecycle()
        val anime = state.anime

        val snackbarHostState = remember { SnackbarHostState() }
        // Resolved outside the effect: turning a failure into words needs the string
        // resources, which only a composable can reach.
        state.categoryDialog?.let { dialog ->
            AnimeCategoryDialog(
                categories = dialog.categories,
                initiallySelected = dialog.selected,
                onDismiss = viewModel::dismissCategoryDialog,
                onConfirm = viewModel::setCategories,
                onEditCategories = {
                    viewModel.dismissCategoryDialog()
                    navigator.push(AnimeCategoryScreen())
                },
            )
        }

        val playbackErrorText = state.playbackError?.let { animeSourceErrorText(it) }
        LaunchedEffect(playbackErrorText) {
            playbackErrorText?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearPlaybackError()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { scrollBehavior ->
                AppBar(
                    title = anime?.title.orEmpty(),
                    navigateUp = navigator::pop,
                    actions = {
                        if (anime != null) {
                            IconButton(onClick = viewModel::toggleFavorite) {
                                Icon(
                                    imageVector = if (anime.favorite) {
                                        MaterialSymbols.RoundedFilled.Favorite
                                    } else {
                                        MaterialSymbols.Rounded.Favorite
                                    },
                                    contentDescription = stringResource(
                                        if (anime.favorite) {
                                            MR.strings.remove_from_library
                                        } else {
                                            MR.strings.add_to_library
                                        },
                                    ),
                                )
                            }
                            // Tracking and categories are only meaningful for something in the
                            // library, so they follow the favourite rather than standing alone.
                            if (anime.favorite) {
                                IconButton(onClick = viewModel::showCategoryDialog) {
                                    Icon(
                                        imageVector = MaterialSymbols.AutoMirroredRounded.Label,
                                        contentDescription = stringResource(MR.strings.categories),
                                    )
                                }
                                IconButton(onClick = { navigator.push(AnimeTrackScreen(anime.id)) }) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.Sync,
                                        contentDescription = stringResource(MR.strings.manga_tracking_tab),
                                    )
                                }
                                var overflow by remember { mutableStateOf(false) }
                                IconButton(onClick = { overflow = true }) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.MoreVert,
                                        contentDescription = stringResource(
                                            MR.strings.action_menu_overflow_description,
                                        ),
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflow,
                                    onDismissRequest = { overflow = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(ANMR.strings.anime_migrate)) },
                                        onClick = {
                                            overflow = false
                                            navigator.push(
                                                AnimeGlobalSearchScreen(
                                                    initialQuery = anime.title,
                                                    migrateFromId = anime.id,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (state.isLoading || anime == null) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }

            LazyColumn(contentPadding = contentPadding) {
                item { AnimeHeader(anime) }
                item {
                    Text(
                        text = pluralStringResource(
                            ANMR.plurals.num_episodes,
                            state.episodes.size,
                            state.episodes.size,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // An empty list and a source that refused to answer look the same
                    // otherwise, and only one of them is worth retrying.
                    state.episodeError?.let { error ->
                        Text(
                            text = animeSourceErrorText(error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    HorizontalDivider()
                }
                items(state.episodes, key = { it.id }) { episode ->
                    ListItem(
                        headlineContent = { Text(episode.name) },
                        supportingContent = episode.scanlator?.let { scanlator ->
                            { Text(scanlator, style = MaterialTheme.typography.bodySmall) }
                        },
                        trailingContent = {
                            val percent = downloadProgress[episode.id]
                            val queued = downloadQueue.any { it.episodeId == episode.id }
                            when {
                                state.resolvingEpisodeId == episode.id ->
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                percent != null ->
                                    CircularProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier.size(20.dp),
                                    )
                                // Queued but not started: an indeterminate spinner would claim
                                // work is happening, so the row just shows it is waiting.
                                queued ->
                                    CircularProgressIndicator(
                                        progress = { 0f },
                                        modifier = Modifier.size(20.dp),
                                    )
                                episode.id in state.downloadedEpisodeIds ->
                                    IconButton(onClick = { viewModel.deleteDownload(episode) }) {
                                        Icon(
                                            imageVector = MaterialSymbols.RoundedFilled.CheckCircle,
                                            contentDescription = stringResource(
                                                ANMR.strings.anime_action_delete_download,
                                            ),
                                        )
                                    }
                                !state.canDownload -> Unit
                                else ->
                                    IconButton(onClick = { viewModel.downloadEpisode(episode) }) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.Download,
                                            contentDescription = stringResource(MR.strings.action_download),
                                        )
                                    }
                            }
                        },
                        modifier = Modifier.clickable(
                            enabled = state.resolvingEpisodeId == null,
                        ) {
                            viewModel.resolveVideo(episode) { url, headers ->
                                if (url != null) {
                                    context.startActivity(
                                        AnimePlayerActivity.newIntent(
                                            context,
                                            url,
                                            episode.name,
                                            episode.id,
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

@Composable
private fun AnimeHeader(anime: Anime) {
    Row(Modifier.padding(16.dp)) {
        AsyncImage(
            model = anime,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(anime.title, style = MaterialTheme.typography.titleMedium)
            anime.author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            anime.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Which categories an anime belongs to.
 *
 * Multi-select, because an anime can sit in several — that is the whole point of categories
 * over a single folder. The way to a library with no categories yet is through the same
 * dialog: offering nothing but Cancel would be a dead end.
 */
@Composable
private fun AnimeCategoryDialog(
    categories: List<AnimeCategory>,
    initiallySelected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    onEditCategories: () -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>().apply { addAll(initiallySelected) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.categories)) },
        text = {
            if (categories.isEmpty()) {
                Text(stringResource(MR.strings.information_empty_category))
            } else {
                LazyColumn {
                    items(categories, key = { it.id }) { category ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (category.id in selected) {
                                        selected.remove(category.id)
                                    } else {
                                        selected.add(category.id)
                                    }
                                },
                        ) {
                            Checkbox(
                                checked = category.id in selected,
                                onCheckedChange = null,
                            )
                            Text(
                                text = category.name,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (categories.isEmpty()) {
                TextButton(onClick = onEditCategories) {
                    Text(stringResource(MR.strings.action_edit_categories))
                }
            } else {
                TextButton(onClick = { onConfirm(selected.toList()) }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
