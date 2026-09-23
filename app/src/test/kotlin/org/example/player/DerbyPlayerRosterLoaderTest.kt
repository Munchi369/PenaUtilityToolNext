package org.example.player

import org.example.savedata.CopiedDerbyTestDatabase
import org.example.savedata.DerbySaveDatabaseAccess
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DerbyPlayerRosterLoaderTest {
    private var testDatabase: CopiedDerbyTestDatabase? = null

    @AfterEach
    fun removeTestDatabaseCopy() {
        testDatabase?.close()
    }

    @Test
    fun copiedReferenceDatabaseLoadsCurrentTeamRosterAndReleasesDatabase() {
        val database = copyPrimaryReferenceDatabase()
        val loader = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess())

        val result = loader.load(database)
        val roster = (result as PlayerRosterLoadResult.Loaded).roster

        assertEquals(29, roster.firstTeam.size)
        assertEquals(44, roster.secondTeam.size)
        assertEquals(4, roster.secondTeam.count { it.registration == PlayerRegistration.DEVELOPMENT })
        assertTrue(roster.firstTeam.all { it.squad == PlayerSquad.FIRST })
        assertTrue(roster.secondTeam.all { it.squad == PlayerSquad.SECOND })
        assertEquals(
            listOf(PlayerInjury.Injured(23)),
            (roster.firstTeam + roster.secondTeam).map { it.injury }.filterNot { it == PlayerInjury.None },
        )
        assertTrue(
            roster.secondTeam
                .filter { it.registration == PlayerRegistration.DEVELOPMENT }
                .all { it.uniformNumber >= 100 },
        )
        assertSorted(roster.firstTeam)
        assertSorted(roster.secondTeam)
        val alwaysPresentProfileKeys = setOf("NENME", "SHUSSIN", "HEIGHT", "WEIGHT", "THROWS_BATS", "FORM")
        assertTrue(roster.players.all { player -> alwaysPresentProfileKeys.all { it in player.information } })
        assertTrue(roster.players.filter { it.major }.all { player -> alwaysPresentProfileKeys.all { it in player.information } })
        assertTrue(roster.players.any { "SHOZOKU" in it.information })
        assertTrue(roster.players.any { "DORAJUN" in it.information })
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun missingActiveTeamStillLoadsAllDomesticTeams() {
        val database = copyPrimaryReferenceDatabase()
        updateCopy(database, "UPDATE SAVE SET MYTEAM = 0 WHERE MYTEAM >= 0")
        updateCopy(
            database,
            """
            UPDATE SENSYU_DATA
            SET NENME = 6,
                SHOZOKU = 3,
                DORAJUN = 102,
                SHUSSIN = 53,
                HEIGHT = 180,
                WEIGHT = 80,
                KIKIUDE = 1,
                KIKIDASEKI = 2,
                FORM = 12
            WHERE SENSYU_ID = (
                SELECT MIN(SENSYU_ID)
                FROM SENSYU_DATA
                WHERE GENINFLG = 3
            )
            """.trimIndent(),
        )
        val loader = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess())

        val result = loader.load(database)

        val roster = (result as PlayerRosterLoadResult.Loaded).roster
        assertEquals(null, roster.currentTeam)
        assertEquals(893, roster.players.count { !it.major })
        assertEquals(5, roster.players.count { it.major })
        val major = roster.players.filter { it.major }.minBy { it.playerId }
        assertEquals(
            listOf("6", "社会人", "育成2位", "オーストラリア", "180", "80", "右投左打", "スリークォーター"),
            listOf("NENME", "SHOZOKU", "DORAJUN", "SHUSSIN", "HEIGHT", "WEIGHT", "THROWS_BATS", "FORM")
                .map { key -> major.cell(RosterDisplay.BASIC, key, 1).text },
        )
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun missingActiveTeamValueFallsBackToAllTeams() {
        val database = copyPrimaryReferenceDatabase()
        updateCopy(database, "UPDATE SAVE SET MYTEAM = NULL WHERE MYTEAM >= 0")
        val loader = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess())

        val result = loader.load(database)

        assertEquals(null, (result as PlayerRosterLoadResult.Loaded).roster.currentTeam)
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun ambiguousSaveContextDoesNotInventCurrentTeamOrStamina() {
        val database = copyPrimaryReferenceDatabase()
        updateCopy(database, "INSERT INTO SAVE SELECT * FROM SAVE")
        val loader = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess())

        val result = loader.load(database)

        val roster = (result as PlayerRosterLoadResult.Loaded).roster
        assertEquals(null, roster.currentTeam)
        assertTrue(roster.players.all { it.information["READY"] == null })
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun unknownPositionRejectsTheWholeRoster() {
        val database = copyPrimaryReferenceDatabase()
        updateCopy(
            database,
            """
            UPDATE SENSYU_DATA
            SET POJI = 99
            WHERE SENSYU_ID = (
                SELECT MIN(SENSYU_ID)
                FROM SENSYU_DATA
                WHERE GENINFLG = 1 AND SENSYU_NUM BETWEEN 500 AND 599
            )
            """.trimIndent(),
        )
        val loader = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess())

        val result = loader.load(database)

        assertInstanceOf(PlayerRosterLoadResult.InvalidData::class.java, result)
        assertTrue((result as PlayerRosterLoadResult.InvalidData).technicalDetails.contains("POJI=99"))
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun nullFirstTeamServiceDaysStillLoadAsZero() {
        val database = copyPrimaryReferenceDatabase()
        updateCopy(database, "UPDATE YASYU_SEISEKI SET ITIGUN = NULL WHERE ITINIGUN = 1")
        updateCopy(database, "UPDATE TOUSYU_SEISEKI SET ITIGUN = NULL WHERE ITINIGUN = 1")
        val loader = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess())

        val result = loader.load(database)

        assertInstanceOf(PlayerRosterLoadResult.Loaded::class.java, result)
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun abilityCellsDistinguishMissingValuesFromUnusedPitchSlots() {
        val database = copyPrimaryReferenceDatabase()
        updateCopy(
            database,
            """
            UPDATE SENSYU_DATA
            SET KOUDA = NULL,
                HEN1 = 0,
                HEN1_DATA = NULL,
                HEN2 = 1,
                HEN2_DATA = NULL
            WHERE SENSYU_ID = (
                SELECT MIN(SENSYU_ID)
                FROM SENSYU_DATA
                WHERE GENINFLG IN (1, 3)
            )
            """.trimIndent(),
        )
        val loader = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess())

        val result = loader.load(database)

        val player = (result as PlayerRosterLoadResult.Loaded).roster.players.minBy { it.playerId }
        assertEquals("—", player.cell(RosterDisplay.BATTING_ABILITY, "KOUDA", 1).text)
        assertEquals("", player.cell(RosterDisplay.PITCHING_ABILITY, "PITCH1", 1).text)
        assertEquals("スライダー —", player.cell(RosterDisplay.PITCHING_ABILITY, "PITCH2", 1).text)
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    private fun assertSorted(players: List<PlayerRosterPlayer>) {
        val expected =
            players.sortedWith(
                compareBy<PlayerRosterPlayer>(
                    { it.position.databaseValue },
                    { it.uniformNumber },
                    { it.playerId },
                ),
            )
        assertEquals(expected, players)
    }

    private fun copyPrimaryReferenceDatabase(): Path {
        val copiedDatabase = CopiedDerbyTestDatabase.copyPrimaryReference()
        testDatabase = copiedDatabase
        return copiedDatabase.path
    }

    private fun updateCopy(
        database: Path,
        sql: String,
    ) {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver")
        val url = "jdbc:derby:${database.toAbsolutePath().normalize()}"
        DriverManager.getConnection("$url;create=false").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { statement -> statement.executeUpdate(sql) }
            connection.commit()
        }
        shutdown(url)
    }

    private fun shutdown(url: String) {
        try {
            DriverManager.getConnection("$url;shutdown=true").use(Connection::close)
            error("Derby DB shutdown did not throw the expected exception")
        } catch (error: SQLException) {
            assertEquals("08006", error.sqlState)
        }
    }
}
