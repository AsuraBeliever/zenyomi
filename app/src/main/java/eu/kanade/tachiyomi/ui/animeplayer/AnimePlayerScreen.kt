package eu.kanade.tachiyomi.ui.animeplayer

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File
import java.util.Locale

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
    videoUrl: String,
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
            view.initialise(
                configDir = File(context.filesDir, "mpv"),
                audioLanguages = viewModel.preferences.preferredAudioLanguages.get(),
                subtitleLanguages = viewModel.preferences.preferredSubtitleLanguages.get(),
                speedPercent = viewModel.preferences.defaultSpeed.get(),
            )
            view.playFile(videoUrl, resumeAt = playerState.resumeAt)
        }
        onDispose {
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

    // mpv reports progress through property observers; polling keeps this first cut
    // small, and a second of drift on a seek bar is not worth an observer plumbing.
    //
    // Every one of these reads blocks on mpv's event loop, so they happen off the main
    // thread: doing them on the composition's dispatcher is what turned a busy player into
    // an ANR.
    LaunchedEffect(Unit) {
        while (true) {
            if (!view.isReady) {
                delay(200)
                continue
            }
            val snapshot = withContext(Dispatchers.IO) {
                Triple(view.timePos, view.duration, view.paused)
            }
            position = snapshot.first ?: position
            duration = snapshot.second ?: duration
            paused = snapshot.third ?: paused
            // Written as it plays, so a process death mid-episode still leaves a
            // usable resume point rather than losing the whole session.
            if (!paused) viewModel.saveProgress(position, duration)
            // Tracks only exist once the file is open, and can change on a new file.
            if (tracks.isEmpty() && duration > 0) {
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

        if (!inPictureInPicture) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val forward = offset.x > size.width / 2
                                val step = viewModel.preferences.seekStep.get()
                                val target = (view.timePos ?: 0) + if (forward) step else -step
                                view.seekTo(target.coerceAtLeast(0))
                                seekFeedback = if (forward) "+$step s" else "-$step s"
                            },
                            onTap = { view.togglePause() },
                        )
                    },
            )
        }

        seekFeedback?.let { text ->
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!inPictureInPicture) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
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

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // The player draws edge to edge, so without this the track buttons sit
                    // under the status bar and the system swallows taps meant for them.
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                TrackPicker(
                    label = stringResource(ANMR.strings.player_track_audio),
                    tracks = tracks.filter { it.isAudio },
                    onSelect = view::selectAudio,
                )
                TrackPicker(
                    label = stringResource(ANMR.strings.player_track_subtitle),
                    tracks = tracks.filter { it.isSubtitle },
                    onSelect = view::selectSubtitle,
                )
                IconButton(onClick = { onEnterPictureInPicture(view.videoAspect ?: DEFAULT_ASPECT) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.FlipToBack,
                        contentDescription = stringResource(ANMR.strings.player_picture_in_picture),
                        tint = Color.White,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                IconButton(onClick = { view.togglePause() }) {
                    Icon(
                        imageVector = if (paused) {
                            MaterialSymbols.RoundedFilled.PlayArrow
                        } else {
                            MaterialSymbols.Rounded.Pause
                        },
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Text(formatTime(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = position.toFloat(),
                    valueRange = 0f..(duration.takeIf { it > 0 }?.toFloat() ?: 1f),
                    onValueChange = { view.seekTo(it.toInt()) },
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
    onSelect: (Int?) -> Unit,
) {
    if (tracks.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.End) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(label, color = Color.White)
        }
        if (expanded) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column {
                    TrackRow(stringResource(MR.strings.off)) {
                        onSelect(null)
                        expanded = false
                    }
                    tracks.forEach { track ->
                        TrackRow(track.label) {
                            onSelect(track.id)
                            expanded = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
