package org.example.team

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
import java.sql.DriverManager
import java.sql.SQLException

class DerbyTeamIdentityLoaderTest {
    @Test
    fun referenceColorIsReadExactlyAndDatabaseIsReleased() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val access = DerbySaveDatabaseAccess()
            val expected =
                access.read(db.path, "チーム色の物理データ確認") { connection ->
                    connection.createStatement().use { statement ->
                        statement
                            .executeQuery(
                                """
                                SELECT T.TEAMNAME, C.SYSNENME, C.SYSDAY, C.MONEY
                                FROM SAVE S JOIN TEAM_INFO T ON T.TEAMNO = S.MYTEAM
                                JOIN SAVE C ON C.MYTEAM = -(S.MYTEAM + 10)
                                WHERE S.MYTEAM BETWEEN 1 AND 12
                                """.trimIndent(),
                            ).use { rows ->
                                assertTrue(rows.next())
                                TeamIdentity(rows.getString(1), (rows.getInt(2) shl 16) or (rows.getInt(3) shl 8) or rows.getInt(4))
                            }
                    }
                }
            assertEquals((expected as SaveDatabaseReadResult.Success).value, DerbyTeamIdentityLoader(access).load(db.path))
            assertTrue(Files.notExists(db.path.resolve("db.lck")))
        }
    }

    @Test
    fun whiteAndYellowRemainUnmodifiedAndInvalidColorDoesNotInventATeam() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val loader = DerbyTeamIdentityLoader(DerbySaveDatabaseAccess())
            update(db.path, "UPDATE SAVE SET SYSNENME = 255, SYSDAY = 255, MONEY = 255 WHERE MYTEAM BETWEEN -22 AND -11")
            assertEquals(0xFFFFFF, loader.load(db.path)?.rgb)
            update(db.path, "UPDATE SAVE SET MONEY = 0 WHERE MYTEAM BETWEEN -22 AND -11")
            assertEquals(0xFFFF00, loader.load(db.path)?.rgb)
            update(db.path, "UPDATE SAVE SET MONEY = 256 WHERE MYTEAM BETWEEN -22 AND -11")
            assertNull(loader.load(db.path))
            assertTrue(Files.notExists(db.path.resolve("db.lck")))
        }
    }

    @Test
    fun missingOrAmbiguousCurrentTeamReturnsNoIdentity() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val loader = DerbyTeamIdentityLoader(DerbySaveDatabaseAccess())
            assertNotNull(loader.load(db.path))
            update(db.path, "INSERT INTO SAVE SELECT * FROM SAVE WHERE MYTEAM >= 0")
            assertNull(loader.load(db.path))
            update(db.path, "DELETE FROM SAVE WHERE MYTEAM >= 0")
            assertNull(loader.load(db.path))
        }
    }

    private fun update(
        path: Path,
        sql: String,
    ) {
        val url = "jdbc:derby:$path"
        DriverManager.getConnection(url).use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { it.executeUpdate(sql) }
            connection.commit()
        }
        try {
            DriverManager.getConnection("$url;shutdown=true").close()
        } catch (error: SQLException) {
            assertEquals("08006", error.sqlState)
        }
    }
}
