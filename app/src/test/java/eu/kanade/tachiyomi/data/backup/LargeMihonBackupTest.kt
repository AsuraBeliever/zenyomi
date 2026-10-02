package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

/**
 * A Mihon backup the size of a real library, built with the backup classes themselves.
 *
 * Nobody's real backup goes in the repo, and restoring hundreds of titles is the one thing
 * the manual checks never did. The test proves the file survives the same encode, gzip and
 * decode a restore goes through, and leaves it in `app/build/fixtures/` to restore on the
 * emulator (docs/TESTING.md). Only Mihon's fields are filled, so it is what Mihon itself
 * would write: the anime lists are empty and take no bytes.
 */
class LargeMihonBackupTest {

    @Test
    fun `a library of hundreds of titles survives the round trip`() {
        val backup = largeMihonBackup()
        val file = File("build/fixtures/mihon-large.tachibk").apply { parentFile.mkdirs() }

        file.sink().gzip().buffer().use { it.write(ProtoBuf.encodeToByteArray(Backup.serializer(), backup)) }
        val decoded = file.source().gzip().buffer().use {
            ProtoBuf.decodeFromByteArray(Backup.serializer(), it.readByteArray())
        }

        assertEquals(MANGA, decoded.backupManga.size)
        assertEquals(backup.backupManga.sumOf { it.chapters.size }, decoded.backupManga.sumOf { it.chapters.size })
        assertEquals(CATEGORIES, decoded.backupCategories.size)
        assertEquals(backup.backupManga.count { it.favorite }, decoded.backupManga.count { it.favorite })
        assertTrue(decoded.backupAnime.isEmpty())
        // The point is size: a real library, not a toy one.
        assertTrue(decoded.backupManga.sumOf { it.chapters.size } > 40_000)
    }

    private fun largeMihonBackup(): Backup {
        // Seeded, so the file is the same every run and a restore can be checked by count.
        val random = Random(20261001)
        val now = 1_790_000_000_000L
        val categories = List(CATEGORIES) { BackupCategory(name = "Categoría ${it + 1}", order = it.toLong()) }

        val manga = List(MANGA) { index ->
            val source = SOURCES[index % SOURCES.size]
            // A long tail of short series and a few that run past a thousand chapters.
            val chapterCount = when {
                index % 97 == 0 -> 1000 + random.nextInt(200)
                index % 10 == 0 -> 200 + random.nextInt(150)
                else -> 1 + random.nextInt(120)
            }
            val read = random.nextInt(chapterCount + 1)
            val chapters = List(chapterCount) { c ->
                BackupChapter(
                    url = "/fixture/$index/chapter/${c + 1}",
                    name = "Capítulo ${c + 1}",
                    scanlator = if (c % 3 == 0) "Fixture Scans" else null,
                    read = c < read,
                    bookmark = c % 50 == 7,
                    lastPageRead = if (c == read) 4 else 0,
                    dateFetch = now - (chapterCount - c) * 86_400_000L,
                    dateUpload = now - (chapterCount - c) * 86_400_000L,
                    chapterNumber = (c + 1).toFloat(),
                    sourceOrder = (chapterCount - 1 - c).toLong(),
                )
            }
            BackupManga(
                source = source.sourceId,
                url = "/fixture/$index",
                title = "Manga de prueba ${"%03d".format(index + 1)}",
                author = "Autor ${index % 40}",
                description = "Entrada $index del backup grande de prueba (KAN-10).",
                genre = listOf("Acción", "Drama", "Comedia", "Romance").shuffled(random).take(2),
                status = 1 + index % 3,
                dateAdded = now - index * 3_600_000L,
                chapters = chapters,
                // Mihon writes a category's order, not its id, in each entry.
                categories = List(random.nextInt(3)) { random.nextInt(CATEGORIES).toLong() }.distinct(),
                tracking = if (index % 5 == 0) {
                    listOf(
                        BackupTracking(
                            syncId = 1,
                            libraryId = 0,
                            mediaId = 10_000L + index,
                            title = "Manga de prueba ${index + 1}",
                            lastChapterRead = read.toFloat(),
                            totalChapters = chapterCount,
                        ),
                    )
                } else {
                    emptyList()
                },
                // Read but no longer followed: Mihon keeps these, and they must not land in the library.
                favorite = index % 12 != 0,
                history = if (read > 0) {
                    listOf(BackupHistory(url = "/fixture/$index/chapter/$read", lastRead = now - index * 60_000L))
                } else {
                    emptyList()
                },
            )
        }

        return Backup(backupManga = manga, backupCategories = categories, backupSources = SOURCES)
    }

    private companion object {
        const val MANGA = 600
        const val CATEGORIES = 10

        // Weeb Central is installed on the emulator; the other two are not, so the restore also
        // has to make stub sources for them, as it does with any real backup.
        val SOURCES = listOf(
            BackupSource(name = "Weeb Central", sourceId = 2131019126180322627),
            BackupSource(name = "MangaDex", sourceId = 2499283573021220255),
            BackupSource(name = "Fuente que ya no existe", sourceId = 1234567890123456789),
        )
    }
}
