package eu.kanade.tachiyomi.ui.animecategory

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.ArrowDownward
import mihon.icons.materialsymbols.rounded.ArrowUpward
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Edit
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Categories of the anime library: create, rename, reorder, delete.
 *
 * Reordering is two arrows rather than drag-and-drop. Mihon uses a drag handle, but its
 * implementation lives inside its own presentation package; arrows cost nothing to get right
 * and work the same for someone who cannot hold a long press steady.
 */
class AnimeCategoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<AnimeCategoryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        var creating by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf<AnimeCategory?>(null) }
        var deleting by remember { mutableStateOf<AnimeCategory?>(null) }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                val message = when (event) {
                    AnimeCategoryViewModel.Event.NameAlreadyExists -> MR.strings.error_category_exists
                    AnimeCategoryViewModel.Event.InternalError -> MR.strings.internal_error
                }
                snackbarHostState.showSnackbar(context.stringResource(message))
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.categories),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Add,
                        contentDescription = stringResource(MR.strings.action_add),
                    )
                }
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isEmpty -> EmptyScreen(
                    stringRes = MR.strings.information_empty_category,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> LazyColumn(contentPadding = contentPadding) {
                    items(state.categories, key = { it.id }) { category ->
                        val index = state.categories.indexOf(category)
                        ListItem(
                            headlineContent = { Text(category.name) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(
                                        onClick = { viewModel.moveUp(category) },
                                        enabled = index > 0,
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.ArrowUpward,
                                            contentDescription = stringResource(ANMR.strings.anime_category_move_up),
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveDown(category) },
                                        enabled = index < state.categories.lastIndex,
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.ArrowDownward,
                                            contentDescription = stringResource(
                                                ANMR.strings.anime_category_move_down,
                                            ),
                                        )
                                    }
                                    IconButton(onClick = { renaming = category }) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.Edit,
                                            contentDescription = stringResource(
                                                MR.strings.action_rename_category,
                                            ),
                                        )
                                    }
                                    IconButton(onClick = { deleting = category }) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.Delete,
                                            contentDescription = stringResource(MR.strings.action_delete),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        if (creating) {
            CategoryNameDialog(
                title = stringResource(MR.strings.action_add),
                initialName = "",
                onDismiss = { creating = false },
                onConfirm = {
                    viewModel.create(it)
                    creating = false
                },
            )
        }

        renaming?.let { category ->
            CategoryNameDialog(
                title = stringResource(MR.strings.action_rename_category),
                initialName = category.name,
                onDismiss = { renaming = null },
                onConfirm = {
                    viewModel.rename(category, it)
                    renaming = null
                },
            )
        }

        deleting?.let { category ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(stringResource(MR.strings.delete_category)) },
                // Says what is not about to happen: deleting a category that holds a hundred
                // entries should not feel like deleting a hundred entries.
                text = { Text(stringResource(ANMR.strings.anime_category_delete_confirm, category.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.delete(category)
                            deleting = null
                        },
                    ) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(MR.strings.name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
