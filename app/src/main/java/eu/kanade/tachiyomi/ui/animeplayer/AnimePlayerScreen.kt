package eu.kanade.tachiyomi.ui.animeplayer

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.anime.animeSourceErrorText
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.KeyboardArrowLeft
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import mihon.icons.materialsymbols.rounded.Pause
import mihon.icons.materialsymbols.rounded.SkipNext
import mihon.icons.materialsymbols.rounded.SkipPrevious
import mihon.icons.materialsymbols.rounded.Subtitles
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Plays one video with mpv.
 *
 * Lives in [AnimePlayerActivity] rather than on the main navigator so it can declare
 * picture-in-picture and handle its own configuration changes. Doing that on MainActivity
 * would have changed how the manga side survives rotation, which the charter does not allow.
 *
 * @param inPictureInPicture hides every control: in a thumbnail there is no room for them and
 * nothing to tap them with.
 */
@Composable
fun AnimePlayerContent(
    request: PlaybackRequest,
    title: String,
    episodeId: Long,
    /**
     * Created and released by [AnimePlayerActivity] rather than here: mpv has to be told the
     * player is closing *before* Android takes the surface away, and a composable that is
     * already leaving the composition cannot know that.
     */
    view: ZenyomiMPVView,
    inPictureInPicture: Boolean,
    onEnterPictureInPicture: (videoAspect: Float) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(emptyList<ZenyomiMPVView.Track>()) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var playbackFailure by remember { mutableStateOf<String?>(null) }
    // Starts true: from the tap until mpv shows a frame there is nothing on screen, and on a
    // stream that takes eight seconds to open, a bare black rectangle reads as a player that
    // is not going to work.
    var loading by remember { mutableStateOf(true) }
    // Read from the polling loop rather than from the button that needs it: asking mpv costs
    // two blocking property reads, and the button is on the main thread.
    var videoAspect by remember { mutableFloatStateOf(DEFAULT_ASPECT) }
    // Set while a finger is on the seek bar, so incoming positions do not fight the finger.
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    // Where the last seek was aimed, held until mpv reports arriving there.
    //
    // A seek is not instant: mpv keeps reporting the old position until it has the new one
    // decoded. Letting those through made the thumb snap back to where the episode was, sit
    // there, and then jump forward — the drag looked like it had been refused and then obeyed
    // a second later.
    var seekTarget by remember { mutableStateOf<Int?>(null) }
    // A stall after the episode is up. Distinct from `loading`, which covers opening it: this
    // one is a spinner over a picture that is still there, and it must not block anything.
    var buffering by remember { mutableStateOf(false) }
    // The controls get out of the way, the way they do in every other video player. Nobody
    // wants to watch an episode through a title bar and a seek bar.
    var controlsVisible by remember { mutableStateOf(true) }
    // Which track list is open, if any — held here rather than inside the picker so the
    // countdown below can see it. A list that vanished mid-choice would be worse than one
    // that overstays.
    var openPicker by remember { mutableStateOf<TrackKind?>(null) }
    // The subtitle panel, held here for the same reason: while it is open the controls have to
    // stay, or adjusting the size would dismiss the thing being adjusted.
    var subtitlePanelOpen by remember { mutableStateOf(false) }
    // The chapters the file declares, or null until they have been asked for. Read once per
    // file, because a container's chapter list does not change while it plays.
    var chapters by remember { mutableStateOf<List<ZenyomiMPVView.Chapter>?>(null) }
    // Whether the opening was skipped without being asked. Once per episode: seeking back
    // into it means the viewer wants to watch it, and being dragged forward again would be
    // the player arguing with them.
    var autoSkippedIntro by remember { mutableStateOf(false) }
    // Where the last skip was made from, or null if there has not been one.
    //
    // The button is a single action, not a fast-forward. Without this it stayed on screen
    // after being pressed — the window it is offered in is minutes long — and pressing it
    // again jumped another eighty-five seconds, and again, until half the episode was gone.
    // It comes back if the viewer seeks to before where they skipped, because pressing it by
    // accident should not cost them the option.
    var skippedFrom by remember { mutableStateOf<Int?>(null) }
    // Shown for a moment after that happens, because an episode that jumps on its own with
    // nothing said is indistinguishable from one that lost its place.
    var skipNotice by remember { mutableStateOf(false) }
    // mpv has run out of file. The clock cannot say this: `keep-open` stops on the last frame
    // a second short of the duration, so the episode ends without the two ever meeting.
    var endReached by remember { mutableStateOf(false) }
    // Set when the viewer tells the end-of-episode countdown to stop. Per episode, and reset
    // with every file: saying "not this time" is not the same as turning the setting off.
    var autoAdvanceCancelled by remember { mutableStateOf(false) }
    // Bumped by anything the viewer does to a control, to start the countdown over. It is the
    // change that matters, not the value: a button press has to restart a timer that is
    // already running, and only a new key does that.
    var interaction by remember { mutableIntStateOf(0) }

    fun showControls() {
        controlsVisible = true
        interaction++
    }

    // They stay while the episode is paused, while a finger is on the seek bar and while a
    // track list is open: in all three the viewer is mid-something and looking right at them.
    LaunchedEffect(
        controlsVisible,
        interaction,
        paused,
        scrubbing != null,
        openPicker,
        subtitlePanelOpen,
        loading,
    ) {
        if (!controlsVisible || paused || scrubbing != null || openPicker != null ||
            subtitlePanelOpen || loading
        ) {
            return@LaunchedEffect
        }
        delay(CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    // The jump indicator is a flash, not a state: clear it shortly after it appears.
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(700)
            seekFeedback = null
        }
    }
    val viewModel = assistedMetroViewModel<AnimePlayerViewModel, AnimePlayerViewModel.Factory> {
        create(episodeId = episodeId, request = request)
    }
    val playerState by viewModel.state.collectAsStateWithLifecycle()

    val subtitleStyle = viewModel.subtitles.collectStyleAsState()
    // Read once: a directory listing whose answer cannot change while an episode plays.
    val availableFonts = remember { ZenyomiMPVView.availableSubtitleFonts() }

    // Whether the picture reaches the bottom edge, which is where the seek bar is. A video
    // narrower than the screen is fitted to the height and touches both edges; a wider one is
    // fitted to the width and sits in a band with black above and below it. Comparing the two
    // shapes says which, without having to ask mpv where it put anything.
    val configuration = LocalConfiguration.current
    val screenAspect = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp
    val chromeOverlapsVideo = videoAspect <= screenAspect

    // In landscape the video fills the screen, so the bottom of the picture and the bottom of
    // the phone are the same edge — and the seek bar sits on top of the subtitles whenever the
    // controls are up. They are lifted clear for as long as the controls are showing and drop
    // back the moment they go. In portrait the video is a band in the middle with black either
    // side and the bar never reaches it, so there is nothing to lift: doing it anyway would
    // move the subtitles for no reason the viewer could see.
    val subtitlesUnderControls = chromeOverlapsVideo && controlsVisible
    val effectiveStyle = remember(subtitleStyle, subtitlesUnderControls) {
        if (subtitlesUnderControls) {
            subtitleStyle.copy(position = (subtitleStyle.position - CONTROLS_SUBTITLE_LIFT).coerceAtLeast(0))
        } else {
            subtitleStyle
        }
    }
    LaunchedEffect(effectiveStyle) { view.applySubtitleStyle(effectiveStyle) }

    /**
     * Hands one file to mpv and resets everything the screen knew about the last one.
     *
     * Two things reach here: the view model saying which episode is playing, and the player
     * coming back from the background, where the file was unloaded so the surface could be
     * given up and has to be put back where it was.
     */
    fun startPlayback(playback: AnimePlayerViewModel.Playback, resumeAt: Int) {
        // The screen's own idea of where the video is belongs to the file being left. The
        // position is kept when reopening the same one, so the bar does not snap to zero and
        // back while mpv loads.
        position = resumeAt
        duration = 0
        tracks = emptyList()
        seekTarget = null
        scrubbing = null
        playbackFailure = null
        loading = true
        chapters = null
        autoSkippedIntro = false
        skippedFrom = null
        skipNotice = false
        endReached = false
        autoAdvanceCancelled = false
        view.playFile(
            playback.request.url,
            resumeAt = resumeAt,
            subtitleTracks = playback.request.subtitleTracks,
            audioTracks = playback.request.audioTracks,
            mpvArgs = playback.request.mpvArgs,
            // Carried per file, not only at startup: the next episode is as likely to come
            // from a host that answers a bare request with 403 as this one was.
            httpHeaders = playback.request.headers.map { (name, value) -> "$name: $value" },
        )
    }

    DisposableEffect(playerState.loaded) {
        if (playerState.loaded) {
            // mpv keeps the window open on a file it could not read, so without this a dead
            // mirror looks exactly like one that is still loading — forever.
            view.onPlaybackError = { reason ->
                playbackFailure = reason.orEmpty()
                viewModel.onPlaybackFailed()
            }
            view.onLoadingChanged = { loading = it }
            view.onBufferingChanged = { buffering = it }
            // Pushed by mpv rather than polled. Polling ran every two seconds, so the clock
            // advanced in two-second steps — and because each pass first made several blocking
            // reads, the steps were uneven: 30, then 34. mpv reports time-pos as it changes,
            // which is once a second and on the beat.
            view.onPositionChanged = { value ->
                val target = seekTarget
                when {
                    // Within a second of where the drag asked for: mpv has arrived, hand the
                    // bar back to it.
                    target != null && abs(value - target) <= SEEK_SETTLED_SECONDS -> {
                        seekTarget = null
                        position = value
                    }
                    // Still on its way there. Keep showing the destination.
                    target != null -> Unit
                    else -> position = value
                }
            }
            view.onDurationChanged = { value -> duration = value }
            view.onPausedChanged = { value -> paused = value }
            view.onEndReachedChanged = { value -> endReached = value }
            // The surface went away without the player closing, so mpv let go of the file.
            // Whatever was playing goes back on at the second it was on.
            view.onNeedsReload = { resumeAt ->
                playerState.playback?.let { startPlayback(it, resumeAt) }
            }
            view.initialise(
                configDir = File(context.filesDir, "mpv"),
                audioLanguages = viewModel.preferences.preferredAudioLanguages.get(),
                subtitleLanguages = viewModel.preferences.preferredSubtitleLanguages.get(),
                speedPercent = viewModel.preferences.defaultSpeed.get(),
                // Dropping these is what made a resolved video open to a black screen: the
                // host answers mpv's bare request with 403 and mpv has nothing to play.
                httpHeaders = request.headers.map { (name, value) -> "$name: $value" },
                // Set before the file opens so its first subtitle is already styled. Applying
                // them afterwards makes the text visibly resize a second into every episode.
                subtitleStyle = subtitleStyle,
            )
        }
        onDispose {
            view.onPlaybackError = null
            view.onLoadingChanged = null
            view.onBufferingChanged = null
            view.onPositionChanged = null
            view.onDurationChanged = null
            view.onPausedChanged = null
            view.onEndReachedChanged = null
            view.onNeedsReload = null
            if (playerState.loaded) {
                // Uses what the polling loop already read instead of asking mpv again:
                // mpv_get_property waits on mpv's own event loop, and onDispose runs on the
                // main thread, so leaving the player hung the UI until Android raised an ANR.
                // The cost is losing at most the last two seconds of progress.
                if (duration > 0) viewModel.saveProgress(position, duration)
            }
        }
    }

    // Every file mpv opens comes through here, the first one included: which episode is
    // playing is the view model's answer, not the intent's, because the player now moves on
    // to the next one by itself.
    //
    // Keyed on the serial rather than the episode or the url: replaying the episode that is
    // already open has to reach mpv too.
    LaunchedEffect(playerState.playback?.serial) {
        val playback = playerState.playback ?: return@LaunchedEffect
        startPlayback(playback, playback.resumeAt)
    }

    // What is left to poll for, now that position, duration and pause arrive as events: the
    // track list and the aspect ratio, neither of which moves while anyone is watching.
    //
    // Every one of these reads blocks on mpv's event loop, so they happen off the main
    // thread: doing them on the composition's dispatcher is what turned a busy player into
    // an ANR. That cost is also why this no longer drives the seek bar — the reads made the
    // loop's period uneven, and the clock inherited the unevenness.
    LaunchedEffect(Unit) {
        while (true) {
            if (!view.isReady) {
                delay(200)
                continue
            }
            videoAspect = withContext(Dispatchers.IO) { view.videoAspect } ?: videoAspect
            // Written as it plays, so a process death mid-episode still leaves a
            // usable resume point rather than losing the whole session.
            if (!paused && duration > 0) viewModel.saveProgress(position, duration)
            // Re-read rather than read once. The selection is no longer only the viewer's to
            // change — an external audio track that arrives late is chosen by the player
            // itself — and a picker showing a stale tick is worse than one showing none.
            if (duration > 0) {
                tracks = withContext(Dispatchers.IO) { view.tracks() }
            }
            // Once per file. Cheap when there are none, which is most streams.
            if (duration > 0 && chapters == null) {
                chapters = withContext(Dispatchers.IO) { view.chapters() }
            }
            delay(2000)
        }
    }

    // Seconds left of the episode, which is also the countdown the card shows: read off the
    // video's own clock rather than a timer of our own, so pausing pauses it and seeking back
    // puts the card away.
    val nextEpisode = playerState.next
    val remaining = if (duration > 0) duration - position else Int.MAX_VALUE
    val autoplayNext = remember { viewModel.preferences.autoplayNext.get() }
    // Half a minute of warning, unless the episode is too short to spare it: on a five minute
    // recap the card would otherwise be up for a tenth of it, and on a ten second clip it
    // would be up from the first frame.
    val endCardLead = minOf(END_CARD_LEAD_SECONDS, duration / 4)
    // The credits are where the next episode is announced when AniSkip knows where they
    // start; otherwise the last half minute of the episode stands in for them.
    val creditsStarted = playerState.ending?.let { position >= it.first } == true
    val endCardVisible = nextEpisode != null && !inPictureInPicture && !loading &&
        playbackFailure == null && (remaining <= endCardLead || creditsStarted || endReached)

    // AniSkip answers for the episode, and needs its length to do it.
    LaunchedEffect(playerState.playback?.serial, duration > 0) {
        viewModel.loadSkipIntervals(duration)
    }

    // Where the opening is, from whoever knows: the file's own chapters first — a release that
    // names them has said it about *this* file — then AniSkip, which knows the episode but not
    // which cut of it is being played.
    //
    // Either way the skip lands SKIP_LANDING_MARGIN_SECONDS short of the end, so the episode
    // picks up on the last bars of the opening rather than a moment into the scene. Where the
    // two differ is what they know: a chapter is about this file, and AniSkip's times were
    // measured on somebody else's copy — so for those the margin is counted from where the
    // opening really ends, which is the reported end give or take the drift between copies.
    val opening = remember(chapters, playerState.opening, playerState.openingDrift) {
        openingChapter(chapters)
            ?.let { Opening(it, slack = SKIP_LANDING_MARGIN_SECONDS) }
            ?: playerState.opening?.let {
                Opening(it, slack = SKIP_LANDING_MARGIN_SECONDS + playerState.openingDrift)
            }
    }
    // Only while the opening is actually on screen, and therefore only when somebody knows
    // where it is. Nothing here guesses: a button offered at the first frame of an episode
    // whose opening starts three minutes in is a button that lies about what it does, and
    // pressing it would jump into the middle of the episode.
    val withinOpening = opening?.range?.contains(position) == true
    val skipUsed = skippedFrom?.let { position >= it } == true
    val skipVisible = withinOpening && !skipUsed && duration > 0 && !inPictureInPicture &&
        !loading && playbackFailure == null && !endCardVisible && !skipNotice

    // Skipping it without being asked, for the viewer who turned that on. Once per episode:
    // seeking back into the opening is a viewer who wants to watch it.
    val autoSkipIntro = remember { viewModel.preferences.autoSkipIntro.get() }
    LaunchedEffect(opening, withinOpening, autoSkippedIntro) {
        if (!autoSkipIntro || autoSkippedIntro || opening == null || !withinOpening) {
            return@LaunchedEffect
        }
        autoSkippedIntro = true
        skippedFrom = position
        val target = opening.landing
        position = target
        seekTarget = target
        view.seekTo(target, exact = true)
        skipNotice = true
    }

    // The notice is a flash, like the jump indicator: it says what happened and goes.
    LaunchedEffect(skipNotice) {
        if (skipNotice) {
            delay(SKIP_NOTICE_MS)
            skipNotice = false
        }
    }

    // A known ending is a different promise from an unknown one. When AniSkip says where the
    // credits start, the next episode begins *there* rather than three or four minutes later
    // at the last frame — which is what a streaming app does, and the only way a long ending
    // does not read as a countdown that is stuck. The clock is ours in that case rather than
    // the video's, so it stops when the video does.
    var creditsCountdown by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(
        creditsStarted,
        autoplayNext,
        autoAdvanceCancelled,
        paused,
        playerState.playback?.serial,
    ) {
        if (!creditsStarted || !autoplayNext || autoAdvanceCancelled) {
            creditsCountdown = null
            return@LaunchedEffect
        }
        // Paused mid-credits: whatever the card says stays said until it plays again.
        if (paused) return@LaunchedEffect
        var left = creditsCountdown ?: AUTOPLAY_COUNTDOWN_SECONDS
        while (left > 0) {
            creditsCountdown = left
            delay(1000)
            left--
        }
        creditsCountdown = 0
    }

    // Resolving the next episode costs what opening this one did, and the credits are exactly
    // the window in which to spend it unnoticed. Without this the countdown reaches zero and
    // hands over to a spinner, which is the thing it promised not to do.
    LaunchedEffect(endCardVisible) {
        if (endCardVisible) viewModel.prefetchNext()
    }

    // The switch itself: the end of the file, or the countdown running out over credits that
    // are known to be credits. Never earlier — the card is up beforehand so it can be seen
    // coming, not so it can cut the episode short.
    LaunchedEffect(endReached, creditsCountdown, autoAdvanceCancelled, playerState.switchError) {
        val due = endReached || creditsCountdown == 0
        if (due && autoplayNext && !autoAdvanceCancelled && playerState.switchError == null) {
            nextEpisode?.let { viewModel.open(it, position, duration) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Gestures sit over the surface, not on it: the SurfaceView itself stays a
        // plain video output and all input is handled in Compose.
        AndroidView(
            factory = {
                view.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Not while loading: the tap that pauses and the double tap that seeks both act on a
        // video that has not started, so the viewer's first tap used to pause it before the
        // first frame.
        if (!inPictureInPicture && !loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val forward = offset.x > size.width / 2
                                val step = viewModel.preferences.seekStep.get()
                                view.seekBy(if (forward) step else -step)
                                seekFeedback = SeekFeedback(seconds = step, forward = forward)
                                // Where the episode landed is worth seeing after a jump.
                                showControls()
                            },
                            // Shows or hides the controls; it does not pause. A tap on the
                            // picture pausing the episode is what made them impossible to get
                            // rid of — the only way to dismiss them also stopped the video.
                            // Pausing is what the button in the middle is for.
                            onTap = { if (controlsVisible) controlsVisible = false else showControls() },
                        )
                    },
            )
        }

        // Not shown once something has gone wrong: a spinner over an error message says the
        // player is still trying, and it is not.
        if (loading && playbackFailure == null && !inPictureInPicture) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Absorbs taps rather than acting on them, so nothing else underneath
                    // responds until there is something to respond about.
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = stringResource(ANMR.strings.player_loading),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    // The way out. A stream that never opens otherwise leaves the viewer on a
                    // black screen with a spinner and nothing to press.
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Text(
                            text = stringResource(ANMR.strings.player_cancel_loading),
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        // A stall once the episode is up: a spinner where the controls were, and nothing
        // blocked. Refilling the cache after a seek is the common case, and a player that
        // locked the screen for each one would be worse than the stutter it was reporting.
        if (buffering && !loading && playbackFailure == null && !inPictureInPicture) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // The three controls people reach for, where every video app puts them: the big one in
        // the middle, the two jumps either side of it. The bottom bar keeps the seek bar and
        // the clock, which is where those belong.
        AnimatedVisibility(
            visible = controlsVisible &&
                !inPictureInPicture && !loading && !buffering && playbackFailure == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val step = remember { viewModel.preferences.seekStep.get() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                FilledIconButton(
                    onClick = {
                        view.seekBy(-step)
                        seekFeedback = SeekFeedback(seconds = step, forward = false)
                        interaction++
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(56.dp),
                ) {
                    SeekLabel(
                        icon = MaterialSymbols.Rounded.KeyboardArrowLeft,
                        seconds = step,
                        contentDescription = stringResource(ANMR.strings.player_seek_backward),
                    )
                }
                FilledIconButton(
                    onClick = {
                        view.togglePause()
                        interaction++
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(80.dp),
                ) {
                    Icon(
                        imageVector = if (paused) {
                            MaterialSymbols.RoundedFilled.PlayArrow
                        } else {
                            MaterialSymbols.RoundedFilled.Pause
                        },
                        contentDescription = stringResource(
                            if (paused) MR.strings.action_resume else ANMR.strings.player_pause,
                        ),
                        modifier = Modifier.size(44.dp),
                    )
                }
                FilledIconButton(
                    onClick = {
                        view.seekBy(step)
                        seekFeedback = SeekFeedback(seconds = step, forward = true)
                        interaction++
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(56.dp),
                ) {
                    SeekLabel(
                        icon = MaterialSymbols.Rounded.KeyboardArrowRight,
                        seconds = step,
                        contentDescription = stringResource(ANMR.strings.player_seek_forward),
                    )
                }
            }
        }

        if (playbackFailure != null && !inPictureInPicture) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            ) {
                Text(
                    text = stringResource(ANMR.strings.anime_error_playback),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                // mpv's own words underneath: "HTTP error 404 Not Found" tells whoever is
                // reading a bug report which half of the problem to look at.
                playbackFailure?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // On the side the jump came from, not over the middle of the picture. The half of the
        // screen that was double tapped is the half the flash belongs on, and it is the same
        // answer for the buttons: the one on the left reports on the left.
        //
        // Held clear of the play button either way. Beside the control row where there is room
        // for it — the row is [SEEK_FEEDBACK_ROW_HALF_WIDTH] either side of centre — and above
        // the row where there is not, which on a phone held upright is always. The choice
        // depends on the width of the screen and not on whether the controls happen to be
        // showing, so the flash appears in the same place every time rather than moving about.
        seekFeedback?.let { feedback ->
            val besideControls = configuration.screenWidthDp.dp / 2 - SEEK_FEEDBACK_ROW_HALF_WIDTH >=
                SEEK_FEEDBACK_ROOM_NEEDED
            Text(
                text = if (feedback.forward) "+${feedback.seconds} s" else "-${feedback.seconds} s",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .align(if (feedback.forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .offset(y = if (besideControls) 0.dp else -SEEK_FEEDBACK_LIFT)
                    .padding(horizontal = SEEK_FEEDBACK_EDGE_MARGIN),
            )
        }

        // The title, the track pickers and the seek bar come and go together: they are the
        // furniture, and a picture with half of it still on it is not a clear picture.
        val chromeVisible = controlsVisible && !inPictureInPicture

        // One row across the top, not two anchored to opposite corners. As two, nothing told
        // the title where the track buttons began: a long episode name simply kept going and
        // ran underneath Audio, Subtitles and the picture-in-picture button. It is the
        // buttons that get the room they need now, and the title takes what is left — which
        // is also why this is a layout rule rather than a cap on characters. How much room
        // there is depends on what is beside it: an episode with no separate audio track
        // does not draw the Audio button at all, and the title is free to be longer.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    // The player draws edge to edge, so without this the track buttons sit
                    // under the status bar and the system swallows taps meant for them.
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                            tint = Color.White,
                        )
                    }
                    Text(
                        // The intent's title only until the view model has read the episode:
                        // from then on it is whatever is playing, which is no longer what the
                        // player was opened with.
                        text = playerState.title.ifBlank { title },
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 8.dp),
                    )
                }
                TrackPicker(
                    label = stringResource(ANMR.strings.player_track_audio),
                    tracks = tracks.filter { it.isAudio },
                    expanded = openPicker == TrackKind.Audio,
                    onExpandedChange = { open -> openPicker = if (open) TrackKind.Audio else null },
                    onSelect = view::selectAudio,
                )
                TrackPicker(
                    label = stringResource(ANMR.strings.player_track_subtitle),
                    tracks = tracks.filter { it.isSubtitle },
                    expanded = openPicker == TrackKind.Subtitle,
                    onExpandedChange = { open -> openPicker = if (open) TrackKind.Subtitle else null },
                    onSelect = view::selectSubtitle,
                )
                IconButton(
                    onClick = {
                        subtitlePanelOpen = !subtitlePanelOpen
                        openPicker = null
                        interaction++
                    },
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Subtitles,
                        contentDescription = stringResource(ANMR.strings.player_subtitle_settings),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { onEnterPictureInPicture(videoAspect) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.FlipToBack,
                        contentDescription = stringResource(ANMR.strings.player_picture_in_picture),
                        tint = Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                // Either side of the seek bar rather than in the middle of the picture: the
                // row in the centre is the three controls reached mid-episode, and two more
                // buttons there would not fit a phone held upright.
                EpisodeStepButton(
                    icon = MaterialSymbols.Rounded.SkipPrevious,
                    contentDescription = stringResource(ANMR.strings.action_previous_episode),
                    episode = playerState.previous,
                    onClick = { episode ->
                        viewModel.open(episode, position, duration)
                        interaction++
                    },
                )
                Text(
                    formatTime(scrubbing?.toInt() ?: position),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = scrubbing ?: position.toFloat(),
                    valueRange = 0f..(duration.takeIf { it > 0 }?.toFloat() ?: 1f),
                    // One seek when the finger lifts, not one per pixel of the drag: mpv
                    // answers each of them by actually moving the stream.
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        scrubbing?.let { value ->
                            val target = value.toInt()
                            // The bar moves to where it was asked to go straight away and
                            // stays there. mpv is told at the same moment, and `seekTarget`
                            // keeps its stale reports from dragging the thumb back.
                            position = target
                            seekTarget = target
                            view.seekTo(target)
                        }
                        scrubbing = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                EpisodeStepButton(
                    icon = MaterialSymbols.Rounded.SkipNext,
                    contentDescription = stringResource(ANMR.strings.action_next_episode),
                    episode = playerState.next,
                    onClick = { episode ->
                        viewModel.open(episode, position, duration)
                        interaction++
                    },
                )
            }
        }

        // Skipping the opening. Exactly to its end when the file declares one, and a jump of
        // the length in Settings when it does not — which is every stream that ships no
        // chapters, so the button has to work without them.
        AnimatedVisibility(
            visible = skipVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(end = 16.dp, bottom = END_CARD_BOTTOM_MARGIN),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(ANMR.strings.player_skip_intro),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable {
                            // Remembered before the jump: the button is one press, and it
                            // only comes back if the viewer returns to before this point.
                            skippedFrom = position
                            // Never null while the button is up: it is only offered on an
                            // opening somebody has put a time on.
                            val target = opening?.landing ?: return@clickable
                            // The bar moves with it, the same way a drag does: mpv keeps
                            // reporting where the episode was until it has decoded where it
                            // is going, and a button that appears to do nothing for a second
                            // gets pressed again.
                            position = target
                            seekTarget = target
                            // Exact: the button names where it is going, and a keyframe seek
                            // lands before it — which on some files is back inside the
                            // opening the viewer just asked to be rid of.
                            view.seekTo(target, exact = true)
                            showControls()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // What just happened, when the opening was skipped without being asked.
        AnimatedVisibility(
            visible = skipNotice && !inPictureInPicture,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(end = 16.dp, bottom = END_CARD_BOTTOM_MARGIN),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(ANMR.strings.player_intro_skipped),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // What is coming next, while what is playing finishes. Shown whether or not the
        // controls are up: it is the one thing on this screen that has to be seen without
        // being asked for, because it is about to act on its own.
        nextEpisode?.let { next ->
            AnimatedVisibility(
                visible = endCardVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .systemBarsPadding()
                    .padding(end = 16.dp, bottom = END_CARD_BOTTOM_MARGIN),
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = END_CARD_MAX_WIDTH)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(ANMR.strings.player_up_next),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = next.name,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // Three things can be true here and only one line is spent on them: a
                        // countdown that is running, a countdown the viewer stopped — which
                        // needs no words, the button is right there — and an episode that
                        // could not be opened, which does.
                        val switchError = playerState.switchError
                        when {
                            switchError != null -> Text(
                                text = animeSourceErrorText(switchError),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            autoplayNext && !autoAdvanceCancelled -> Text(
                                text = stringResource(
                                    ANMR.strings.player_autoplay_in,
                                    creditsCountdown ?: remaining.coerceAtLeast(0),
                                ),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            if (playerState.switching) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                if (autoplayNext && !autoAdvanceCancelled) {
                                    TextButton(onClick = { autoAdvanceCancelled = true }) {
                                        Text(
                                            text = stringResource(MR.strings.action_cancel),
                                            color = Color.White,
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        viewModel.clearSwitchError()
                                        viewModel.open(next, position, duration)
                                    },
                                ) {
                                    Text(
                                        text = stringResource(ANMR.strings.player_play_now),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Last, so it is on top of everything: in portrait it sits where the seek bar is, and
        // a seek bar drawn over the panel would put a slider you cannot use across a slider
        // you can.
        AnimatedVisibility(
            visible = subtitlePanelOpen && !inPictureInPicture,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(
                if (chromeOverlapsVideo) Alignment.CenterEnd else Alignment.BottomCenter,
            ),
        ) {
            SubtitleSettingsPanel(
                preferences = viewModel.subtitles,
                availableFonts = availableFonts,
                onDismiss = { subtitlePanelOpen = false },
            )
        }
    }
}

/**
 * One step through the episode list, drawn the same on both sides.
 *
 * Disabled rather than hidden at the ends of a series: a button that disappears takes the
 * layout with it, and the seek bar jumping a few pixels wider on the last episode is a worse
 * answer than a greyed arrow.
 */
@Composable
private fun EpisodeStepButton(
    icon: ImageVector,
    contentDescription: String,
    episode: Episode?,
    onClick: (Episode) -> Unit,
) {
    IconButton(
        onClick = { episode?.let(onClick) },
        enabled = episode != null,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (episode != null) 1f else 0.3f),
        )
    }
}

/**
 * An opening somebody has put a time on, and how short of its end to land.
 *
 * [slack] is how many seconds before [range]'s end the skip goes: the margin the player always
 * leaves, plus whatever the times may be out by when they were measured on another copy.
 */
internal data class Opening(val range: IntRange, val slack: Int) {

    /** Where the skip button goes, never back past the start of the opening itself. */
    val landing: Int get() = (range.last - slack).coerceAtLeast(range.first + 1)
}

/**
 * The stretch of the episode a chapter calls the opening, or null if nothing does.
 *
 * Matched on whole words so that "Ending" is not read as an opening and "Part 2" is not read
 * as anything. The chapter after it is where it ends: a last chapter named "Opening" would be
 * an opening that runs to the end of the file, which is not a thing, and skipping to the end
 * of the episode on the strength of a title is worse than not offering to.
 */
internal fun openingChapter(chapters: List<ZenyomiMPVView.Chapter>?): IntRange? {
    if (chapters == null) return null
    val index = chapters.indexOfFirst { OPENING_TITLE.containsMatchIn(it.title.orEmpty()) }
    if (index < 0) return null
    val start = chapters[index].start
    val end = chapters.getOrNull(index + 1)?.start ?: return null
    return if (end > start) start..end else null
}

/**
 * What a chapter calls an opening, in the releases that name their chapters at all.
 *
 * Not "avant": that is the cold open *before* the opening, and treating it as one would skip
 * the scene the episode starts with and leave the opening itself to play.
 */
private val OPENING_TITLE = Regex("""\b(op|opening|intro)\b""", RegexOption.IGNORE_CASE)

/**
 * How short of the end of the opening the skip lands.
 *
 * Five seconds. The end of an opening is not a frame, it is a handover — the last bars of the
 * song over the first shot of the scene — and landing on the reported second means trusting
 * it to be exactly right, which nothing here ever is. Five seconds of music is a moment; five
 * seconds of episode is a scene starting without you, and you cannot get it back without
 * seeking. So the button lands just short and the episode carries on from there.
 */
private const val SKIP_LANDING_MARGIN_SECONDS = 5

/** Used only until mpv reports the real one, which takes a moment after the file opens. */
private const val DEFAULT_ASPECT = 16f / 9f

/**
 * How long before the end the next episode is announced.
 *
 * Half a minute: about the length of an ending, so the card arrives with the credits rather
 * than over the last scene of the episode, and it leaves time for the next one to be resolved
 * before the countdown reaches zero.
 */
private const val END_CARD_LEAD_SECONDS = 30

/**
 * The countdown over credits that are known to be credits.
 *
 * Ten seconds, the same as every streaming app: long enough to be read and stopped, short
 * enough that it is the announcement and not the wait.
 */
private const val AUTOPLAY_COUNTDOWN_SECONDS = 10

/** How long the "opening skipped" notice stays up. */
private const val SKIP_NOTICE_MS = 2_000L

/** Enough to clear the seek bar, which is what the card sits above. */
private val END_CARD_BOTTOM_MARGIN = 72.dp

/** Wide enough for two lines of an episode name, narrow enough to leave the picture visible. */
private val END_CARD_MAX_WIDTH = 320.dp

/**
 * How long the controls stay up with nobody touching them.
 *
 * Five seconds: long enough to read the clock and reach the button you came for, short enough
 * that they are gone before the scene they are sitting on top of matters.
 */
private const val CONTROLS_TIMEOUT_MS = 5_000L

/**
 * How far the subtitles move up while the controls are on screen, in mpv's `sub-pos` units —
 * hundredths of the video's height, so ten is a tenth of the picture.
 *
 * Enough to clear the seek bar and its clock on a video that reaches the bottom edge, and no
 * more: every unit of it is picture the text is covering instead.
 */
private const val CONTROLS_SUBTITLE_LIFT = 10

/** Which of the two track lists is open. Only one is, and often neither. */
private enum class TrackKind { Audio, Subtitle }

private fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

/**
 * Picks one of the file's tracks, or turns them off.
 *
 * Expands in place rather than in a DropdownMenu: a popup window over the player's
 * SurfaceView did not open at all here, and an inline list avoids the question entirely
 * while staying reachable with one hand.
 *
 * Hidden when the file has nothing to choose between, which is the common case for a
 * single-audio episode with no subtitles.
 */
@Composable
private fun TrackPicker(
    label: String,
    tracks: List<ZenyomiMPVView.Track>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int?) -> Unit,
) {
    if (tracks.isEmpty()) return
    val nothingSelected = tracks.none { it.selected }

    Column(horizontalAlignment = Alignment.End) {
        TextButton(onClick = { onExpandedChange(!expanded) }) {
            Text(label, color = Color.White)
        }
        if (expanded) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column {
                    TrackRow(
                        label = stringResource(MR.strings.off),
                        selected = nothingSelected,
                    ) {
                        onSelect(null)
                        onExpandedChange(false)
                    }
                    tracks.forEach { track ->
                        TrackRow(label = track.label, selected = track.selected) {
                            onSelect(track.id)
                            onExpandedChange(false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // A tick in a fixed-width slot rather than only on the chosen row: without the space
        // reserved, picking a different track shifts every label sideways.
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * A jump button: which way, and how far.
 *
 * The number is drawn rather than baked into the icon because the step is a setting — it can be
 * 5 seconds or 90 — and a "10" stamped on the glyph would be a lie the moment someone changed
 * it. The icon set carries no replay-10 or forward-10 symbol anyway.
 */
@Composable
private fun SeekLabel(icon: ImageVector, seconds: Int, contentDescription: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        Text(text = "$seconds", style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * How close mpv has to get to a seek's destination before the bar follows it again.
 *
 * One second, because that is the resolution mpv reports positions at: asking for exactly the
 * requested second would mean waiting for a report that may never come.
 */
private const val SEEK_SETTLED_SECONDS = 1

/**
 * A jump that has just happened: how far it went, and which way.
 *
 * The direction is carried rather than folded into a finished string because it decides where
 * the flash is drawn, not only what it says.
 */
private data class SeekFeedback(val seconds: Int, val forward: Boolean)

/**
 * Half the width of the row of controls in the middle: two 56 dp jump buttons, an 80 dp play
 * button, and the two 40 dp gaps between them.
 */
private val SEEK_FEEDBACK_ROW_HALF_WIDTH = 136.dp

/** Room a `-90 s` needs beside that row before it is worth putting it there. */
private val SEEK_FEEDBACK_ROOM_NEEDED = 96.dp

/** How far above the row the flash sits when there is no room beside it. */
private val SEEK_FEEDBACK_LIFT = 72.dp

/** How far in from the edge of the screen the flash sits. */
private val SEEK_FEEDBACK_EDGE_MARGIN = 24.dp
