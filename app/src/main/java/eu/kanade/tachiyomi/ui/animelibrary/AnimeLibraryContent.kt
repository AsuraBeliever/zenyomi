package eu.kanade.tachiyomi.ui.animelibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.animelibrary.setting.AnimeLibraryDisplayMode
import tachiyomi.domain.library.anime.LibraryAnime

/**
 * Grid of anime in the library.
 *
 * Written for the anime side rather than reusing Mihon's library grid, whose components
 * are internal to its own package; sharing them would mean widening their visibility and
 * touching files that need to keep merging cleanly from upstream.
 *
 * Covers go through [eu.kanade.tachiyomi.data.coil.AnimeCoverFetcher], which asks the source
 * that published them and carries its headers. Requesting the thumbnail url directly, as this
 * used to, gets a 403 from any site that checks the Referer.
 */
@Composable
fun AnimeLibraryContent(
    library: List<LibraryAnime>,
    displayMode: AnimeLibraryDisplayMode,
    contentPadding: PaddingValues,
    onAnimeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (displayMode == AnimeLibraryDisplayMode.LIST) {
        LazyColumn(contentPadding = contentPadding, modifier = modifier) {
            items(library, key = { it.id }) { item ->
                AnimeLibraryListItem(item, onClick = { onAnimeClick(item.id) })
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        items(library, key = { it.id }) { item ->
            AnimeLibraryGridItem(
                item = item,
                titleUnderCover = displayMode == AnimeLibraryDisplayMode.COMFORTABLE_GRID,
                onClick = { onAnimeClick(item.id) },
            )
        }
    }
}

@Composable
private fun AnimeLibraryGridItem(
    item: LibraryAnime,
    titleUnderCover: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = item.anime,
                contentDescription = item.anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(4.dp)),
            )
            // Only when there is something to say: a "0" on every finished anime is noise.
            if (item.unseenCount > 0) {
                Text(
                    text = item.unseenCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            // Cover-only lays the title over the bottom of the art, on a scrim so it stays
            // readable whatever the cover happens to be.
            if (!titleUnderCover) {
                Text(
                    text = item.anime.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        if (titleUnderCover) {
            Text(
                text = item.anime.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AnimeLibraryListItem(item: LibraryAnime, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model = item.anime,
                contentDescription = item.anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 40.dp, height = 60.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        },
        headlineContent = {
            Text(item.anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            if (item.unseenCount > 0) {
                Text(
                    text = item.unseenCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
