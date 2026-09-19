package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.animeplayer.ZenyomiMPVView
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitleBorderStyle
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitleJustification
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitlePreferences
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Settings for the video player.
 *
 * Sits next to Mihon's reader settings rather than inside them: a manga reader has no use for
 * a seek step, and the charter keeps the two trees apart.
 */
private val speedFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))

object SettingsPlayerScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ANMR.strings.pref_category_player

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val playerPref = remember { context.appGraph.playerPreferences }
        val subtitlePref = remember { context.appGraph.subtitlePreferences }

        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = playerPref.seekStep,
                entries = listOf(5, 10, 15, 30, 60).associateWith { seconds ->
                    stringResource(ANMR.strings.player_seconds, seconds)
                },
                title = stringResource(ANMR.strings.pref_player_seek_step),
                subtitle = stringResource(ANMR.strings.pref_player_seek_step_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = playerPref.seenThreshold,
                entries = listOf(70, 80, 85, 90, 95, 100).associateWith { percent -> "$percent%" },
                title = stringResource(ANMR.strings.pref_player_seen_threshold),
                subtitle = stringResource(ANMR.strings.pref_player_seen_threshold_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = playerPref.defaultSpeed,
                // Integer maths would render 75 as "0.7x" and lose the quarter step.
                entries = listOf(50, 75, 100, 125, 150, 175, 200).associateWith { percent ->
                    speedFormat.format(percent / 100.0) + "x"
                },
                title = stringResource(ANMR.strings.pref_player_default_speed),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = playerPref.preferredAudioLanguages,
                title = stringResource(ANMR.strings.pref_player_audio_languages),
                subtitle = stringResource(ANMR.strings.pref_player_languages_summary),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = playerPref.preferredSubtitleLanguages,
                title = stringResource(ANMR.strings.pref_player_subtitle_languages),
                subtitle = stringResource(ANMR.strings.pref_player_languages_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = playerPref.aniskipEnabled,
                title = stringResource(ANMR.strings.pref_enable_aniskip),
                subtitle = stringResource(ANMR.strings.pref_player_aniskip_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = playerPref.autoSkipIntro,
                title = stringResource(ANMR.strings.pref_enable_auto_skip_ani_skip),
                subtitle = stringResource(ANMR.strings.pref_player_auto_skip_intro_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = playerPref.skipIntroLength,
                entries = listOf(60, 70, 80, 85, 90, 120).associateWith { seconds ->
                    stringResource(ANMR.strings.player_seconds, seconds)
                },
                title = stringResource(ANMR.strings.pref_player_skip_intro_length),
                subtitle = stringResource(ANMR.strings.pref_player_skip_intro_length_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = playerPref.autoplayNext,
                title = stringResource(ANMR.strings.pref_player_autoplay_next),
                subtitle = stringResource(ANMR.strings.pref_player_autoplay_next_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = playerPref.hideSystemBars,
                title = stringResource(ANMR.strings.pref_player_fullscreen),
            ),
            // The same values the in-player panel writes. Here for the person who wants to set
            // them up once and be done, there for the person who wants to see the text change
            // over the scene they are watching — which is the only way to answer "is this big
            // enough". One store behind both, so neither can be the stale one.
            subtitleAppearanceGroup(subtitlePref),
        )
    }
}

/**
 * How subtitles are drawn, as a group in the settings list.
 *
 * The same preferences the in-player panel writes, so whichever one you reach for, the other
 * already agrees. Sliders here rather than text boxes for the numbers that have a sensible
 * range and no meaningful value outside it: a border of 400 is not a setting, it is a typo.
 */
@Composable
private fun subtitleAppearanceGroup(pref: SubtitlePreferences): Preference.PreferenceGroup {
    val fonts = remember {
        ZenyomiMPVView.availableSubtitleFonts().ifEmpty { listOf(SubtitlePreferences.DEFAULT_FONT) }
    }
    val fontSize by pref.fontSize.collectAsState()
    val scale by pref.scale.collectAsState()
    val borderSize by pref.borderSize.collectAsState()
    val shadowOffset by pref.shadowOffset.collectAsState()
    val position by pref.position.collectAsState()

    return Preference.PreferenceGroup(
        title = stringResource(ANMR.strings.pref_player_subtitle_appearance),
        preferenceItems = listOf(
            Preference.PreferenceItem.ListPreference(
                preference = pref.font,
                entries = fonts.associateWith { it },
                title = stringResource(ANMR.strings.player_sheets_sub_typography_font),
            ),
            Preference.PreferenceItem.SliderPreference(
                value = fontSize,
                valueRange = 10..125,
                title = stringResource(ANMR.strings.player_sheets_sub_typography_font_size),
                valueString = "$fontSize",
                onValueChanged = {
                    pref.fontSize.set(it)
                    true
                },
            ),
            Preference.PreferenceItem.SliderPreference(
                value = scale,
                valueRange = 25..400,
                title = stringResource(ANMR.strings.player_sheets_sub_scale),
                valueString = stringResource(ANMR.strings.player_percent, scale),
                onValueChanged = {
                    pref.scale.set(it)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = pref.bold,
                title = stringResource(ANMR.strings.player_sheets_sub_bold),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = pref.italic,
                title = stringResource(ANMR.strings.player_sheets_sub_italic),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = pref.justification,
                entries = mapOf(
                    SubtitleJustification.Auto to stringResource(ANMR.strings.player_sheets_sub_justify_auto),
                    SubtitleJustification.Left to stringResource(ANMR.strings.player_sheets_sub_justify_left),
                    SubtitleJustification.Center to stringResource(ANMR.strings.player_sheets_sub_justify_center),
                    SubtitleJustification.Right to stringResource(ANMR.strings.player_sheets_sub_justify_right),
                ),
                title = stringResource(ANMR.strings.player_sheets_sub_justify),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = pref.borderStyle,
                entries = mapOf(
                    SubtitleBorderStyle.OutlineAndShadow to
                        stringResource(ANMR.strings.player_sheets_subtitles_border_style_outline_and_shadow),
                    SubtitleBorderStyle.OpaqueBox to
                        stringResource(ANMR.strings.player_sheets_subtitles_border_style_opaque_box),
                    SubtitleBorderStyle.BackgroundBox to
                        stringResource(ANMR.strings.player_sheets_subtitles_border_style_background_box),
                ),
                title = stringResource(ANMR.strings.player_sheets_sub_typography_border_style),
            ),
            Preference.PreferenceItem.SliderPreference(
                value = borderSize,
                valueRange = 0..10,
                title = stringResource(ANMR.strings.player_sheets_sub_typography_border_size),
                valueString = "$borderSize",
                onValueChanged = {
                    pref.borderSize.set(it)
                    true
                },
            ),
            Preference.PreferenceItem.SliderPreference(
                value = shadowOffset,
                valueRange = 0..10,
                title = stringResource(ANMR.strings.player_sheets_subtitles_shadow_offset),
                valueString = "$shadowOffset",
                onValueChanged = {
                    pref.shadowOffset.set(it)
                    true
                },
            ),
            Preference.PreferenceItem.SliderPreference(
                value = position,
                valueRange = 0..150,
                title = stringResource(ANMR.strings.player_sheets_sub_position),
                subtitle = stringResource(ANMR.strings.player_sheets_sub_position_summary),
                valueString = "$position",
                onValueChanged = {
                    pref.position.set(it)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = pref.overrideAssStyles,
                title = stringResource(ANMR.strings.player_sheets_sub_override_ass),
                subtitle = stringResource(ANMR.strings.player_sheets_sub_override_ass_summary),
            ),
            // The colours are not here. Four sliders each, and the only way to judge the
            // result is against a picture — so they live in the player's own panel, which has
            // one behind it.
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(ANMR.strings.pref_player_subtitle_colours_note),
            ),
        ),
    )
}
