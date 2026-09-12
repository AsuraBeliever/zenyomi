package eu.kanade.presentation.anime

import androidx.compose.runtime.Composable
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The sentence to put on screen for a failure from an anime source.
 *
 * Composable because the mapping ends in a string resource; [AnimeSourceError] itself stays
 * free of Compose so it can be reasoned about — and changed — without touching the UI.
 */
@Composable
fun animeSourceErrorText(error: Throwable): String {
    val message = AnimeSourceError.describe(error)
    val body = message.text ?: message.res?.let { stringResource(it) } ?: return error.javaClass.simpleName
    return message.detail?.let { "$body ($it)" } ?: body
}
