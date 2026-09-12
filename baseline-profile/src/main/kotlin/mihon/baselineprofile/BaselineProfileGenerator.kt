package mihon.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the screens worth having compiled ahead of time.
 *
 * The anime half is included because half the app is anime: without it, every anime screen
 * pays the interpreter on first open, which is exactly the first impression the profile
 * exists to fix.
 *
 * Tab titles are matched by text, so this file has to move whenever one is renamed. It did
 * not: Mihon's "Extensions" tab became "Manga extensions" here in 0.5.4 and this generator
 * kept looking for the old name, which means it had been failing ever since.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(TARGET_PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()

            device.waitAndClick(By.text("Anime"))
            device.waitForIdle()

            device.waitAndClick(By.text("Updates"))
            device.waitForIdle()

            device.waitAndClick(By.text("History"))
            device.waitForIdle()

            device.waitAndClick(By.text("Browse"))
            device.waitForIdle()
            device.waitAndClick(By.textStartsWith("Manga"))
            device.waitForIdle()
            device.waitAndClick(By.textStartsWith("Anime s"))
            device.waitForIdle()
            device.waitAndClick(By.textStartsWith("Anime e"))
            device.waitForIdle()

            device.waitAndClick(By.text("More"))
            device.waitForIdle()
        }
    }
}

/**
 * Clicks when it appears, and simply moves on when it does not.
 *
 * The original threw on a missing target, which turns one renamed tab into a failed
 * generation and a profile that silently stops being refreshed.
 */
private fun UiDevice.waitAndClick(by: BySelector) {
    wait(Until.findObject(by), 30_000)?.click()
}
