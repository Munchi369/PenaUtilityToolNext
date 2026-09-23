package org.example.player

import org.example.savedata.CopiedDerbyTestDatabase
import org.example.savedata.DerbySaveDatabaseAccess
import org.example.savedata.SaveDatabaseReadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.sql.SQLException

class PlayerDetailSeasonIdentityTest {
    @Test
    fun initialPlaceholderAndFirstSeasonDoNotConflict() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { copy ->
            val url = "jdbc:derby:${copy.path}"
            DriverManager.getConnection("$url;create=false").use { connection ->
                connection.autoCommit = false
                connection.createStatement().use {
                    it.executeUpdate("DELETE FROM YASYU_SEISEKI WHERE NUM = 1239 AND (NENME > 1 OR ITINIGUN <> 1)")
                }
                connection.commit()
            }
            try {
                DriverManager.getConnection("$url;shutdown=true").close()
            } catch (error: SQLException) {
                assertEquals("08006", error.sqlState)
            }
            val data = (DerbyPlayerDetailLoader(DerbySaveDatabaseAccess()).load(copy.path, 1239) as PlayerDetailLoadResult.Loaded).data
            assertNull(data.batting.error, data.batting.error)
            assertNull(data.pitching.error, data.pitching.error)
            assertEquals(listOf(1), data.batting.value!!.map { it.playerYear })
            val total = detailTotals(data.batting.value)
            assertEquals(15, total["DASEKI"])
            assertEquals(1, total["ANDA"])
        }
    }

    @Test
    fun savedCareerRowsWithRepeatedCalendarYearRemainReadable() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { copy ->
            val access = DerbySaveDatabaseAccess()
            val candidates =
                access.read(copy.path, "成績年度の検証") { connection ->
                    connection
                        .detailRows(
                            "SELECT NUM FROM YASYU_SEISEKI GROUP BY NUM, NENDO, ITINIGUN HAVING COUNT(*) > 1",
                        ).map { it.int("NUM") }
                        .distinct()
                        .sorted()
                } as SaveDatabaseReadResult.Success
            val loader = DerbyPlayerDetailLoader(access)
            assertTrue(candidates.value.isNotEmpty(), "重複年度を持つ実データが必要です")
            for (id in candidates.value) {
                val data = (loader.load(copy.path, id) as PlayerDetailLoadResult.Loaded).data
                assertNull(data.batting.error, "選手ID=$id: ${data.batting.error}")
                assertNull(data.pitching.error, "選手ID=$id: ${data.pitching.error}")
                assertTrue(data.batting.value!!.all { it.playerYear > 0 })
                assertTrue(data.pitching.value!!.all { it.playerYear > 0 })
            }
        }
    }
}
