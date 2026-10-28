package tachiyomi.domain.library.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

/**
 * Zenyomi's defaults for the automatic library update, which differ from Mihon's on purpose:
 * on, and not skipping the series being followed.
 */
class LibraryPreferencesTest {

    private val preferences = LibraryPreferences(InMemoryPreferenceStore())

    @Test
    fun `Automatic update is on every 12 hours by default`() {
        preferences.autoUpdateInterval.get() shouldBe 12
    }

    @Test
    fun `Automatic update does not skip series with unread or unstarted chapters by default`() {
        preferences.autoUpdateMangaRestrictions.get() shouldBe setOf(
            LibraryPreferences.MANGA_NON_COMPLETED,
            LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD,
        )
    }

    @Test
    fun `An interval the user picked, never included, is kept`() {
        listOf(0, 24).forEach { picked ->
            val store = InMemoryPreferenceStore(
                sequenceOf(InMemoryPreference("pref_library_update_interval_key", picked, 12)),
            )
            LibraryPreferences(store).autoUpdateInterval.get() shouldBe picked
        }
    }
}
