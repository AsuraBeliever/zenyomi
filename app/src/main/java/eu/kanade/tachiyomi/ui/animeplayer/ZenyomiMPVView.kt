package eu.kanade.tachiyomi.ui.animeplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import `is`.xyz.mpv.MPVLib
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import eu.kanade.tachiyomi.animesource.model.Track as SourceTrack

/**
 * Surface that hosts an mpv instance.
 *
 * Written against the MPVLib API rather than ported from Aniyomi's AniyomiMPVView, which
 * carries its gesture handling, picture-in-picture and track selection along with it.
 * This is the core only: create mpv, hand it a surface, play a file.
 */
class ZenyomiMPVView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs),
    SurfaceHolder.Callback {

    private var initialised = false

    /**
     * mpv's Android video output asserts on a window id, so loading a file before the
     * surface exists aborts the process:
     *   android_common.c: assertion "vo->opts->WinID != 0 && vo->opts->WinID != -1" failed
     * The file is held here until surfaceCreated attaches the surface.
     */
    private var pendingFile: String? = null
    private var surfaceReady = false
    private var pendingResumeAt = 0

    /**
     * Subtitles and audio that live in their own files rather than in the container.
     *
     * They cannot be handed over with the file: mpv rejects `sub-add` until something is
     * loaded, so they are kept here and added when mpv reports the file open.
     */
    private var externalSubtitles: List<SourceTrack> = emptyList()
    private var externalAudio: List<SourceTrack> = emptyList()

    /**
     * Offered by the source, listed in the picker, and not handed to mpv until someone picks
     * them.
     *
     * `sub-add` and `audio-add` block mpv's core for as long as the download takes, and every
     * other command — pause, seek, choosing a track — waits behind them. A source offering
     * sixteen subtitle languages therefore cost about a minute during which the player's own
     * buttons did nothing, and then did everything that had been pressed at once.
     *
     * Only the language the viewer actually wants is loaded when the episode opens. The rest
     * exist as [Track]s with a negative id, which the picker draws like any other and
     * [selectSubtitle] resolves back to a download.
     */
    private var pendingSubtitles: List<SourceTrack> = emptyList()
    private var pendingAudio: List<SourceTrack> = emptyList()

    /**
     * Whether the viewer has overridden the language preference for this file.
     *
     * The preference says what to start with, not what to keep going back to.
     */
    private var viewerChoseSubtitle = false
    private var viewerChoseAudio = false

    /** Whether the source offered subtitles, for the "pick one rather than none" rule. */
    private var addedSubtitles = false

    /**
     * Told when the file was unloaded to give the surface back, with the second it was on.
     *
     * Only when the player is coming back — backgrounded, screen off, anything that takes the
     * surface away without closing the player.
     */
    var onNeedsReload: ((resumeAt: Int) -> Unit)? = null

    /**
     * Whether the player is closing for good, as opposed to going to the background.
     *
     * The surface goes away in both cases and what has to happen to mpv is not the same:
     * closing unloads the file, backgrounding only pauses it so that coming back resumes where
     * it was. Set by [AnimePlayerActivity], which is the only place that knows the difference.
     */
    var finishing = false

    /** The viewer's language preferences, as codes, in order. */
    private var preferredAudio: List<String> = emptyList()
    private var preferredSubtitles: List<String> = emptyList()

    /**
     * Called when mpv gives up on a file, with whatever it said about why.
     *
     * A dead mirror is the single most common way an episode fails, and `keep-open` means mpv
     * simply sits on a black frame when it happens. Without this the screen is identical to a
     * player that is still loading, forever.
     */
    var onPlaybackError: ((String?) -> Unit)? = null

    /**
     * Held so it can be taken off again.
     *
     * MPVLib keeps its log observers in a list of its own that a destroy does not touch, so a
     * second player added a second one and every line of mpv's log appeared twice — three
     * times for the third, and so on for as long as the app was running.
     */
    private var logObserver: MPVLib.LogObserver? = null

    /** The last "HTTP error" mpv logged, which is the useful half of a failure. */
    private var lastHttpError: String? = null

    /**
     * Called with whether mpv currently has nothing to show.
     *
     * True while a file is opening, while a seek is resolving, and while the cache is
     * refilling. Without it the player is a black rectangle for as long as the network takes —
     * eight seconds from a standing start and longer when resuming mid-episode — and a black
     * rectangle is what a broken player looks like too.
     */
    var onLoadingChanged: ((Boolean) -> Unit)? = null

    /**
     * Called when mpv stalls *after* the episode has started: a seek, or a cache that ran dry.
     *
     * Kept apart from [onLoadingChanged] because the two deserve different treatment. Opening
     * an episode blocks the screen and offers a way out; a stall two minutes in is a spinner
     * over a picture that is still there, and blocking the controls for it would mean a drag
     * on the seek bar locks the player every time.
     */
    var onBufferingChanged: ((Boolean) -> Unit)? = null

    /** mpv's position, duration and paused state, pushed rather than polled. */
    var onPositionChanged: ((Int) -> Unit)? = null
    var onDurationChanged: ((Int) -> Unit)? = null
    var onPausedChanged: ((Boolean) -> Unit)? = null

    /**
     * Whether mpv has run out of file.
     *
     * The only honest "this episode is over": with `keep-open=always` mpv stops on the last
     * frame without ever reporting a position equal to the duration, so anything that waits
     * for the clock to run out waits through the credits and then keeps waiting.
     */
    var onEndReachedChanged: ((Boolean) -> Unit)? = null

    /** The two reasons there is nothing to show, tracked apart because they overlap. */
    private var restarting = true
    private var bufferingForCache = false

    /**
     * Set once mpv has put a frame on the screen for this file.
     *
     * The line between "opening" and "stalled": before it, there is nothing to look at and the
     * viewer is waiting on the network; after it, the picture is there and a stall is a
     * hiccup. Cleared on every new file.
     */
    private var shownFirstFrame = false

    /**
     * Reports the combined state, on the main thread.
     *
     * mpv delivers its events on its own threads, and the only consumer of this is Compose
     * state driving the screen.
     */
    private fun updateLoading() {
        val stalled = restarting || bufferingForCache
        val opening = !shownFirstFrame
        post {
            onLoadingChanged?.invoke(opening && stalled)
            onBufferingChanged?.invoke(!opening && stalled)
        }
    }

    /** Held open for as long as mpv reads from them. */
    private val openFds = mutableListOf<ParcelFileDescriptor>()

    private val audioManager = context.getSystemService<AudioManager>()

    /** Held for as long as mpv exists; see [requestAudioFocus]. */
    private var audioFocus: AudioFocusRequest? = null

    /**
     * Whether the pause in effect is ours rather than the viewer's.
     *
     * Only then does regaining focus resume: a call that interrupts an episode should hand it
     * back where it was, and an episode the viewer paused themselves should stay paused.
     */
    private var pausedForFocusLoss = false

    /** Observers are registered against MPVLib globally, so keep ours to remove them later. */
    private val observers = mutableListOf<MPVLib.EventObserver>()

    /**
     * @param audioLanguages / [subtitleLanguages] mpv's `alang` and `slang`: comma separated
     * codes in order of preference. Empty is left unset, because mpv reads an empty list as
     * "prefer no track at all".
     * @param speedPercent playback speed, 100 being normal.
     * @param httpHeaders "Name: value" pairs for mpv's `http-header-fields`.
     */
    fun initialise(
        configDir: File,
        audioLanguages: String = "",
        subtitleLanguages: String = "",
        speedPercent: Int = 100,
        httpHeaders: List<String> = emptyList(),
        subtitleStyle: SubtitleStyle = SubtitleStyle(),
    ) {
        if (initialised) return
        initialised = true
        preferredAudio = TrackLanguage.ofList(audioLanguages).split(',').filter { it.isNotBlank() }
        preferredSubtitles = TrackLanguage.ofList(subtitleLanguages).split(',').filter { it.isNotBlank() }
        configDir.mkdirs()
        copyAssets(configDir)
        val fontsDir = prepareFonts(configDir)
        holder.addCallback(this)
        requestAudioFocus()

        // All of libmpv is driven from one thread; see runOnMpvThread.
        postToMpv {
            MPVLib.create(context, LOG_LEVEL)
            // Registered before anything else: without it a rejected option or a failed
            // loadfile is silent, and mpv only ever complains on its own log.
            //
            // Verbose in a debug build only. At `v` mpv narrates every hls segment it opens,
            // which on a stream that rotates its host is four lines per segment, each one
            // crossing jni to be formatted and written to logcat while the episode plays.
            // Diagnosing is worth that; a release build on someone's phone is not.
            MPVLib.setOptionString("msg-level", "all=$LOG_LEVEL")
            val observer = MPVLib.LogObserver { prefix, _, text ->
                if (text.contains("HTTP error")) lastHttpError = text.trim()
                logcat(LogPriority.DEBUG) { "mpv [$prefix] $text".trim() }
            }
            logObserver = observer
            MPVLib.addLogObserver(observer)
            MPVLib.setOptionString("config", "yes")
            MPVLib.setOptionString("config-dir", configDir.path)
            // Hardware decoding where the device offers it, falling back to software.
            MPVLib.setOptionString("hwdec", "auto-safe")
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.setOptionString("keep-open", "always")
            MPVLib.setOptionString("ao", "audiotrack")
            // mpv picks its subtitle font up from the config dir by name. Without it libass
            // reports "failed to find any fallback with glyph" and draws nothing at all, so a
            // correctly selected subtitle track was still an episode with no subtitles.
            MPVLib.setOptionString("sub-font-provider", "none")
            // With the provider off, this folder is the whole world of fonts libass has. It
            // is what makes the font setting mean anything: before it there was one face in
            // the config dir and every choice rendered identically.
            setOptionChecked("sub-fonts-dir", fontsDir.path)
            // mpv sizes and places subtitles against the *window* by default, which is right
            // on a desktop where the window is the video. Here the window is a whole portrait
            // phone screen and the video a band across the middle of it, so the defaults drew
            // text tall enough to run off the bottom edge and across the seek bar. Both are
            // tied to the video rectangle instead.
            MPVLib.setOptionString("sub-scale-by-window", "no")
            MPVLib.setOptionString("sub-use-margins", "no")
            MPVLib.setOptionString("sub-ass-force-margins", "no")
            // How the viewer wants them to look. Set here as well as live so the very first
            // subtitle of an episode is already styled — applying them only afterwards makes
            // the text visibly change size a second into every episode.
            subtitleStyle.toMpvOptions().forEach { (name, value) ->
                setOptionChecked(name, value)
            }
            // Android ships no CA bundle mpv can read, so without this its TLS is unverified.
            MPVLib.setOptionString("tls-verify", "yes")
            MPVLib.setOptionString("tls-ca-file", File(configDir, CA_BUNDLE).path)
            // There is no youtube-dl here for its hook to call, and it runs on every file.
            MPVLib.setOptionString("ytdl", "no")
            // mpv's desktop defaults buffer far more than a phone should hold.
            MPVLib.setOptionString("demuxer-max-bytes", DEMUXER_CACHE_BYTES.toString())
            MPVLib.setOptionString("demuxer-max-back-bytes", DEMUXER_CACHE_BYTES.toString())
            // Every one of these is about one thing: an episode that plays without sound.
            //
            // A source like KickAssAnime serves a video-only hls manifest and hands the audio
            // over as a separate stream, and it spreads the segments of both across a rotating
            // set of hosts. mpv's defaults do not survive that. It reads one second ahead, so
            // it starts playing the moment the first packet lands and runs dry immediately
            // after ("Audio device underrun detected", then "restarting audio after underrun",
            // then "Audio/Video desynchronisation detected" — over and over). Video hides it,
            // because a dropped frame is invisible and a gap in the audio is silence.
            //
            // So: hold a real read-ahead, and wait for it before starting rather than starting
            // into an empty buffer.
            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("cache-secs", READAHEAD_SECONDS.toString())
            MPVLib.setOptionString("demuxer-readahead-secs", READAHEAD_SECONDS.toString())
            MPVLib.setOptionString("cache-pause-initial", "yes")
            MPVLib.setOptionString("cache-pause-wait", CACHE_WAIT_SECONDS.toString())
            // The AudioTrack's own buffer. mpv's default 0.2s is a fifth of a second of
            // scheduling headroom, which a phone juggling a decode and two http streams does
            // not always have.
            MPVLib.setOptionString("audio-buffer", AUDIO_BUFFER_SECONDS.toString())
            // ffmpeg's hls demuxer keeps the connection alive between segments, which cannot
            // work when consecutive segments come from different hosts: it spent a failed
            // request on every single segment ("keepalive request failed ... retrying with new
            // connection") before opening the one that worked. Told not to try, it opens each
            // segment once.
            MPVLib.setOptionString("demuxer-lavf-o", "http_persistent=0")
            // These have to be options rather than properties: mpv applies them while opening
            // a file, so setting them after init does nothing until the *next* file.
            // Through the same normaliser as the tracks, so "es" in the setting and
            // "Español (Spain)" on the track meet in the middle at "spa". mpv applies these
            // to what the file itself carries; tracks added afterwards are ours to choose.
            preferredAudio.takeIf { it.isNotEmpty() }
                ?.let { MPVLib.setOptionString("alang", it.joinToString(",")) }
            preferredSubtitles.takeIf { it.isNotEmpty() }
                ?.let { MPVLib.setOptionString("slang", it.joinToString(",")) }
            MPVLib.setOptionString("speed", (speedPercent.coerceIn(25, 400) / 100.0).toString())
            // Video hosts that check the Referer answer mpv's bare request with 403, so the
            // headers the source resolved the video with have to travel with it.
            if (httpHeaders.isNotEmpty()) {
                MPVLib.setOptionString("http-header-fields", httpHeaders.toMpvList())
            }
            MPVLib.init()

            MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
            // Set while mpv has stopped to refill, which is the difference between "stalled"
            // and "paused" and the only one of the two worth showing a spinner for.
            MPVLib.observeProperty("paused-for-cache", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
            // The end of the episode, which the clock cannot be asked for: `keep-open` leaves
            // mpv sitting on the last frame with time-pos a second short of the duration, so
            // waiting for the two to meet waits forever.
            MPVLib.observeProperty("eof-reached", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
            // How the language preferences get applied to tracks that arrive late; see
            // applyTrackPreferences.
            MPVLib.observeProperty("track-list/count", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        }

        addObserver(
            PlaybackObserver(
                onFileLoaded = {
                    lastHttpError = null
                    shownFirstFrame = false
                    viewerChoseSubtitle = false
                    viewerChoseAudio = false
                    addExternalTracks()
                    // The file's own tracks are here now even if the external ones are not.
                    applyTrackPreferences()
                },
                onTrackListChanged = { applyTrackPreferences() },
                onFailure = { reason ->
                    restarting = false
                    bufferingForCache = false
                    updateLoading()
                    onPlaybackError?.invoke(lastHttpError ?: reason)
                },
                onRestarting = { value ->
                    restarting = value
                    if (!value) shownFirstFrame = true
                    updateLoading()
                },
                onBuffering = { value ->
                    bufferingForCache = value
                    updateLoading()
                },
                onPosition = { value -> post { onPositionChanged?.invoke(value) } },
                onDuration = { value -> post { onDurationChanged?.invoke(value) } },
                onPaused = { value -> post { onPausedChanged?.invoke(value) } },
                onEndReached = { value -> post { onEndReachedChanged?.invoke(value) } },
            ),
        )

        // AndroidView hands over a SurfaceView whose surface may already exist, and
        // surfaceCreated only fires for surfaces created after the callback is added.
        // Without this the pending file waits forever and the screen stays black.
        if (holder.surface?.isValid == true) {
            attach(holder)
        }
    }

    /**
     * Puts mpv's own data files where it looks for them.
     *
     * Both ship inside the mpv library's aar and end up in the app's assets, but mpv reads
     * them from its config directory as plain files: it has no idea Android assets exist.
     */
    private fun copyAssets(configDir: File) {
        listOf(SUBTITLE_FONT, CA_BUNDLE).forEach { name ->
            val target = File(configDir, name)
            if (target.exists() && target.length() > 0) return@forEach
            runCatching {
                context.assets.open(name).use { source ->
                    target.outputStream().use { source.copyTo(it) }
                }
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Could not unpack $name for mpv" }
            }
        }
    }

    /**
     * Fills the folder libass reads faces from, and answers with it.
     *
     * The aar ships exactly one font — Droid Sans Fallback — which is why the subtitle font
     * setting had nothing to choose between: whatever it was set to, there was only ever one
     * face to fall back on. Android carries a couple of hundred more in `/system/fonts`, but
     * pointing libass at all of them means indexing two hundred files before the first
     * subtitle draws, most of them scripts nobody watching this is reading. So a handful are
     * copied in instead, each one a family a person might actually pick.
     *
     * The bundled fallback stays, and stays the default, because it is the only one of them
     * that covers CJK: choose Roboto for an English track and a Japanese sign in the same file
     * still has something to render with.
     */
    private fun prepareFonts(configDir: File): File {
        val fontsDir = File(configDir, "fonts")
        fontsDir.mkdirs()
        val bundled = File(fontsDir, SUBTITLE_FONT)
        if (!bundled.exists() || bundled.length() == 0L) {
            runCatching {
                context.assets.open(SUBTITLE_FONT).use { source ->
                    bundled.outputStream().use { source.copyTo(it) }
                }
            }.onFailure { logcat(LogPriority.WARN, it) { "Could not unpack the fallback font" } }
        }
        SYSTEM_FONT_FILES.forEach { name ->
            val target = File(fontsDir, name)
            if (target.exists() && target.length() > 0) return@forEach
            val source = File(SYSTEM_FONTS_DIR, name)
            // Missing is normal, not a failure: which faces a build of Android ships is its
            // own business, and the picker only offers what made it across.
            if (!source.isFile) return@forEach
            runCatching { source.copyTo(target, overwrite = true) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not copy $name" } }
        }
        logcat(LogPriority.INFO) {
            "subtitle fonts: ${fontsDir.list()?.joinToString().orEmpty()}"
        }
        return fontsDir
    }

    fun addObserver(observer: MPVLib.EventObserver) {
        observers += observer
        MPVLib.addObserver(observer)
    }

    /**
     * @param mpvArgs options the source asked for, applied to this file only. They are set
     * rather than passed to loadfile because a source is free to name any mpv option, and a
     * single rejected one in the loadfile argument makes mpv refuse the whole command.
     */
    fun playFile(
        uri: String,
        resumeAt: Int = 0,
        subtitleTracks: List<SourceTrack> = emptyList(),
        audioTracks: List<SourceTrack> = emptyList(),
        mpvArgs: List<Pair<String, String>> = emptyList(),
        httpHeaders: List<String> = emptyList(),
    ) {
        pendingResumeAt = resumeAt
        externalSubtitles = subtitleTracks
        externalAudio = audioTracks
        // Everything below belongs to the file that was playing, and the player now opens a
        // second one without being torn down first — the next episode. Left alone, the new
        // episode inherited the old one's deferred track lists in its picker, and its
        // "the source offered subtitles" flag, which is what decides whether a file with no
        // preferred language gets a subtitle forced on anyway.
        pendingSubtitles = emptyList()
        pendingAudio = emptyList()
        addedSubtitles = false
        viewerChoseSubtitle = false
        viewerChoseAudio = false
        lastHttpError = null
        // As a property rather than an option, because by now mpv is initialised: the option
        // form is read at startup and the second episode would have gone out with the first
        // one's Referer. Cleared explicitly when the new file needs no headers, or the same
        // staleness happens the other way round.
        postToMpv {
            MPVLib.setPropertyString("http-header-fields", httpHeaders.toMpvList())
        }
        if (mpvArgs.isNotEmpty()) {
            postToMpv {
                mpvArgs.forEach { (name, value) ->
                    val code = MPVLib.setOptionString(name, value)
                    if (code < 0) logcat(LogPriority.WARN) { "mpv refused source option $name=$value" }
                }
            }
        }
        if (surfaceReady) {
            load(uri, resumeAt)
        } else {
            pendingFile = uri
        }
    }

    /**
     * Adds the side-car tracks, once mpv has a file open to attach them to.
     *
     * Added with `auto`, which in mpv means "do not select this one" — the flag reads like it
     * defers to `slang`, and it does not; it simply leaves every track off. So the choice is
     * made here: the viewer's preferred language when a track speaks it, and otherwise
     * whatever mpv already settled on, except for subtitles, where nothing selected means a
     * stream of Japanese audio with no text on screen.
     */
    private fun addExternalTracks() {
        val subtitles = externalSubtitles
        val audio = externalAudio
        if (subtitles.isEmpty() && audio.isEmpty()) return
        externalSubtitles = emptyList()
        externalAudio = emptyList()
        // Remembered so the selection below knows whether a subtitle was offered at all,
        // long after these lists have been cleared.
        addedSubtitles = subtitles.isNotEmpty()

        val wantedSubtitles = subtitles.preferredFirst(preferredSubtitles)
        val wantedAudio = audio.preferredFirst(preferredAudio)
        pendingSubtitles = wantedSubtitles.drop(1)
        pendingAudio = wantedAudio.drop(1)

        postToMpv {
            // Audio before subtitles, and one of each: both are downloaded synchronously
            // against mpv's core, so anything queued here is time the viewer spends with a
            // player that will not answer its own buttons.
            //
            // `select` rather than `auto`: mpv chooses the track the moment it registers it.
            // Choosing afterwards meant a round trip through the track-list observer, which
            // joins the back of this same queue — and racing `audio-add`, which returns
            // before the track exists.
            wantedAudio.firstOrNull()?.let { track ->
                MPVLib.command(
                    arrayOf("audio-add", track.url, "select", track.lang, TrackLanguage.of(track.lang)),
                )
            }
            // The source's label stays as the track title, because that is what the picker
            // shows and "Español (Spain)" tells a person more than "spa" does. The language
            // field gets the code, because that is what mpv matches the preference against.
            wantedSubtitles.firstOrNull()?.let { track ->
                MPVLib.command(
                    arrayOf("sub-add", track.url, "select", track.lang, TrackLanguage.of(track.lang)),
                )
            }
        }
    }

    /** Hands one deferred track to mpv and selects it. */
    private fun loadPending(track: SourceTrack, command: String) = postToMpv {
        MPVLib.command(arrayOf(command, track.url, "select", track.lang, TrackLanguage.of(track.lang)))
    }

    /**
     * The viewer's languages first, in the order they prefer them, then everything else.
     *
     * Decides which track gets mpv's `select` flag, and so which one is playing when the
     * episode starts. Stable within each group, so a source's own ordering survives for the
     * languages the preference says nothing about.
     */
    private fun List<SourceTrack>.preferredFirst(preferred: List<String>): List<SourceTrack> {
        if (preferred.isEmpty()) return this
        return sortedBy { track ->
            val rank = preferred.indexOf(TrackLanguage.of(track.lang))
            if (rank < 0) preferred.size else rank
        }
    }

    /**
     * Picks the tracks the viewer asked for, every time mpv's track list changes.
     *
     * This used to run once, immediately after issuing `audio-add`, and read the track list
     * straight back. That is a race: `audio-add` on a network url returns long before mpv has
     * opened the stream and registered the track, so the list came back without it and nothing
     * was selected. On the emulator the stream happened to open in time and the audio played;
     * on a real phone it did not, and the episode ran with a track present but unselected —
     * video, subtitles, and silence, with nothing in the log that looked like a failure.
     *
     * So the trigger is mpv itself. Every selection here is idempotent: [preferring] returns
     * null for a track that is already chosen, so re-running on each change settles rather than
     * fighting the viewer.
     */
    private fun applyTrackPreferences() = postToMpv {
        selectPreferredAudio()
        selectPreferredSubtitle()
    }

    /**
     * Chooses the audio track. Must run on the mpv thread.
     *
     * Split out so it can be called straight after `audio-add` rather than only from the
     * track-list observer. The observer's call goes through [postToMpv], which is a queue
     * behind every pending `sub-add` — so on a source with sixteen subtitle languages the
     * track existed within three seconds and stayed unselected for another twenty-three,
     * which sounds exactly like audio that loads after the video.
     */
    private fun selectPreferredAudio() {
        if (viewerChoseAudio) return
        // Asked of the track list rather than of `aid`: that property is a choice — it
        // holds "no" and "auto" as well as a number — so reading it as an integer always
        // fails, and the failure is indistinguishable from "nothing is selected".
        val audioTracks = loadedTracks().filter { it.isAudio }
        // Nothing selected at all is the case this exists for: an external audio track that
        // arrived after the file opened leaves mpv with no audio chosen.
        val wantedAudio = audioTracks.preferring(preferredAudio)
            ?: audioTracks.firstOrNull()?.takeIf { audioTracks.none { track -> track.selected } }
        wantedAudio?.let { applyAudio(it.id) }
    }

    /** Chooses the subtitle track. Must run on the mpv thread. */
    private fun selectPreferredSubtitle() {
        if (viewerChoseSubtitle) return
        val subtitleTracks = loadedTracks().filter { it.isSubtitle }
        val wantedSubtitle = subtitleTracks.preferring(preferredSubtitles)
        when {
            wantedSubtitle != null -> applySubtitle(wantedSubtitle.id)
            addedSubtitles && subtitleTracks.isNotEmpty() && subtitleTracks.none { it.selected } ->
                subtitleTracks.firstOrNull()?.let { applySubtitle(it.id) }
        }
    }

    /**
     * mpv takes the start position as a loadfile option, which avoids a visible seek.
     *
     * The command is `loadfile <url> [<flags> [<index> [<options>]]]`, so the options belong in
     * the *fourth* argument. Passing them third made mpv try to read "start=71" as the integer
     * index and refuse the whole command — the file simply never loaded, so every episode with
     * a saved position opened to a black screen. INSERT_AT_END is the documented default index.
     */
    private fun load(uri: String, resumeAt: Int) {
        val target = resolve(uri) ?: return
        postToMpv {
            if (resumeAt > 0) {
                MPVLib.command(arrayOf("loadfile", target, "replace", INSERT_AT_END, "start=$resumeAt"))
            } else {
                MPVLib.command(arrayOf("loadfile", target))
            }
            // `keep-open` leaves mpv paused on the last frame when an episode ends, and pause
            // is a property of the player, not of the file: the episode opened from there —
            // which is every episode the player moves on to by itself — arrived already
            // stopped on its first frame.
            pausedForFocusLoss = false
            MPVLib.setPropertyBoolean("pause", false)
        }
    }

    /**
     * mpv opens paths and network urls, but not Android's content:// scheme, which is
     * what the storage framework hands back for a file the user granted access to. Those
     * are resolved to a file descriptor and passed as fd://, the form mpv understands.
     */
    private fun resolve(uri: String): String? {
        if (!uri.startsWith("content://")) return uri
        return runCatching {
            val parcelFd = context.contentResolver.openFileDescriptor(uri.toUri(), "r")
                ?: return@runCatching null
            openFds += parcelFd
            "fd://${parcelFd.fd}"
        }.getOrNull()
    }

    /**
     * Asks Android for the audio output, which is not a formality: it is why episodes played
     * silently.
     *
     * Android 16 mutes media from an app it does not consider to be the one playing, and
     * holding audio focus is how an app says that it is. Without it the platform muted us
     * outright — `AudioHardening background playback muted for app.zenyomi.dev, level:
     * partial, usage: USAGE_MEDIA` in `dumpsys audio`, with this app absent from the focus
     * stack entirely. mpv opened its AudioTrack, wrote to it and reported healthy playback the
     * whole time, which is why the player looked right and sounded like nothing.
     *
     * Asking also buys the behaviour a video player should have anyway: an episode pauses for
     * a phone call and resumes after it, instead of playing on underneath it.
     */
    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::onAudioFocusChange)
            .build()
        audioFocus = request
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            logcat(LogPriority.WARN) { "Audio focus refused; playback may be muted" }
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        audioFocus?.let { manager.abandonAudioFocusRequest(it) }
        audioFocus = null
    }

    /**
     * Pauses while something else owns the output, and resumes when it is handed back.
     *
     * Ducking is treated as a loss rather than turned into a quieter episode: half-hearing
     * dialogue under a notification is worse than the episode waiting a moment.
     */
    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedForFocusLoss) {
                    pausedForFocusLoss = false
                    setPaused(false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                pausedForFocusLoss = true
                setPaused(true)
            }
            // A permanent loss is someone else taking over for good, so nothing is remembered
            // to resume: the viewer comes back and presses play.
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedForFocusLoss = false
                setPaused(true)
            }
        }
    }

    /**
     * Everything a tap can reach queues onto the mpv thread instead of calling libmpv here.
     *
     * libmpv's property reads, property writes and commands are all synchronous against mpv's
     * core, so one made while the core is busy blocks the caller — and the core is busy for as
     * long as a network stream takes to answer, which can be forever when a host stalls. These
     * are invoked from gesture and button handlers, which run on the main thread, and a main
     * thread blocked past five seconds is an ANR: a tap on pause became "Zenyomi isn't
     * responding". Nothing here needs an answer, so nothing here waits for one.
     */
    fun togglePause() {
        // The viewer has taken over the decision, so a later focus gain must not undo it.
        pausedForFocusLoss = false
        postToMpv {
            val paused = MPVLib.getPropertyBoolean("pause") ?: return@postToMpv
            MPVLib.setPropertyBoolean("pause", !paused)
        }
    }

    private fun setPaused(paused: Boolean) = postToMpv {
        MPVLib.setPropertyBoolean("pause", paused)
    }

    /**
     * @param exact decode to the requested second instead of stopping at the keyframe before
     * it. mpv's plain absolute seek lands on the nearest keyframe *behind* the target, which
     * on a file with a long gap between them is tens of seconds early — the skip button aimed
     * at the end of an opening and landed back inside it. It costs the decode between the two,
     * so it is for the jumps that name a destination, not for dragging the bar.
     */
    fun seekTo(seconds: Int, exact: Boolean = false) = postToMpv {
        val flags = if (exact) "absolute+exact" else "absolute"
        MPVLib.command(arrayOf("seek", seconds.toString(), flags))
    }

    /** Relative, so a double tap never has to read time-pos to know where it started. */
    fun seekBy(seconds: Int) = postToMpv {
        MPVLib.command(arrayOf("seek", seconds.toString(), "relative"))
    }

    val isReady: Boolean get() = initialised

    val timePos: Int? get() = MPVLib.getPropertyInt("time-pos")
    val duration: Int? get() = MPVLib.getPropertyInt("duration")
    val paused: Boolean? get() = MPVLib.getPropertyBoolean("pause")

    /**
     * Width over height of the video actually being decoded, or null before it is known.
     * Picture-in-picture needs this to size its window; guessing 16:9 would letterbox
     * anything that is not.
     *
     * A blocking read, like [timePos] and the rest: call it from the polling loop, never from
     * a button.
     */
    val videoAspect: Float?
        get() {
            val width = MPVLib.getPropertyInt("width") ?: return null
            val height = MPVLib.getPropertyInt("height") ?: return null
            if (width <= 0 || height <= 0) return null
            return width.toFloat() / height
        }

    /**
     * What the picker shows: the tracks mpv has, plus the ones the source offered and nobody
     * has asked for yet.
     *
     * The deferred ones carry a negative id, which is not an id mpv can ever issue, so
     * [selectSubtitle] and [selectAudio] can tell them apart and download them on the spot.
     */
    fun tracks(): List<Track> = loadedTracks() +
        pendingAudio.mapIndexed { index, track -> track.placeholder(-(index + 1), "audio") } +
        pendingSubtitles.mapIndexed { index, track -> track.placeholder(-(index + 1), "sub") }

    /**
     * The chapters the file itself declares, in order.
     *
     * Worth asking for because of what the good ones are called: a release with a chapter
     * named "Opening" has said exactly where the opening ends, which is more than any guess at
     * a duration can do. Files without chapters — most streams — answer with nothing, and the
     * skip button falls back to jumping a fixed length.
     *
     * A blocking read like [tracks]: from the polling loop, not from a button.
     */
    fun chapters(): List<Chapter> {
        val count = MPVLib.getPropertyInt("chapter-list/count") ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            // As a string and parsed here: the property is a double, and MPVLib's integer
            // getter on a double property fails rather than rounding.
            val start = MPVLib.getPropertyString("chapter-list/$index/time")?.toDoubleOrNull()
                ?: return@mapNotNull null
            Chapter(
                title = MPVLib.getPropertyString("chapter-list/$index/title"),
                start = start.toInt(),
            )
        }
    }

    private fun SourceTrack.placeholder(id: Int, type: String) = Track(
        id = id,
        type = type,
        lang = TrackLanguage.of(lang),
        title = lang,
        selected = false,
    )

    /**
     * Only what mpv actually holds. Every decision this class makes is about these.
     *
     * mpv exposes track-list as a node, which MPVLib cannot hand over directly, so the
     * entries are read one property at a time through the track-list/N/... paths.
     */
    private fun loadedTracks(): List<Track> {
        val count = MPVLib.getPropertyInt("track-list/count") ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val type = MPVLib.getPropertyString("track-list/$index/type") ?: return@mapNotNull null
            val id = MPVLib.getPropertyInt("track-list/$index/id") ?: return@mapNotNull null
            Track(
                id = id,
                type = type,
                lang = MPVLib.getPropertyString("track-list/$index/lang"),
                title = MPVLib.getPropertyString("track-list/$index/title"),
                selected = MPVLib.getPropertyBoolean("track-list/$index/selected") ?: false,
            )
        }
    }

    /**
     * The first track speaking the earliest language on [preferred], or null.
     *
     * Null also when that track is already selected: re-selecting an audio track mid-file
     * makes mpv reopen and resync it, which is a visible hiccup for no gain.
     */
    private fun List<Track>.preferring(preferred: List<String>): Track? {
        preferred.forEach { language ->
            val match = firstOrNull { TrackLanguage.of(it.lang.orEmpty()) == language }
            if (match != null) return match.takeUnless { it.selected }
        }
        return null
    }

    /**
     * The viewer picking a track from the player's own picker.
     *
     * Records that the choice was theirs, which stops [applyTrackPreferences] from putting the
     * preferred language back. It runs on every change to the track list, and loading the
     * track they just asked for is itself such a change — so choosing English on a source that
     * offers it alongside the preferred Spanish used to flip back to Spanish a second later.
     */
    fun selectAudio(trackId: Int?) {
        viewerChoseAudio = true
        val deferred = pendingAudio.getOrNull(trackId.pendingIndex())
        if (deferred != null) {
            pendingAudio = pendingAudio - deferred
            return loadPending(deferred, "audio-add")
        }
        applyAudio(trackId)
    }

    fun selectSubtitle(trackId: Int?) {
        viewerChoseSubtitle = true
        val deferred = pendingSubtitles.getOrNull(trackId.pendingIndex())
        if (deferred != null) {
            pendingSubtitles = pendingSubtitles - deferred
            return loadPending(deferred, "sub-add")
        }
        applySubtitle(trackId)
    }

    /**
     * Restyles the subtitles of the episode already playing.
     *
     * Properties rather than options, because options are read while a file is being opened
     * and changing one afterwards does nothing until the next episode. Off the main thread
     * like every other call into mpv: each of these waits on mpv's event loop, and eighteen of
     * them on the thread that draws the screen is a dropped frame every time a slider moves.
     */
    fun applySubtitleStyle(style: SubtitleStyle) = postToMpv {
        // MPVLib's property setter reports nothing, so a name mpv does not know is silent
        // here. The option pass at startup does return a code, and it covers the same names —
        // so if one of these is wrong, that is where it says so.
        style.toMpvOptions().forEach { (name, value) -> MPVLib.setPropertyString(name, value) }
    }

    /**
     * Sets an option and says so when mpv will not have it.
     *
     * mpv reports a rejected option in a return code and nowhere else — no log line, no
     * exception — so an option this build of libmpv does not know is indistinguishable from
     * one that worked. That is a whole class of setting that silently does nothing, which is
     * worse than one that visibly fails.
     */
    private fun setOptionChecked(name: String, value: String) {
        val code = MPVLib.setOptionString(name, value)
        if (code < 0) logcat(LogPriority.WARN) { "mpv refused option $name=$value ($code)" }
    }

    private fun applyAudio(trackId: Int?) = postToMpv {
        MPVLib.setPropertyString("aid", trackId?.toString() ?: "no")
    }

    private fun applySubtitle(trackId: Int?) = postToMpv {
        MPVLib.setPropertyString("sid", trackId?.toString() ?: "no")
    }

    /** Where a negative track id points in the deferred list, or -1 for a real one. */
    private fun Int?.pendingIndex(): Int = if (this != null && this < 0) -this - 1 else -1

    data class Track(
        val id: Int,
        val type: String,
        val lang: String?,
        val title: String?,
        val selected: Boolean,
    ) {
        val isAudio: Boolean get() = type == "audio"
        val isSubtitle: Boolean get() = type == "sub"

        /** What to show in a picker: the track's own title, else its language, else its id. */
        val label: String get() = title ?: lang ?: "#$id"
    }

    /**
     * Tears mpv down without letting it hang the caller.
     *
     * Every libmpv call queues behind mpv's own core thread, so tearing the player down while
     * it is still opening a file blocks for as long as that takes. Doing that from the main
     * thread — which is where a screen is disposed — is what made backing out of a
     * just-opened episode freeze the UI until Android raised an ANR.
     *
     * The work is handed to a background thread and waited on with a cap. mpv has to finish
     * detaching before the surface goes, so this cannot simply be fired and forgotten, but a
     * bounded wait can never reach the ANR threshold.
     */
    fun release() {
        if (!initialised) return
        initialised = false
        holder.removeCallback(this)
        abandonAudioFocus()
        val fds = openFds.toList()
        openFds.clear()
        val toRemove = observers.toList()
        observers.clear()

        runOnMpvThread {
            // Also here, and not only in surfaceDestroyed: which of the two comes first is
            // Android's to decide, and a destroy that runs while a file is still being
            // decoded is the same wait as the one above.
            MPVLib.command(arrayOf("stop"))
            toRemove.forEach { MPVLib.removeObserver(it) }
            logObserver?.let { MPVLib.removeLogObserver(it) }
            logObserver = null
            MPVLib.destroy()
            fds.forEach { runCatching { it.close() } }
        }
    }

    /** Queues [block] on the mpv thread without waiting. Ordering is what matters here. */
    private fun postToMpv(block: () -> Unit) {
        if (!initialised) return
        mpvThread.execute(block)
    }

    /**
     * Runs [block] on the single mpv thread and waits up to [TEARDOWN_TIMEOUT_MS].
     *
     * One thread, so create and destroy can never race each other: libmpv is a process-wide
     * singleton and interleaving those corrupts it.
     */
    private fun runOnMpvThread(block: () -> Unit) {
        val done = mpvThread.submit(block)
        runCatching { done.get(TEARDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            .onFailure { logcat(LogPriority.WARN) { "mpv teardown did not finish in time" } }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = attach(holder)

    private fun attach(holder: SurfaceHolder) {
        if (surfaceReady) return
        surfaceReady = true
        val surface = holder.surface
        val file = pendingFile
        pendingFile = null
        postToMpv {
            MPVLib.attachSurface(surface)
            MPVLib.setOptionString("force-window", "yes")
            MPVLib.setPropertyString("vo", "gpu")
        }
        file?.let { load(it, pendingResumeAt) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        postToMpv { MPVLib.setPropertyString("android-surface-size", "${width}x$height") }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        if (!initialised) return
        // Same reasoning as release(): these are blocking libmpv calls and this runs on the
        // main thread, but mpv must stop drawing before the surface is gone, so the wait is
        // bounded rather than skipped.
        runOnMpvThread {
            // As a property, and without touching force-window. Asking for `force-window=no`
            // here is what hung the whole player: mpv reconfigures its window on that option
            // and the call never came back, so the wait below gave up, Android took the
            // surface away underneath a mpv that was still holding it, and every later call
            // on this thread — the destroy right after, and the create of the next player —
            // queued behind one that will never finish. The player was then dead for the life
            // of the process: opening another episode sat on "Loading episode…" forever.
            //
            // Dropping the video output and handing the surface back is all that is needed,
            // and it is what mpv's own Android player does here.
            // mpv has to stop drawing before the surface can be handed back, and asking it
            // to drop the video output while it is mid-frame is what hung the player: the
            // call never returned, the wait below gave up, Android took the surface away
            // underneath a mpv still holding it, and everything queued on this thread
            // afterwards — the destroy right after, and the create of the *next* player —
            // waited behind a call that would never finish. Opening another episode then sat
            // on "Loading episode…" for the life of the process.
            //
            // Unloading the file when the player is closing, and pausing when it is only
            // going to the background: the detach that took longer than a second and a half
            // now takes about thirty milliseconds either way.
            val resumeAt = MPVLib.getPropertyInt("time-pos") ?: 0
            MPVLib.command(arrayOf("stop"))
            MPVLib.setPropertyString("vo", "null")
            MPVLib.detachSurface()
            // Going to the background rather than closing: the episode has to come back, and
            // it can only be put back once the surface is here again. Asking mpv to merely
            // pause instead of unloading does not work — the detach hangs exactly as it did
            // before — so the file is reopened where it was.
            if (!finishing) post { onNeedsReload?.invoke(resumeAt) }
        }
    }

    /**
     * mpv's list syntax for `http-header-fields`: comma separated, commas inside an item
     * escaped with a backslash.
     *
     * Not the `%<length>%` prefix form mpv documents for its path lists. That form is *not*
     * parsed for this option: mpv took the prefix as part of the field name and sent a header
     * literally called `%8%Origin`, so every host that checks one answered 403 and the player
     * opened to a black screen. Verified against a local server that echoes what arrives.
     */
    private fun List<String>.toMpvList(): String =
        joinToString(",") { it.replace(",", "\\,") }

    /** One chapter of the file being played: where it starts, and what it is called. */
    data class Chapter(val title: String?, val start: Int)

    /**
     * Watches for the two moments that matter: a file opening, and mpv giving up on one.
     *
     * MPVLib's observer is one wide interface rather than a set of callbacks, so listening for
     * two of them means implementing all seven members. `efEvent` is the library's own "this
     * file ended badly" signal; the plain end-of-file event does not say whether it failed.
     */
    private class PlaybackObserver(
        private val onFileLoaded: () -> Unit,
        private val onFailure: (String?) -> Unit,
        private val onRestarting: (Boolean) -> Unit,
        private val onBuffering: (Boolean) -> Unit,
        private val onTrackListChanged: () -> Unit,
        private val onPosition: (Int) -> Unit,
        private val onDuration: (Int) -> Unit,
        private val onPaused: (Boolean) -> Unit,
        private val onEndReached: (Boolean) -> Unit,
    ) : MPVLib.EventObserver {
        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> onFileLoaded()
                // START_FILE and SEEK both mean the picture is about to be gone for a while;
                // PLAYBACK_RESTART is mpv saying it is showing frames again, and is the only
                // honest signal that the wait is over — FILE_LOADED fires well before it.
                MPVLib.mpvEventId.MPV_EVENT_START_FILE,
                MPVLib.mpvEventId.MPV_EVENT_SEEK,
                -> onRestarting(true)
                MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> onRestarting(false)
            }
        }

        override fun efEvent(error: String?) = onFailure(error)

        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "track-list/count" -> onTrackListChanged()
                "time-pos" -> onPosition(value.toInt())
                "duration" -> onDuration(value.toInt())
            }
        }
        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "paused-for-cache" -> onBuffering(value)
                "pause" -> onPaused(value)
                "eof-reached" -> onEndReached(value)
            }
        }
        override fun eventProperty(property: String, value: String) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
    }

    companion object {
        /** The fallback face that ships with the mpv library. Family: Droid Sans Fallback. */
        private const val SUBTITLE_FONT = "subfont.ttf"
        private const val CA_BUNDLE = "cacert.pem"

        private const val SYSTEM_FONTS_DIR = "/system/fonts"

        /**
         * The faces copied out of Android for subtitles to be set in.
         *
         * The bold and italic files earn their place: without a real one, libass slants and
         * thickens the regular face itself, and a synthesised italic at subtitle size is
         * noticeably worse than a drawn one.
         *
         * `DroidSans.ttf` is not here despite the name: Android ships it with Roboto's family
         * name inside, so offering it would have been the same font twice under two labels.
         */
        private val SYSTEM_FONT_FILES = listOf(
            "Roboto-Regular.ttf",
            "NotoSerif-Regular.ttf",
            "NotoSerif-Bold.ttf",
            "NotoSerif-Italic.ttf",
            "NotoSerif-BoldItalic.ttf",
            "DroidSansMono.ttf",
            "CutiveMono.ttf",
            "ComingSoon.ttf",
        )

        /**
         * The families the picker can offer.
         *
         * Asks `/system/fonts` rather than the folder [prepareFonts] fills, because the
         * picker is drawn before any of that has run: the player copies its fonts when it
         * initialises mpv, and the settings screen never initialises one at all. What is in
         * the source directory is what will be in the destination, so it answers the same
         * question a step earlier.
         */
        fun availableSubtitleFonts(): List<String> = SUBTITLE_FONT_FAMILIES
            .filter { (_, file) -> file == null || File(SYSTEM_FONTS_DIR, file).isFile }
            .keys
            .toList()

        /**
         * Family name as libass knows it, against the file it needs — null for the bundled
         * one, which is always there.
         *
         * The names are the fonts' own, read out of their `name` table, not guesses: asking
         * for a family that does not exist is not an error libass reports, it just quietly
         * draws in something else, which looks exactly like a setting that does nothing.
         */
        val SUBTITLE_FONT_FAMILIES: Map<String, String?> = linkedMapOf(
            "Droid Sans Fallback" to null,
            "Roboto" to "Roboto-Regular.ttf",
            "Noto Serif" to "NotoSerif-Regular.ttf",
            "Droid Sans Mono" to "DroidSansMono.ttf",
            "Cutive Mono" to "CutiveMono.ttf",
            "Coming Soon" to "ComingSoon.ttf",
        )

        /** 64 MB, matching what Aniyomi settled on for phones. */
        private const val DEMUXER_CACHE_BYTES = 64L * 1024 * 1024

        /**
         * Seconds of stream to keep ahead of the playhead, against mpv's default of one.
         *
         * Sized for the bad case rather than the good one: a host that needs a fresh
         * connection per segment, and an audio stream fetched separately from the video.
         * It is a ceiling, not a reservation — [DEMUXER_CACHE_BYTES] still caps what is held.
         */
        private const val READAHEAD_SECONDS = 30

        /** How much of that has to be there before playback starts. */
        private const val CACHE_WAIT_SECONDS = 3

        /** The audio output's own buffer; mpv's default is 0.2. */
        private const val AUDIO_BUFFER_SECONDS = 0.5

        /**
         * mpv's log level, and ours. `v` narrates enough to diagnose a stream; anything
         * quieter than that hid the underruns that made episodes play silently.
         */
        private val LOG_LEVEL = if (BuildConfig.DEBUG) "v" else "error"

        /** mpv's default playlist index for loadfile, meaning "append". */
        private const val INSERT_AT_END = "-1"

        /**
         * How long the UI thread will wait for mpv to finish tearing down. Well under
         * Android's five second input timeout, so a wedged mpv costs a dropped frame or two
         * rather than an ANR.
         */
        private const val TEARDOWN_TIMEOUT_MS = 1500L

        private val mpvThread: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "mpv-lifecycle") }
    }
}
