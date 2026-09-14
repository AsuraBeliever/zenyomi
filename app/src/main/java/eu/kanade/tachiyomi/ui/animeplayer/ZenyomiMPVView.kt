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

    /** Whether the source offered subtitles, for the "pick one rather than none" rule. */
    private var addedSubtitles = false

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
     * Set while the picture is being held back for a side-car audio track.
     *
     * `audio-add` opens a second network stream, and mpv does not wait for it: the video
     * starts, plays silently for as long as that stream takes to answer, and the sound joins
     * several seconds in. Nothing is out of sync — it is simply missing at the start, which is
     * the part of an episode a person is most likely to be paying attention to.
     *
     * So the file is loaded paused and released once the track is in and chosen. The loading
     * overlay stays up meanwhile, which is the truth: it is still opening.
     */
    private var waitingForExternalAudio = false

    /** How many audio tracks the file itself carried, to tell an external one from its own. */
    private var ownAudioCount = 0

    /**
     * Reports the combined state, on the main thread.
     *
     * mpv delivers its events on its own threads, and the only consumer of this is Compose
     * state driving the screen.
     */
    private fun updateLoading() {
        val stalled = restarting || bufferingForCache
        // Waiting on an external audio track counts as opening, not as stalling: the picture
        // is deliberately held on its first frame until the sound is ready to go with it.
        val opening = !shownFirstFrame || waitingForExternalAudio
        post {
            onLoadingChanged?.invoke(opening && (stalled || waitingForExternalAudio))
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
    ) {
        if (initialised) return
        initialised = true
        preferredAudio = TrackLanguage.ofList(audioLanguages).split(',').filter { it.isNotBlank() }
        preferredSubtitles = TrackLanguage.ofList(subtitleLanguages).split(',').filter { it.isNotBlank() }
        configDir.mkdirs()
        copyAssets(configDir)
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
            MPVLib.addLogObserver { prefix, _, text ->
                if (text.contains("HTTP error")) lastHttpError = text.trim()
                logcat(LogPriority.DEBUG) { "mpv [$prefix] $text".trim() }
            }
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
            // mpv sizes and places subtitles against the *window* by default, which is right
            // on a desktop where the window is the video. Here the window is a whole portrait
            // phone screen and the video a band across the middle of it, so the defaults drew
            // text tall enough to run off the bottom edge and across the seek bar. Both are
            // tied to the video rectangle instead.
            MPVLib.setOptionString("sub-scale-by-window", "no")
            MPVLib.setOptionString("sub-use-margins", "no")
            MPVLib.setOptionString("sub-ass-force-margins", "no")
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
            // How the language preferences get applied to tracks that arrive late; see
            // applyTrackPreferences.
            MPVLib.observeProperty("track-list/count", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        }

        addObserver(
            PlaybackObserver(
                onFileLoaded = {
                    lastHttpError = null
                    shownFirstFrame = false
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
    ) {
        pendingResumeAt = resumeAt
        externalSubtitles = subtitleTracks
        externalAudio = audioTracks
        waitingForExternalAudio = audioTracks.isNotEmpty()
        if (waitingForExternalAudio) {
            setPaused(true)
            // Nothing else can rescue a side-car track that never answers: mpv reports no
            // failure for one, it simply never appears. Without a deadline the episode would
            // sit on its first frame for as long as the viewer let it.
            mpvThread.schedule(
                { if (waitingForExternalAudio) releaseForAudio() },
                EXTERNAL_AUDIO_WAIT_SECONDS,
                TimeUnit.SECONDS,
            )
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

        postToMpv {
            ownAudioCount = tracks().count { it.isAudio }
            // The source's label stays as the track title, because that is what the picker
            // shows and "Español (Spain)" tells a person more than "spa" does. The language
            // field gets the code, because that is what mpv matches the preference against.
            subtitles.forEach { track ->
                MPVLib.command(
                    arrayOf("sub-add", track.url, "auto", track.lang, TrackLanguage.of(track.lang)),
                )
            }
            audio.forEach { track ->
                MPVLib.command(
                    arrayOf("audio-add", track.url, "auto", track.lang, TrackLanguage.of(track.lang)),
                )
            }
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
        // Asked of the track list rather than of `sid`: that property is a choice — it
        // holds "no" and "auto" as well as a number — so reading it as an integer always
        // fails, and the failure is indistinguishable from "nothing is selected".
        val all = tracks()
        val subtitleTracks = all.filter { it.isSubtitle }
        val audioTracks = all.filter { it.isAudio }

        val wantedSubtitle = subtitleTracks.preferring(preferredSubtitles)
        when {
            wantedSubtitle != null -> selectSubtitle(wantedSubtitle.id)
            addedSubtitles && subtitleTracks.isNotEmpty() && subtitleTracks.none { it.selected } ->
                subtitleTracks.firstOrNull()?.let { selectSubtitle(it.id) }
        }
        // Nothing selected at all is the case this exists for: an external audio track that
        // arrived after the file opened leaves mpv with no audio chosen.
        val wantedAudio = audioTracks.preferring(preferredAudio)
            ?: audioTracks.firstOrNull()?.takeIf { audioTracks.none { track -> track.selected } }
        wantedAudio?.let { selectAudio(it.id) }

        // The side-car track is in and something is playing it, so the picture can go.
        if (waitingForExternalAudio && audioTracks.size > ownAudioCount) {
            if (wantedAudio != null || audioTracks.any { it.selected }) releaseForAudio()
        }
    }

    /** Lets the held-back file play, whether the audio arrived or the wait ran out. */
    private fun releaseForAudio() {
        if (!waitingForExternalAudio) return
        waitingForExternalAudio = false
        setPaused(false)
        updateLoading()
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

    fun seekTo(seconds: Int) = postToMpv {
        MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))
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
     * The tracks mpv found in the current file.
     *
     * mpv exposes track-list as a node, which MPVLib cannot hand over directly, so the
     * entries are read one property at a time through the track-list/N/... paths.
     */
    fun tracks(): List<Track> {
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

    fun selectAudio(trackId: Int?) = postToMpv {
        MPVLib.setPropertyString("aid", trackId?.toString() ?: "no")
    }

    fun selectSubtitle(trackId: Int?) = postToMpv {
        MPVLib.setPropertyString("sid", trackId?.toString() ?: "no")
    }

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
            toRemove.forEach { MPVLib.removeObserver(it) }
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
            MPVLib.setOptionString("vo", "gpu")
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
            MPVLib.setOptionString("vo", "null")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.detachSurface()
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
            }
        }
        override fun eventProperty(property: String, value: String) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
    }

    companion object {
        /** The name mpv looks for in its config dir; it has no other font on Android. */
        private const val SUBTITLE_FONT = "subfont.ttf"
        private const val CA_BUNDLE = "cacert.pem"

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

        /**
         * How long the picture waits for a side-car audio track before giving up on it.
         *
         * Long enough for a slow host to answer, short enough that a dead audio url costs the
         * viewer one wait rather than the episode. Silent video beats no video.
         */
        private const val EXTERNAL_AUDIO_WAIT_SECONDS = 15L

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
