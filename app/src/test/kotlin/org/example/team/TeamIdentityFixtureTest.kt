package org.example.team

import org.example.savedata.CopiedDerbyTestDatabase
import org.example.savedata.DerbySaveDatabaseAccess
import org.example.savedata.SaveDatabaseReadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Types
import java.util.Comparator
import java.util.UUID

class TeamIdentityFixtureTest {
    @Test
    fun createsMinimalFixtureFromReferenceAndLoadsWithoutPlayerTables() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { reference ->
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve("settings.gradle.kts")) }
            val fixturesRoot = root.resolve("test-data/fixtures").normalize()
            val fixture = fixturesRoot.resolve("team-identity-${UUID.randomUUID()}")
            val access = DerbySaveDatabaseAccess()
            val expected = DerbyTeamIdentityLoader(access).load(reference.path)
            assertNotNull(expected)
            try {
                val result =
                    access.read(reference.path, "自チームfixture作成") { source ->
                        DriverManager.getConnection("jdbc:derby:$fixture;create=true").use { target ->
                            target.autoCommit = false
                            copyTable(source, target, "SAVE", "MYTEAM, SYSNENME, SYSDAY, MONEY")
                            copyTable(source, target, "TEAM_INFO", "TEAMNO, TEAMNAME")
                            target.commit()
                        }
                    }
                assertEquals(SaveDatabaseReadResult.Success(Unit), result)
                try {
                    DriverManager.getConnection("jdbc:derby:$fixture;shutdown=true").close()
                } catch (error: SQLException) {
                    assertEquals("08006", error.sqlState)
                }
                // Boot and shutdown verification uses only a temporary copy of the fixture.
                CopiedDerbyTestDatabase.copyFixture(fixture).use { copy ->
                    assertEquals(expected, DerbyTeamIdentityLoader(access).load(copy.path))
                }
            } finally {
                check(fixture.startsWith(fixturesRoot) && fixture != fixturesRoot)
                if (Files.exists(fixture)) {
                    Files.walk(fixture).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
                }
            }
        }
    }

    private fun copyTable(
        source: Connection,
        target: Connection,
        table: String,
        columns: String,
    ) {
        source.createStatement().use { statement ->
            statement.executeQuery("SELECT $columns FROM $table").use { rows ->
                val metadata = rows.metaData
                val indices = 1..metadata.columnCount
                val definitions =
                    indices.joinToString { index ->
                        val length = if (metadata.getColumnType(index) == Types.VARCHAR) "(${metadata.getPrecision(index)})" else ""
                        "${metadata.getColumnName(index)} ${metadata.getColumnTypeName(index)}$length"
                    }
                target.createStatement().use { it.executeUpdate("CREATE TABLE $table ($definitions)") }
                val placeholders = indices.joinToString { "?" }
                target.prepareStatement("INSERT INTO $table VALUES ($placeholders)").use { insert ->
                    while (rows.next()) {
                        indices.forEach { insert.setObject(it, rows.getObject(it)) }
                        insert.executeUpdate()
                    }
                }
            }
        }
    }
}
