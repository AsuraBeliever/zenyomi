package eu.kanade.tachiyomi.ui.animeplayer

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import mihon.icons.materialsymbols.MaterialSymbols
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource
import mihon.icons.materialsymbols.rounded.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import java.io.File
import java.util.Locale

/**
 * Plays one video with mpv.
 *
 * A deliberate first cut: surface, play/pause and a seek bar. Aniyomi's player carries
 * gestures, picture-in-picture, track and subtitle panels across some fifty files; those
 * come later, on top of this.
 */
class AnimePlayerScreen(
    private val videoUrl: String,
    private val title: String,
    private val episodeId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        var position by remember { mutableIntStateOf(0) }
        var duration by remember { mutableIntStateOf(0) }
        var paused by remember { mutableStateOf(false) }
        var tracks by remember { mutableStateOf(emptyList<ZenyomiMPVView.Track>()) }
        val view = remember { ZenyomiMPVView(context) }
        val viewModel = assistedMetroViewModel<AnimePlayerViewModel, AnimePlayerViewModel.Factory> {
            create(episodeId = episodeId)
        }
        val playerState by viewModel.state.collectAsStateWithLifecycle()

        DisposableEffect(playerState.loaded) {
            if (playerState.loaded) {
                view.initialise(File(context.filesDir, "mpv"))
                view.playFile(videoUrl, resumeAt = playerState.resumeAt)
            }
            onDispose {
                if (playerState.loaded) {
                    view.timePos?.let { pos -> viewModel.saveProgress(pos, view.duration ?: 0) }
                    view.release()
                }
            }
        }

        // mpv reports progress through property observers; polling keeps this first cut
        // small, and a second of drift on a seek bar is not worth an observer plumbing.
        LaunchedEffect(Unit) {
            while (true) {
                if (!view.isReady) {
                    delay(200)
                    continue
                }
                position = view.timePos ?: position
                duration = view.duration ?: duration
                paused = view.paused ?: paused
                // Written as it plays, so a process death mid-episode still leaves a
                // usable resume point rather than losing the whole session.
                if (!paused) viewModel.saveProgress(position, duration)
                // Tracks only exist once the file is open, and can change on a new file.
                if (tracks.isEmpty() && duration > 0) tracks = view.tracks()
                delay(2000)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
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
