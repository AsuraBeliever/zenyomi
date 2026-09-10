package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.animelibrary.AnimeLibraryUpdateJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * Schedules the anime library update alongside Mihon's manga one. A separate migration rather
 * than an extra line in [SetupLibraryUpdateMigration] so the manga scheduling keeps working
 * untouched even if this one throws.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class SetupAnimeLibraryUpdateMigration(
    private val context: Context,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        AnimeLibraryUpdateJob.setupTask(context)
        return true
    }
}
