package org.example.player

import java.math.BigDecimal
import java.math.RoundingMode

internal fun numberCell(value: Int): PlayerCell = PlayerCell(value.toString(), value.toDouble())

internal fun abilityCell(value: Int): PlayerCell {
    if (value == 0) return PlayerCell("", 0.0)
    val rank =
        when {
            value >= 90 -> AbilityRank.S
            value >= 80 -> AbilityRank.A
            value >= 70 -> AbilityRank.B
            value >= 60 -> AbilityRank.C
            value >= 50 -> AbilityRank.D
            value >= 40 -> AbilityRank.E
            value >= 30 -> AbilityRank.F
            else -> AbilityRank.G
        }
    return PlayerCell("${rank.symbol}$value", value.toDouble(), AbilityStyle(rank, colorStart = 0))
}

internal fun pitchAbilityCell(
    name: String,
    value: Int?,
): PlayerCell {
    if (value == null) return missingCell("$name —")
    val ability = abilityCell(value)
    if (ability.text.isEmpty()) return PlayerCell(name, value.toDouble())
    val prefix = "$name "
    val style = requireNotNull(ability.abilityStyle)
    return ability.copy(text = prefix + ability.text, abilityStyle = style.copy(colorStart = prefix.length))
}

internal val pitchNames =
    listOf(
        "",
        "スライダー",
        "カット",
        "カーブ",
        "スラーブ",
        "スローカーブ",
        "ドロップ",
        "フォーク",
        "チェンジアップ",
        "スプリット",
        "パーム",
        "ナックル",
        "シンカー",
        "スクリュー",
        "シュート",
        "ツーシーム",
        "ムービング",
    )

internal fun readyStamina(
    stamina: Int,
    adjustment: Int,
    squad: PlayerSquad,
    day: Int,
): Int {
    val value =
        if (squad == PlayerSquad.SECOND) {
            (0.35 * (stamina - 55) + 24).toInt()
        } else {
            (0.32 * (stamina - 55) + 4 * adjustment).toInt()
        }
    return value - if (day >= 316) 10 else 0
}

internal fun faCell(
    origin: Int,
    total: Int,
    current: Int?,
    major: Boolean,
): PlayerCell {
    if (major) return missingCell()
    if (origin == 0) return PlayerCell("設定依存")
    if (origin !in 1..4) return missingCell()
    if (current == null) return missingCell()
    val base = if (origin == 1) 1144 else 1001
    val target =
        (0..3).map { base + 572 * it }.firstOrNull { it > total - current }
            ?: return missingCell()
    val remaining = (target - total).coerceAtLeast(0)
    return PlayerCell(if (remaining == 0) "今季取得圏内" else "残り${remaining}試合", remaining.toDouble())
}

/** Game-native season metrics. Missing records remain absent; zero denominators are not zero rates. */
internal object GameSeasonMetrics {
    fun batting(values: Map<String, Int?>): Map<String, PlayerCell> =
        buildMap {
            addCounts(values)

            fun n(key: String) = values[key]?.toDouble()

            fun computed(
                key: String,
                required: List<String>,
                calculation: (Map<String, Double>) -> Double?,
            ) {
                val inputs = required.mapNotNull { field -> n(field)?.let { field to it } }.toMap()
                if (inputs.size == required.size) {
                    val value = calculation(inputs)
                    put(key, decimal(value, leadingZero = key == "OPS" || key == "RC" || key == "RC27", rc = key == "RC" || key == "RC27"))
                }
            }
            computed("AVG", listOf("ANDA", "DASUU")) { ratio(it.getValue("ANDA"), it.getValue("DASUU")) }
            computed("OBP", listOf("ANDA", "DASUU", "SISI", "GIHI")) {
                if (it.getValue("DASUU") ==
                    0.0
                ) {
                    null
                } else {
                    ratio(
                        it.getValue("ANDA") + it.getValue("SISI"),
                        it.getValue("DASUU") + it.getValue("SISI") + it.getValue("GIHI"),
                    )
                }
            }
            val slugFields = listOf("ANDA", "DASUU", "NIRUIDA", "SANRUIDA", "HONRUIDA")

            fun slug(v: Map<String, Double>) =
                ratio(
                    v.getValue("ANDA") + v.getValue("NIRUIDA") + 2 * v.getValue("SANRUIDA") + 3 * v.getValue("HONRUIDA"),
                    v.getValue("DASUU"),
                )
            computed("SLG", slugFields, ::slug)
            computed("OPS", slugFields + listOf("SISI", "GIHI")) {
                slug(it)?.let { slg ->
                    slg +
                        (it.getValue("ANDA") + it.getValue("SISI")) / (it.getValue("DASUU") + it.getValue("SISI") + it.getValue("GIHI"))
                }
            }
            for ((key, prefix) in listOf("RIGHT" to "MIGI", "LEFT" to "HIDARI", "RISP" to "TOKU")) {
                computed(
                    key,
                    listOf("${prefix}ANDA", "${prefix}DASUU"),
                ) { ratio(it.getValue("${prefix}ANDA"), it.getValue("${prefix}DASUU")) }
            }
            computed(
                "CS",
                listOf("HITOURUI", "SASITORUI"),
            ) { ratio(it.getValue("HITOURUI") - it.getValue("SASITORUI"), it.getValue("HITOURUI")) }
            val rcFields =
                listOf(
                    "ANDA",
                    "SISI",
                    "TOURUIKIKAKU",
                    "TOURUI",
                    "HEISATU",
                    "NIRUIDA",
                    "SANRUIDA",
                    "HONRUIDA",
                    "KEIEN",
                    "GIDA",
                    "GIHI",
                    "SANSIN",
                    "DASUU",
                )

            fun rc(v: Map<String, Double>): Double? {
                fun get(key: String) = v.getValue(key)
                val a = get("ANDA") + get("SISI") + get("TOURUIKIKAKU") - get("TOURUI") - get("HEISATU")
                val b =
                    get("ANDA") + get("NIRUIDA") + 2 * get("SANRUIDA") + 3 * get("HONRUIDA") +
                        0.24 * (get("SISI") - get("KEIEN")) + 0.62 * get("TOURUI") + 0.5 * (get("GIDA") + get("GIHI")) -
                        0.03 * get("SANSIN")
                val c = get("DASUU") + get("SISI") + get("GIDA") + get("GIHI")
                return if (c == 0.0) null else (a + 2.4 * c) * (b + 3 * c) / (9 * c) - 0.9 * c
            }
            computed("RC", rcFields, ::rc)
            computed("RC27", rcFields) {
                rc(it)?.let { rc ->
                    ratio(
                        rc * 27,
                        it.getValue("DASUU") - it.getValue("ANDA") + it.getValue("TOURUIKIKAKU") - it.getValue("TOURUI") +
                            it.getValue("GIDA") +
                            it.getValue("GIHI") +
                            it.getValue("HEISATU"),
                    )
                }
            }
        }

