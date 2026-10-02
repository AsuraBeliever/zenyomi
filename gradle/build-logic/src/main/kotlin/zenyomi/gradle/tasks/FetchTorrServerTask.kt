package zenyomi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * Puts TorrServer's Android executables where the torrent add-on packages its native libraries.
 *
 * The binaries are 63-66 MB each, so they never enter git: they come from TorrServer's own
 * release, are checked against a pinned SHA-256 and are cached in the Gradle user home, so a
 * clean build or a second checkout does not download them again.
 *
 * Each one lands as `<abi>/libtorrserver.so`. The name is what makes Android install it in the
 * app's `nativeLibraryDir`, the only place an app is allowed to execute a file from.
 */
abstract class FetchTorrServerTask : DefaultTask() {

    /** TorrServer release tag, e.g. `MatriX.145.1`. */
    @get:Input
    abstract val version: Property<String>

    /** Android ABI → `<release asset name>:<sha256>`. */
    @get:Input
    abstract val assets: MapProperty<String, String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        val tag = version.get()
        val out = outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        assets.get().forEach { (abi, spec) ->
            val (asset, sha256) = spec.split(':', limit = 2)
            val cached = cacheDir.get().asFile.resolve("$tag/$asset")
            if (!cached.isFile || cached.sha256() != sha256) {
                download("https://github.com/YouROK/TorrServer/releases/download/$tag/$asset", cached, sha256)
            }
            cached.copyTo(out.resolve("$abi/libtorrserver.so"), overwrite = true)
        }
    }

    private fun download(url: String, target: File, sha256: String) {
        logger.lifecycle("Downloading $url")
        target.parentFile.mkdirs()
        val partial = File(target.path + ".part")
        URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        val actual = partial.sha256()
        if (actual != sha256) {
            partial.delete()
            throw GradleException("$url: expected sha256 $sha256, got $actual")
        }
        if (!partial.renameTo(target)) throw GradleException("Could not move $partial to $target")
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
