package org.example.player

import org.example.savedata.CopiedDerbyTestDatabase
import org.example.savedata.DerbySaveDatabaseAccess
import org.example.savedata.SaveDatabaseReadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Types
import java.util.Comparator
import java.util.UUID

class PlayerDetailFixtureTest {
    @Test
    fun minimalFixtureSupportsPartialFailureUnknownsAndIndependentDivisions() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { reference ->
            val root =
                generateSequence(
                    Path.of("").toAbsolutePath(),
                ) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
            val fixtures = root.resolve("test-data/fixtures").normalize()
            val fixture = fixtures.resolve("player-detail-${UUID.randomUUID()}")
            val access = DerbySaveDatabaseAccess()
            try {
                val built =
                    access.read(reference.path, "選手詳細fixture作成") { source ->
                        try {
                            DriverManager.getConnection("jdbc:derby:$fixture;create=true").use { target ->
                                target.autoCommit = false
                                val filters =
                                    mapOf(
                                        "SAVE" to "MYTEAM >= 0",
                                        "SETTEI" to "1=1",
                                        "TEAM_INFO" to "1=1",
                                        "SENSYU_DATA" to "SENSYU_ID = 1",
                                        "SENSYU_NORYOKU" to "SENSYU_ID = 1",
                                        "YASYU_SEISEKI" to "NUM = 1",
                                        "TOUSYU_SEISEKI" to "NUM = 1",
                                        "YASYU_SEISEKI_GEKKAN" to "NUM = 1",
                                        "TOUSYU_SEISEKI_GEKKAN" to "NUM = 1",
                                        "DAJUN_POJI" to "ID = 1",
                                        "RIREKI" to "HAN2 = 1 AND KUBUN IN (4,8)",
                                        "BEST_GG" to "1=1",
                                        "GEKKAN_MVP" to "NUM = 1",
                                        "KIYO" to "HIKAEDH12 = 3",
                                        "TOKUBETU_KIROKU" to "SENSYU = 1",
                                    )
                                filters.forEach { (table, filter) -> copyTable(source, target, table, filter) }
                                target.commit()
                            }
                        } finally {
                            shutdown(fixture)
                        }
                    }
                assertTrue(built is SaveDatabaseReadResult.Success, built.toString())
                CopiedDerbyTestDatabase.copyFixture(fixture).use { copy ->
                    val loader = DerbyPlayerDetailLoader(access)
                    val before = (loader.load(copy.path, 1) as PlayerDetailLoadResult.Loaded).data
                    assertNull(before.honors.getValue("月間MVP").error)
                    DriverManager.getConnection("jdbc:derby:${copy.path};create=false").use { connection ->
                        connection.autoCommit = false
                        connection.createStatement().use {
                            it.executeUpdate("DROP TABLE GEKKAN_MVP")
                            it.executeUpdate("UPDATE SENSYU_NORYOKU SET KOUDA = NULL WHERE NENME = (SELECT MIN(NENME) FROM SENSYU_NORYOKU)")
                            it.executeUpdate("INSERT INTO RIREKI (KUBUN, NENME, HAN1, HAN2) VALUES (8, 5, 2, 1)")
                            it.executeUpdate("INSERT INTO TOUSYU_SEISEKI (NUM,NENME,NENDO,ITINIGUN,KAISUU,JISEKI) VALUES (1,10,3,1,12,2)")
                            it.executeUpdate("INSERT INTO TOUSYU_SEISEKI (NUM,NENME,NENDO,ITINIGUN,KAISUU,JISEKI) VALUES (1,10,3,2,6,1)")
                            it.executeUpdate("INSERT INTO TOUSYU_SEISEKI (NUM,NENME,NENDO,ITINIGUN,KAISUU,JISEKI) VALUES (1,11,4,3,9,0)")
                        }
                        connection.commit()
                    }
                    shutdown(copy.path)
                    val after = (loader.load(copy.path, 1) as PlayerDetailLoadResult.Loaded).data
                    assertNotNull(after.honors.getValue("月間MVP").error)
                    assertEquals(before.batting, after.batting)
                    assertNotNull(after.currentAbility.value)
                    assertEquals(
                        "—",
                        after.abilities.value!!
                            .first()
                            .cells
                            .getValue("KOUDA")
                            .text,
                    )
                    assertTrue(
                        after.honors
                            .getValue("タイトル")
                            .value!!
                            .any { it.title == "新人賞" && it.context == "二軍" },
                    )
                    assertEquals(
                        setOf(1, 2, 3),
                        after.pitching.value!!
                            .map { it.division }
                            .toSet(),
                    )
                    assertTrue(after.monthlyPitching.value!!.isEmpty())
                    assertTrue(Files.notExists(copy.path.resolve("db.lck")))
                    DriverManager.getConnection("jdbc:derby:${copy.path};create=false").use { connection ->
                        connection.autoCommit = false
                        connection.createStatement().use {
                            it.executeUpdate(
                                "INSERT INTO YASYU_SEISEKI SELECT * FROM YASYU_SEISEKI WHERE NUM = 1 AND NENME = 1 AND ITINIGUN = 1",
                            )
                        }
                        connection.commit()
                    }
                    shutdown(copy.path)
                    val duplicate = (loader.load(copy.path, 1) as PlayerDetailLoadResult.Loaded).data
                    assertTrue(
                        duplicate.batting.error
                            .orEmpty()
                            .contains("YASYU_SEISEKI に重複した記録があります"),
                    )
                    assertTrue(
                        duplicate.batting.error
                            .orEmpty()
                            .contains("選手ID: 1"),
                    )
                    assertNull(duplicate.pitching.error)
                }
            } finally {
                check(fixture.startsWith(fixtures) && fixture != fixtures)
                if (Files.exists(fixture)) {
                    Files.walk(fixture).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
                    }
                }
            }
        }
    }

    private fun copyTable(
        source: Connection,
        target: Connection,
        table: String,
        filter: String,
    ) {
        source.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM $table WHERE $filter").use { rows ->
                val metadata = rows.metaData
                val indices = 1..metadata.columnCount
                val definitions =
                    indices.joinToString { index ->
                        val length =
                            if (metadata.getColumnType(index) in
                                setOf(Types.VARCHAR, Types.CHAR)
                            ) {
                                "(${metadata.getPrecision(index)})"
                            } else {
                                ""
                            }
                        "${metadata.getColumnName(index)} ${metadata.getColumnTypeName(index)}$length"
                    }
                target.createStatement().use { it.executeUpdate("CREATE TABLE $table ($definitions)") }
                target.prepareStatement("INSERT INTO $table VALUES (${indices.joinToString { "?" }})").use { insert ->
                    while (rows.next()) {
                        indices.forEach { insert.setObject(it, rows.getObject(it)) }
                        insert.executeUpdate()
                    }
                }
            }
        }
    }

    private fun shutdown(path: Path) {
        try {
            DriverManager.getConnection("jdbc:derby:$path;shutdown=true").close()
        } catch (error: SQLException) {
            assertEquals("08006", error.sqlState)
        }
    }
}
