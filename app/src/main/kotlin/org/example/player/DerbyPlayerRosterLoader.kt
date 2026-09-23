package org.example.player

import org.example.savedata.DerbySaveDatabaseAccess
import org.example.savedata.SaveDatabaseReadResult
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types

class DerbyPlayerRosterLoader(
    private val databaseAccess: DerbySaveDatabaseAccess,
) : PlayerRosterLoader {
    override fun load(path: Path): PlayerRosterLoadResult =
        when (val result = databaseAccess.read(path, "現役選手一覧の読み込み", ::queryRoster)) {
            is SaveDatabaseReadResult.Success -> result.value
            is SaveDatabaseReadResult.Failed -> PlayerRosterLoadResult.Failed(result.kind, result.technicalDetails)
        }

    private fun queryRoster(connection: Connection): PlayerRosterLoadResult =
        try {
            val save = connection.rows("SELECT MYTEAM, SYSNENME, SYSDAY FROM SAVE WHERE MYTEAM >= 0 OR MYTEAM IS NULL") { integers() }
            val context = save.singleOrNull()
            val currentTeam = context?.get("MYTEAM")?.takeIf { it in 1..12 }
            val day = context?.get("SYSDAY")
            val teams =
                connection
                    .rows("SELECT TEAMNO, TEAMNAME FROM TEAM_INFO WHERE TEAMNO BETWEEN 1 AND 12") {
                        requiredInt("TEAMNO") to (getString("TEAMNAME") ?: error("TEAM_INFO.TEAMNAMEがありません"))
                    }.toMap()
            require(teams.size == 12) { "国内12球団の情報が揃っていません" }
            val batting = seasonRows(connection, "YASYU_SEISEKI", "NUM")
            val pitching = seasonRows(connection, "TOUSYU_SEISEKI", "NUM")
            val appearances = seasonRows(connection, "DAJUN_POJI", "ID")
            val battingService = serviceTotals(connection, "YASYU_SEISEKI")
            val pitchingService = serviceTotals(connection, "TOUSYU_SEISEKI")
            val players =
                connection
                    .rows("SELECT * FROM SENSYU_DATA WHERE GENINFLG IN (1, 3)") {
                        val id = requiredInt("SENSYU_ID")
                        val raw = requiredInt("SENSYU_NUM")
                        val major = requiredInt("GENINFLG") == 3
                        val team = if (major) 13 else raw % 10000 / 100 + 1
                        require(major || (raw >= 0 && (raw < 1200 || raw in 10000..11199))) { "SENSYU_NUM=$raw, 選手ID: $id" }
                        val positionValue = requiredInt("POJI")
                        val position = PlayerPosition.fromDatabaseValue(positionValue) ?: error("POJI=$positionValue, 選手ID: $id")
                        val squad =
                            when (val value = requiredInt("ITINIGUN")) {
                                1 -> PlayerSquad.FIRST
                                2 -> PlayerSquad.SECOND
                                else -> error("ITINIGUN=$value, 選手ID: $id")
                            }
                        val injury =
                            when (val value = requiredInt("KEGA")) {
                                0 -> PlayerInjury.None
                                in 1000..1999 -> PlayerInjury.Discomfort(value % 1000)
                                in 2000..2999 -> PlayerInjury.Injured(value % 1000)
                                else -> error("KEGA=$value, 選手ID: $id")
                            }
                        val values = integers()
                        val isPitcher = position == PlayerPosition.PITCHER
                        val information =
                            buildMap {
                                putAll(playerProfileCells(values))
                                (RosterColumns.battingAbility + RosterColumns.pitchingAbility).forEach { column ->
                                    values[column.key]?.let { value ->
                                        put(
                                            column.key,
                                            if (column.key ==
                                                "MAXMAX"
                                            ) {
                                                numberCell(value)
                                            } else {
                                                abilityCell(value)
                                            },
                                        )
                                    }
                                }
                                for (index in 1..5) {
                                    val pitch = values["HEN$index"]
                                    val amount = values["HEN${index}_DATA"]
                                    if (pitch == null || pitch == 0) {
                                        put("PITCH$index", PlayerCell(""))
                                    } else {
                                        require(pitch in 1..16) { "HEN$index=$pitch, 選手ID: $id" }
                                        put("PITCH$index", pitchAbilityCell(pitchNames[pitch], amount))
                                    }
                                }
                                values["HIROU"]?.let { put("HIROU", numberCell(it)) }
                                val stamina = values["SUTAMINA"]
                                val adjustment = values["SENPATUTYOUSEI"]
                                if (isPitcher && !major && stamina != null && adjustment != null && day != null) {
                                    put("READY", numberCell(readyStamina(stamina, adjustment, squad, day)))
                                }
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
                            uniformNumber = raw % 100 + raw / 10000 * 100,
                            name = name,
                            overall = overall,
                            position = position,
                            age = age,
                            squad = squad,
                            registration = if (!major && raw >= 10000) PlayerRegistration.DEVELOPMENT else PlayerRegistration.CONTROLLED,
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
                    home.filter {
                        it.squad ==
                            PlayerSquad.SECOND
                    },
                    players,
                    teams,
                    currentTeam,
                    context?.get("SYSNENME"),
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
            "SELECT S.* FROM $table S JOIN SENSYU_DATA P ON P.SENSYU_ID = S.$idColumn AND P.NENME = S.NENME WHERE P.GENINFLG IN (1, 3)",
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

    private fun ResultSet.requiredInt(column: String): Int {
        val value = getInt(column)
        check(!wasNull()) { "必須値がありません: $column" }
        return value
    }

    private fun ResultSet.integers(): Map<String, Int?> =
        buildMap {
            val metadata = metaData
            for (index in 1..metadata.columnCount) {
                if (metadata.getColumnType(index) == Types.INTEGER) {
                    val value = getInt(index)
                    put(metadata.getColumnName(index), if (wasNull()) null else value)
                }
            }
        }

    private fun <T> Connection.rows(
        sql: String,
        read: ResultSet.() -> T,
    ): List<T> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.read()) } }
        }
}
