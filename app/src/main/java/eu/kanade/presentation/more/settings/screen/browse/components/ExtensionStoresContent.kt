package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import mihon.domain.extension.model.ExtensionStore
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.simpleicons.Discord
import mihon.icons.simpleicons.SimpleIcons
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExtensionStoresContent(
    repos: List<ExtensionStore>,
    // Zenyomi: anime repositories live in their own table but belong on the same screen —
    // a user adding one has no reason to care which half of the app stores it.
    animeRepos: List<ExtensionStore>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onCopy: (ExtensionStore) -> Unit,
    onOpenWebsite: (ExtensionStore) -> Unit,
    onOpenDiscord: (ExtensionStore) -> Unit,
    onClickDelete: (ExtensionStore) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = modifier,
    ) {
        // Headers only once both kinds are present. With one kind the labels say nothing the
        // user does not already know.
        val labelled = repos.isNotEmpty() && animeRepos.isNotEmpty()

        if (labelled) {
            item(key = "manga-store-header") { StoreSectionHeader(ANMR.strings.browse_section_manga_extensions) }
        }
        repos.forEach {
            item(key = "manga-${it.indexUrl}") {
                ExtensionStoresListItem(
                    modifier = Modifier.animateItem(),
                    store = it,
                    onOpenWebsite = { onOpenWebsite(it) },
                    onOpenDiscord = { onOpenDiscord(it) },
                    onCopy = { onCopy(it) },
                    onDelete = { onClickDelete(it) },
                )
            }
        }

        if (labelled) {
            item(key = "anime-store-header") { StoreSectionHeader(ANMR.strings.browse_section_anime_extensions) }
        }
        animeRepos.forEach {
            item(key = "anime-${it.indexUrl}") {
                ExtensionStoresListItem(
                    modifier = Modifier.animateItem(),
                    store = it,
                    onOpenWebsite = { onOpenWebsite(it) },
                    onOpenDiscord = { onOpenDiscord(it) },
                    onCopy = { onCopy(it) },
                    onDelete = { onClickDelete(it) },
                )
            }
        }
    }
}

@Composable
private fun StoreSectionHeader(label: dev.icerock.moko.resources.StringResource) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.small),
    )
}

@Composable
private fun ExtensionStoresListItem(
    store: ExtensionStore,
    onOpenWebsite: () -> Unit,
    onOpenDiscord: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = MaterialSymbols.AutoMirroredRounded.Label, contentDescription = null)
            Text(
                text = store.name,
                modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onOpenWebsite) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Public,
                    contentDescription = stringResource(MR.strings.action_open_in_browser),
                )
            }

            if (store.contact.discord != null) {
                IconButton(onClick = onOpenDiscord) {
                    Icon(
                        imageVector = SimpleIcons.Discord,
                        contentDescription = null,
                    )
                }
            }

            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.ContentCopy,
                    contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
    }
}
