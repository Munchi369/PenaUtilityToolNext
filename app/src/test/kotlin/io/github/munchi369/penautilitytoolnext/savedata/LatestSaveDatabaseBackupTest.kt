package io.github.munchi369.penautilitytoolnext.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class LatestSaveDatabaseBackupTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun failedReplacementRestoresThePreviousBackup() {
        val database = Files.createDirectories(root.resolve("penanto3"))
        Files.writeString(database.resolve("service.properties"), "first")
        val target = root.resolve("backups/latest")
        LatestSaveDatabaseBackup(target).backup(database)
        Files.writeString(database.resolve("service.properties"), "second")
        val backup =
            LatestSaveDatabaseBackup(target) { source, destination ->
                if (source.fileName.toString().startsWith("latest.incoming-")) throw IOException("replacement failed")
                Files.move(source, destination)
            }

        assertThrows(IOException::class.java) { backup.backup(database) }

        assertEquals("first", Files.readString(target.resolve("service.properties")))
        assertEquals(listOf("latest"), Files.list(target.parent).use { paths -> paths.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun failedRestorationKeepsThePreviousCopyAndReportsItsLocation() {
        val database = Files.createDirectories(root.resolve("penanto3"))
        Files.writeString(database.resolve("service.properties"), "first")
        val target = root.resolve("backups/latest")
        LatestSaveDatabaseBackup(target).backup(database)
        Files.writeString(database.resolve("service.properties"), "second")
        val backup =
            LatestSaveDatabaseBackup(target) { source, destination ->
                if (source != target) throw IOException("move failed")
                Files.move(source, destination)
            }

        val failure = assertThrows(IOException::class.java) { backup.backup(database) }

        val retained = Files.list(target.parent).use { paths -> paths.toList().single() }
        assertTrue(retained.fileName.toString().startsWith("latest.outgoing-"))
        assertEquals("first", Files.readString(retained.resolve("service.properties")))
        assertTrue(requireNotNull(failure.message).contains(retained.toString()))
        assertEquals("second", Files.readString(database.resolve("service.properties")))
    }

    @Test
    fun keepsOnlyTheLatestCopyAndSkipsTheLockFile() {
        val database = Files.createDirectories(root.resolve("penanto3"))
        Files.writeString(database.resolve("service.properties"), "first")
        Files.createDirectories(database.resolve("seg0"))
        Files.writeString(database.resolve("seg0/c10.dat"), "data")
        val target = root.resolve("config/backups/penanto3-latest")
        val backup = LatestSaveDatabaseBackup(target)

        backup.backup(database)
        Files.writeString(database.resolve("service.properties"), "second")
        Files.delete(database.resolve("seg0/c10.dat"))
        Files.writeString(database.resolve("db.lck"), "lock")
        backup.backup(database)

        assertEquals("second", Files.readString(target.resolve("service.properties")))
        assertTrue(Files.notExists(target.resolve("seg0/c10.dat")))
        assertTrue(Files.notExists(target.resolve("db.lck")))
        assertEquals(listOf("penanto3-latest"), Files.list(target.parent).use { paths -> paths.map { it.fileName.toString() }.toList() })
    }
}
