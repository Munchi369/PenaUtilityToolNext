package org.example.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class SaveDatabaseSettingsStoreTest {
    @Test
    fun missingFileLoadsEmptySettingsWithoutCreatingFile(
        @TempDir root: Path,
    ) {
        val settingsFile = root.resolve("config/settings.properties")
        val store = PropertiesSaveDatabaseSettingsStore(settingsFile)

        val result = store.load() as SaveDatabaseSettingsLoadResult.Loaded

        assertEquals(null, result.settings.selectedPath)
        assertFalse(Files.exists(settingsFile))
    }

    @Test
    fun selectedPathRoundTripsWithSchemaVersion(
        @TempDir root: Path,
    ) {
        val settingsFile = root.resolve("config/settings.properties")
        val store = PropertiesSaveDatabaseSettingsStore(settingsFile)
        val selectedPath = root.resolve("日本語のセーブデータ")

        store.save(SaveDatabaseSettings(selectedPath))
        val result = store.load() as SaveDatabaseSettingsLoadResult.Loaded

        assertEquals(selectedPath.toAbsolutePath().normalize(), result.settings.selectedPath)
        assertTrue(Files.readString(settingsFile, StandardCharsets.UTF_8).contains("schemaVersion=1"))
    }

    @Test
    fun savingEmptySettingsClearsSelectedPath(
        @TempDir root: Path,
    ) {
        val store = PropertiesSaveDatabaseSettingsStore(root.resolve("config/settings.properties"))
        store.save(SaveDatabaseSettings(root.resolve("selected")))

        store.save(SaveDatabaseSettings(selectedPath = null))

        val result = store.load() as SaveDatabaseSettingsLoadResult.Loaded
        assertEquals(null, result.settings.selectedPath)
    }

    @Test
    fun unsupportedSchemaVersionIsRejected(
        @TempDir root: Path,
    ) {
        val settingsFile = root.resolve("config/settings.properties")
        Files.createDirectories(settingsFile.parent)
        Files.writeString(settingsFile, "schemaVersion=2\nselectedPath=C:\\\\save\n")

        val result = PropertiesSaveDatabaseSettingsStore(settingsFile).load()

        assertInstanceOf(SaveDatabaseSettingsLoadResult.Invalid::class.java, result)
    }

    @Test
    fun relativeSelectedPathIsRejected(
        @TempDir root: Path,
    ) {
        val settingsFile = root.resolve("config/settings.properties")
        Files.createDirectories(settingsFile.parent)
        Files.writeString(settingsFile, "schemaVersion=1\nselectedPath=relative/path\n")

        val result = PropertiesSaveDatabaseSettingsStore(settingsFile).load()

        assertInstanceOf(SaveDatabaseSettingsLoadResult.Invalid::class.java, result)
    }

    @Test
    fun writeFailureIsReported(
        @TempDir root: Path,
    ) {
        val blockedDirectory = root.resolve("config")
        Files.writeString(blockedDirectory, "not a directory")
        val store = PropertiesSaveDatabaseSettingsStore(blockedDirectory.resolve("settings.properties"))

        assertThrows(SaveDatabaseSettingsWriteException::class.java) {
            store.save(SaveDatabaseSettings(root.resolve("selected")))
        }
    }
}
