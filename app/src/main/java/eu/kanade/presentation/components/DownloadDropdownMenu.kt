package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.manga.DownloadAction
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadDropdownMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    offset: DpOffset? = null,
    episodes: Boolean = false,
) {
    if (offset != null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            content = {
                DownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                    episodes = episodes,
                )
            },
        )
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = {
                DownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                    episodes = episodes,
                )
            },
        )
    }
}

@Composable
private fun DownloadDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    episodes: Boolean,
) {
    // El mismo menu para las dos bibliotecas, con las palabras de cada una. El enum sigue
    // diciendo CHAPTER porque es el de Mihon y no se toca; lo que el usuario lee, no: sobre
    // una lista de episodios, "Next 5 chapters" y "Unread" son de otra pantalla.
    val amount = if (episodes) ANMR.plurals.download_amount_anime else MR.plurals.download_amount
    val pending = if (episodes) ANMR.strings.download_unseen else MR.strings.download_unread
    val options = listOf(
        DownloadAction.NEXT_1_CHAPTER to pluralStringResource(amount, 1, 1),
        DownloadAction.NEXT_5_CHAPTERS to pluralStringResource(amount, 5, 5),
        DownloadAction.NEXT_10_CHAPTERS to pluralStringResource(amount, 10, 10),
        DownloadAction.NEXT_25_CHAPTERS to pluralStringResource(amount, 25, 25),
        DownloadAction.UNREAD_CHAPTERS to stringResource(pending),
        DownloadAction.BOOKMARKED_CHAPTERS to stringResource(MR.strings.download_bookmarked),
    )

    options.map { (downloadAction, string) ->
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onDownloadClicked(downloadAction)
                onDismissRequest()
            },
        )
    }
}
