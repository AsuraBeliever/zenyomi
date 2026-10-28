package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR

/**
 * The "smart update" restrictions are applied by the automatic update only; a pull to refresh
 * runs with none, and then the only thing left out is an entry set to be fetched once.
 */
class LibraryUpdateJobTest {

    private val mihonDefaults = setOf(
        LibraryPreferences.MANGA_HAS_UNREAD,
        LibraryPreferences.MANGA_NON_COMPLETED,
        LibraryPreferences.MANGA_NON_READ,
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD,
    )

    private fun item(manga: Manga = Manga.create(), total: Long = 10, read: Long = 0) = LibraryManga(
        manga = manga,
        categories = emptyList(),
        totalChapters = total,
        readCount = read,
        bookmarkCount = 0,
        latestUpload = 0,
        chapterFetchedAt = 0,
        lastRead = 0,
    )

    // Being followed (some read), with unread chapters left, completed, and not due yet.
    private val followed = item(read = 3)
    private val notStarted = item(read = 0)
    private val completed = item(manga = Manga.create().copy(status = SManga.COMPLETED.toLong()), read = 10)
    private val notDue = item(manga = Manga.create().copy(nextUpdate = 2_000L), read = 10)
    private val upperBound = 1_000L

    @Test
    fun `Manual refresh skips nothing`() {
        listOf(followed, notStarted, completed, notDue).forEach {
            LibraryUpdateJob.skipReason(it, emptySet(), upperBound) shouldBe null
        }
    }

    @Test
    fun `Automatic update keeps Mihon's restrictions`() {
        LibraryUpdateJob.skipReason(followed, mihonDefaults, upperBound) shouldBe
            MR.strings.skipped_reason_not_caught_up
        LibraryUpdateJob.skipReason(notStarted, mihonDefaults, upperBound) shouldBe
            MR.strings.skipped_reason_not_caught_up
        LibraryUpdateJob.skipReason(completed, mihonDefaults, upperBound) shouldBe
            MR.strings.skipped_reason_completed
        LibraryUpdateJob.skipReason(notDue, mihonDefaults, upperBound) shouldBe
            MR.strings.skipped_reason_not_in_release_period
    }

    @Test
    fun `Fetch-once entries are skipped even on a manual refresh`() {
        val once = item(manga = Manga.create().copy(updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE))
        LibraryUpdateJob.skipReason(once, emptySet(), upperBound) shouldBe MR.strings.skipped_reason_not_always_update
    }
}
