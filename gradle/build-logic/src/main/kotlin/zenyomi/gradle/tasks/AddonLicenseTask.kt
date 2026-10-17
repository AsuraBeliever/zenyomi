package zenyomi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Puts the torrent add-on's licence inside its APK.
 *
 * The add-on carries TorrServer, which is GPL-3.0, and the GPL asks for the licence to travel
 * with the object code. The add-on has no screen to show it on, so it goes in as an asset:
 * `LICENSE`, the GPL itself, and `NOTICE`, which says where the exact source of the executable
 * is. Generated from the module's own `LICENSE` rather than kept as a second copy, so the two
 * cannot drift.
 */
abstract class AddonLicenseTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val license: RegularFileProperty

    /** TorrServer release tag, e.g. `MatriX.145.1`. */
    @get:Input
    abstract val tag: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        license.get().asFile.copyTo(out.resolve("LICENSE"))
        val release = tag.get()
        out.resolve("NOTICE").writeText(
            """
            |Zenyomi Torrent
            |
            |This add-on is licensed under the GNU General Public License, version 3. The full
            |text is in LICENSE, next to this file.
            |
            |It runs TorrServer, by YouROK and contributors, also GPL-3.0. The executable is the
            |unmodified $release release:
            |  https://github.com/YouROK/TorrServer/releases/tag/$release
            |and its source is the $release tag of https://github.com/YouROK/TorrServer
            |
            |The add-on's own code is the torrent/ module of https://github.com/AsuraBeliever/zenyomi
            |
            """.trimMargin(),
        )
    }
}
