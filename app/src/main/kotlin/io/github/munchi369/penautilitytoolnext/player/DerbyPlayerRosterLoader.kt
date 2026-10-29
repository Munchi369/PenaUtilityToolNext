package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.game.GameTeams
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerNumberCode
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.game.PlayerStatusCode
import io.github.munchi369.penautilitytoolnext.game.SaveRows
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import io.github.munchi369.penautilitytoolnext.savedata.integers
import io.github.munchi369.penautilitytoolnext.savedata.requiredInt
import io.github.munchi369.penautilitytoolnext.savedata.rows
import io.github.munchi369.penautilitytoolnext.team.teamColor
import io.github.munchi369.penautilitytoolnext.ui.numberCell
import java.nio.file.Path
import java.sql.Connection

class DerbyPlayerRosterLoader(
    private val databaseAccess: DerbySaveDatabaseAccess,
) : PlayerRosterLoader {
    override fun load(path: Path): PlayerRosterLoadResult =
        when (val result = databaseAccess.read(path, "現役選手一覧の読み込み", ::queryRoster)) {
            is SaveDatabaseResult.Success -> result.value
            is SaveDatabaseResult.Failed -> PlayerRosterLoadResult.Failed(result.kind, result.technicalDetails)
        }

    private fun queryRoster(connection: Connection): PlayerRosterLoadResult =
        try {
            val save = connection.rows("SELECT MYTEAM, SYSNENME, SYSDAY FROM SAVE WHERE ${SaveRows.CURRENT_CONDITION}") { integers() }
            val context = save.singleOrNull()
            val currentTeam = context?.get("MYTEAM")?.takeIf { it in GameTeams.domestic }
            val day = context?.get("SYSDAY")
            val completedGames =
                if (day == null) {
                    emptyMap()
                } else {
                    connection
                        .rows(
                            "SELECT COUNT(CASE WHEN EVENT = 1 THEN 1 END) FIRST_GAMES, " +
                                "COUNT(CASE WHEN EVENT2 = 1 THEN 1 END) SECOND_GAMES FROM NITTEI WHERE DAY < ?",
                            day,
                        ) {
                            mapOf(1 to requiredInt("FIRST_GAMES"), 2 to requiredInt("SECOND_GAMES"))
                        }.single()
                }
            val teams =
                connection
                    .rows("SELECT TEAMNO, TEAMNAME FROM TEAM_INFO WHERE TEAMNO BETWEEN 1 AND ${GameTeams.DOMESTIC_COUNT}") {
                        requiredInt("TEAMNO") to (getString("TEAMNAME") ?: error("TEAM_INFO.TEAMNAMEがありません"))
                    }.toMap()
            require(teams.size == GameTeams.DOMESTIC_COUNT) { "国内12球団の情報が揃っていません" }
            val batting = seasonRows(connection, "YASYU_SEISEKI", "NUM")
            val pitching = seasonRows(connection, "TOUSYU_SEISEKI", "NUM")
            val appearances = seasonRows(connection, "DAJUN_POJI", "ID")
            val battingService = serviceTotals(connection, "YASYU_SEISEKI")
            val pitchingService = serviceTotals(connection, "TOUSYU_SEISEKI")
            val players =
                connection
                    .rows("SELECT * FROM SENSYU_DATA WHERE GENINFLG IN ${PlayerStatusCode.ACTIVE_OR_MAJOR_SQL}") {
                        val id = requiredInt("SENSYU_ID")
                        val raw = requiredInt("SENSYU_NUM")
                        val major = requiredInt("GENINFLG") == PlayerStatusCode.MAJOR
                        val team = if (major) GameTeams.MAJOR else PlayerNumberCode.team(raw)
                        require(major || PlayerNumberCode.isDomestic(raw)) { "SENSYU_NUM=$raw, 選手ID: $id" }
                        val positionValue = requiredInt("POJI")
                        val position = PlayerPosition.fromDatabaseValue(positionValue) ?: error("POJI=$positionValue, 選手ID: $id")
                        val squadValue = requiredInt("ITINIGUN")
                        val squad = PlayerSquad.fromDatabaseValue(squadValue) ?: error("ITINIGUN=$squadValue, 選手ID: $id")
                        val injuryValue = requiredInt("KEGA")
                        val injury = PlayerInjury.fromDatabaseValue(injuryValue) ?: error("KEGA=$injuryValue, 選手ID: $id")
                        val values = integers()
                        val isPitcher = position == PlayerPosition.PITCHER
                        val information =
                            buildMap {
                                putAll(playerProfileCells(values))
                                putAll(abilityCells(values, id, readySquad = squad.takeIf { isPitcher && !major }, day = day))
                                val service = if (isPitcher) pitchingService[id] else battingService[id]
                                val current = (if (isPitcher) pitching else batting)[id]?.get(1)?.get("ITIGUN")
                                values["SHOZOKU"]?.let { origin -> put("FA", faCell(origin, service ?: 0, current, major)) }
                            }
                        val name = getString("NAME") ?: error("SENSYU_DATA.NAMEがありません: $id")
                        val age = requiredInt("NENREI")
                        val overall = requiredInt("SOUGOU")
                        require(name.isNotBlank() && age >= 0 && overall >= 0) { "選手基本情報が不正です: $id" }
                        PlayerRosterPlayer(
                            playerId = id,
                            uniformNumber = PlayerNumberCode.uniformNumber(raw),
                            name = name,
                            overall = overall,
                            position = position,
                            age = age,
                            squad = squad,
                            registration = if (major) PlayerRegistration.CONTROLLED else PlayerNumberCode.registration(raw),
                            injury = injury,
                            teamNumber = team,
                            teamName = if (major) "メジャー" else teams.getValue(team),
                            major = major,
                            information = information,
                            batting =
                                batting[id].orEmpty().mapValues { (army, stats) ->
                                    GameSeasonMetrics.batting(stats) +
                                        appearances[id]
                                            ?.get(army)
                                            .orEmpty()
                                            .mapNotNull { (key, value) -> value?.let { key to numberCell(it) } }
                                            .toMap()
                                },
                            pitching = pitching[id].orEmpty().mapValues { (_, stats) -> GameSeasonMetrics.pitching(stats) },
                        )
                    }.sortedWith(compareBy({ it.position.databaseValue }, { it.uniformNumber }, { it.playerId }))
            val home = players.filter { !it.major && (currentTeam == null || it.teamNumber == currentTeam) }
            PlayerRosterLoadResult.Loaded(
                PlayerRoster(
                    home.filter { it.squad == PlayerSquad.FIRST },
                    home.filter { it.squad == PlayerSquad.SECOND },
                    players,
                    teams,
                    currentTeam,
                    context?.get("SYSNENME"),
                    GameTeams.domestic.mapNotNull { team -> connection.teamColor(team)?.let { team to it } }.toMap(),
                    completedGames,
                ),
            )
        } catch (error: IllegalArgumentException) {
            PlayerRosterLoadResult.InvalidData(error.message ?: "選手データを解釈できません")
        } catch (error: IllegalStateException) {
            PlayerRosterLoadResult.InvalidData(error.message ?: "選手データを解釈できません")
        }

    private fun seasonRows(
        connection: Connection,
        table: String,
        idColumn: String,
    ): Map<Int, Map<Int, Map<String, Int?>>> {
        val result = mutableMapOf<Int, MutableMap<Int, Map<String, Int?>>>()
        connection.rows(
            "SELECT S.* FROM $table S JOIN SENSYU_DATA P ON P.SENSYU_ID = S.$idColumn AND P.NENME = S.NENME WHERE P.GENINFLG IN ${PlayerStatusCode.ACTIVE_OR_MAJOR_SQL}",
        ) {
            val id = requiredInt(idColumn)
            val army = requiredInt("ITINIGUN")
            require(result.getOrPut(id) { mutableMapOf() }.put(army, integers()) == null) { "$table に重複した今季成績があります: $id / $army" }
        }
        return result
    }

    private fun serviceTotals(
        connection: Connection,
        table: String,
    ): Map<Int, Int> =
        connection
            .rows("SELECT NUM, SUM(COALESCE(ITIGUN, 0)) TOTAL FROM $table WHERE ITINIGUN = 1 GROUP BY NUM") {
                requiredInt("NUM") to requiredInt("TOTAL")
            }.toMap()
}
