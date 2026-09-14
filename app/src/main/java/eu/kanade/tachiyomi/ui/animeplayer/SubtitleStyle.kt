package eu.kanade.tachiyomi.ui.animeplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitleBorderStyle
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitleJustification
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitlePreferences
import tachiyomi.presentation.core.util.collectAsState

/**
 * Everything mpv needs to know about how subtitles should look, in one value.
 *
 * The player view takes this rather than the preference store so it stays a thing that plays
 * video: the screen reads the settings and decides what they mean — including the shift that
 * keeps the text clear of the seek bar — and hands over the result.
 */
data class SubtitleStyle(
    val font: String = SubtitlePreferences.DEFAULT_FONT,
    val fontSize: Int = 55,
    val scalePercent: Int = 100,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val justification: SubtitleJustification = SubtitleJustification.Auto,
    val textColor: Int = DEFAULT_TEXT_COLOR,
    val borderColor: Int = DEFAULT_BORDER_COLOR,
    val backgroundColor: Int = 0,
    val borderStyle: SubtitleBorderStyle = SubtitleBorderStyle.OutlineAndShadow,
    val borderSize: Int = 3,
    val shadowOffset: Int = 0,
    val position: Int = 100,
    val delayMillis: Int = 0,
    val speedPercent: Int = 100,
    val secondaryDelayMillis: Int = 0,
    val overrideAssStyles: Boolean = false,
) {

    /**
     * The `sub-*` options, ready to hand to mpv.
     *
     * One list used for both the options set before `mpv_initialize` and the properties set
     * while an episode plays, so the two cannot drift: a setting that only took effect on the
     * next episode would look like a setting that does not work.
     */
    fun toMpvOptions(): List<Pair<String, String>> = buildList {
        add("sub-font" to font)
        add("sub-font-size" to fontSize.toString())
        add("sub-scale" to (scalePercent / 100.0).toString())
        add("sub-bold" to bold.toYesNo())
        add("sub-italic" to italic.toYesNo())
        add("sub-justify" to justification.mpvValue)
        add("sub-color" to textColor.toMpvColor())
        add("sub-border-color" to borderColor.toMpvColor())
        add("sub-back-color" to backgroundColor.toMpvColor())
        add("sub-border-style" to borderStyle.mpvValue)
        add("sub-border-size" to borderSize.toString())
        add("sub-shadow-offset" to shadowOffset.toString())
        add("sub-pos" to position.toString())
        add("sub-delay" to (delayMillis / 1000.0).toString())
        add("secondary-sub-delay" to (secondaryDelayMillis / 1000.0).toString())
        add("sub-speed" to (speedPercent / 100.0).toString())
        // A subtitle file can carry its own font, size and colours, and mpv honours them by
        // default — so on exactly the tracks fansubbers produce, everything above would look
        // like it did nothing. "force" is what makes these settings mean something there;
        // "scale" is mpv's own default, which keeps the author's styling but still applies
        // the size.
        add("sub-ass-override" to if (overrideAssStyles) "force" else "scale")
        add("sub-ass-justify" to overrideAssStyles.toYesNo())
    }

    companion object {
        const val DEFAULT_TEXT_COLOR: Int = 0xFFFFFFFF.toInt()
        const val DEFAULT_BORDER_COLOR: Int = 0xFF000000.toInt()
    }
}

/**
 * Reads the stored settings into a [SubtitleStyle] that recomposes when any of them changes.
 *
 * All seventeen collected together rather than passed around individually: the player only
 * ever wants the whole set, and one value means one place where a new setting has to be added
 * for it to reach mpv.
 */
@Composable
fun SubtitlePreferences.collectStyleAsState(): SubtitleStyle {
    val font by font.collectAsState()
    val fontSize by fontSize.collectAsState()
    val scale by scale.collectAsState()
    val bold by bold.collectAsState()
    val italic by italic.collectAsState()
    val justification by justification.collectAsState()
    val textColor by textColor.collectAsState()
    val borderColor by borderColor.collectAsState()
    val backgroundColor by backgroundColor.collectAsState()
    val borderStyle by borderStyle.collectAsState()
    val borderSize by borderSize.collectAsState()
    val shadowOffset by shadowOffset.collectAsState()
    val position by position.collectAsState()
    val delay by delay.collectAsState()
    val speed by speed.collectAsState()
    val secondaryDelay by secondaryDelay.collectAsState()
    val overrideAss by overrideAssStyles.collectAsState()

    return SubtitleStyle(
        font = font,
        fontSize = fontSize,
        scalePercent = scale,
        bold = bold,
        italic = italic,
        justification = justification,
        textColor = textColor,
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        borderStyle = borderStyle,
        borderSize = borderSize,
        shadowOffset = shadowOffset,
        position = position,
        delayMillis = delay,
        speedPercent = speed,
        secondaryDelayMillis = secondaryDelay,
        overrideAssStyles = overrideAss,
    )
}

private fun Boolean.toYesNo() = if (this) "yes" else "no"

/**
 * mpv wants `#AARRGGBB`, which is the order an Android colour int is already packed in — but
 * [Int.toString] would hand it back signed and decimal, and a short hex string would be read
 * as opaque. Padded to eight digits so a transparent background stays transparent.
 */
private fun Int.toMpvColor(): String = "#%08X".format(this)
