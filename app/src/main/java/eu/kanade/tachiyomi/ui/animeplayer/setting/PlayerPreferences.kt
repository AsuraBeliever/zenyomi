package eu.kanade.tachiyomi.ui.animeplayer.setting

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Settings for the video player.
 *
 * Its own class rather than an addition to [eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences]:
 * nothing here means anything to a manga reader, and keeping Mihon's file untouched is what
 * keeps upstream merges cheap.
 */
@Inject
@SingleIn(AppScope::class)
class PlayerPreferences(
    preferenceStore: PreferenceStore,
) {

    /** Seconds a double tap jumps. */
    val seekStep: Preference<Int> = preferenceStore.getInt("pref_player_seek_step", 10)

    /**
     * Percentage of an episode that counts as watched.
     *
     * Stored as an int because a float preference would show up in backups as an
     * unhelpfully precise 0.8500000238418579.
     */
    val seenThreshold: Preference<Int> = preferenceStore.getInt("pref_player_seen_threshold", 85)

    /** Playback speed as a percentage, so 100 is normal. */
    val defaultSpeed: Preference<Int> = preferenceStore.getInt("pref_player_default_speed", 100)

    /**
     * Preferred audio and subtitle languages, as mpv understands them: a comma separated list
     * of codes in order of preference, empty to let mpv decide.
     */
    val preferredAudioLanguages: Preference<String> = preferenceStore.getString("pref_player_audio_langs", "")

    val preferredSubtitleLanguages: Preference<String> = preferenceStore.getString("pref_player_sub_langs", "")

    /** Whether starting an episode goes fullscreen with the system bars hidden. */
    val hideSystemBars: Preference<Boolean> = preferenceStore.getBoolean("pref_player_hide_system_bars", true)
}
