package eu.kanade.tachiyomi.ui.animeextension

import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The number on the "Anime extensions" header.
 *
 * It has to match the rows under it. A fresh install has nothing installed and every
 * extension waiting to be trusted, and the header used to say 0 over a list of 21.
 */
class AnimeExtensionsStateTest {

    @Test
    fun `untrusted extensions are counted`() {
        val state = AnimeExtensionsViewModel.State(untrusted = List(21) { untrusted("u$it") })

        assertEquals(21, state.shownCount)
    }

    @Test
    fun `every section under the header is counted`() {
        val state = AnimeExtensionsViewModel.State(
            installed = listOf(installed("i1"), installed("i2")),
            untrusted = listOf(untrusted("u1")),
            available = listOf(available("a1", "en"), available("a2", "es"), available("a3", "es")),
        )

        assertEquals(6, state.shownCount)
    }

    @Test
    fun `available ones hidden by the language filter are not counted`() {
        val state = AnimeExtensionsViewModel.State(
            untrusted = listOf(untrusted("u1")),
            available = listOf(available("a1", "en"), available("a2", "es")),
            selectedLanguage = "es",
        )

        assertEquals(2, state.shownCount)
    }

    private fun untrusted(pkg: String) = AnimeExtension.Untrusted(
        name = pkg,
        pkgName = pkg,
        versionName = "1.0",
        versionCode = 1,
        libVersion = 16.0,
        signatureHash = "hash",
    )

    private fun installed(pkg: String) = AnimeExtension.Installed(
        name = pkg,
        pkgName = pkg,
        versionName = "1.0",
        versionCode = 1,
        libVersion = 16.0,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = false,
    )

    private fun available(pkg: String, lang: String) = AnimeExtension.Available(
        name = pkg,
        pkgName = pkg,
        versionName = "1.0",
        versionCode = 1,
        libVersion = 16.0,
        lang = lang,
        isNsfw = false,
        sources = emptyList(),
        apkUrl = "",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "",
            name = "",
            badgeLabel = "",
            signingKey = "",
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )
}
