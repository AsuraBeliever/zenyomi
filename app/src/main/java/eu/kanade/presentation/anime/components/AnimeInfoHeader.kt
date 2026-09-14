package eu.kanade.presentation.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.manga.components.DotSeparatorText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.util.system.copyToClipboard
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.AttachMoney
import mihon.icons.materialsymbols.rounded.Block
import mihon.icons.materialsymbols.rounded.Brush
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Done
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.Pause
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Warning
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * The header of an anime entry: blurred backdrop, cover, title, author and status.
 *
 * A deliberate mirror of Mihon's [eu.kanade.presentation.manga.components.MangaInfoBox] rather
 * than a design of its own. The charter makes Mihon the reference for how a thing is done, and
 * a reader who opens a manga and then an anime should not be able to tell that two different
 * screens drew them.
 *
 * It is a copy rather than a reuse because Mihon's takes a [tachiyomi.domain.manga.model.Manga]
 * and its inner pieces are private. Everything here that Mihon exposes on primitives —
 * the action row, the description, the toolbar, the list rows — is reused outright instead, so
 * what is duplicated is only what the domain type forced.
 */
@Composable
fun AnimeInfoBox(
    appBarPadding: Dp,
    anime: Anime,
    sourceName: String,
    isStubSource: Boolean,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Backdrop: the cover again, blurred and faded into the background behind the header.
        val backdropGradientColors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background,
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(anime)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    drawRect(brush = Brush.verticalGradient(colors = backdropGradientColors))
                }
                .blur(4.dp)
                .alpha(0.2f),
        )

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = appBarPadding + 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MangaCover.Book(
                    modifier = Modifier
                        .sizeIn(maxWidth = 100.dp)
                        .align(Alignment.Top),
                    data = ImageRequest.Builder(LocalContext.current)
                        .data(anime)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(MR.strings.manga_cover),
                    onClick = onCoverClick,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AnimeContentInfo(
                        title = anime.title,
                        author = anime.author,
                        artist = anime.artist,
                        status = anime.status,
                        sourceName = sourceName,
                        isStubSource = isStubSource,
                        doSearch = doSearch,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AnimeContentInfo(
    title: String,
    author: String?,
    artist: String?,
    status: Long,
    sourceName: String,
    isStubSource: Boolean,
    doSearch: (query: String, global: Boolean) -> Unit,
    textAlign: TextAlign? = LocalTextStyle.current.textAlign,
) {
    val context = LocalContext.current
    Text(
        text = title.ifBlank { stringResource(MR.strings.unknown_title) },
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.clickableNoIndication(
            onLongClick = { if (title.isNotBlank()) context.copyToClipboard(title, title) },
            onClick = { if (title.isNotBlank()) doSearch(title, true) },
        ),
        textAlign = textAlign,
    )

    Spacer(modifier = Modifier.height(2.dp))

    Row(
        modifier = Modifier.secondaryItemAlpha(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Person,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = author?.takeIf { it.isNotBlank() } ?: stringResource(MR.strings.unknown_author),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.clickableNoIndication(
                onLongClick = { if (!author.isNullOrBlank()) context.copyToClipboard(author, author) },
                onClick = { if (!author.isNullOrBlank()) doSearch(author, true) },
            ),
            textAlign = textAlign,
        )
    }

    if (!artist.isNullOrBlank() && author != artist) {
        Row(
            modifier = Modifier.secondaryItemAlpha(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Brush,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.clickableNoIndication(
                    onLongClick = { context.copyToClipboard(artist, artist) },
                    onClick = { doSearch(artist, true) },
                ),
                textAlign = textAlign,
            )
        }
    }

    Spacer(modifier = Modifier.height(2.dp))

    Row(
        modifier = Modifier.secondaryItemAlpha(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (status) {
                SAnime.ONGOING.toLong() -> MaterialSymbols.Rounded.Schedule
                SAnime.COMPLETED.toLong() -> MaterialSymbols.Rounded.DoneAll
                SAnime.LICENSED.toLong() -> MaterialSymbols.Rounded.AttachMoney
                SAnime.PUBLISHING_FINISHED.toLong() -> MaterialSymbols.Rounded.Done
                SAnime.CANCELLED.toLong() -> MaterialSymbols.Rounded.Close
                SAnime.ON_HIATUS.toLong() -> MaterialSymbols.Rounded.Pause
                else -> MaterialSymbols.Rounded.Block
            },
            contentDescription = null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(16.dp),
        )
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Text(
                text = when (status) {
                    SAnime.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
                    SAnime.COMPLETED.toLong() -> stringResource(MR.strings.completed)
                    SAnime.LICENSED.toLong() -> stringResource(MR.strings.licensed)
                    SAnime.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
                    SAnime.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
                    SAnime.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
                    else -> stringResource(MR.strings.unknown)
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            DotSeparatorText()
            if (isStubSource) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = sourceName,
                modifier = Modifier.clickableNoIndication { doSearch(sourceName, false) },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}
