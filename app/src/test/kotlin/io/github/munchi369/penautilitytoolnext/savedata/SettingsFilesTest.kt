package io.github.munchi369.penautilitytoolnext.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

class SettingsFilesTest {
    @Test
    fun writeReplacesTheFileWithoutLeavingTemporaryFiles(
        @TempDir root: Path,
    ) {
        val file = root.resolve("config/settings.json")

        writeSettingsFile(file, "first")
        writeSettingsFile(file, "選手")

        assertEquals("選手", Files.readString(file))
        assertEquals(listOf(file), file.parent.listDirectoryEntries())
    }

    @Test
    fun saveDatabaseIdentityIgnoresCaseAndRedundantSegments(
        @TempDir root: Path,
    ) {
        assertTrue(sameSaveDatabase(root.resolve("Save"), root.resolve("sub/../SAVE")))
        assertFalse(sameSaveDatabase(root.resolve("save1"), root.resolve("save2")))
    }
}
