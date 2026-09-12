package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensionStores: List<BackupExtensionStore> = emptyList(),
    // Zenyomi values start at 200, far above anything Mihon is likely to claim, so a backup
    // written here stays readable by Mihon (it skips fields it does not know) and a Mihon
    // backup restores here with these simply left empty.
    @ProtoNumber(200) var backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(201) var backupAnimeSources: List<BackupSource> = emptyList(),
    @ProtoNumber(202) var backupAnimeCategories: List<BackupCategory> = emptyList(),
)
