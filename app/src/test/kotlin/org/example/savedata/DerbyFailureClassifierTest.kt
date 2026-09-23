package org.example.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.SQLException

class DerbyFailureClassifierTest {
    @Test
    fun nestedDatabaseInUseStateIsClassifiedAsInUse() {
        val bootFailure = SQLException("Database failed to start", "XJ040")
        bootFailure.setNextException(SQLException("Database is already booted", "XSDB6"))

        val result = DerbyFailureClassifier.classify(bootFailure)

        assertEquals(SaveDatabaseFailureKind.IN_USE, result)
    }

    @Test
    fun genericDatabaseBootFailureIsUnavailable() {
        val bootFailure = SQLException("Database failed to start", "XJ040")

        val result = DerbyFailureClassifier.classify(bootFailure)

        assertEquals(SaveDatabaseFailureKind.UNAVAILABLE, result)
    }
}
