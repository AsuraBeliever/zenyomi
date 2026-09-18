package eu.kanade.presentation.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

/**
 * The app's own icon, at the top of More and of About.
 *
 * The launcher's two layers rather than the notification vector: the drawn apple that used to
 * be here is the single-tint mark the status bar needs, and it stopped being what the app
 * looks like the day the icon changed. Both layers are read from the same resources the
 * launcher uses, so the debug build shows its own background here too and the two can never
 * disagree.
 *
 * Composed the way a launcher composes an adaptive icon: the background fills the shape, the
 * foreground is drawn on a 108dp canvas whose middle 72dp is all that is guaranteed to be
 * visible, and the mask is a circle. Hence the 108/72 scale — without it the artwork sits
 * small inside a large field of background.
 */
@Composable
fun LogoHeader(
    iconPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(iconPadding)
                .size(LogoSize)
                .clip(CircleShape),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(ADAPTIVE_ICON_SCALE),
            )
        }

        HorizontalDivider()
    }
}

private val LogoSize = 64.dp

/** The adaptive icon's canvas over its guaranteed-visible area: 108dp to 72dp. */
private const val ADAPTIVE_ICON_SCALE = 108f / 72f
