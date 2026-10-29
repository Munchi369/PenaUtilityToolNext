package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.game.SaveRows
import io.github.munchi369.penautilitytoolnext.savedata.CopiedDerbyTestDatabase
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException

class PlayerDetailRecordHighsCacheTest {
    @Test
    fun recordHighsAreReusedUntilTheSaveDateChanges() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { copy ->
            val loader = DerbyPlayerDetailLoader(DerbySaveDatabaseAccess())
            val before = gameHigh(loader, copy.path)
            update(copy.path, "UPDATE YASYU_SEISEKI SET SIAI = ${before + 1} WHERE ITINIGUN = 1 AND NUM = 1239")

            assertEquals(before, gameHigh(loader, copy.path))

            update(copy.path, "UPDATE SAVE SET SYSDAY = SYSDAY + 1 WHERE ${SaveRows.CURRENT_CONDITION}")

            assertEquals(before + 1, gameHigh(loader, copy.path))
        }
    }

    private fun gameHigh(
        loader: DerbyPlayerDetailLoader,
        path: Path,
    ): Int =
        (loader.load(path, 1239) as PlayerDetailLoadResult.Loaded)
            .data.recordHighs.value!!
            .battingMax
            .getValue(1)
            .getValue("SIAI")

    private fun update(
        path: Path,
        sql: String,
    ) {
        DriverManager.getConnection("jdbc:derby:$path;create=false").use { connection ->
            connection.createStatement().use { it.executeUpdate(sql) }
        }
        try {
            DriverManager.getConnection("jdbc:derby:$path;shutdown=true").close()
        } catch (error: SQLException) {
            assertEquals("08006", error.sqlState)
        }
    }
}
