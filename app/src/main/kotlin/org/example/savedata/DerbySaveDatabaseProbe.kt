package org.example.savedata

import java.nio.file.Path
import java.sql.SQLException

fun interface SaveDatabaseProbe {
    fun probe(path: Path): SaveDatabaseProbeResult
}

class DerbySaveDatabaseProbe(
    private val databaseAccess: DerbySaveDatabaseAccess = DerbySaveDatabaseAccess(),
) : SaveDatabaseProbe {
    override fun probe(path: Path): SaveDatabaseProbeResult =
        when (val result = databaseAccess.read(path, "接続確認") {}) {
            is SaveDatabaseReadResult.Success -> SaveDatabaseProbeResult.Available
            is SaveDatabaseReadResult.Failed ->
                SaveDatabaseProbeResult.Failed(
                    kind = result.kind,
                    technicalDetails = result.technicalDetails,
                )
        }
}

internal object DerbyFailureClassifier {
    private val inUseSqlStates = setOf("XSDB6", "40XL1", "40XL2")

    fun classify(error: Throwable): SaveDatabaseFailureKind =
        if (error.throwableSequence().filterIsInstance<SQLException>().any { it.sqlState in inUseSqlStates }) {
            SaveDatabaseFailureKind.IN_USE
        } else {
            SaveDatabaseFailureKind.UNAVAILABLE
        }
}
