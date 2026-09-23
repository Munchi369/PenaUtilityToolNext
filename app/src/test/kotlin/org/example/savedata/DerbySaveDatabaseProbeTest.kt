package org.example.savedata

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DerbySaveDatabaseProbeTest {
    private var testDatabase: CopiedDerbyTestDatabase? = null

    @AfterEach
    fun removeTestDatabaseCopy() {
        testDatabase?.close()
    }

    @Test
    fun missingMarkerIsRejectedWithoutCreatingDatabase(
        @TempDir root: Path,
    ) {
        val path = root.resolve("not-a-database")

        val result = DerbySaveDatabaseProbe().probe(path)

        val failure = result as SaveDatabaseProbeResult.Failed
        assertEquals(SaveDatabaseFailureKind.NOT_SAVE_DATABASE, failure.kind)
        assertFalse(Files.exists(path))
    }

    @Test
    fun copiedReferenceDatabaseCanBeOpenedReadOnlyAndShutdown() {
        val copiedDatabase = CopiedDerbyTestDatabase.copyPrimaryReference()
        testDatabase = copiedDatabase

        val firstResult = DerbySaveDatabaseProbe().probe(copiedDatabase.path)
        val secondResult = DerbySaveDatabaseProbe().probe(copiedDatabase.path)

        assertInstanceOf(SaveDatabaseProbeResult.Available::class.java, firstResult)
        assertInstanceOf(SaveDatabaseProbeResult.Available::class.java, secondResult)
    }
}
