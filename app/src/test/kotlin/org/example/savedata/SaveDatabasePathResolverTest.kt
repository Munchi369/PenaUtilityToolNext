package org.example.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SaveDatabasePathResolverTest {
    @Test
    fun jvmPropertyHasHighestPriority(
        @TempDir root: Path,
    ) {
        val selected = root.resolve("selected")
        val resolver =
            resolver(
                root = root,
                systemProperty = root.resolve("property").toString(),
                environmentVariable = root.resolve("environment").toString(),
            )

        val result = resolver.resolve(selected) as SaveDatabasePathResolution.Found

        assertEquals(root.resolve("property"), result.target.path)
        assertEquals(SaveDatabaseOrigin.JVM_PROPERTY, result.target.origin)
    }

    @Test
    fun environmentVariablePrecedesSavedSetting(
        @TempDir root: Path,
    ) {
        val resolver =
            resolver(
                root = root,
                systemProperty = " ",
                environmentVariable = root.resolve("environment").toString(),
            )

        val result = resolver.resolve(root.resolve("selected")) as SaveDatabasePathResolution.Found

        assertEquals(root.resolve("environment"), result.target.path)
        assertEquals(SaveDatabaseOrigin.ENVIRONMENT_VARIABLE, result.target.origin)
    }

    @Test
    fun missingSavedSettingDoesNotFallBack(
        @TempDir root: Path,
    ) {
        val selected = root.resolve("missing-selected")
        val resolver = resolver(root = root, markerExists = { true })

        val result = resolver.resolve(selected) as SaveDatabasePathResolution.Found

        assertEquals(selected, result.target.path)
        assertEquals(SaveDatabaseOrigin.SAVED_SETTING, result.target.origin)
    }

    @Test
    fun developmentDatabaseIsSelectedOnlyWhenMarkerExists(
        @TempDir root: Path,
    ) {
        val developmentDatabase = root.resolve("test-data/dev/penanto3")
        val resolver = resolver(root = root, markerExists = { it == developmentDatabase })

        val result = resolver.resolve(savedPath = null) as SaveDatabasePathResolution.Found

        assertEquals(developmentDatabase, result.target.path)
        assertEquals(SaveDatabaseOrigin.DEVELOPMENT, result.target.origin)
    }

    @Test
    fun packagedApplicationUsesDatabaseBesideApplicationDirectoryParent(
        @TempDir root: Path,
    ) {
        val applicationDirectory = root.resolve("PenaUtilityToolNext")
        val standardDatabase = root.resolve("penanto3")
        val resolver =
            SaveDatabasePathResolver(
                applicationDirectory = applicationDirectory,
                developmentMode = false,
                systemProperty = { null },
                environmentVariable = { null },
                markerExists = { it == standardDatabase },
            )

        val result = resolver.resolve(savedPath = null) as SaveDatabasePathResolution.Found

        assertEquals(standardDatabase, result.target.path)
        assertEquals(SaveDatabaseOrigin.STANDARD_LAYOUT, result.target.origin)
    }

    @Test
    fun absentAutomaticDatabaseProducesNoSelection(
        @TempDir root: Path,
    ) {
        val result = resolver(root = root, markerExists = { false }).resolve(savedPath = null)

        assertInstanceOf(SaveDatabasePathResolution.NotFound::class.java, result)
    }

    private fun resolver(
        root: Path,
        systemProperty: String? = null,
        environmentVariable: String? = null,
        markerExists: (Path) -> Boolean = { false },
    ): SaveDatabasePathResolver =
        SaveDatabasePathResolver(
            applicationDirectory = root,
            developmentMode = true,
            systemProperty = { systemProperty },
            environmentVariable = { environmentVariable },
            markerExists = markerExists,
        )
}
