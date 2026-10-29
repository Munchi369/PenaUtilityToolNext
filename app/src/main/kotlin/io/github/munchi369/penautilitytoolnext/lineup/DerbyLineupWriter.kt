package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.KiyoRows
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseBackup
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import io.github.munchi369.penautilitytoolnext.savedata.toTechnicalDetails
import java.io.IOException
import java.io.UncheckedIOException
import java.sql.Connection

class DerbyLineupWriter(
    private val databaseAccess: DerbySaveDatabaseAccess,
    private val backup: SaveDatabaseBackup,
) : LineupSaver {
    override fun save(
        snapshot: LineupSnapshot,
        draft: LineupDraft,
    ): LineupSaveResult {
        LineupEditing(snapshot).validate(draft).firstOrNull()?.let { return LineupSaveResult.InvalidChanges(it) }
        val path = snapshot.path
        when (val probe = databaseAccess.read(path, "保存前の確認") { DerbyLineupLoader.fingerprint(it, snapshot.teamNumber) }) {
            is SaveDatabaseResult.Failed -> return LineupSaveResult.Failed(probe.kind, probe.technicalDetails)
            is SaveDatabaseResult.Success -> if (probe.value != snapshot.fingerprint) return LineupSaveResult.Conflict
        }
        try {
            backup.backup(path)
        } catch (error: UncheckedIOException) {
            return LineupSaveResult.BackupFailed(error.toTechnicalDetails("セーブデータのバックアップ: $path"))
        } catch (error: IOException) {
            return LineupSaveResult.BackupFailed(error.toTechnicalDetails("セーブデータのバックアップ: $path"))
        }
        val result =
            databaseAccess.write(path, "起用の保存") { connection ->
                if (DerbyLineupLoader.fingerprint(connection, snapshot.teamNumber) != snapshot.fingerprint) {
                    return@write LineupSaveResult.Conflict
                }
                updateKiyo(connection, kiyoValues(snapshot, draft))
                updatePlayers(connection, snapshot, draft)
                LineupSaveResult.Saved
            }
        return when (result) {
            is SaveDatabaseResult.Success ->
                if (result.value == LineupSaveResult.Saved && result.releaseWarning != null) {
                    LineupSaveResult.SavedWithWarning(result.releaseWarning)
                } else {
                    result.value
                }
            is SaveDatabaseResult.Failed -> LineupSaveResult.Failed(result.kind, result.technicalDetails)
        }
    }

    private fun updateKiyo(
        connection: Connection,
        values: Map<String, Int>,
    ) {
        val columns = values.keys.toList()
        val assignments = columns.joinToString { "$it = ?" }
        connection.prepareStatement("UPDATE KIYO SET $assignments WHERE ${KiyoRows.REGULAR_CONDITION}").use { statement ->
            columns.forEachIndexed { index, column -> statement.setInt(index + 1, values.getValue(column)) }
            check(statement.executeUpdate() == 1) { "KIYOの通常起用行を1行だけ更新できませんでした" }
        }
    }

    private fun updatePlayers(
        connection: Connection,
        snapshot: LineupSnapshot,
        draft: LineupDraft,
    ) {
        val stored = snapshot.fingerprint.players
        snapshot.players.forEach { player ->
            val squad = draft.squad(player.id)
            val restDays =
                if (player.initialSquad == PlayerSquad.FIRST && squad == PlayerSquad.SECOND) {
                    LineupRules.restDaysAfterDemotion(snapshot.day) ?: player.restDays
                } else {
                    player.restDays
                }
            val pitching = draft.pitchingAptitude(player.id)
            val next =
                mapOf(
                    "SENSYU_NUM" to draft.contract(player.id).encodedNumber(snapshot.teamNumber),
                    "ITINIGUN" to squad.databaseValue,
                    "TOROKU" to restDays,
                    "POJI" to draft.mainPosition(player.id).databaseValue,
                    "SENPATUTEKISEI" to pitching.starter,
                    "NAKATUGITEKISEI" to pitching.reliever,
                ) + FieldSlot.defense.associate { requireNotNull(it.aptitudeColumn) to draft.aptitude(player.id, it) }
            check(next.keys == DerbyLineupLoader.playerStateColumns.toSet()) { "SENSYU_DATAの更新列が読み込み列と一致しません" }
            val current = DerbyLineupLoader.playerFingerprintColumns.zip(stored.getValue(player.id)).toMap()
            val changed = DerbyLineupLoader.playerStateColumns.filter { next.getValue(it) != current.getValue(it) }
            if (changed.isEmpty()) return@forEach
            val assignments = changed.joinToString { "$it = ?" }
            connection.prepareStatement("UPDATE SENSYU_DATA SET $assignments WHERE SENSYU_ID = ?").use { statement ->
                changed.forEachIndexed { index, column -> statement.setInt(index + 1, next.getValue(column)) }
                statement.setInt(changed.size + 1, player.id)
                check(statement.executeUpdate() == 1) { "SENSYU_DATAを更新できませんでした: ${player.id}" }
            }
            if (draft.contract(player.id) != player.initialContract) {
                updateSeasonNumbers(connection, snapshot, player, draft.contract(player.id).uniformNumber)
            }
        }
    }

    private fun updateSeasonNumbers(
        connection: Connection,
        snapshot: LineupSnapshot,
        player: LineupPlayer,
        number: Int,
    ) {
        DerbyLineupLoader.seasonTables.forEach { table ->
            val expected =
                snapshot.fingerprint.seasonNumbers.keys.count {
                    it.table == table && it.playerId == player.id && it.year == player.year
                }
            connection.prepareStatement("UPDATE $table SET SEBANGOU = ? WHERE NUM = ? AND NENME = ?").use { statement ->
                statement.setInt(1, number)
                statement.setInt(2, player.id)
                statement.setInt(3, player.year)
                check(statement.executeUpdate() == expected) { "成績の背番号を更新できませんでした: $table / ${player.id}" }
            }
        }
    }

    internal companion object {
        fun kiyoValues(
            snapshot: LineupSnapshot,
            draft: LineupDraft,
        ): Map<String, Int> =
            buildMap {
                val editing = LineupEditing(snapshot)
                LineupMode.entries.forEach { mode ->
                    val entries = draft.lineup(mode)
                    val defense = if (mode == LineupMode.NORMAL) "POJI" else "POJIDH"
                    val order = if (mode == LineupMode.NORMAL) "DAJUN" else "DAJUNDH"
                    val bench = if (mode == LineupMode.NORMAL) "HIKAE" else "HIKAEDH"
                    val benchSlots = if (mode == LineupMode.NORMAL) LineupRules.NORMAL_BENCH_SLOTS else LineupRules.DH_BENCH_SLOTS
                    val slots = FieldSlot.defense + if (mode == LineupMode.DH) listOf(FieldSlot.DH) else emptyList()
                    slots.forEachIndexed { index, slot ->
                        put("$defense${index + 2}", entries.firstOrNull { it.slot == slot }?.playerId ?: 0)
                    }
                    entries.forEachIndexed { index, entry -> put("$order${index + 1}", entry.playerId ?: 0) }
                    val benchIds = editing.bench(draft, mode).map { it.id }
                    (1..benchSlots).forEach { put("$bench$it", benchIds.getOrElse(it - 1) { 0 }) }
                }
                listOf(PitchingRole.STARTER to "SENPATU", PitchingRole.RELIEVER to "NAKATUGI").forEach { (role, prefix) ->
                    val ids = draft.roles(role).filterNotNull()
                    (1..LineupRules.ROLE_SLOTS).forEach { put("$prefix$it", ids.getOrElse(it - 1) { 0 }) }
                }
            }
    }
}
