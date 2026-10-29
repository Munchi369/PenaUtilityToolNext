package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.lineup.SyntheticLineupDatabase.OTHER_TEAM_PLAYER
import io.github.munchi369.penautilitytoolnext.savedata.CopiedDerbyTestDatabase
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.OptionalSaveDatabaseBackup
import io.github.munchi369.penautilitytoolnext.savedata.PropertiesSaveDatabaseSettingsStore
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/** Lineup saving against a synthetic Derby DB, so the only game-DB writer is covered without the Reference DB. */
class SyntheticLineupDatabaseTest {
    private val access = DerbySaveDatabaseAccess()

    @Test
    fun savingWithoutBackupCommitsAndEnablingBackupStopsSavingIfCopyFails(
        @TempDir root: Path,
    ) {
        SyntheticLineupDatabase.create().use { database ->
            val store = PropertiesSaveDatabaseSettingsStore(root.resolve("settings.properties"))
            var backups = 0
            val backup =
                OptionalSaveDatabaseBackup(store) {
                    backups++
                    throw IOException("copy failed")
                }
            val snapshot = load(database.path)
            val draft = LineupEditing(snapshot).swapLineup(snapshot.initial, LineupMode.DH, 0, 1)
            assertEquals(LineupSaveResult.Saved, DerbyLineupWriter(access, backup).save(snapshot, draft))
            assertEquals(0, backups)
            val reloaded = load(database.path)
            assertEquals(draft.dh, reloaded.initial.dh)
            store.save(SaveDatabaseSettings(null, backupEnabled = true))
            val changed = LineupEditing(reloaded).swapLineup(reloaded.initial, LineupMode.DH, 0, 1)
            assertTrue(DerbyLineupWriter(access, backup).save(reloaded, changed) is LineupSaveResult.BackupFailed)
            assertEquals(1, backups)
            assertEquals(reloaded.initial.dh, load(database.path).initial.dh)
        }
    }

    @Test
    fun committedLineupIsReportedAsSavedWithWarningWhenShutdownFails() {
        SyntheticLineupDatabase.create().use { database ->
            val snapshot = load(database.path)
            val draft = LineupEditing(snapshot).swapLineup(snapshot.initial, LineupMode.DH, 0, 1)
            var shutdowns = 0
            val failingAccess =
                DerbySaveDatabaseAccess { url ->
                    if (url.endsWith(";shutdown=true") && ++shutdowns == 2) throw SQLException("shutdown failed", "XTEST")
                    DriverManager.getConnection(url)
                }

            val result = DerbyLineupWriter(failingAccess) {}.save(snapshot, draft)
            val reloaded = load(database.path)

            assertTrue(result is LineupSaveResult.SavedWithWarning)
            assertEquals(draft.dh, reloaded.initial.dh)
        }
    }

