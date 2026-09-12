package eu.kanade.presentation.anime

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.SerializationException
import tachiyomi.i18n.anime.ANMR
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** An episode the source accepted but could produce no video for. */
class NoVideoFoundException : Exception("No video found for this episode")

/**
 * Turns whatever an extension threw into a sentence a person can act on.
 *
 * Extensions are third-party code scraping sites that change without notice, so they fail
 * constantly and in every imaginable way. What used to reach the screen was the raw exception
 * text — `Unable to resolve host "cached.freeanimehentai.net"`, or an NPE's
 * `Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()'`. That tells
 * the user nothing they can use and reads as though the app is broken, when in almost every
 * case the site is down or the extension is out of date.
 *
 * The distinction worth drawing is not which Java class was thrown but **whose problem it is**:
 *
 * - the site is unreachable → wait, or the source is gone for good
 * - the site refused us → a challenge to solve in the WebView
 * - the extension could not read what came back → the extension is out of date, nothing the
 *   user can do but pick another source
 *
 * The raw throwable still goes to logcat at the call site; it belongs in a bug report, not on
 * screen.
 */
object AnimeSourceError {

    /**
     * Exception types that only ever mean the extension's parsing broke. They are thrown from
     * inside the extension while it picks apart a page that no longer looks the way it expects,
     * so their messages describe our internals rather than anything the user did.
     */
    private val PARSE_FAILURES = listOf(
        NullPointerException::class,
        ClassCastException::class,
        IndexOutOfBoundsException::class,
        NoSuchElementException::class,
        NumberFormatException::class,
        SerializationException::class,
    )

    fun describe(error: Throwable): Message = when {
        error is NoVideoFoundException -> Message(ANMR.strings.anime_error_no_video)

        error is TimeoutCancellationException -> Message(ANMR.strings.anime_error_timeout)

        error is UnknownHostException -> Message(ANMR.strings.anime_error_host_not_found)

        error is ConnectException || error is SocketTimeoutException ->
            Message(ANMR.strings.anime_error_cannot_connect)

        error is HttpException -> httpMessage(error)

        PARSE_FAILURES.any { it.isInstance(error) } -> Message(ANMR.strings.anime_error_outdated)

        // Anything else from the network layer: a reset connection, a broken pipe, a TLS
        // failure. The user's move is the same in all of them.
        error is IOException -> Message(ANMR.strings.anime_error_cannot_connect)

        // Left last on purpose. Extensions routinely throw a plain exception carrying a note
        // meant to be read — Jellyfin's "Select library in the extension settings." is one —
        // and swallowing that in favour of something generic would hide the one instruction
        // that fixes the source. Only a message that reads like prose is passed through.
        error.message?.looksLikeProse() == true -> Message(text = error.message)

        else -> Message(ANMR.strings.anime_error_unknown)
    }

    private fun httpMessage(error: HttpException): Message = when (error.code) {
        // Cloudflare and friends. Solvable, and the WebView is how.
        403, 503 -> Message(ANMR.strings.anime_error_blocked)
        // The extension asked for a page the site no longer serves.
        404, 410 -> Message(ANMR.strings.anime_error_outdated)
        // 520-527 are Cloudflare's "the origin is not answering me either".
        in 500..599 -> Message(ANMR.strings.anime_error_site_down, detail = "HTTP ${error.code}")
        else -> Message(ANMR.strings.anime_error_site_refused, detail = "HTTP ${error.code}")
    }

    /**
     * Whether a message was written for a person rather than produced by the runtime. Short,
     * and free of the punctuation that gives away a type name, a stack frame or a URL dump.
     */
    private fun String.looksLikeProse(): Boolean {
        if (isBlank() || length > 160) return false
        val technical = listOf("java.", "kotlin.", "Exception", "at ", "com.", "okhttp3.", "$")
        return technical.none { contains(it) }
    }

    /**
     * [text] wins when it is set — that is an extension's own words. Otherwise [res] is the
     * sentence to show, and [detail] a short technical suffix such as the HTTP status, which
     * is worth keeping because it is the one thing a user can quote back.
     */
    data class Message(
        val res: StringResource? = null,
        val detail: String? = null,
        val text: String? = null,
    )
}