    fun pitching(values: Map<String, Int?>): Map<String, PlayerCell> =
        buildMap {
            addCounts(values)
            values["KAISUU"]?.let { outs ->
                put("KAISUU", PlayerCell("${outs / 3}" + if (outs % 3 == 0) "" else " ${outs % 3}/3", outs.toDouble()))
            }

            fun metric(
                key: String,
                numerator: String,
                denominator: String,
                multiplier: Int = 1,
                rounded: Boolean = false,
                leadingZero: Boolean = false,
            ) {
                val a = values[numerator]
                val b = values[denominator]
                if (a != null && b != null) put(key, decimal(ratio(a.toDouble() * multiplier, b.toDouble()), leadingZero, rounded))
            }
            metric("ERA", "JISEKI", "KAISUU", 27, leadingZero = true)
            metric("AVG", "HIAN", "T_DASUU")
            metric("RIGHT", "MIGIHIAN", "MIGIDASUU")
            metric("LEFT", "HIDARIHIAN", "HIDARIDASUU")
            metric("RISP", "TOKUHIAN", "TOKUDASUU")
            metric("SB_RATE", "HITOURUI", "HITOURUIKIKAKU")
            metric("QS_RATE", "QS", "SENPATU", leadingZero = true)
            metric("HQS_RATE", "HQS", "SENPATU", leadingZero = true)
            metric("BB9", "YOSI", "KAISUU", 27, rounded = true, leadingZero = true)
            metric("K9", "DASSAN", "KAISUU", 27, rounded = true, leadingZero = true)
            val whip = listOf("HIAN", "YOSI", "KAISUU").map { values[it] }
            if (whip.all { it != null }) {
                put("WHIP", decimal(ratio((whip[0]!! + whip[1]!!) * 3.0, whip[2]!!.toDouble()), leadingZero = true, rounded = true))
            }
            val babip = listOf("HIAN", "HIHON", "T_DASUU", "DASSAN").map { values[it] }
            if (babip.all { it != null }) {
                put(
                    "BABIP",
                    decimal(
                        ratio((babip[0]!! - babip[1]!!).toDouble(), (babip[2]!! - babip[3]!! - babip[1]!!).toDouble()),
                        leadingZero = true,
                        rounded = true,
                    ),
                )
            }
        }

    private fun MutableMap<String, PlayerCell>.addCounts(values: Map<String, Int?>) {
        values.forEach { (key, value) -> if (value != null) put(key, numberCell(value)) }
    }

    private fun ratio(
        numerator: Double,
        denominator: Double,
    ): Double? = if (denominator <= 0) null else numerator / denominator

    private fun decimal(
        value: Double?,
        leadingZero: Boolean = false,
        rounded: Boolean = false,
        rc: Boolean = false,
    ): PlayerCell {
        if (value == null || !value.isFinite()) return missingCell("---")
        val scaled = BigDecimal.valueOf(value).setScale(3, if (rounded || rc) RoundingMode.HALF_EVEN else RoundingMode.DOWN)
        val full = (if (rc) scaled.setScale(2, RoundingMode.DOWN) else scaled).toPlainString()
        return PlayerCell(if (!leadingZero && full.startsWith("0.")) full.drop(1) else full, value)
    }
}
