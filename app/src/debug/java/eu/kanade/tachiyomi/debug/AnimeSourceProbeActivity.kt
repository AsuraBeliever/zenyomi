package eu.kanade.tachiyomi.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mihon.app.di.appGraph
import java.io.File

/**
 * Walks every installed anime source and records how far each one gets.
 *
 * Debug-only, and never reachable from the UI: it exists so the state of the extension
 * ecosystem is measured rather than guessed. Tapping through eighteen sources by hand takes
 * an afternoon and is stale the next week; this takes minutes and writes a file.
 *
 *     adb shell am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity
 *     adb shell run-as app.zenyomi.dev cat files/source-probe.tsv
 *
 * Each source is taken through the three steps a user actually performs — list the popular
 * anime, open the first entry's episodes, resolve the first episode's video — because a
 * source whose catalogue loads can still be useless, and that is the case worth knowing about.
 *
 * It deliberately stays on screen for the whole run. A first version finished immediately and
 * probed from the background, where Android restricts the process's network: every source came
 * back "Unable to resolve host", which reads exactly like a dead site and was pure measurement
 * error. A probe that can produce a confident wrong answer is worse than no probe.
 */
class AnimeSourceProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val status = TextView(this).apply { textSize = 18f }
        setContentView(status)

        val graph = appGraph
        CoroutineScope(Dispatchers.IO).launch {
            val only = intent.getStringExtra("only")
            val sources = graph.animeSourceManager.getAll()
                .filterIsInstance<AnimeCatalogueSource>()
                .filter { only == null || it.name.contains(only, ignoreCase = true) }
                .sortedBy { it.name }

            if (intent.getStringExtra("list") != null) {
                sources.forEach { Log.i(TAG, row(it.id.toString(), it.name, it.lang)) }
                runOnUiThread { status.text = "Listed ${sources.size} source(s)" }
                return@launch
            }

            val rows = mutableListOf(HEADER)
            sources.forEachIndexed { index, source ->
                runOnUiThread { status.text = "Probing ${index + 1}/${sources.size}: ${source.name}" }
                val row = probe(source)
                Log.i(TAG, row)
                rows += row
            }

            val out = File(filesDir, "source-probe.tsv")
            out.writeText(rows.joinToString("\n", postfix = "\n"))
            Log.i(TAG, "done: ${sources.size} source(s) -> ${out.absolutePath}")
            runOnUiThread { status.text = "Done: ${sources.size} source(s)" }
        }
    }

    private suspend fun probe(source: AnimeCatalogueSource): String {
        val id = source.id.toString()
        val name = source.name
        val lang = source.lang

        val popular = step { withTimeout(STEP_TIMEOUT) { source.getPopularAnime(1) } }
        val anime = popular.value?.animes?.firstOrNull()
        if (popular.value == null || anime == null) {
            val detail = popular.failure ?: "returned an empty catalogue"
            return row(id, name, lang, "CATALOGUE", "0", detail)
        }

        val episodes = step { withTimeout(STEP_TIMEOUT) { source.episodesOf(anime) } }
        val episode = episodes.value?.firstOrNull()
        if (episode == null) {
            val detail = episodes.failure ?: "the entry lists no episodes"
            return row(id, name, lang, "EPISODES", popular.value.animes.size.toString(), detail)
        }

        val videos = step { withTimeout(STEP_TIMEOUT) { source.videosOf(episode) } }
        val count = videos.value?.size ?: 0
        if (count == 0) {
            val detail = videos.failure ?: "the episode resolves to no video"
            return row(id, name, lang, "VIDEO", popular.value.animes.size.toString(), detail)
        }

        return row(id, name, lang, "OK", popular.value.animes.size.toString(), "$count video(s)")
    }

    /** Episodes go through the same two routes the app uses, newest API first. */
    private suspend fun AnimeCatalogueSource.episodesOf(anime: SAnime): List<SEpisode> =
        runCatching { getAnimeEpisodeUpdate(anime, emptyList(), true, true).episodes }
            .getOrElse { getEpisodeList(anime) }

    private suspend fun AnimeCatalogueSource.videosOf(episode: SEpisode): List<Any> {
        val hosters = runCatching { getHosterList(episode) }.getOrNull()
        if (!hosters.isNullOrEmpty()) {
            val fromHosters = hosters.flatMap { hoster ->
                hoster.videoList ?: runCatching { getVideoList(hoster) }.getOrDefault(emptyList())
            }
            if (fromHosters.isNotEmpty()) return fromHosters
        }
        @Suppress("DEPRECATION")
        return getVideoList(episode)
    }

    /**
     * Catches [Throwable], not [Exception]: an extension can recurse into a StackOverflowError
     * or hit an OOM, and a probe that dies on the first such source measures nothing.
     */
    private inline fun <T> step(block: () -> T): Step<T> = try {
        Step(block(), null)
    } catch (t: Throwable) {
        // The stack goes to logcat in full: the summary says which source broke, the stack says
        // whether it broke in the extension or in us, and only the second is ours to fix.
        Log.w(TAG, "step failed", t)
        Step(null, "${t::class.java.simpleName}: ${t.message?.take(200) ?: "no message"}")
    }

    private data class Step<T>(val value: T?, val failure: String?)

    private fun row(vararg cells: String) = cells.joinToString("\t") { it.replace('\t', ' ') }

    companion object {
        private const val TAG = "AnimeSourceProbe"
        private const val STEP_TIMEOUT = 45_000L
        private val HEADER = listOf("id", "source", "lang", "stage", "catalogue", "detail")
            .joinToString("\t")
    }
}
