package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.data.torrent.TorrentAddon
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.torrent.TorrentTrackers
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * The torrent part of the player settings.
 *
 * Aniyomi's screen, less two settings that do not apply here: the port, because the add-on
 * picks a free one on 127.0.0.1 every time, and the proxy, because the stock TorrServer the
 * add-on runs takes none from outside. What is added is what Aniyomi does not need: whether the
 * add-on is there, installing or updating it from here, and a way to clear what an add-on
 * stopped mid-episode left behind.
 *
 * Everything applies on the next torrent opened, with no restart: the engine reads these
 * preferences each time it opens one.
 */
@Composable
internal fun torrentGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.appGraph.torrentPreferences }
    val addon = remember { context.appGraph.torrentAddon }
    val engine = remember { context.appGraph.torrentEngine }
    val installer = remember { context.appGraph.torrentAddonInstaller }

    val enabled by preferences.enabled.collectAsState()
    val trackers by preferences.trackers.collectAsState()
    val installStep by installer.step.collectAsState()
    // Read again when the switch moves and when an install moves on: a finished one changes it.
    val addonState = remember(enabled, installStep) { addon.state() }
    // Whether there is an add-on to install or update over what is there. One signed by someone
    // else cannot be replaced: Android refuses an update with a different key.
    val addonWanted = addonState == TorrentAddon.State.NotInstalled ||
        (addonState is TorrentAddon.State.Installed && addonState.isOutdated)
    val canInstall = installer.downloadUrl != null &&
        installStep != InstallStep.Downloading &&
        installStep != InstallStep.Installing &&
        installStep != InstallStep.Pending

    val startInstall = {
        if (!installer.install()) context.toast(ANMR.strings.pref_player_torrents_addon_unavailable)
    }

    var installOffered by rememberSaveable { mutableStateOf(false) }
    if (installOffered) {
        val outdated = addonState as? TorrentAddon.State.Installed
        AlertDialog(
            onDismissRequest = { installOffered = false },
            title = {
                Text(
                    stringResource(
                        if (outdated != null) {
                            ANMR.strings.pref_player_torrents_addon_update_title
                        } else {
                            ANMR.strings.pref_player_torrents_addon_install_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    if (outdated != null) {
                        stringResource(ANMR.strings.pref_player_torrents_addon_update_text, outdated.versionName)
                    } else {
                        stringResource(ANMR.strings.pref_player_torrents_addon_install_text)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        installOffered = false
                        startInstall()
                    },
                ) {
                    Text(
                        stringResource(
                            if (outdated != null) MR.strings.ext_update else MR.strings.ext_install,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { installOffered = false }) {
                    Text(stringResource(ANMR.strings.pref_player_torrents_addon_not_now))
                }
            },
        )
    }

    var noticeShown by rememberSaveable { mutableStateOf(false) }
    if (noticeShown) {
        // The install offer waits for the notice to be read, rather than covering it.
        val closeNotice = {
            noticeShown = false
            if (addonWanted && canInstall) installOffered = true
        }
        AlertDialog(
            onDismissRequest = closeNotice,
            title = { Text(stringResource(ANMR.strings.pref_player_torrents_notice)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                    Text(stringResource(ANMR.strings.pref_player_torrents_notice_text))
                    Text(stringResource(ANMR.strings.pref_player_torrents_notice_footer))
                }
            },
            confirmButton = {
                TextButton(onClick = closeNotice) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
        )
    }

    val items = buildList<Preference.PreferenceItem<out Any, out Any>> {
        add(
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.enabled,
                title = stringResource(ANMR.strings.pref_player_torrents_enable),
                subtitle = stringResource(ANMR.strings.pref_player_torrents_enable_summary),
                onValueChanged = { on ->
                    if (on && !preferences.noticeShown.get()) {
                        noticeShown = true
                        preferences.noticeShown.set(true)
                    } else if (on && addonWanted && canInstall) {
                        installOffered = true
                    }
                    if (!on) engine.stopWhenUnused()
                    true
                },
            ),
        )
        add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(ANMR.strings.pref_player_torrents_addon),
                subtitle = when {
                    installStep == InstallStep.Pending || installStep == InstallStep.Downloading ->
                        stringResource(ANMR.strings.pref_player_torrents_addon_downloading)
                    installStep == InstallStep.Installing ->
                        stringResource(ANMR.strings.pref_player_torrents_addon_installing)
                    installStep == InstallStep.Error && addonWanted ->
                        stringResource(ANMR.strings.pref_player_torrents_addon_failed)
                    addonState is TorrentAddon.State.Installed && addonState.isOutdated ->
                        stringResource(ANMR.strings.pref_player_torrents_addon_outdated, addonState.versionName)
                    addonState is TorrentAddon.State.Installed ->
                        stringResource(ANMR.strings.pref_player_torrents_addon_installed, addonState.versionName)
                    addonState == TorrentAddon.State.NotInstalled && installer.downloadUrl != null ->
                        stringResource(ANMR.strings.pref_player_torrents_addon_missing_install)
                    addonState == TorrentAddon.State.NotInstalled ->
                        stringResource(ANMR.strings.pref_player_torrents_addon_missing)
                    else -> stringResource(ANMR.strings.pref_player_torrents_addon_untrusted)
                },
                enabled = enabled,
                onClick = { if (addonWanted && canInstall) startInstall() },
            ),
        )
        // A custom item is shown whatever its enabled says, so it is left out instead.
        if (enabled) {
            add(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(ANMR.strings.pref_player_torrents_trackers),
                ) {
                    TrackersPreference(
                        value = trackers,
                        onConfirm = { preferences.trackers.set(it) },
                    )
                },
            )
        }
        add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(ANMR.strings.pref_player_torrents_trackers_reset),
                enabled = enabled && trackers != preferences.trackers.defaultValue(),
                onClick = { preferences.trackers.delete() },
            ),
        )
        add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(ANMR.strings.pref_player_torrents_clear_cache),
                subtitle = stringResource(ANMR.strings.pref_player_torrents_clear_cache_summary),
                enabled = enabled && addonState is TorrentAddon.State.Installed,
                onClick = {
                    scope.launch {
                        try {
                            val removed = engine.clearCache()
                            context.toast(
                                context.stringResource(ANMR.strings.pref_player_torrents_cache_cleared, removed),
                            )
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Could not clear TorrServer's cache" }
                            context.toast(ANMR.strings.pref_player_torrents_cache_failed)
                        }
                    }
                },
            ),
        )
    }

    return Preference.PreferenceGroup(
        title = stringResource(ANMR.strings.pref_player_torrents),
        preferenceItems = items,
    )
}

/**
 * The tracker list, edited as text. Mihon's text preference is one line and refuses an empty
 * value; a tracker list is many lines, and empty is a valid choice — no trackers of our own.
 */
@Composable
private fun TrackersPreference(value: String, onConfirm: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val count = remember(value) { TorrentTrackers.parse(value).size }

    TextPreferenceWidget(
        title = stringResource(ANMR.strings.pref_player_torrents_trackers),
        subtitle = stringResource(ANMR.strings.pref_player_torrents_trackers_summary, count),
        onPreferenceClick = { editing = true },
    )

    if (editing) {
        var text by rememberSaveable { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(ANMR.strings.pref_player_torrents_trackers)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(ANMR.strings.pref_player_torrents_trackers_hint)) },
                    // Capped: the default list is 26 lines, and a field that tall pushes OK under
                    // the keyboard. Past the cap it scrolls inside.
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Stored as the viewer typed it, minus the blank lines; whatever is not
                        // a tracker url is kept too, so a typo can be seen and fixed.
                        onConfirm(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
                        editing = false
                    },
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
