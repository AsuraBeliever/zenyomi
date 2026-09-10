package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Watch history for one episode, keyed by the episode url because ids are local to a device.
 */
@Serializable
data class BackupAnimeHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastSeen: Long,
)