    @Test
    fun savingWritesOnlyTheOwnTeamsRegularLineupAndCurrentSeasonNumbers() {
        SyntheticLineupDatabase.create().use { database ->
            val snapshot = load(database.path)
            assertTrue(snapshot.notices.isEmpty(), snapshot.notices.toString())
            val editing = LineupEditing(snapshot)
            var draft = editing.swapLineup(snapshot.initial, LineupMode.NORMAL, 0, 1)
            draft = (editing.demote(draft, 116) as EditResult.Applied).draft
            draft = (LineupContracts(snapshot).change(draft, 116) as EditResult.Applied).draft
            draft = editing.setAptitude(draft, 101, FieldSlot.FIRST, 55)
            draft = editing.setMainPosition(draft, 101, FieldSlot.FIRST)
            draft = editing.setPitchingAptitude(draft, 7, PitchingRole.STARTER, 12)
            draft = editing.swapRoles(draft, PitchingRole.STARTER, 0, PitchingRole.RELIEVER, 0)
            val untouched =
                listOf(
                    "SELECT ${DerbyLineupLoader.playerFingerprintColumns.joinToString()} FROM SENSYU_DATA WHERE SENSYU_ID = $OTHER_TEAM_PLAYER",
                    "SELECT * FROM KIYO WHERE HIKAEDH12 = 3",
                    "SELECT * FROM YASYU_SEISEKI WHERE NENME = 1 OR NUM = $OTHER_TEAM_PLAYER ORDER BY NUM, NENME",
                    "SELECT * FROM SAVE ORDER BY MYTEAM",
                )
            val before = untouched.map { query(database.path, it) }
            var backups = 0

            val result = DerbyLineupWriter(access) { backups++ }.save(snapshot, draft)

            assertEquals(LineupSaveResult.Saved, result)
            assertEquals(1, backups)
            val reloaded = load(database.path)
            assertEquals(draft.normal, reloaded.initial.normal)
            assertEquals(draft.dh, reloaded.initial.dh)
            assertEquals(draft.starters, reloaded.initial.starters)
            assertEquals(draft.relievers, reloaded.initial.relievers)
            assertEquals(PlayerSquad.SECOND, reloaded.player(116).initialSquad)
            assertEquals(10, reloaded.player(116).restDays)
            assertEquals(PlayerRegistration.DEVELOPMENT, reloaded.player(116).initialRegistration)
            val number = draft.contract(116).uniformNumber
            assertEquals(number, reloaded.player(116).uniformNumber)
            assertEquals(listOf(listOf(number)), query(database.path, "SELECT SEBANGOU FROM YASYU_SEISEKI WHERE NUM = 116 AND NENME = 2"))
            assertEquals(55, reloaded.initial.aptitude(101, FieldSlot.FIRST))
            assertEquals(PlayerPosition.FIRST_BASEMAN, reloaded.initial.mainPosition(101))
            assertEquals(12, reloaded.initial.pitchingAptitude(7).starter)
            assertEquals(draft.players, reloaded.initial.players)
            assertEquals(before, untouched.map { query(database.path, it) })
            assertTrue(Files.notExists(database.path.resolve("db.lck")))
        }
    }

    @Test
    fun changesMadeAfterLoadingStopTheSave() {
        SyntheticLineupDatabase.create().use { database ->
            val snapshot = load(database.path)
            val draft = LineupEditing(snapshot).swapLineup(snapshot.initial, LineupMode.DH, 0, 1)
            update(database.path, "UPDATE SENSYU_DATA SET TOROKU = 5 WHERE SENSYU_ID = 1")
            val before = query(database.path, "SELECT * FROM KIYO ORDER BY HIKAEDH12")

            val result = DerbyLineupWriter(access) { error("a conflicting save reached the backup") }.save(snapshot, draft)

            assertEquals(LineupSaveResult.Conflict, result)
            assertEquals(before, query(database.path, "SELECT * FROM KIYO ORDER BY HIKAEDH12"))
        }
    }

    @Test
    fun syntheticColumnsMatchTheReferenceDatabase() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { reference ->
            SyntheticLineupDatabase.create().use { synthetic ->
                val expected = columns(synthetic.path)
                val actual = columns(reference.path).filterKeys { it in expected }
                expected.forEach { (column, type) -> assertEquals(type, actual[column], column) }
            }
        }
    }

    private fun columns(database: Path): Map<String, String> =
        withConnection(database) { connection ->
            buildMap {
                SyntheticLineupDatabase.tables.keys.forEach { table ->
                    connection.metaData.getColumns(null, null, table, null).use { rows ->
                        while (rows.next()) {
                            put(
                                "$table.${rows.getString("COLUMN_NAME")}",
                                "${rows.getString("TYPE_NAME")}(${rows.getInt("COLUMN_SIZE")}) nullable=${rows.getInt("NULLABLE")}",
                            )
                        }
                    }
                }
            }
        }

    private fun load(database: Path): LineupSnapshot = (DerbyLineupLoader(access).load(database) as LineupLoadResult.Loaded).snapshot

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
        val url = "jdbc:derby:${database.toAbsolutePath().normalize()}"
        val value = DriverManager.getConnection("$url;create=false").use(block)
        try {
            DriverManager.getConnection("$url;shutdown=true").close()
            error("Derby DB shutdown did not throw the expected exception")
        } catch (error: SQLException) {
            assertEquals("08006", error.sqlState)
        }
        return value
    }
}
