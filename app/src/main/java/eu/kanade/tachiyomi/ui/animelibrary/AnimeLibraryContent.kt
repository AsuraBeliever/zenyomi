package eu.kanade.tachiyomi.ui.animelibrary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.library.components.MangaListItem
import eu.kanade.presentation.library.components.UnreadBadge
import eu.kanade.tachiyomi.ui.animelibrary.setting.AnimeLibraryDisplayMode
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

/**
 * The anime library's grid and list, drawn by Mihon's own components.
 *
 * This used to be a hand-written grid, on the reasoning that Mihon's were internal to their
 * package and sharing them would mean touching upstream files. Half of that was wrong:
 * `internal` in Kotlin is visible across the whole module and both live in `:app`, so
 * [LazyLibraryGrid], the badges and the three item composables were always callable from here.
 * The only real obstacle was that the items typed their cover parameter as a MangaCover; it is
 * `Any` now, because Coil picks its fetcher from the runtime type and an AnimeCover finds
 * AnimeCoverFetcher on its own.
 *
 * What is left here is only the part that differs: which entries to draw and what their badges
 * count. Every pixel comes from the manga library, which is the point — two libraries that look
 * different are two libraries someone has to keep looking different on purpose.
 */
@Composable
fun AnimeLibraryContent(
    library: List<LibraryAnime>,
    displayMode: AnimeLibraryDisplayMode,
    columns: Int,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onAnimeClick: (Long) -> Unit,
    onGlobalSearchClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (displayMode) {
        AnimeLibraryDisplayMode.LIST -> FastScrollLazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
        ) {
            item {
                if (!searchQuery.isNullOrEmpty()) {
                    GlobalSearchItem(
                        modifier = Modifier.fillMaxWidth(),
                        searchQuery = searchQuery,
                        onClick = onGlobalSearchClicked,
                    )
                }
            }
            items(
                items = library,
                key = { it.id },
                contentType = { "anime_library_list_item" },
            ) { item ->
                MangaListItem(
                    isSelected = false,
                    title = item.anime.title,
                    coverData = item.anime.asAnimeCover(),
                    badge = { UnreadBadge(count = item.unseenCount) },
                    onLongClick = {},
                    onClick = { onAnimeClick(item.id) },
                    onClickContinueReading = null,
                )
            }
        }

        AnimeLibraryDisplayMode.COMFORTABLE_GRID -> LazyLibraryGrid(
            modifier = modifier.fillMaxSize(),
            columns = columns,
            contentPadding = contentPadding,
        ) {
            globalSearch(searchQuery, onGlobalSearchClicked)
            items(
                items = library,
                key = { it.id },
                contentType = { "anime_library_comfortable_grid_item" },
            ) { item ->
                MangaComfortableGridItem(
                    isSelected = false,
                    title = item.anime.title,
                    coverData = item.anime.asAnimeCover(),
                    coverBadgeStart = {
                        DownloadsBadge(count = 0)
                        UnreadBadge(count = item.unseenCount)
                    },
                    onLongClick = {},
                    onClick = { onAnimeClick(item.id) },
                    onClickContinueReading = null,
                )
            }
        }

        AnimeLibraryDisplayMode.COMPACT_GRID -> LazyLibraryGrid(
            modifier = modifier.fillMaxSize(),
            columns = columns,
            contentPadding = contentPadding,
        ) {
            globalSearch(searchQuery, onGlobalSearchClicked)
            items(
                items = library,
                key = { it.id },
                contentType = { "anime_library_compact_grid_item" },
            ) { item ->
                MangaCompactGridItem(
                    isSelected = false,
                    title = item.anime.title,
                    coverData = item.anime.asAnimeCover(),
                    coverBadgeStart = {
                        DownloadsBadge(count = 0)
                        UnreadBadge(count = item.unseenCount)
                    },
                    onLongClick = {},
                    onClick = { onAnimeClick(item.id) },
                    onClickContinueReading = null,
                )
            }
        }

        AnimeLibraryDisplayMode.COVER_ONLY_GRID -> LazyLibraryGrid(
            modifier = modifier.fillMaxSize(),
            columns = columns,
            contentPadding = contentPadding,
        ) {
            globalSearch(searchQuery, onGlobalSearchClicked)
            items(
                items = library,
                key = { it.id },
                contentType = { "anime_library_cover_only_grid_item" },
            ) { item ->
                MangaCompactGridItem(
                    isSelected = false,
                    // Cover only: the title is what tells this mode from the compact grid.
                    title = null,
                    coverData = item.anime.asAnimeCover(),
                    coverBadgeStart = {
                        DownloadsBadge(count = 0)
                        UnreadBadge(count = item.unseenCount)
                    },
                    onLongClick = {},
                    onClick = { onAnimeClick(item.id) },
                    onClickContinueReading = null,
                )
            }
        }
    }
}

/** The "search this everywhere" row above the grid, spanning its full width. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.globalSearch(
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
        if (!searchQuery.isNullOrEmpty()) {
            GlobalSearchItem(
                modifier = Modifier.fillMaxWidth(),
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }
    }
}
