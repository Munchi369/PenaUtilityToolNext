package org.example.player

enum class AbilityRank(
    val symbol: String,
) {
    S("S"),
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    E("E"),
    F("F"),
    G("G"),
}

data class AbilityStyle(
    val rank: AbilityRank,
    val colorStart: Int,
) {
    init {
        require(colorStart >= 0) { "能力色の開始位置は0以上である必要があります" }
    }
}

data class PlayerCell(
    val text: String,
    val number: Double? = null,
    val abilityStyle: AbilityStyle? = null,
    val sortStatus: PlayerCellSortStatus = PlayerCellSortStatus.VALUE,
    val recordHigh: Boolean = false,
) {
    init {
        abilityStyle?.let { require(it.colorStart < text.length) { "能力色の開始位置が表示文字列の範囲外です" } }
    }
}

enum class PlayerCellSortStatus {
    VALUE,
    MISSING,
    UNKNOWN,
}

internal fun missingCell(text: String = "—"): PlayerCell = PlayerCell(text, sortStatus = PlayerCellSortStatus.MISSING)

internal fun unknownCell(): PlayerCell = PlayerCell("不明", sortStatus = PlayerCellSortStatus.UNKNOWN)

enum class RosterDisplay(
    val title: String,
    val initialSort: String,
) {
    BASIC("基本情報", "SOUGOU"),
    BATTING_ABILITY("野手能力", "SOUGOU"),
    PITCHING_ABILITY("投手能力", "SOUGOU"),
    BATTING_STATS("打撃成績", "DASEKI"),
    PITCHING_STATS("投手成績", "KAISUU"),
    ;

    val isStats: Boolean get() = this == BATTING_STATS || this == PITCHING_STATS
}

data class RosterColumn(
    val key: String,
    val title: String,
    val width: Int = 68,
    val standard: Boolean = true,
)

object RosterColumns {
    private fun columns(vararg entries: Pair<String, String>): List<RosterColumn> = entries.map { (key, title) -> RosterColumn(key, title) }

    val basic =
        listOf(
            RosterColumn("ID", "選手ID"),
            RosterColumn("SOUGOU", "総合"),
            RosterColumn("POSITION", "ポジション", 96),
            RosterColumn("AGE", "年齢"),
            RosterColumn("NENME", "年目"),
            RosterColumn("SHOZOKU", "入団経路", 88),
            RosterColumn("DORAJUN", "ドラフト順位", 112),
            RosterColumn("SHUSSIN", "出身", 96),
            RosterColumn("HEIGHT", "身長(cm)", 80),
            RosterColumn("WEIGHT", "体重(kg)", 80),
            RosterColumn("THROWS_BATS", "投打", 96),
            RosterColumn("FORM", "フォーム", 128),
            RosterColumn("FA", "FAまで（試合）", 128, standard = false),
            RosterColumn("HIROU", "疲労", standard = false),
            RosterColumn("READY", "先発時スタミナ", 120, standard = false),
            RosterColumn("INJURY", "怪我状態", 148, standard = false),
        )
    val battingAbility =
        columns(
            "SOUGOU" to "総合",
            "POSITION" to "ポジション",
            "KOUDA" to "巧打",
            "POWER" to "長打",
            "SOURYOKU" to "走力",
            "KENRYOKU" to "肩力",
            "HOKYUU" to "捕球",
            "SOUKYUU" to "送球",
            "SENKYUU" to "選球",
            "KOWAZA" to "小技",
            "TAIRYOKU" to "体力",
            "RIDO" to "リード",
            "TOURUIGIJUTU" to "盗技",
            "POJI_HOSYU" to "捕",
            "POJI_ITIRUI" to "一",
            "POJI_NIRUI" to "二",
            "POJI_SANRUI" to "三",
            "POJI_YUUGEKI" to "遊",
            "POJI_LEFT" to "左",
            "POJI_CENTER" to "中",
            "POJI_RIGHT" to "右",
        ).map { column ->
            if (column.key == "POSITION") column.copy(width = 96) else column
        }
    val pitchingAbility =
        columns(
            "SOUGOU" to "総合",
            "POSITION" to "ポジション",
            "MAXMAX" to "MAX",
            "SEIKYUU" to "制球",
            "SUTAMINA" to "スタミナ",
            "KAIHUKU" to "回復",
            "SENPATUTEKISEI" to "先発適性",
            "NAKATUGITEKISEI" to "中継適性",
            "SEISIN" to "精神",
            "QUICK" to "クイック",
        ).map { column ->
            when (column.key) {
                "POSITION" -> column.copy(width = 96)
                "SUTAMINA", "SENPATUTEKISEI", "NAKATUGITEKISEI", "QUICK" -> column.copy(width = 80)
                else -> column
            }
        } + (1..5).map { RosterColumn("PITCH$it", "球種$it・変化量", 200) }

