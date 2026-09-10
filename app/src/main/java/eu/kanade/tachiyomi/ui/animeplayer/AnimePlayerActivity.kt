package eu.kanade.tachiyomi.ui.animeplayer

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.view.setComposeContent

/**
 * Hosts the video player.
 *
 * Its own activity rather than a screen on the main navigator, for two reasons that both come
 * back to the charter: picture-in-picture has to be declared per activity, and it needs
 * `configChanges` so entering it does not tear the player down. Putting either on MainActivity
 * would have changed how the manga side handles rotation.
 */
class AnimePlayerActivity : BaseActivity() {

    private var inPictureInPicture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1L)

        if (videoUrl.isNullOrBlank() || episodeId == -1L) {
            // Nothing to play; leaving a blank black activity on the stack would be worse.
            finish()
            return
        }

        // setComposeContent, not setContent: it installs the Metro view-model factory the
        // player's ViewModel is resolved through, which MainActivity also relies on.
        setComposeContent {
            AnimePlayerContent(
                videoUrl = videoUrl,
                title = title,
                episodeId = episodeId,
                inPictureInPicture = inPictureInPicture,
                onEnterPictureInPicture = ::enterPictureInPicture,
                onBack = ::finish,
            )
        }
    }

    private fun enterPictureInPicture(videoAspect: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return

        // Android rejects anything outside roughly 0.42..2.39, and a rejected ratio throws
        // rather than falling back, so it is clamped before being handed over.
        val clamped = videoAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
            .build()
        runCatching { enterPictureInPictureMode(params) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    companion object {
        private const val EXTRA_VIDEO_URL = "video_url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_EPISODE_ID = "episode_id"

        private const val MIN_ASPECT = 0.5f
        private const val MAX_ASPECT = 2.35f

        fun newIntent(context: Context, videoUrl: String, title: String, episodeId: Long): Intent {
            return Intent(context, AnimePlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_EPISODE_ID, episodeId)
            }
        }
    }
}
