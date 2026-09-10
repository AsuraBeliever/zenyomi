package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource
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
                preference = playerPref.hideSystemBars,
                title = stringResource(ANMR.strings.pref_player_fullscreen),
            ),
        )
    }
}
