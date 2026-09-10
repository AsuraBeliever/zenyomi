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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animeplayer.AnimePlayerActivity
import eu.kanade.tachiyomi.ui.animetrack.AnimeTrackScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.Sync
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import mihon.icons.materialsymbols.roundedfilled.Favorite
import tachiyomi.domain.anime.model.Anime
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

        Scaffold(
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
                            // Tracking is only meaningful for something in the library, so the
                            // action follows the favourite rather than standing on its own.
                            if (anime.favorite) {
                                IconButton(onClick = { navigator.push(AnimeTrackScreen(anime.id)) }) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.Sync,
                                        contentDescription = stringResource(MR.strings.manga_tracking_tab),
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
                            viewModel.resolveVideo(episode) { url ->
                                if (url != null) {
                                    context.startActivity(
                                        AnimePlayerActivity.newIntent(context, url, episode.name, episode.id),
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
            model = anime.thumbnailUrl,
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
