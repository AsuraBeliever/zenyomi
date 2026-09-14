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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
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
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
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
    inPictureInPicture: Boolean,
    onEnterPictureInPicture: (videoAspect: Float) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(emptyList<ZenyomiMPVView.Track>()) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(controlsVisible, interaction, paused, scrubbing != null, openPicker, loading) {
        if (!controlsVisible || paused || scrubbing != null || openPicker != null || loading) {
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
    val view = remember { ZenyomiMPVView(context) }
    val viewModel = assistedMetroViewModel<AnimePlayerViewModel, AnimePlayerViewModel.Factory> {
        create(episodeId = episodeId)
    }
    val playerState by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(playerState.loaded) {
        if (playerState.loaded) {
            // mpv keeps the window open on a file it could not read, so without this a dead
            // mirror looks exactly like one that is still loading — forever.
            view.onPlaybackError = { reason -> playbackFailure = reason.orEmpty() }
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
            view.initialise(
                configDir = File(context.filesDir, "mpv"),
                audioLanguages = viewModel.preferences.preferredAudioLanguages.get(),
                subtitleLanguages = viewModel.preferences.preferredSubtitleLanguages.get(),
                speedPercent = viewModel.preferences.defaultSpeed.get(),
                // Dropping these is what made a resolved video open to a black screen: the
                // host answers mpv's bare request with 403 and mpv has nothing to play.
                httpHeaders = request.headers.map { (name, value) -> "$name: $value" },
            )
            view.playFile(
                request.url,
                resumeAt = playerState.resumeAt,
                subtitleTracks = request.subtitleTracks,
                audioTracks = request.audioTracks,
                mpvArgs = request.mpvArgs,
            )
        }
        onDispose {
            view.onPlaybackError = null
            view.onLoadingChanged = null
            view.onBufferingChanged = null
            view.onPositionChanged = null
            view.onDurationChanged = null
            view.onPausedChanged = null
            if (playerState.loaded) {
                // Uses what the polling loop already read instead of asking mpv again:
                // mpv_get_property waits on mpv's own event loop, and onDispose runs on the
                // main thread, so leaving the player hung the UI until Android raised an ANR.
                // The cost is losing at most the last two seconds of progress.
                if (duration > 0) viewModel.saveProgress(position, duration)
                view.release()
            }
        }
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
            delay(2000)
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
                                seekFeedback = if (forward) "+$step s" else "-$step s"
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
                        seekFeedback = "-$step s"
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
                        seekFeedback = "+$step s"
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

        seekFeedback?.let { text ->
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // The title, the track pickers and the seek bar come and go together: they are the
        // furniture, and a picture with half of it still on it is not a clear picture.
        val chromeVisible = controlsVisible && !inPictureInPicture

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                        tint = Color.White,
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    // The player draws edge to edge, so without this the track buttons sit
                    // under the status bar and the system swallows taps meant for them.
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
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
            }
        }
    }
}

/** Used only until mpv reports the real one, which takes a moment after the file opens. */
private const val DEFAULT_ASPECT = 16f / 9f

/**
 * How long the controls stay up with nobody touching them.
 *
 * Five seconds: long enough to read the clock and reach the button you came for, short enough
 * that they are gone before the scene they are sitting on top of matters.
 */
private const val CONTROLS_TIMEOUT_MS = 5_000L

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
