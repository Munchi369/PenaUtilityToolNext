package org.example.player

import java.sql.Connection

/** All-time highs used to paint season cells red, matching the game's topflg==2 rules. */
data class DetailSeasonRecordHighs(
    val battingMax: Map<Int, Map<String, Int>> = emptyMap(),
    val pitchingMax: Map<Int, Map<String, Int>> = emptyMap(),
    val battingAvg: Map<Int, Map<String, Int>> = emptyMap(),
    val battingObp: Map<Int, Map<String, Int>> = emptyMap(),
    val battingOps: Map<Int, Map<String, Int>> = emptyMap(),
    val pitchingEra: Map<Int, Map<String, Int>> = emptyMap(),
)

private val battingCountKeys =
    listOf(
        "DASEKI",
        "DASUU",
        "ANDA",
        "NIRUIDA",
        "SANRUIDA",
        "HONRUIDA",
        "DATEN",
        "TOKUTEN",
        "SISI",
        "SANSIN",
        "TOURUIKIKAKU",
        "TOURUI",
        "GIDA",
        "GIHI",
        "HEISATU",
        "SISSAKU",
    )

private val pitchingCountKeys =
    listOf(
        "TOUBAN",
        "SENPATU",
        "NAKATUGI",
        "KATI",
        "MAKE",
        "H",
        "S",
        "KAISUU",
        "DASYA",
        "T_DASUU",
        "HIAN",
        "HIHON",
        "DASSAN",
        "YOSI",
        "SITTEN",
        "JISEKI",
        "KANTOU",
        "KANPUU",
        "BOUTOU",
    )

internal fun loadDetailSeasonRecordHighs(connection: Connection): DetailSeasonRecordHighs {
    val battingMax = mutableMapOf<Int, Map<String, Int>>()
    val pitchingMax = mutableMapOf<Int, Map<String, Int>>()
    val battingAvg = mutableMapOf<Int, Map<String, Int>>()
    val battingObp = mutableMapOf<Int, Map<String, Int>>()
    val battingOps = mutableMapOf<Int, Map<String, Int>>()
    val pitchingEra = mutableMapOf<Int, Map<String, Int>>()
    for (division in 1..3) {
        battingMax[division] = battingMaxRow(connection, division)
        pitchingMax[division] = pitchingMaxRow(connection, division)
        battingRateLeader(connection, division, "AVG")?.let { battingAvg[division] = it }
        battingRateLeader(connection, division, "OBP")?.let { battingObp[division] = it }
        battingRateLeader(connection, division, "OPS")?.let { battingOps[division] = it }
        pitchingEraLeader(connection, division)?.let { pitchingEra[division] = it }
    }
    return DetailSeasonRecordHighs(battingMax, pitchingMax, battingAvg, battingObp, battingOps, pitchingEra)
}

internal fun detailRecordHighKeys(
    pitching: Boolean,
    division: Int,
    values: Map<String, Int?>,
    highs: DetailSeasonRecordHighs,
): Set<String> {
    val keys = mutableSetOf<String>()
    if (pitching) {
        val max = highs.pitchingMax[division] ?: return emptySet()
        if ((max["KAISUU"] ?: 0) <= 0) return emptySet()
        for (key in pitchingCountKeys) {
            val peak = max[key] ?: continue
            if (values[key] == peak) keys += key
        }
        highs.pitchingEra[division]?.let { leader ->
            if (values["KAISUU"] == leader["KAISUU"] && values["JISEKI"] == leader["JISEKI"]) keys += "ERA"
        }
    } else {
        val max = highs.battingMax[division] ?: return emptySet()
        if ((max["SIAI"] ?: 0) <= 0) return emptySet()
        for (key in battingCountKeys) {
            val peak = max[key] ?: continue
            if (values[key] == peak) keys += key
        }
        highs.battingAvg[division]?.let { leader ->
            if (values["DASUU"] == leader["DASUU"] && values["ANDA"] == leader["ANDA"]) keys += "AVG"
        }
        highs.battingObp[division]?.let { leader ->
            if (
                values["DASUU"] == leader["DASUU"] &&
                values["ANDA"] == leader["ANDA"] &&
                values["SISI"] == leader["SISI"] &&
                values["GIHI"] == leader["GIHI"]
            ) {
                keys += "OBP"
            }
        }
        highs.battingOps[division]?.let { leader ->
            if (
                values["DASUU"] == leader["DASUU"] &&
                values["ANDA"] == leader["ANDA"] &&
                values["SISI"] == leader["SISI"] &&
                values["NIRUIDA"] == leader["NIRUIDA"] &&
                values["SANRUIDA"] == leader["SANRUIDA"] &&
                values["HONRUIDA"] == leader["HONRUIDA"] &&
                values["GIHI"] == leader["GIHI"]
            ) {
                keys += "OPS"
            }
        }
    }
    return keys
}

internal fun markDetailRecordHighs(
    cells: Map<String, PlayerCell>,
    keys: Set<String>,
): Map<String, PlayerCell> =
    if (keys.isEmpty()) {
        cells
    } else {
        cells.mapValues { (key, cell) -> if (key in keys) cell.copy(recordHigh = true) else cell }
    }

