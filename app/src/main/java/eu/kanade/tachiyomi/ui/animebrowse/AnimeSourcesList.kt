package eu.kanade.tachiyomi.ui.animebrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.anime.AnimeSourceHealth
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.ui.animebrowse.globalsearch.AnimeGlobalSearchScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.TravelExplore
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * The installed anime sources, with the same language filter Mihon offers for manga ones.
 */
@Composable
fun AnimeSourcesList(
    state: AnimeSourcesViewModel.State,
    contentPadding: PaddingValues,
    onSelectLanguage: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow

    when {
        state.isLoading -> LoadingScreen(modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.empty_screen,
            modifier = modifier.padding(contentPadding),
        )
        else -> LazyColumn(contentPadding = contentPadding, modifier = modifier) {
            item(key = "filter") {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Here rather than in the Browse toolbar: that toolbar is shared with the
                    // manga tabs, whose reselect already opens Mihon's own global search.
                    IconButton(onClick = { navigator.push(AnimeGlobalSearchScreen()) }) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.TravelExplore,
                            contentDescription = stringResource(ANMR.strings.anime_global_search),
                        )
                    }
                    SourceLanguageFilter(
                        languages = state.languages,
                        selected = state.selectedLanguage,
                        onSelect = onSelectLanguage,
                    )
                }
            }

            if (state.isFilteredEmpty) {
                item(key = "no-results") {
                    Text(
                        text = stringResource(ANMR.strings.anime_sources_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(state.visibleSources, key = { it.id }) { source ->
                val broken = state.broken[source.id]
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = {
                        Column {
                            Text(
                                text = LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // Said here rather than left for the user to discover by opening it:
                            // the whole cost of a dead source is the trip you take to find out.
                            if (broken != null) {
                                Text(
                                    text = stringResource(
                                        when (broken) {
                                            AnimeSourceHealth.Reason.Gone ->
                                                ANMR.strings.anime_source_health_gone
                                            AnimeSourceHealth.Reason.Outdated ->
                                                ANMR.strings.anime_source_health_outdated
                                        },
                                        state.checkedOn,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        if (source is ConfigurableAnimeSource) {
                            IconButton(
                                onClick = { navigator.push(AnimeSourcePreferencesScreen(source.id)) },
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Settings,
                                    contentDescription = stringResource(MR.strings.label_settings),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        navigator.push(AnimeCatalogScreen(source.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceLanguageFilter(
    languages: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = MaterialSymbols.Rounded.FilterList,
            contentDescription = stringResource(MR.strings.action_filter),
            tint = if (selected != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(ANMR.strings.anime_extension_all_languages)) },
            trailingIcon = {
                if (selected == null) Icon(MaterialSymbols.Rounded.Check, contentDescription = null)
            },
            onClick = {
                onSelect(null)
                expanded = false
            },
        )
        languages.forEach { lang ->
            DropdownMenuItem(
                text = { Text(LocaleHelper.getSourceDisplayName(lang, context)) },
                trailingIcon = {
                    if (selected == lang) Icon(MaterialSymbols.Rounded.Check, contentDescription = null)
                },
                onClick = {
                    onSelect(lang)
                    expanded = false
                },
            )
        }
    }
}