    val battingStats =
        columns(
            "POSITION" to "ポジション",
            "ITIGUN" to "一軍登録",
            "SIAI" to "試合",
            "DASEKI" to "打席",
            "DASUU" to "打数",
            "ANDA" to "安打",
            "NIRUIDA" to "二塁",
            "SANRUIDA" to "三塁",
            "HONRUIDA" to "本塁",
            "DATEN" to "打点",
            "TOKUTEN" to "得点",
            "SISI" to "四死",
            "SANSIN" to "三振",
            "TOURUIKIKAKU" to "盗企",
            "TOURUI" to "盗塁",
            "GIDA" to "犠打",
            "GIHI" to "犠飛",
            "HEISATU" to "併殺",
            "SISSAKU" to "失策",
            "AVG" to "打率",
            "OBP" to "出塁率",
            "OPS" to "OPS",
            "NENPOU" to "年俸",
        ).map { column ->
            when (column.key) {
                "POSITION" -> column.copy(width = 96)
                "ITIGUN" -> column.copy(width = 88)
                else -> column
            }
        } +
            listOf(
                RosterColumn("RISP", "得点圏打率", 110, standard = false),
                RosterColumn("SLG", "長打率", standard = false),
                RosterColumn("RIGHT", "対右打率", 88, standard = false),
                RosterColumn("LEFT", "対左打率", 88, standard = false),
                RosterColumn("RC", "RC", standard = false),
                RosterColumn("RC27", "RC27", standard = false),
                RosterColumn("CS", "盗塁阻止率", 110, standard = false),
            ) +
            (1..9).map { RosterColumn("DAJUN$it", "${it}番", standard = false) } +
            columns("DAIDA" to "代打", "DAISOU" to "代走", "SYUBI" to "守備").map { it.copy(standard = false) } +
            listOf("捕", "一", "二", "三", "遊", "左", "中", "右").mapIndexed { i, name ->
                RosterColumn("POJI${i + 2}", "出場・$name", 88, standard = false)
            } + RosterColumn("DH", "DH", standard = false)

    val pitchingStats =
        columns(
            "POSITION" to "ポジション",
            "ITIGUN" to "一軍登録",
            "TOUBAN" to "登板",
            "SENPATU" to "先発",
            "NAKATUGI" to "中継",
            "KATI" to "勝",
            "MAKE" to "敗",
            "H" to "H",
            "S" to "S",
            "KANTOU" to "完投",
            "KANPUU" to "完封",
            "KAISUU" to "回数",
            "DASYA" to "打者",
            "T_DASUU" to "打数",
            "HIAN" to "被安",
            "HIHON" to "被本",
            "DASSAN" to "奪三",
            "YOSI" to "与四",
            "BOUTOU" to "暴投",
            "HITOURUI" to "被盗",
            "SITTEN" to "失点",
            "JISEKI" to "自責",
            "ERA" to "防御率",
            "NENPOU" to "年俸",
        ).map { column ->
            when (column.key) {
                "POSITION" -> column.copy(width = 96)
                "ITIGUN" -> column.copy(width = 88)
                else -> column
            }
        } +
            columns(
                "AVG" to "被打率",
                "RIGHT" to "右打率",
                "LEFT" to "左打率",
                "RISP" to "得圏率",
                "SB_RATE" to "被盗率",
                "QS" to "QS",
                "QS_RATE" to "QS率",
                "HQS" to "HQS",
                "HQS_RATE" to "HQS率",
                "WHIP" to "WHIP",
                "BABIP" to "BABIP",
                "BB9" to "BB/9",
                "K9" to "K/9",
            ).map { it.copy(standard = false) }

    fun forDisplay(display: RosterDisplay): List<RosterColumn> =
        when (display) {
            RosterDisplay.BASIC -> basic
            RosterDisplay.BATTING_ABILITY -> battingAbility
            RosterDisplay.PITCHING_ABILITY -> pitchingAbility
            RosterDisplay.BATTING_STATS -> battingStats
            RosterDisplay.PITCHING_STATS -> pitchingStats
        }
}

fun PlayerRosterPlayer.cell(
    display: RosterDisplay,
    key: String,
    statsSquad: Int,
): PlayerCell =
    when (key) {
        "NAME" -> PlayerCell(name)
        "TEAM" -> PlayerCell(teamName)
        "NUMBER" -> PlayerCell(uniformNumber.toString(), uniformNumber.toDouble())
        "ID" -> PlayerCell(playerId.toString(), playerId.toDouble())
        "SOUGOU" -> PlayerCell(overall.toString(), overall.toDouble())
        "POSITION" -> PlayerCell(position.displayName, position.databaseValue.toDouble())
        "AGE" -> PlayerCell(age.toString(), age.toDouble())
        "INJURY" -> PlayerCell(injury.displayName)
        "ITIGUN" -> if (major) missingCell() else seasonCell(display, key, statsSquad)
        else ->
            seasonCell(display, key, statsSquad)
    }

private fun PlayerRosterPlayer.seasonCell(
    display: RosterDisplay,
    key: String,
    statsSquad: Int,
): PlayerCell =
    when (display) {
        RosterDisplay.BATTING_STATS -> batting[if (major) 3 else statsSquad]?.get(key)
        RosterDisplay.PITCHING_STATS -> pitching[if (major) 3 else statsSquad]?.get(key)
        else -> information[key]
    } ?: missingCell()
