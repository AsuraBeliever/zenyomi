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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import mihon.app.di.appGraph

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

    /**
     * mpv lives as long as this activity, not as long as the composition.
     *
     * The order matters at the end: Android destroys the surface before the composition is
     * disposed, and mpv has to be told the player is closing before that happens or it is
     * asked to give up a surface it is still drawing into — which is a wait that never ends.
     * Only the activity knows the difference between closing and going to the background.
     */
    private lateinit var player: ZenyomiMPVView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (appGraph.playerPreferences.hideSystemBars.get()) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                // Swiping brings them back briefly instead of pinning them, which is what
                // every other full-screen video app does.
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        player = ZenyomiMPVView(this)

        val request = intent.getStringExtra(EXTRA_REQUEST)?.let(PlaybackRequest::decode)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1L)

        if (request == null || request.url.isBlank() || episodeId == -1L) {
            // Nothing to play; leaving a blank black activity on the stack would be worse.
            finish()
            return
        }

        // setComposeContent, not setContent: it installs the Metro view-model factory the
        // player's ViewModel is resolved through, which MainActivity also relies on.
        setComposeContent {
            AnimePlayerContent(
                request = request,
                title = title,
                episodeId = episodeId,
                view = player,
                inPictureInPicture = inPictureInPicture,
                onEnterPictureInPicture = ::enterPictureInPicture,
                onBack = ::finish,
            )
        }
    }

    /**
     * The one place that knows the player is closing rather than being backgrounded. Reached
     * by the X, by the system back gesture and by closing the picture-in-picture window.
     */
    override fun finish() {
        if (::player.isInitialized) player.finishing = true
        super.finish()
    }

    override fun onDestroy() {
        // Before super, and before the view hierarchy goes: releasing mpv is a blocking
        // conversation with it, and it has to happen while there is still something to
        // release.
        if (::player.isInitialized) player.release()
        super.onDestroy()
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
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_EPISODE_ID = "episode_id"

        /**
         * The whole [PlaybackRequest] as json rather than a url plus loose extras. Headers and
         * side-car subtitle tracks are as much a part of "what to play" as the url is, and
         * spelling each of them out as its own extra is how they came to be dropped.
         */
        private const val EXTRA_REQUEST = "request"

        private const val MIN_ASPECT = 0.5f
        private const val MAX_ASPECT = 2.35f

        fun newIntent(
            context: Context,
            request: PlaybackRequest,
            title: String,
            episodeId: Long,
        ): Intent {
            return Intent(context, AnimePlayerActivity::class.java).apply {
                putExtra(EXTRA_REQUEST, request.encode())
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_EPISODE_ID, episodeId)
            }
        }
    }
}
