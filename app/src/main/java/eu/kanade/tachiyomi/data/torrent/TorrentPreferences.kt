package eu.kanade.tachiyomi.data.torrent

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Settings for playing torrents.
 *
 * The keys are Aniyomi's, so a backup made there brings its torrent settings along. There is
 * no port setting: the add-on picks a free one on 127.0.0.1 each time it starts TorrServer
 * (docs/adr/0008), which cannot collide with anything, where a fixed port can.
 */
@Inject
@SingleIn(AppScope::class)
class TorrentPreferences(
    preferenceStore: PreferenceStore,
) {

    /**
     * Off until the viewer turns it on. A torrent uploads what it downloads to strangers, which
     * is a decision for the person whose connection it is.
     */
    val enabled: Preference<Boolean> = preferenceStore.getBoolean("pref_torrserver_enable", false)

    /** Whether the notice about sharing was shown. App state: it does not travel in backups. */
    val noticeShown: Preference<Boolean> =
        preferenceStore.getBoolean(Preference.appStateKey("pref_torrserver_shownotice"), false)

    /**
     * Trackers added to every torrent, one per line. A magnet often carries none of its own,
     * and without a tracker the only way to find peers is the DHT, which is slow to answer.
     */
    val trackers: Preference<String> = preferenceStore.getString("pref_torrserver_tackers", DEFAULT_TRACKERS)

    companion object {
        val DEFAULT_TRACKERS = listOf(
            "http://nyaa.tracker.wf:7777/announce",
            "http://anidex.moe:6969/announce",
            "http://tracker.anirena.com:80/announce",
            "udp://tracker.uw0.xyz:6969/announce",
            "http://share.camoe.cn:8080/announce",
            "http://t.nyaatracker.com:80/announce",
            "udp://47.ip-51-68-199.eu:6969/announce",
            "udp://9.rarbg.me:2940",
            "udp://9.rarbg.to:2820",
            "udp://exodus.desync.com:6969/announce",
            "udp://explodie.org:6969/announce",
            "udp://ipv4.tracker.harry.lu:80/announce",
            "udp://open.stealth.si:80/announce",
            "udp://opentor.org:2710/announce",
            "udp://opentracker.i2p.rocks:6969/announce",
            "udp://retracker.lanta-net.ru:2710/announce",
            "udp://tracker.cyberia.is:6969/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://tracker.ds.is:6969/announce",
            "udp://tracker.internetwarriors.net:1337",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://valakas.rollo.dnsabr.com:2710/announce",
            "udp://www.torrent.eu.org:451/announce",
        ).joinToString("\n")
    }
}
