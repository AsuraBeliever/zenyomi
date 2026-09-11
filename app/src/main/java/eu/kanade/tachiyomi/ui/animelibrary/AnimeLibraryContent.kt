package eu.kanade.tachiyomi.ui.animelibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    contentPadding: PaddingValues,
    onAnimeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        items(library, key = { it.id }) { item ->
            AnimeLibraryGridItem(item, onClick = { onAnimeClick(item.id) })
        }
    }
}

@Composable
private fun AnimeLibraryGridItem(item: LibraryAnime, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
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
        }
        Text(
            text = item.anime.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
