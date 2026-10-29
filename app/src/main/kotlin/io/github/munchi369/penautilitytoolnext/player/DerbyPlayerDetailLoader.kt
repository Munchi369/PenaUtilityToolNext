package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.game.PlayerNumberCode
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerStatusCode
import io.github.munchi369.penautilitytoolnext.game.SaveRows
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import io.github.munchi369.penautilitytoolnext.savedata.integers
import io.github.munchi369.penautilitytoolnext.savedata.rows
import io.github.munchi369.penautilitytoolnext.savedata.saveDatabaseKey
import io.github.munchi369.penautilitytoolnext.savedata.strings
import java.nio.file.Path
import java.sql.Connection

class DerbyPlayerDetailLoader(
    private val databaseAccess: DerbySaveDatabaseAccess,
) : PlayerDetailLoader {
    private data class RecordHighsKey(
        val database: String,
        val season: Int,
        val day: Int,
    )

    @Volatile private var recordHighsCache: Pair<RecordHighsKey, DetailSeasonRecordHighs>? = null

    override fun load(
        path: Path,
        playerId: Int,
    ): PlayerDetailLoadResult =
        when (val result = databaseAccess.read(path, "選手詳細の読み込み") { query(it, path, playerId) }) {
            is SaveDatabaseResult.Success -> PlayerDetailLoadResult.Loaded(result.value)
            is SaveDatabaseResult.Failed ->
                PlayerDetailLoadResult.Failed(result.kind.message(PlayerDetailLoadResult.UNAVAILABLE_MESSAGE), result.technicalDetails)
        }

    private fun query(
        connection: Connection,
        path: Path,
        id: Int,
    ): PlayerDetailData {
        val season =
            detailSection {
                connection
                    .detailRows("SELECT SYSNENME FROM SAVE WHERE ${SaveRows.CURRENT_CONDITION}")
                    .single()
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
                val major = row.int("GENINFLG") == PlayerStatusCode.MAJOR
                val team =
                    if (major) {
                        "メジャー"
                    } else {
                        connection
                            .detailRows("SELECT TEAMNAME FROM TEAM_INFO WHERE TEAMNO = ?", PlayerNumberCode.team(number))
                            .single()
                            .text("TEAMNAME")
                    }
                DetailProfile(
                    name = row.text("NAME"),
                    team = team,
                    number = PlayerNumberCode.uniformNumber(number),
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
            recordHighs = detailSection { recordHighs(connection, path) },
            contextErrors = listOfNotNull(season.error, start.error),
            teamNames =
                detailSection {
                    connection
                        .detailRows("SELECT TEAMNO, TEAMNAME FROM TEAM_INFO")
                        .associate { it.int("TEAMNO") to it.text("TEAMNAME") }
                },
        )
    }

    /** Season records only change when the game advances a day, so one save date shares a single scan of every season. */
    private fun recordHighs(
        connection: Connection,
        path: Path,
    ): DetailSeasonRecordHighs {
        val key =
            runCatching {
                val save = connection.detailRows("SELECT SYSNENME, SYSDAY FROM SAVE WHERE ${SaveRows.CURRENT_CONDITION}").single()
                RecordHighsKey(saveDatabaseKey(path), save.int("SYSNENME"), save.int("SYSDAY"))
            }.getOrNull()
        recordHighsCache?.let { (cachedKey, highs) -> if (cachedKey == key) return highs }
        return loadDetailSeasonRecordHighs(connection).also { if (key != null) recordHighsCache = key to it }
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
    vararg parameters: Int,
): List<DetailDatabaseRow> = rows(sql, *parameters) { DetailDatabaseRow(integers(), strings()) }

internal fun <T> detailSection(read: () -> T): DetailSection<T> =
    try {
        DetailSection(value = read())
    } catch (error: Exception) {
        DetailSection(error = "${error.javaClass.simpleName}: ${error.message}")
    }
