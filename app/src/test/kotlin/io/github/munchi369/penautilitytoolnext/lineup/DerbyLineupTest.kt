package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.savedata.CopiedDerbyTestDatabase
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.LatestSaveDatabaseBackup
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseBackup
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DerbyLineupTest {
    private var testDatabase: CopiedDerbyTestDatabase? = null
    private val access = DerbySaveDatabaseAccess()

    @AfterEach
    fun removeTestDatabaseCopy() {
        testDatabase?.close()
    }

    @Test
    fun referenceLineupLoadsValidAndRepairsOrderToMatchDefense() {
        val database = copyReference()

        val snapshot = load(database)

        assertEquals(6, snapshot.teamNumber)
        val editing = LineupEditing(snapshot)
        assertEquals(29, editing.counts(snapshot.initial).total)
        assertTrue(editing.validate(snapshot.initial).isEmpty(), editing.validate(snapshot.initial).toString())
        LineupMode.entries.forEach { mode ->
            val lineup = snapshot.initial.lineup(mode)
            assertEquals(9, lineup.size)
            val defenseIds = lineup.filter { it.slot in FieldSlot.defense }.map { it.playerId }
            assertTrue(defenseIds.all { it != null })
        }
        assertEquals(FieldSlot.PITCHER, snapshot.initial.normal[8].slot)
        assertTrue(snapshot.notices.none { it.contains("打順を守備位置") })
        assertEquals(6, snapshot.initial.starters.count { it != null })
        assertEquals(7, snapshot.initial.relievers.count { it != null })
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun savingWritesLineupRolesRegistrationAndAptitudeInOneTransaction() {
        val database = copyReference()
        update(database, "UPDATE SAVE SET SYSDAY = 100 WHERE MYTEAM = 6")
        val snapshot = load(database)
        val editing = LineupEditing(snapshot)
        val farmFielder =
            snapshot.players.first {
                !it.isPitcher &&
                    it.initialSquad == PlayerSquad.SECOND &&
                    it.restDays == 0 &&
                    !it.foreign &&
                    !it.injured
            }
        val rightIndex = snapshot.initial.normal.indexOfFirst { it.slot == FieldSlot.RIGHT }
        var draft = editing.swapLineup(snapshot.initial, LineupMode.NORMAL, 0, 1)
        draft = (editing.placeInLineup(draft, LineupMode.NORMAL, rightIndex, farmFielder.id) as EditResult.Applied).draft
        val demoted = snapshot.players.single { it.initialSquad == PlayerSquad.FIRST && draft.squad(it.id) == PlayerSquad.SECOND }
        draft = editing.swapRoles(draft, PitchingRole.STARTER, 0, PitchingRole.RELIEVER, 0)
        draft = editing.removeFromRole(draft, requireNotNull(draft.starters[2]))
        val catcher = requireNotNull(draft.normal.single { it.slot == FieldSlot.CATCHER }.playerId)
        draft = editing.setAptitude(draft, catcher, FieldSlot.FIRST, 55)
        draft = editing.setAptitude(draft, catcher, FieldSlot.CATCHER, 0)
        draft = editing.setMainPosition(draft, catcher, FieldSlot.FIRST)
        val reliever = requireNotNull(draft.relievers[1])
        draft = editing.setPitchingAptitude(draft, reliever, PitchingRole.STARTER, 12)
        val readinessBefore = query(database, "SELECT SENSYU_ID, SENPATUTYOUSEI FROM SENSYU_DATA ORDER BY SENSYU_ID")
        val backupDirectory = database.parent.resolve("backup/penanto3-latest")

        val result = DerbyLineupWriter(access, LatestSaveDatabaseBackup(backupDirectory)).save(snapshot, draft)

        assertEquals(LineupSaveResult.Saved, result)
        assertTrue(Files.isRegularFile(backupDirectory.resolve("service.properties")))
        val reloaded = load(database)
        assertEquals(draft.normal, reloaded.initial.normal)
        assertEquals(draft.dh, reloaded.initial.dh)
        assertEquals(draft.starters.filterNotNull(), reloaded.initial.starters.filterNotNull())
        assertEquals(draft.relievers.filterNotNull(), reloaded.initial.relievers.filterNotNull())
        assertEquals(PlayerSquad.FIRST, reloaded.player(farmFielder.id).initialSquad)
        assertEquals(PlayerSquad.SECOND, reloaded.player(demoted.id).initialSquad)
        assertEquals(10, reloaded.player(demoted.id).restDays)
        assertEquals(55, reloaded.initial.aptitude(catcher, FieldSlot.FIRST))
        assertEquals(0, reloaded.initial.aptitude(catcher, FieldSlot.CATCHER))
        assertEquals(PlayerPosition.FIRST_BASEMAN, reloaded.initial.mainPosition(catcher))
        assertEquals(12, reloaded.initial.pitchingAptitude(reliever).starter)
        assertTrue(reloaded.notices.isEmpty(), reloaded.notices.toString())
        assertEquals(readinessBefore, query(database, "SELECT SENSYU_ID, SENPATUTYOUSEI FROM SENSYU_DATA ORDER BY SENSYU_ID"))
        assertEquals(listOf(listOf(1)), query(database, "SELECT COUNT(*) FROM KIYO WHERE HIKAEDH12 = 0"))
        val dhBench = query(database, "SELECT ${(1..11).joinToString { "HIKAEDH$it" }} FROM KIYO WHERE HIKAEDH12 = 0").single()
        assertEquals(LineupEditing(reloaded).bench(reloaded.initial, LineupMode.DH).take(11).map { it.id }, dhBench.filter { it != 0 })
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun changesMadeAfterLoadingStopTheSave() {
        val database = copyReference()
        val snapshot = load(database)
        val draft = LineupEditing(snapshot).swapLineup(snapshot.initial, LineupMode.DH, 0, 1)
        update(database, "UPDATE SENSYU_DATA SET TOROKU = 5 WHERE SENSYU_ID = ${snapshot.players.first().id}")
        val before = query(database, "SELECT * FROM KIYO WHERE HIKAEDH12 = 0")

        val result = DerbyLineupWriter(access) { }.save(snapshot, draft)

        assertEquals(LineupSaveResult.Conflict, result)
        assertEquals(before, query(database, "SELECT * FROM KIYO WHERE HIKAEDH12 = 0"))
    }

    @Test
    fun backupFailureLeavesTheSaveUntouched() {
        val database = copyReference()
        val snapshot = load(database)
        val draft = LineupEditing(snapshot).swapLineup(snapshot.initial, LineupMode.DH, 0, 1)
        val before = query(database, "SELECT * FROM KIYO WHERE HIKAEDH12 = 0")
        val failing = SaveDatabaseBackup { throw IOException("disk full") }

        val result = DerbyLineupWriter(access, failing).save(snapshot, draft)

        assertInstanceOf(LineupSaveResult.BackupFailed::class.java, result)
        assertEquals(before, query(database, "SELECT * FROM KIYO WHERE HIKAEDH12 = 0"))
    }

    @Test
    fun failedWriteRollsBackEveryStatement() {
        val database = copyReference()
        val before = query(database, "SELECT DAJUN1 FROM KIYO WHERE HIKAEDH12 = 0")

        val result =
            access.write(database, "rollback test") { connection ->
                connection.createStatement().use { it.executeUpdate("UPDATE KIYO SET DAJUN1 = 0 WHERE HIKAEDH12 = 0") }
                error("fail after update")
            }

        assertInstanceOf(SaveDatabaseResult.Failed::class.java, result)
        assertEquals(before, query(database, "SELECT DAJUN1 FROM KIYO WHERE HIKAEDH12 = 0"))
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @ParameterizedTest
    @ValueSource(ints = [1317, 1548])
    fun contractRegistrationSynchronizesCurrentPlayerYearAndPreservesHistory(playerId: Int) {
        val database = copyReference()
        val snapshot = load(database)
        val player = snapshot.player(playerId)
        assertEquals(PlayerRegistration.DEVELOPMENT, player.initialRegistration)
        assertEquals(4, LineupContracts(snapshot).counts(snapshot.initial).development)
        val historical = seasonRows(database, playerId).filter { it[0] != player.year }
        val identity = query(database, "SELECT SENSYU_ID, NENME, GENINFLG, TOROKU FROM SENSYU_DATA WHERE SENSYU_ID = $playerId")
        val salary = seasonSalary(database, playerId)
        val abilities = query(database, "SELECT * FROM SENSYU_NORYOKU WHERE SENSYU_ID = $playerId ORDER BY NENME")
        val draft = (LineupContracts(snapshot).change(snapshot.initial, playerId) as EditResult.Applied).draft
        val backup = database.parent.resolve("contract-backup")

        assertEquals(LineupSaveResult.Saved, DerbyLineupWriter(access, LatestSaveDatabaseBackup(backup)).save(snapshot, draft))

        val reloaded = load(database)
        assertEquals(draft.contract(playerId), reloaded.initial.contract(playerId))
        assertEquals(PlayerSquad.SECOND, reloaded.player(playerId).initialSquad)
        val number = draft.contract(playerId).uniformNumber
        val current = seasonRows(database, playerId).filter { it[0] == player.year }
        assertTrue(current.isNotEmpty())
        assertTrue(current.all { it[2] == number })
        if (player.isPitcher) assertTrue(current.size >= 4, current.toString())
        assertEquals(historical, seasonRows(database, playerId).filter { it[0] != player.year })
        assertEquals(
            identity,
            query(database, "SELECT SENSYU_ID, NENME, GENINFLG, TOROKU FROM SENSYU_DATA WHERE SENSYU_ID = $playerId"),
        )
        assertEquals(salary, seasonSalary(database, playerId))
        assertEquals(abilities, query(database, "SELECT * FROM SENSYU_NORYOKU WHERE SENSYU_ID = $playerId ORDER BY NENME"))
        assertTrue(Files.isRegularFile(backup.resolve("service.properties")))
    }

    @Test
    fun demotionToDevelopmentKeepsTheExistingTenDayWaitAndSeasonNumbers() {
        val database = copyReference()
        update(database, "UPDATE SAVE SET SYSDAY = 100 WHERE MYTEAM = 6")
        val snapshot = load(database)
        val editing = LineupEditing(snapshot)
        val player =
            snapshot.players.first {
                it.initialSquad == PlayerSquad.FIRST &&
                    !it.isPitcher &&
                    !snapshot.initial.inAnyLineup(it.id) &&
                    !snapshot.initial.inAnyRole(it.id)
            }
        val demoted = (editing.demote(snapshot.initial, player.id) as EditResult.Applied).draft
        val draft = (LineupContracts(snapshot).change(demoted, player.id) as EditResult.Applied).draft

        val writer = DerbyLineupWriter(access, LatestSaveDatabaseBackup(database.parent.resolve("backup")))
        assertEquals(LineupSaveResult.Saved, writer.save(snapshot, draft))

        val reloaded = load(database)
        assertEquals(PlayerRegistration.DEVELOPMENT, reloaded.player(player.id).initialRegistration)
        assertEquals(PlayerSquad.SECOND, reloaded.player(player.id).initialSquad)
        assertEquals(10, reloaded.player(player.id).restDays)
        assertEquals(101, reloaded.player(player.id).uniformNumber)
        assertTrue(seasonRows(database, player.id).filter { it[0] == player.year }.all { it[2] == 101 })
    }

    @ParameterizedTest
    @ValueSource(strings = ["number", "day", "year", "join", "leave", "stats", "delete", "add"])
    fun contractSaveDetectsExternalChanges(change: String) {
        val database = copyReference()
        val snapshot = load(database)
        val draft = (LineupContracts(snapshot).change(snapshot.initial, 1317) as EditResult.Applied).draft
        val year = snapshot.player(1317).year
        val sql =
            when (change) {
                "number" -> "UPDATE SENSYU_DATA SET SENSYU_NUM = 10512 WHERE SENSYU_ID = 1317"
                "day" -> "UPDATE SAVE SET SYSDAY = 310 WHERE MYTEAM = 6"
                "year" -> "UPDATE SAVE SET SYSNENME = 6 WHERE MYTEAM = 6"
                "join" ->
                    "UPDATE SENSYU_DATA SET SENSYU_NUM = 10590 WHERE SENSYU_ID = " +
                        "(SELECT MIN(SENSYU_ID) FROM SENSYU_DATA WHERE SENSYU_NUM BETWEEN 0 AND 99 AND GENINFLG = 1)"
                "leave" -> "UPDATE SENSYU_DATA SET GENINFLG = 0 WHERE SENSYU_ID = 1317"
                "stats" -> "UPDATE TOUSYU_SEISEKI SET SEBANGOU = 199 WHERE NUM = 1317 AND NENME = $year"
                "delete" -> "DELETE FROM YASYU_SEISEKI WHERE NUM = 1317 AND NENME = $year"
                else -> "INSERT INTO YASYU_SEISEKI (NUM, NENME, ITINIGUN, SEBANGOU) VALUES (1317, $year, 3, 111)"
            }
        update(database, sql)
        val before = load(database).fingerprint

        assertEquals(LineupSaveResult.Conflict, DerbyLineupWriter(access) { error("backup should not run") }.save(snapshot, draft))
        assertEquals(before, load(database).fingerprint)
    }

    @Test
    fun changesDuringBackupAreDetectedAgainInsideTheTransaction() {
        val database = copyReference()
        val snapshot = load(database)
        val draft = (LineupContracts(snapshot).change(snapshot.initial, 1317) as EditResult.Applied).draft
        val before = query(database, "SELECT SENSYU_NUM FROM SENSYU_DATA WHERE SENSYU_ID = 1317")
        val result = DerbyLineupWriter(access) { update(database, "UPDATE SAVE SET SYSDAY = 310 WHERE MYTEAM = 6") }.save(snapshot, draft)
        assertEquals(LineupSaveResult.Conflict, result)
        assertEquals(before, query(database, "SELECT SENSYU_NUM FROM SENSYU_DATA WHERE SENSYU_ID = 1317"))
    }

    @Test
    fun failedPitchingSeasonSyncRollsBackContractBattingStatsAndLineup() {
        val database = copyReference()
        val snapshot = load(database)
        val player = snapshot.player(1317)
        update(
            database,
            "ALTER TABLE TOUSYU_SEISEKI ADD CONSTRAINT CONTRACT_SYNC_TEST " +
                "CHECK (NUM <> 1317 OR NENME <> ${player.year} OR SEBANGOU = ${player.uniformNumber})",
        )
        var draft = (LineupContracts(snapshot).change(snapshot.initial, 1317) as EditResult.Applied).draft
        draft = LineupEditing(snapshot).swapLineup(draft, LineupMode.DH, 0, 1)
        val before = load(database).fingerprint
        val stats = seasonRows(database, 1317)

        val result = DerbyLineupWriter(access, LatestSaveDatabaseBackup(database.parent.resolve("backup"))).save(snapshot, draft)

        assertInstanceOf(LineupSaveResult.Failed::class.java, result)
        assertEquals(before, load(database).fingerprint)
        assertEquals(stats, seasonRows(database, 1317))
        assertTrue(Files.notExists(database.resolve("db.lck")))
    }

    @Test
    fun writerRejectsInvalidContractsBeforeBackupOrDatabaseUpdates() {
        val database = copyReference()
        update(database, "UPDATE SAVE SET SYSDAY = 213 WHERE MYTEAM = 6")
        val snapshot = load(database)
        val draft = snapshot.initial.withState(1317) { it.copy(contract = PlayerContract(PlayerRegistration.CONTROLLED, 0)) }
        val result = DerbyLineupWriter(access) { error("invalid contract reached backup") }.save(snapshot, draft)
        assertInstanceOf(LineupSaveResult.InvalidChanges::class.java, result)
        assertEquals(snapshot.fingerprint, load(database).fingerprint)
    }

    private fun seasonSalary(
        database: Path,
        playerId: Int,
    ): List<List<Int>> =
        DerbyLineupLoader.seasonTables.flatMap { table ->
            query(database, "SELECT NUM, NENME, ITINIGUN, NENPOU FROM $table WHERE NUM = $playerId ORDER BY NENME, ITINIGUN")
        }

    private fun seasonRows(
        database: Path,
        playerId: Int,
    ): List<List<Int>> =
        DerbyLineupLoader.seasonTables.flatMap { table ->
            query(database, "SELECT NENME, ITINIGUN, SEBANGOU FROM $table WHERE NUM = $playerId ORDER BY NENME, ITINIGUN")
        }

    private fun load(database: Path): LineupSnapshot = (DerbyLineupLoader(access).load(database) as LineupLoadResult.Loaded).snapshot

    private fun copyReference(): Path {
        val copied = CopiedDerbyTestDatabase.copyPrimaryReference()
        testDatabase = copied
        return copied.path
    }

    private fun update(
        database: Path,
        sql: String,
    ) {
        withConnection(database) { connection -> connection.createStatement().use { it.executeUpdate(sql) } }
    }

    private fun query(
        database: Path,
        sql: String,
    ): List<List<Int>> =
        withConnection(database) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    buildList { while (rows.next()) add((1..rows.metaData.columnCount).map(rows::getInt)) }
                }
            }
        }

    private fun <T> withConnection(
        database: Path,
        block: (Connection) -> T,
    ): T {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver")
        val url = "jdbc:derby:${database.toAbsolutePath().normalize()}"
        val value = DriverManager.getConnection("$url;create=false").use(block)
        try {
            DriverManager.getConnection("$url;shutdown=true").use(Connection::close)
            error("Derby DB shutdown did not throw the expected exception")
        } catch (error: SQLException) {
            assertEquals("08006", error.sqlState)
        }
        return value
    }
}
