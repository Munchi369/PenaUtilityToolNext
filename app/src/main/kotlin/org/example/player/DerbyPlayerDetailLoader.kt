package org.example.player

import org.example.savedata.DerbySaveDatabaseAccess
import org.example.savedata.SaveDatabaseFailureKind
import org.example.savedata.SaveDatabaseReadResult
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types

class DerbyPlayerDetailLoader(
    private val databaseAccess: DerbySaveDatabaseAccess,
) : PlayerDetailLoader {
    override fun load(
        path: Path,
        playerId: Int,
    ): PlayerDetailLoadResult =
        when (val result = databaseAccess.read(path, "選手詳細の読み込み") { query(it, playerId) }) {
            is SaveDatabaseReadResult.Success -> PlayerDetailLoadResult.Loaded(result.value)
            is SaveDatabaseReadResult.Failed ->
                PlayerDetailLoadResult.Failed(
                    when (result.kind) {
                        SaveDatabaseFailureKind.IN_USE -> "ゲームまたは別ツールがセーブデータを使用中です"
                        SaveDatabaseFailureKind.NOT_SAVE_DATABASE -> "選択したフォルダーはセーブデータではありません"
                        SaveDatabaseFailureKind.UNAVAILABLE -> "選手詳細を読み込めません"
                    },
                    result.technicalDetails,
                )
        }

    private fun query(
        connection: Connection,
        id: Int,
    ): PlayerDetailData {
        val season =
            detailSection {
                connection
                    .detailRows(
                        "SELECT SYSNENME FROM SAVE WHERE MYTEAM >= 0 OR MYTEAM IS NULL",
                    ).single()
                    .int("SYSNENME")
            }
        val start = detailSection { connection.detailRows("SELECT NENDO FROM SETTEI").single().int("NENDO") }
        val raw =
            detailSection {
                connection.detailRows("SELECT * FROM SENSYU_DATA WHERE SENSYU_ID = ?", id).singleOrNull()
                    ?: error("選手がセーブデータ内に見つかりません: $id")
            }
        val profile =
            detailSection {
                val row = raw.value ?: error(raw.error ?: "選手基本情報を取得できません")
                val number = row.int("SENSYU_NUM")
                val major = row.int("GENINFLG") == 3
                val team =
                    if (major) {
                        "メジャー"
                    } else {
                        connection
                            .detailRows("SELECT TEAMNAME FROM TEAM_INFO WHERE TEAMNO = ?", number % 10000 / 100 + 1)
                            .single()
                            .text("TEAMNAME")
                    }
                DetailProfile(
                    name = row.text("NAME"),
                    team = team,
                    number = number % 100 + number / 10000 * 100,
                    age = row.numbers["NENREI"],
                    position = PlayerPosition.fromDatabaseValue(row.int("POJI"))?.displayName ?: "不明",
                    playerYear = row.numbers["NENME"],
                    cells = playerProfileCells(row.numbers),
                )
            }
        val current = detailSection { detailAbilityCells(raw.value?.numbers ?: error(raw.error ?: "現在能力を取得できません")) }
        val abilities =
            detailSection {
                connection
                    .detailRows("SELECT * FROM SENSYU_NORYOKU WHERE SENSYU_ID = ? ORDER BY NENME", id)
                    .map { DetailAbilityRecord(it.int("NENME"), detailAbilityCells(it.numbers)) }
            }
        return PlayerDetailData(
            playerId = id,
            season = season.value,
            startYear = start.value,
            profile = profile,
            currentAbility = current,
            abilities = abilities,
            batting = detailSection { stats(connection, "YASYU_SEISEKI", id) },
            pitching = detailSection { stats(connection, "TOUSYU_SEISEKI", id) },
            monthlyBatting = detailSection { stats(connection, "YASYU_SEISEKI_GEKKAN", id, monthly = true) },
            monthlyPitching = detailSection { stats(connection, "TOUSYU_SEISEKI_GEKKAN", id, monthly = true) },
            appearances =
                detailSection {
                    connection
                        .detailRows("SELECT * FROM DAJUN_POJI WHERE ID = ?", id)
                        .map {
                            DetailStatRecord(0, it.int("NENME"), it.int("ITINIGUN"), it.numbers)
                        }.also { rows -> require(rows.distinctBy { it.playerYear to it.division }.size == rows.size) { "出場記録が重複しています" } }
                },
            honors = loadDetailHonors(connection, id),
            records = detailSection { loadDetailRecords(connection, id) },
            recordHighs = detailSection { loadDetailSeasonRecordHighs(connection) },
            contextErrors = listOfNotNull(season.error, start.error),
        )
    }

    private fun stats(
        connection: Connection,
        table: String,
        id: Int,
        monthly: Boolean = false,
    ): List<DetailStatRecord> =
        connection
            .detailRows("SELECT * FROM $table WHERE NUM = ? AND NENME > 0 ORDER BY NENDO, ITINIGUN", id)
            .map {
                val division = it.int("ITINIGUN")
                require(division in if (monthly) 4..9 else 1..3) { "$table の区分が不正です: $division" }
                DetailStatRecord(it.int("NENDO"), it.int("NENME"), division, it.numbers)
            }.also { rows ->
                val duplicate = rows.groupBy { it.playerYear to it.division }.entries.firstOrNull { it.value.size > 1 }
                require(duplicate == null) {
                    "$table に重複した記録があります（選手ID: $id、年目・区分: ${duplicate?.key}）"
                }
            }
}

internal data class DetailDatabaseRow(
    val numbers: Map<String, Int?>,
    val strings: Map<String, String?>,
) {
    fun int(key: String): Int = numbers[key] ?: error("必須値がありません: $key")

    fun text(key: String): String = strings[key]?.takeIf { it.isNotBlank() } ?: error("必須値がありません: $key")
}

internal fun Connection.detailRows(
    sql: String,
    id: Int? = null,
): List<DetailDatabaseRow> =
    prepareStatement(sql).use { statement ->
        if (id != null) statement.setInt(1, id)
        statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.detailRow()) } }
    }

private fun ResultSet.detailRow(): DetailDatabaseRow {
    val numbers = mutableMapOf<String, Int?>()
    val strings = mutableMapOf<String, String?>()
    for (index in 1..metaData.columnCount) {
        val key = metaData.getColumnLabel(index)
        if (metaData.getColumnType(index) in setOf(Types.INTEGER, Types.SMALLINT)) {
            val number = getInt(index)
            numbers[key] = if (wasNull()) null else number
        } else {
            strings[key] = getString(index)
        }
    }
    return DetailDatabaseRow(numbers, strings)
}

internal fun <T> detailSection(read: () -> T): DetailSection<T> =
    try {
        DetailSection(value = read())
    } catch (error: Exception) {
        DetailSection(error = "${error.javaClass.simpleName}: ${error.message}")
    }
