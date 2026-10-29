package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.GameTeams
import io.github.munchi369.penautilitytoolnext.game.KiyoRows
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerNumberCode
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.game.PlayerStatusCode
import io.github.munchi369.penautilitytoolnext.game.SaveRows
import io.github.munchi369.penautilitytoolnext.player.abilityCells
import io.github.munchi369.penautilitytoolnext.player.playerProfileCells
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import io.github.munchi369.penautilitytoolnext.savedata.integers
import io.github.munchi369.penautilitytoolnext.savedata.nullableInt
import io.github.munchi369.penautilitytoolnext.savedata.requiredInt
import io.github.munchi369.penautilitytoolnext.savedata.rows
import java.nio.file.Path
import java.sql.Connection

class DerbyLineupLoader(
    private val databaseAccess: DerbySaveDatabaseAccess,
) : LineupLoader {
    override fun load(path: Path): LineupLoadResult =
        when (val result = databaseAccess.read(path, "起用の読み込み") { connection -> query(connection, path) }) {
            is SaveDatabaseResult.Success -> result.value
            is SaveDatabaseResult.Failed -> LineupLoadResult.Failed(result.kind, result.technicalDetails)
        }

    private fun query(
        connection: Connection,
        path: Path,
    ): LineupLoadResult =
        try {
            val context =
                connection
                    .rows("SELECT MYTEAM, SYSDAY, SYSNENME FROM SAVE WHERE ${SaveRows.CURRENT_CONDITION}") { integers() }
                    .singleOrNull()
            val team = context?.get("MYTEAM")?.takeIf { it in GameTeams.domestic }
            val day = context?.get("SYSDAY")
            if (team == null || day == null) {
                LineupLoadResult.NoActiveTeam("SAVEの自チーム行: ${context ?: "1行ではありません"}")
            } else {
                read(connection, path, team, day)
            }
        } catch (error: IllegalArgumentException) {
            LineupLoadResult.InvalidData(error.message ?: "起用データを解釈できません")
        } catch (error: IllegalStateException) {
            LineupLoadResult.InvalidData(error.message ?: "起用データを解釈できません")
        }

    private fun read(
        connection: Connection,
        path: Path,
        team: Int,
        day: Int,
    ): LineupLoadResult {
        val players =
            connection
                .rows(teamPlayersSql(team)) {
                    val id = requiredInt("SENSYU_ID")
                    val values = integers()
                    val positionValue = requiredInt("POJI")
                    val position = PlayerPosition.fromDatabaseValue(positionValue) ?: error("POJI=$positionValue, 選手ID: $id")
                    val squadValue = requiredInt("ITINIGUN")
                    val squad = PlayerSquad.fromDatabaseValue(squadValue) ?: error("ITINIGUN=$squadValue, 選手ID: $id")
                    val injuryValue = requiredInt("KEGA")
                    val injury = PlayerInjury.fromDatabaseValue(injuryValue) ?: error("KEGA=$injuryValue, 選手ID: $id")
                    val number = requiredInt("SENSYU_NUM")
                    LineupPlayer(
                        id = id,
                        name = getString("NAME")?.takeIf(String::isNotBlank) ?: error("SENSYU_DATA.NAMEがありません: $id"),
                        uniformNumber = PlayerNumberCode.uniformNumber(number),
                        initialRegistration = PlayerNumberCode.registration(number),
                        year = requiredInt("NENME"),
                        position = position,
                        initialSquad = squad,
                        restDays = values["TOROKU"] ?: 0,
                        foreign = values["SHOZOKU"] == 0,
                        injury = injury,
                        age = requiredInt("NENREI"),
                        overall = requiredInt("SOUGOU"),
                        batting = values["KOUDA"] ?: 0,
                        power = values["POWER"] ?: 0,
                        starterAptitude = values["SENPATUTEKISEI"] ?: 0,
                        relieverAptitude = values["NAKATUGITEKISEI"] ?: 0,
                        initialAptitudes = FieldSlot.defense.associateWith { values[it.aptitudeColumn] ?: 0 },
                        cells =
                            playerProfileCells(values) + abilityCells(values, id, squad.takeIf { position == PlayerPosition.PITCHER }, day),
                    )
                }.sortedWith(compareBy({ it.position.databaseValue }, { it.uniformNumber }, { it.id }))
        val kiyo =
            connection.rows("SELECT * FROM KIYO WHERE ${KiyoRows.REGULAR_CONDITION}") { integers() }.singleOrNull()
                ?: error("KIYOの通常起用行（${KiyoRows.REGULAR_CONDITION}）が1行ではありません")
        val byId = players.associateBy { it.id }
        val notices = mutableListOf<String>()

        fun fielder(id: Int?): Int? =
            id?.takeIf {
                byId[it]?.let { player ->
                    !player.isPitcher &&
                        player.initialSquad == PlayerSquad.FIRST &&
                        player.initialRegistration == PlayerRegistration.CONTROLLED
                } ==
                    true
            }

        fun lineup(mode: LineupMode): List<LineupEntry> {
            val defenseColumns = if (mode == LineupMode.NORMAL) "POJI" else "POJIDH"
            val orderColumns = if (mode == LineupMode.NORMAL) "DAJUN" else "DAJUNDH"
            val slots = FieldSlot.defense + if (mode == LineupMode.DH) listOf(FieldSlot.DH) else emptyList()
            val defense =
                slots.mapIndexed { index, slot -> slot to fielder(kiyo["$defenseColumns${index + 2}"]) }.let { pairs ->
                    val seen = mutableSetOf<Int>()
                    pairs.map { (slot, id) -> slot to id?.takeIf(seen::add) }
                }
            val bySlot = defense.toMap()
            val slotOf = defense.mapNotNull { (slot, id) -> id?.let { it to slot } }.toMap()
            val stored = (1..9).map { kiyo["$orderColumns$it"] ?: 0 }
            val order = arrayOfNulls<LineupEntry>(9)
            val placed = mutableSetOf<FieldSlot>()
            stored.forEachIndexed { index, id ->
                val slot = slotOf[id]
                when {
                    slot != null && placed.add(slot) -> order[index] = LineupEntry(id, slot)
                    mode == LineupMode.NORMAL && FieldSlot.PITCHER !in placed && (id == 0 || byId[id]?.isPitcher == true) -> {
                        order[index] = LineupEntry(null, FieldSlot.PITCHER)
                        placed += FieldSlot.PITCHER
                    }
                }
            }
            val missing =
                (slots + if (mode == LineupMode.NORMAL) listOf(FieldSlot.PITCHER) else emptyList())
                    .filterNot(placed::contains)
                    .map { slot -> LineupEntry(bySlot[slot], slot) }
            missing.forEach { entry ->
                val index = if (entry.slot == FieldSlot.PITCHER && order[8] == null) 8 else order.indexOfFirst { it == null }
                order[index] = entry
            }
            return order.map { requireNotNull(it) }
        }

        fun roles(prefix: String): List<Int?> {
            val stored = (1..LineupRules.ROLE_SLOTS).map { kiyo["$prefix$it"] ?: 0 }.filter { it != 0 }
            val valid =
                stored
                    .filter {
                        byId[it]?.let { player ->
                            player.isPitcher &&
                                player.initialSquad == PlayerSquad.FIRST &&
                                player.initialRegistration == PlayerRegistration.CONTROLLED
                        } == true
                    }.distinct()
            if (valid.size != stored.size) notices += "1軍外の投手を役割から除外"
            return valid + List(LineupRules.ROLE_SLOTS - valid.size) { null }
        }

        val normal = lineup(LineupMode.NORMAL)
        val dh = lineup(LineupMode.DH)
        val starters = roles("SENPATU")
        val relievers = roles("NAKATUGI")
        val initial =
            LineupDraft(
                normal = normal,
                dh = dh,
                starters = starters,
                relievers = relievers,
                players = players.associate { it.id to it.initialState },
            )
        return LineupLoadResult.Loaded(
            LineupSnapshot(
                path = path.toAbsolutePath().normalize(),
                teamNumber = team,
                day = day,
                players = players,
                initial = initial,
                fingerprint = fingerprint(connection, team) ?: error("KIYOの通常起用行を読み込めません"),
                notices = notices.distinct(),
            ),
        )
    }

    internal companion object {
        val playerStateColumns =
            listOf("SENSYU_NUM", "ITINIGUN", "TOROKU") + FieldSlot.defense.map { requireNotNull(it.aptitudeColumn) } +
                listOf("POJI", "SENPATUTEKISEI", "NAKATUGITEKISEI")

        val playerFingerprintColumns = playerStateColumns + listOf("NENME", "GENINFLG")
        val seasonTables = listOf("YASYU_SEISEKI", "TOUSYU_SEISEKI")

        /** Active own-team players, including developmental contracts. */
        fun teamPlayersSql(team: Int): String = "SELECT * FROM SENSYU_DATA WHERE ${teamPlayersCondition(team)}"

        private fun teamPlayersCondition(team: Int): String =
            "GENINFLG = ${PlayerStatusCode.ACTIVE} AND ${PlayerNumberCode.teamCondition(team)}"

        fun fingerprint(
            connection: Connection,
            team: Int,
        ): LineupFingerprint? {
            val kiyo =
                connection.rows("SELECT * FROM KIYO WHERE ${KiyoRows.REGULAR_CONDITION}") { integers() }.singleOrNull() ?: return null
            val players =
                connection
                    .rows(teamPlayersSql(team)) { requiredInt("SENSYU_ID") to playerFingerprintColumns.map { getInt(it) } }
                    .toMap()
            val context =
                connection
                    .rows("SELECT MYTEAM, SYSDAY, SYSNENME FROM SAVE WHERE ${SaveRows.CURRENT_CONDITION}") { integers() }
                    .singleOrNull() ?: return null
            val numbers =
                seasonTables
                    .flatMap { table ->
                        connection.rows(
                            "SELECT NUM, NENME, ITINIGUN, SEBANGOU FROM $table " +
                                "WHERE EXISTS (SELECT 1 FROM SENSYU_DATA WHERE SENSYU_ID = $table.NUM " +
                                "AND SENSYU_DATA.NENME = $table.NENME AND ${teamPlayersCondition(team)})",
                        ) {
                            SeasonNumberKey(table, requiredInt("NUM"), requiredInt("NENME"), requiredInt("ITINIGUN")) to
                                nullableInt("SEBANGOU")
                        }
                    }.toMap()
            return LineupFingerprint(kiyo.mapValues { it.value ?: 0 }, players, context, numbers)
        }
    }
}
