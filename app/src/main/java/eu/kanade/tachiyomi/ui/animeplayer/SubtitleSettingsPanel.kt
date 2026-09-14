package eu.kanade.tachiyomi.ui.animeplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitleBorderStyle
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitleJustification
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitlePreferences
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Everything about how subtitles look, over the episode they apply to.
 *
 * In the player rather than only in Settings because the question — is this big enough, is
 * this readable over this scene — cannot be answered anywhere else. Every control writes
 * straight to the stored preference, which the player is already watching, so a change is on
 * the picture behind the panel by the time the finger leaves the slider, and is still there
 * for the next episode.
 *
 * Drawn in place rather than in a ModalBottomSheet for the same reason the track picker is: a
 * popup window over mpv's SurfaceView did not open at all here.
 */
@Composable
fun SubtitleSettingsPanel(
    preferences: SubtitlePreferences,
    availableFonts: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Beside the picture in landscape, under it in portrait — in both cases off the video, so
    // the subtitles being adjusted stay in sight. A fixed side panel in portrait covered three
    // quarters of a phone screen and most of the picture with it, which defeats the point of
    // putting the settings in the player at all.
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    Surface(
        color = Color.Black.copy(alpha = 0.92f),
        shape = if (landscape) {
            RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        },
        modifier = if (landscape) {
            modifier.fillMaxHeight().width(PANEL_WIDTH)
        } else {
            modifier.fillMaxWidth().fillMaxHeight(PORTRAIT_PANEL_HEIGHT_FRACTION)
        },
    ) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(ANMR.strings.player_sheets_subtitles_settings_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                        tint = Color.White,
                    )
                }
            }

            Section(stringResource(ANMR.strings.player_sheets_sub_typography_title))

            val font by preferences.font.collectAsState()
            ChoiceRow(
                label = stringResource(ANMR.strings.player_sheets_sub_typography_font),
                options = availableFonts.associateWith { it },
                selected = font,
                onSelect = preferences.font::set,
            )

            val fontSize by preferences.fontSize.collectAsState()
            SliderRow(
                label = stringResource(ANMR.strings.player_sheets_sub_typography_font_size),
                value = fontSize,
                range = 10f..125f,
                valueLabel = "$fontSize",
                onChange = preferences.fontSize::set,
            )

            val scale by preferences.scale.collectAsState()
            SliderRow(
                label = stringResource(ANMR.strings.player_sheets_sub_scale),
                value = scale,
                range = 25f..400f,
                valueLabel = stringResource(ANMR.strings.player_percent, scale),
                onChange = preferences.scale::set,
            )

            val bold by preferences.bold.collectAsState()
            SwitchRow(stringResource(ANMR.strings.player_sheets_sub_bold), bold, preferences.bold::set)

            val italic by preferences.italic.collectAsState()
            SwitchRow(stringResource(ANMR.strings.player_sheets_sub_italic), italic, preferences.italic::set)

            val justification by preferences.justification.collectAsState()
            ChoiceRow(
                label = stringResource(ANMR.strings.player_sheets_sub_justify),
                options = justificationLabels(),
                selected = justification,
                onSelect = preferences.justification::set,
            )

            Section(stringResource(ANMR.strings.player_sheets_sub_colors_title))

            val textColor by preferences.textColor.collectAsState()
            ColorRow(
                label = stringResource(ANMR.strings.player_sheets_subtitles_color_text),
                argb = textColor,
                onChange = preferences.textColor::set,
            )

            val borderColor by preferences.borderColor.collectAsState()
            ColorRow(
                label = stringResource(ANMR.strings.player_sheets_subtitles_color_border),
                argb = borderColor,
                onChange = preferences.borderColor::set,
            )

            val backgroundColor by preferences.backgroundColor.collectAsState()
            ColorRow(
                label = stringResource(ANMR.strings.player_sheets_subtitles_color_background),
                argb = backgroundColor,
                onChange = preferences.backgroundColor::set,
            )

            val borderStyle by preferences.borderStyle.collectAsState()
            ChoiceRow(
                label = stringResource(ANMR.strings.player_sheets_sub_typography_border_style),
                options = borderStyleLabels(),
                selected = borderStyle,
                onSelect = preferences.borderStyle::set,
            )

            val borderSize by preferences.borderSize.collectAsState()
            SliderRow(
                label = stringResource(ANMR.strings.player_sheets_sub_typography_border_size),
                value = borderSize,
                range = 0f..10f,
                valueLabel = "$borderSize",
                onChange = preferences.borderSize::set,
            )

            val shadowOffset by preferences.shadowOffset.collectAsState()
            SliderRow(
                label = stringResource(ANMR.strings.player_sheets_subtitles_shadow_offset),
                value = shadowOffset,
                range = 0f..10f,
                valueLabel = "$shadowOffset",
                onChange = preferences.shadowOffset::set,
            )

            Section(stringResource(ANMR.strings.player_sheets_sub_misc_title))

            val position by preferences.position.collectAsState()
            SliderRow(
                label = stringResource(ANMR.strings.player_sheets_sub_position),
                value = position,
                range = 0f..150f,
                valueLabel = "$position",
                onChange = preferences.position::set,
            )

            val delay by preferences.delay.collectAsState()
            SliderRow(
                label = stringResource(ANMR.strings.player_sheets_sub_delay_delay),
                value = delay,
                range = -10_000f..10_000f,
                valueLabel = stringResource(ANMR.strings.player_milliseconds, delay),
                onChange = preferences.delay::set,
            )

            val speed by preferences.speed.collectAsState()
            SliderRow(
                label = stringResource(ANMR.strings.player_sheets_sub_delay_speed),
                value = speed,
                range = 25f..400f,
                valueLabel = stringResource(ANMR.strings.player_percent, speed),
                onChange = preferences.speed::set,
            )

            val overrideAss by preferences.overrideAssStyles.collectAsState()
            SwitchRow(
                label = stringResource(ANMR.strings.player_sheets_sub_override_ass),
                checked = overrideAss,
                onChange = preferences.overrideAssStyles::set,
                subtitle = stringResource(ANMR.strings.player_sheets_sub_override_ass_summary),
            )

            TextButton(onClick = preferences::reset, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(ANMR.strings.player_sheets_sub_reset), color = Color.White)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

