package eu.kanade.tachiyomi.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.animeplayer.AnimePlayerActivity
import eu.kanade.tachiyomi.ui.animeplayer.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mihon.app.di.appGraph
import tachiyomi.domain.episode.model.Episode
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

            // Plays an arbitrary url through our own player. The activity is not exported, so
            // adb cannot start it directly; this is the only way to ask "does the player open a
            // network stream at all" without tapping through the UI.
            val playUrl = intent.getStringExtra("playurl")
            if (playUrl != null) {
                val headers = intent.getStringArrayListExtra("headers").orEmpty()
                    .mapNotNull { line ->
                        val at = line.indexOf(':')
                        if (at <= 0) null else line.take(at).trim() to line.drop(at + 1).trim()
                    }
                    .toMap()
                Log.i(TAG, "playurl: $playUrl headers=${headers.keys}")
                startActivity(
                    AnimePlayerActivity.newIntent(
                        this@AnimeSourceProbeActivity,
                        request = PlaybackRequest(url = playUrl, headers = headers),
                        title = "probe",
                        episodeId = intent.getLongExtra("episode_id", 1L),
                    ),
                )
                return@launch
            }

            // Takes a source all the way to the player the way a tap does, through the same
            // interactor and the same PlaybackRequest. "The source resolves a url" and "the
            // episode plays" are different claims, and only this one tests the second.
            val playSource = intent.getStringExtra("play")
            if (playSource != null) {
                val source = sources.firstOrNull() ?: run {
                    Log.i(TAG, "play: no source matching '$playSource'")
                    return@launch
                }
                val videos = GetEpisodeVideos(graph.animeSourceManager)
                // Every hop is wrapped: an extension is free to throw, and KickAssAnime does
                // exactly that from getSearchAnime when a search comes back empty. Letting it
                // through killed the process and left no measurement at all.
                val anime = step { source.pickAnime(intent.getStringExtra("title")) }
                    .also { it.failure?.let { why -> Log.i(TAG, "play: entry failed: $why") } }
                    .value ?: run {
                    Log.i(TAG, "play: nothing found on ${source.name}")
                    return@launch
                }
                val episode = step { source.episodesOf(anime).pick(intent.getStringExtra("ep")) }
                    .also { it.failure?.let { why -> Log.i(TAG, "play: episodes failed: $why") } }
                    .value ?: run {
                    Log.i(TAG, "play: no matching episode")
                    return@launch
                }
                val found = step { videos.await(source.id, episode.toEpisode()) }.value.orEmpty()
                val video = step { videos.playable(source.id, found) }.value
                Log.i(TAG, "play: ${source.name} / ${anime.title} / ${episode.name}")
                Log.i(TAG, "play: ${found.size} video(s), chose ${video?.videoUrl ?: "none"}")
                if (video == null) return@launch
                val request = PlaybackRequest.from(video)
                Log.i(
                    TAG,
                    "play: headers=${request.headers.keys} subs=${request.subtitleTracks.size} " +
                        "audio=${request.audioTracks.size}",
                )
                startActivity(
                    AnimePlayerActivity.newIntent(
                        this@AnimeSourceProbeActivity,
                        request = request,
                        title = episode.name,
                        episodeId = intent.getLongExtra("episode_id", 1L),
                    ),
                )
                return@launch
            }

            // Dumps what a source actually hands back for a video, field by field. "No video"
            // and "a video whose url is empty because it still needs resolving" look identical
            // from the outside and need opposite fixes.
            val videosOf = intent.getStringExtra("videos")
            if (videosOf != null) {
                sources.forEach { source ->
                    describeVideos(
                        source,
                        status,
                        intent.getStringExtra("title"),
                        intent.getStringExtra("ep"),
                    )
                }
                runOnUiThread { status.text = "Done: videos" }
                return@launch
            }

            val coverage = intent.getStringExtra("coverage")
            if (coverage != null) {
                val titles = coverage.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                val rows = mutableListOf(
                    row("source", "lang", "found", "of", *titles.toTypedArray()),
                )
                sources.forEachIndexed { index, source ->
                    runOnUiThread {
                        status.text = "Coverage ${index + 1}/${sources.size}: ${source.name}"
                    }
                    val hits = titles.map { title ->
                        step {
                            withTimeout(STEP_TIMEOUT) {
                                source.getSearchAnime(1, title, source.getFilterList()).animes.size
                            }
                        }.value ?: -1
                    }
                    val found = hits.count { it > 0 }
                    val cells = hits.map { if (it < 0) "err" else it.toString() }
                    val line = row(
                        source.name,
                        source.lang,
                        found.toString(),
                        titles.size.toString(),
                        *cells.toTypedArray(),
                    )
                    Log.i(TAG, line)
                    rows += line
                }
                File(filesDir, "source-coverage.tsv")
                    .writeText(rows.joinToString("\n", postfix = "\n"))
                Log.i(TAG, "done: coverage over ${sources.size} source(s)")
                runOnUiThread { status.text = "Done: coverage" }
                return@launch
            }

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

    /**
     * The domain [Episode] the interactor wants, from the [SEpisode] a source hands back.
     *
     * Only the url and name survive, which is all the interactor reads; nothing here is
     * written to the database.
     */
    private fun SEpisode.toEpisode() = Episode.create().copy(url = url, name = name)

    /** Walks one source to its first video and logs every field the player depends on. */
    private suspend fun describeVideos(
        source: AnimeCatalogueSource,
        status: TextView,
        title: String?,
        episodeName: String?,
    ) {
        runOnUiThread { status.text = "Videos: ${source.name}" }
        Log.i(TAG, "== ${source.name} (${source.id}) ==")

        val anime = step { withTimeout(STEP_TIMEOUT) { source.pickAnime(title) } }
            .also { it.failure?.let { why -> Log.i(TAG, "entry failed: $why") } }
            .value ?: return
        Log.i(TAG, "anime: ${anime.title} url=${anime.url}")

        val episode = step { withTimeout(STEP_TIMEOUT) { source.episodesOf(anime).pick(episodeName) } }
            .also { it.failure?.let { why -> Log.i(TAG, "episodes failed: $why") } }
            .value ?: return
        Log.i(TAG, "episode: ${episode.name} url=${episode.url}")

        val hosters = step { withTimeout(STEP_TIMEOUT) { source.getHosterList(episode) } }
        Log.i(TAG, "hosters: ${hosters.value?.size ?: -1} ${hosters.failure.orEmpty()}")
        hosters.value?.forEach { hoster ->
            Log.i(
                TAG,
                "  hoster name=${hoster.hosterName} url=${hoster.hosterUrl} " +
                    "lazy=${hoster.lazy} videos=${hoster.videoList?.size ?: -1}",
            )
        }

        val videos = step { withTimeout(STEP_TIMEOUT) { source.videosOf(episode) } }
        Log.i(TAG, "videos: ${videos.value?.size ?: -1} ${videos.failure.orEmpty()}")
        videos.value.orEmpty().filterIsInstance<Video>().forEachIndexed { index, video ->
            Log.i(
                TAG,
                "  [$index] title=${video.videoTitle} res=${video.resolution} " +
                    "initialized=${video.initialized} subs=${video.subtitleTracks.size} " +
                    "audio=${video.audioTracks.size} mpvArgs=${video.mpvArgs}",
            )
            // The languages, not just how many: "eight subtitles" and "eight subtitles, one
            // of them Spanish" are different answers to the only question worth asking here.
            if (video.subtitleTracks.isNotEmpty()) {
                Log.i(TAG, "  [$index] sub langs: " + video.subtitleTracks.joinToString { it.lang })
            }
            if (video.audioTracks.isNotEmpty()) {
                Log.i(TAG, "  [$index] audio langs: " + video.audioTracks.joinToString { it.lang })
            }
            Log.i(TAG, "  [$index] url=${video.videoUrl}")
            video.headers?.forEach { (name, value) -> Log.i(TAG, "  [$index] header $name: $value") }

            val resolved = step { withTimeout(STEP_TIMEOUT) { (source as AnimeHttpSource).resolveVideo(video) } }
            Log.i(
                TAG,
                "  [$index] resolveVideo -> ${resolved.value?.videoUrl ?: resolved.failure ?: "null"}",
            )
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

    /**
     * The entry to probe: whatever [title] finds, or the first of the popular list.
     *
     * Probing the first popular entry answers "does this source work at all"; it does not
     * answer "does it work for the show I want", and the two come apart often enough that
     * assuming one from the other has already been wrong once.
     */
    private suspend fun AnimeCatalogueSource.pickAnime(title: String?): SAnime? =
        if (title.isNullOrBlank()) {
            getPopularAnime(1).animes.firstOrNull()
        } else {
            // The source's own filters, exactly as the app passes them. An empty list is not
            // the same input, and measuring with it measured something the app never does.
            val hits = getSearchAnime(1, title, getFilterList()).animes
            Log.i(TAG, "search '$title': " + hits.take(10).joinToString(" | ") { it.title })
            // Exact first: searching "One Piece" matches a dozen spin-offs before the show
            // itself, and probing the wrong entry answers the wrong question.
            hits.firstOrNull { it.title.equals(title, ignoreCase = true) }
                ?: hits.firstOrNull { it.title.contains(title, ignoreCase = true) }
        }

    /** The episode whose name contains [name], or the first one the source lists. */
    private fun List<SEpisode>.pick(name: String?): SEpisode? =
        if (name.isNullOrBlank()) {
            firstOrNull()
        } else {
            firstOrNull { it.name.contains(name, ignoreCase = true) }
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
