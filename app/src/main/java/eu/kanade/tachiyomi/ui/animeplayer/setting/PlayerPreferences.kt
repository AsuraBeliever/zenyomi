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

    /**
     * How much of an opening the skip button jumps when nothing says where it ends.
     *
     * Eighty-five seconds, which is what a television opening is: the standard cut is 90 s
     * with a few of them spent on the first shots of the episode. Aniyomi settled on the same
     * number. It is a setting because a series that runs a shorter one exists, and because a
     * number that is nearly right is worse than one the viewer chose.
     */
    val skipIntroLength: Preference<Int> = preferenceStore.getInt("pref_player_skip_intro_length", 85)

    /**
     * Whether the end of an episode opens the next one on its own.
     *
     * On, because the alternative is what watching a season used to be: leave the player, find
     * the row below, tap it, wait. The countdown on screen is what makes it safe to default to
     * — it says what is about to happen and takes a tap to stop.
     */
    val autoplayNext: Preference<Boolean> = preferenceStore.getBoolean("pref_player_autoplay_next", true)

    /** Whether starting an episode goes fullscreen with the system bars hidden. */
    val hideSystemBars: Preference<Boolean> = preferenceStore.getBoolean("pref_player_hide_system_bars", true)
}