/**
 * A slider that reports whole numbers.
 *
 * Every one of these settings is an integer to mpv — a pixel size, a percentage, a number of
 * milliseconds — so the float the slider works in is rounded before it goes anywhere. Writing
 * on every change rather than on release is deliberate: seeing the text resize under your
 * finger is the whole reason this panel is in the player.
 */
@Composable
private fun SliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(valueLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat().coerceIn(range),
            valueRange = range,
            onValueChange = { onChange(it.toInt()) },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(uncheckedTrackColor = Color.White.copy(alpha = 0.2f)),
        )
    }
}

/** A row of chips, one of which is on. Used where the choice is short and worth seeing at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: Map<T, String>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        // Flowed rather than chunked into fixed rows: the labels range from "Left" to
        // "Background box", and any fixed number per row truncates one of them.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Text(
                    text = text,
                    color = if (isSelected) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.15f))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * A colour, as a swatch that opens four sliders.
 *
 * Alpha included and listed first, because it is the one that does something the others
 * cannot: a background is only useful at all if it can be turned off, and the way to turn it
 * off is to make it transparent.
 */
@Composable
private fun ColorRow(label: String, argb: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
        ) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // On a chequered ground so a transparent colour reads as transparent rather than
            // as whatever happens to be behind the panel.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .background(Color(argb)),
            )
        }
        if (expanded) {
            ColorChannel(stringResource(ANMR.strings.player_sheets_sub_color_alpha), argb ushr 24) {
                onChange((argb and 0x00FFFFFF) or (it shl 24))
            }
            ColorChannel(stringResource(ANMR.strings.player_sheets_sub_color_red), (argb shr 16) and 0xFF) {
                onChange((argb and 0xFF00FFFF.toInt()) or (it shl 16))
            }
            ColorChannel(stringResource(ANMR.strings.player_sheets_sub_color_green), (argb shr 8) and 0xFF) {
                onChange((argb and 0xFFFF00FF.toInt()) or (it shl 8))
            }
            ColorChannel(stringResource(ANMR.strings.player_sheets_sub_color_blue), argb and 0xFF) {
                onChange((argb and 0xFFFFFF00.toInt()) or it)
            }
        }
    }
}

@Composable
private fun ColorChannel(label: String, value: Int, onChange: (Int) -> Unit) {
    SliderRow(label = label, value = value, range = 0f..255f, valueLabel = "$value", onChange = onChange)
}

@Composable
private fun justificationLabels(): Map<SubtitleJustification, String> = linkedMapOf(
    SubtitleJustification.Auto to stringResource(ANMR.strings.player_sheets_sub_justify_auto),
    SubtitleJustification.Left to stringResource(ANMR.strings.player_sheets_sub_justify_left),
    SubtitleJustification.Center to stringResource(ANMR.strings.player_sheets_sub_justify_center),
    SubtitleJustification.Right to stringResource(ANMR.strings.player_sheets_sub_justify_right),
)

@Composable
private fun borderStyleLabels(): Map<SubtitleBorderStyle, String> = linkedMapOf(
    SubtitleBorderStyle.OutlineAndShadow to
        stringResource(ANMR.strings.player_sheets_subtitles_border_style_outline_and_shadow),
    SubtitleBorderStyle.OpaqueBox to
        stringResource(ANMR.strings.player_sheets_subtitles_border_style_opaque_box),
    SubtitleBorderStyle.BackgroundBox to
        stringResource(ANMR.strings.player_sheets_subtitles_border_style_background_box),
)

/**
 * Wide enough for a slider to be worth dragging, narrow enough to leave the subtitles visible
 * beside it — which is the only reason to put this in the player at all.
 */
private val PANEL_WIDTH = 320.dp

/**
 * How much of a portrait screen the panel takes from the bottom.
 *
 * The video sits as a band across the middle, so a little over a third leaves the whole
 * picture — and the subtitles on it — above the panel.
 */
private const val PORTRAIT_PANEL_HEIGHT_FRACTION = 0.38f