private fun battingMaxRow(
    connection: Connection,
    division: Int,
): Map<String, Int> {
    val sql =
        """
        SELECT MAX(SIAI) SIAI, MAX(DASEKI) DASEKI, MAX(DASUU) DASUU, MAX(ANDA) ANDA, MAX(NIRUIDA) NIRUIDA,
               MAX(SANRUIDA) SANRUIDA, MAX(HONRUIDA) HONRUIDA, MAX(DATEN) DATEN, MAX(TOKUTEN) TOKUTEN,
               MAX(SISI) SISI, MAX(SANSIN) SANSIN, MAX(TOURUIKIKAKU) TOURUIKIKAKU, MAX(TOURUI) TOURUI,
               MAX(GIDA) GIDA, MAX(GIHI) GIHI, MAX(HEISATU) HEISATU, MAX(SISSAKU) SISSAKU
        FROM YASYU_SEISEKI WHERE ITINIGUN = ?
        """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setInt(1, division)
        statement.executeQuery().use { rows ->
            require(rows.next()) { "打撃歴代最高を取得できません" }
            listOf(
                "SIAI",
                "DASEKI",
                "DASUU",
                "ANDA",
                "NIRUIDA",
                "SANRUIDA",
                "HONRUIDA",
                "DATEN",
                "TOKUTEN",
                "SISI",
                "SANSIN",
                "TOURUIKIKAKU",
                "TOURUI",
                "GIDA",
                "GIHI",
                "HEISATU",
                "SISSAKU",
            ).associateWith { rows.getInt(it) }
        }
    }
}

private fun pitchingMaxRow(
    connection: Connection,
    division: Int,
): Map<String, Int> {
    val sql =
        """
        SELECT MAX(TOUBAN) TOUBAN, MAX(SENPATU) SENPATU, MAX(NAKATUGI) NAKATUGI, MAX(KATI) KATI, MAX(MAKE) MAKE,
               MAX(H) H, MAX(S) S, MAX(KAISUU) KAISUU, MAX(DASYA) DASYA, MAX(T_DASUU) T_DASUU, MAX(HIAN) HIAN,
               MAX(HIHON) HIHON, MAX(DASSAN) DASSAN, MAX(YOSI) YOSI, MAX(SITTEN) SITTEN, MAX(JISEKI) JISEKI,
               MAX(KANTOU) KANTOU, MAX(KANPUU) KANPUU, MAX(BOUTOU) BOUTOU
        FROM TOUSYU_SEISEKI WHERE ITINIGUN = ?
        """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setInt(1, division)
        statement.executeQuery().use { rows ->
            require(rows.next()) { "投手歴代最高を取得できません" }
            pitchingCountKeys.associateWith { rows.getInt(it) }
        }
    }
}

private fun battingPlateMinimum(division: Int): Int = if (division == 1) 443 else (3.1 * 92).toInt()

private fun pitchingOutMinimum(division: Int): Int = if (division == 1) 432 else 85 * 3

private fun battingRateLeader(
    connection: Connection,
    division: Int,
    kind: String,
): Map<String, Int>? {
    val minimum = battingPlateMinimum(division)
    val order =
        when (kind) {
            "AVG" -> "(1.00 * ANDA / DASUU) DESC"
            "OBP" -> "((1.00 * ANDA + SISI) / (1.00 * DASUU + SISI + GIHI)) DESC"
            "OPS" ->
                "(((1.00 * ANDA + SISI) / (1.00 * DASUU + SISI + GIHI)) + ((1.00 * ANDA + NIRUIDA + 2 * SANRUIDA + 3 * HONRUIDA) / (1.00 * DASUU))) DESC"
            else -> error("未知の打撃率: $kind")
        }
    val columns =
        when (kind) {
            "AVG" -> "ANDA, DASUU"
            "OBP" -> "ANDA, SISI, DASUU, GIHI"
            else -> "ANDA, SISI, DASUU, GIHI, NIRUIDA, SANRUIDA, HONRUIDA"
        }
    val sql =
        """
        SELECT $columns FROM YASYU_SEISEKI
        WHERE ITINIGUN = ? AND DASEKI > ?
        ORDER BY $order, NUM ASC
        """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setInt(1, division)
        statement.setInt(2, minimum)
        statement.executeQuery().use { rows ->
            if (!rows.next()) return null
            columns.split(", ").associateWith { rows.getInt(it) }
        }
    }
}

private fun pitchingEraLeader(
    connection: Connection,
    division: Int,
): Map<String, Int>? {
    val sql =
        """
        SELECT JISEKI, KAISUU FROM TOUSYU_SEISEKI
        WHERE ITINIGUN = ? AND KAISUU >= ?
        ORDER BY (1.00 * JISEKI / KAISUU) ASC, NUM ASC
        """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setInt(1, division)
        statement.setInt(2, pitchingOutMinimum(division))
        statement.executeQuery().use { rows ->
            if (!rows.next()) return null
            mapOf("JISEKI" to rows.getInt("JISEKI"), "KAISUU" to rows.getInt("KAISUU"))
        }
    }
}
