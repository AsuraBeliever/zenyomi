package tachiyomi.data.release

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReleaseServiceImplTest {

    // The assets of the v0.20.0 release, in the order GitHub lists them.
    private val assets = listOf(
        "zenyomi-arm64-v8a-v0.20.0.apk",
        "zenyomi-armeabi-v7a-v0.20.0.apk",
        "zenyomi-torrent-arm64-v8a-v0.20.0.apk",
        "zenyomi-torrent-armeabi-v7a-v0.20.0.apk",
        "zenyomi-torrent-x86-v0.20.0.apk",
        "zenyomi-torrent-x86_64-v0.20.0.apk",
        "zenyomi-v0.20.0-foss.apk",
        "zenyomi-v0.20.0.apk",
        "zenyomi-x86-v0.20.0.apk",
        "zenyomi-x86_64-v0.20.0.apk",
    ).map { it to "https://example.com/$it" }

    private fun pick(abi: String, isFoss: Boolean = false) =
        ReleaseServiceImpl.pickDownloadLink(assets, isFoss, abi)?.substringAfterLast('/')

    @Test
    fun `Picks the app APK for each ABI, never the torrent add-on`() {
        listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").forEach { abi ->
            pick(abi) shouldBe "zenyomi-$abi-v0.20.0.apk"
        }
    }

    @Test
    fun `Skips the add-on under its 0_21_0 name too`() {
        // With an underscore the add-on never competes for an ABI, only for the universal slot,
        // and listed after the universal APK it would take it.
        val renamed = listOf(
            "zenyomi-v0.21.0.apk",
            "zenyomi-arm64-v8a-v0.21.0.apk",
            "zenyomi-torrent_arm64-v8a-v0.21.0.apk",
            "zenyomi-torrent_x86_64-v0.21.0.apk",
        ).map { it to "https://example.com/$it" }
        ReleaseServiceImpl.pickDownloadLink(renamed, isFoss = false, abi = "riscv64")
            ?.substringAfterLast('/') shouldBe "zenyomi-v0.21.0.apk"
        ReleaseServiceImpl.pickDownloadLink(renamed, isFoss = false, abi = "arm64-v8a")
            ?.substringAfterLast('/') shouldBe "zenyomi-arm64-v8a-v0.21.0.apk"
    }

    @Test
    fun `Falls back to the universal APK for an unknown ABI`() {
        pick("riscv64") shouldBe "zenyomi-v0.20.0.apk"
    }

    @Test
    fun `Ignores files that are not APKs`() {
        val withChecksum = assets + ("zenyomi-v0.20.0.apk.sha256" to "https://example.com/zenyomi-v0.20.0.apk.sha256")
        ReleaseServiceImpl.pickDownloadLink(withChecksum, isFoss = false, abi = "riscv64")
            ?.substringAfterLast('/') shouldBe "zenyomi-v0.20.0.apk"
    }

    @Test
    fun `Picks the foss APK for foss builds`() {
        pick("arm64-v8a", isFoss = true) shouldBe "zenyomi-v0.20.0-foss.apk"
    }
}
