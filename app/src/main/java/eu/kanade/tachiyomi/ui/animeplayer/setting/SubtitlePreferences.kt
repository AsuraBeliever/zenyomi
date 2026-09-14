package eu.kanade.tachiyomi.ui.animeplayer.setting

import android.graphics.Color
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * How subtitles are drawn.
 *
 * Separate from [PlayerPreferences] because it is a different question: that one is about how
 * the player behaves, this one is about how the text looks, and the two are read by different
 * screens. It is also the shape Aniyomi settled on, which keeps the port honest.
 *
 * Everything here maps to one of mpv's `sub-*` options. The names are ours; the values are
 * whatever mpv expects, converted at the point of use rather than stored pre-formatted, so a
 * backup stays readable.
 */
@Inject
@SingleIn(AppScope::class)
class SubtitlePreferences(
    private val preferenceStore: PreferenceStore,
) {

    // Typography

    /** Font family name. Resolved against the fonts mpv knows about; falls back to its own. */
    val font: Preference<String> = preferenceStore.getString("pref_subtitle_font", DEFAULT_FONT)

    /** Font size in mpv's units, which are relative to a 720-pixel-tall video. */
    val fontSize: Preference<Int> = preferenceStore.getInt("pref_subtitle_font_size", 55)

    /**
     * A multiplier on top of [fontSize], as a percentage.
     *
     * Percent rather than a float for the same reason the playback speed is: a float
     * preference turns up in a backup as 1.2000000476837158.
     */
    val scale: Preference<Int> = preferenceStore.getInt("pref_subtitle_scale", 100)

    val bold: Preference<Boolean> = preferenceStore.getBoolean("pref_subtitle_bold", false)

    val italic: Preference<Boolean> = preferenceStore.getBoolean("pref_subtitle_italic", false)

    val justification: Preference<SubtitleJustification> =
        preferenceStore.getEnum("pref_subtitle_justify", SubtitleJustification.Auto)

    // Colours, stored as packed ARGB ints.

    val textColor: Preference<Int> = preferenceStore.getInt("pref_subtitle_text_color", Color.WHITE)

    val borderColor: Preference<Int> = preferenceStore.getInt("pref_subtitle_border_color", Color.BLACK)

    val backgroundColor: Preference<Int> =
        preferenceStore.getInt("pref_subtitle_background_color", Color.TRANSPARENT)

    // Border and shadow

    val borderStyle: Preference<SubtitleBorderStyle> =
        preferenceStore.getEnum("pref_subtitle_border_style", SubtitleBorderStyle.OutlineAndShadow)

    val borderSize: Preference<Int> = preferenceStore.getInt("pref_subtitle_border_size", 3)

    val shadowOffset: Preference<Int> = preferenceStore.getInt("pref_subtitle_shadow_offset", 0)

    // Placement

    /**
     * Vertical position: mpv's `sub-pos`, where 100 sits the text on the bottom edge of the
     * video and lower numbers lift it. It goes past 100 so subtitles can be pushed into the
     * black band below a letterboxed video.
     */
    val position: Preference<Int> = preferenceStore.getInt("pref_subtitle_position", 100)

    // Timing

    /** Milliseconds the subtitles run ahead of, or behind, the picture. */
    val delay: Preference<Int> = preferenceStore.getInt("pref_subtitle_delay", 0)

    /** Playback rate of the subtitle track as a percentage, for a track timed to another cut. */
    val speed: Preference<Int> = preferenceStore.getInt("pref_subtitle_speed", 100)

    val secondaryDelay: Preference<Int> = preferenceStore.getInt("pref_subtitle_secondary_delay", 0)

    // Behaviour

    /**
     * Whether these settings win over a subtitle file that carries its own styling.
     *
     * An ASS track states its own font, size and colours, and by default mpv honours them —
     * so without this, changing anything above appears to do nothing on exactly the tracks
     * fansubbers produce.
     */
    val overrideAssStyles: Preference<Boolean> =
        preferenceStore.getBoolean("pref_subtitle_override_ass", false)

    /** Puts every value back to what it ships as, in one go. */
    fun reset() {
        listOf(
            font, fontSize, scale, bold, italic, justification,
            textColor, borderColor, backgroundColor,
            borderStyle, borderSize, shadowOffset,
            position, delay, speed, secondaryDelay, overrideAssStyles,
        ).forEach(Preference<*>::delete)
    }

    companion object {
        /** The face bundled with the mpv library, and the only one that covers CJK. */
        const val DEFAULT_FONT = "Droid Sans Fallback"
    }
}

/** Where a line sits within the subtitle block when it wraps onto more than one line. */
enum class SubtitleJustification(val mpvValue: String) {
    Auto("auto"),
    Left("left"),
    Center("center"),
    Right("right"),
}

/** How the text is separated from the picture behind it. */
enum class SubtitleBorderStyle(val mpvValue: String) {
    /** An outline around each glyph, plus a drop shadow. mpv's default, and the readable one. */
    OutlineAndShadow("outline-and-shadow"),

    /** A filled box behind the text, sized to the line. */
    OpaqueBox("opaque-box"),

    /** A filled box behind the whole subtitle block rather than per line. */
    BackgroundBox("background-box"),
}
