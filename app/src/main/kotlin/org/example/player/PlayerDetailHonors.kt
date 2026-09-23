package org.example.player

import java.sql.Connection

internal fun loadDetailHonors(
    connection: Connection,
    id: Int,
): Map<String, DetailSection<List<DetailHonor>>> =
    linkedMapOf(
        "タイトル" to
            detailSection {
                connection
                    .detailRows("SELECT * FROM RIREKI WHERE HAN2 = ? AND KUBUN IN (4, 8)", id)
                    .map {
                        val second = it.int("KUBUN") == 8
                        val code = it.int("HAN1")
                        val title = detailTitleName(code, second)
                        DetailHonor(it.int("NENME"), title, if (second) "二軍" else "一軍", code = code)
                    }.distinct()
            },
        "ベストナイン・ゴールデングラブ" to
            detailSection {
                connection.detailRows("SELECT * FROM BEST_GG").flatMap { row ->
                    buildList {
                        for ((prefix, title, count) in listOf(Triple("BEST", "ベストナイン", 10), Triple("GG", "ゴールデングラブ", 9))) {
                            for (position in 1..count) {
                                if (row.numbers["$prefix$position"] == id) {
                                    val name = if (position == 10) "DH" else PlayerPosition.fromDatabaseValue(position)?.displayName ?: "不明"
                                    add(DetailHonor(row.int("NENME"), title, leagueLabel(row.int("LEAGUE")), facet = name))
                                }
                            }
                        }
                    }
                }
            },
        "月間MVP" to
            detailSection {
                connection.detailRows("SELECT * FROM GEKKAN_MVP WHERE NUM = ?", id).map {
                    val month = detailMonthLabel(it.int("TSUKI"))
                    val position = if (it.int("POJI") == 1) "投手" else "野手"
                    DetailHonor(it.int("NENME"), "月間MVP", "$month ${leagueLabel(it.int("LEAGUE"))}", facet = position)
                }
            },
        "オールスター" to
            detailSection {
                val slots =
                    (1..6).map { "SENPATU$it" } + (1..6).map { "NAKATUGI$it" } +
                        (2..9).map { "POJI$it" } + (1..8).map { "HIKAE$it" }
                connection
                    .detailRows("SELECT * FROM KIYO WHERE HIKAEDH12 = 3")
                    .filter { row -> slots.any { row.numbers[it] == id } }
                    .map { DetailHonor(it.int("HIKAE13"), "オールスター選出") }
                    .distinct()
            },
    )

internal fun loadDetailRecords(
    connection: Connection,
    id: Int,
): List<DetailHonor> =
    connection.detailRows("SELECT * FROM TOKUBETU_KIROKU WHERE SENSYU = ? ORDER BY NEN, SORT", id).map {
        val code = it.int("KUBUN")
        DetailHonor(
            it.int("NEN"),
            recordNames[code] ?: "不明な特別記録（$code）",
            if (code in 1..3) "達成" else "記録値 ${it.numbers["KIROKU"] ?: "—"}",
        )
    }

internal fun detailMonthLabel(month: Int): String = if (month == 4) "3・4月" else "${month}月"

private fun leagueLabel(league: Int): String =
    when (league) {
        1 -> "1stリーグ"
        2 -> "2ndリーグ"
        else -> "リーグ$league"
    }

internal enum class DetailAwardMark {
    Crown,
    Sprout,
    Bat,
    Ball,
    Shield,
    Glove,
    Medal,
    Star,
}

internal data class DetailAwardRow(
    val label: String,
    val chips: List<String>,
    val mark: DetailAwardMark? = null,
    val boldText: String? = null,
    val inlineYears: Boolean = false,
    val monochromeMark: Boolean = false,
)

internal fun detailAwardMark(code: Int): DetailAwardMark? =
    when (code) {
        1 -> DetailAwardMark.Crown
        2 -> DetailAwardMark.Sprout
        8, 9, 10, 11, 12, 14 -> DetailAwardMark.Bat
        3, 4, 5, 6, 7, 13 -> DetailAwardMark.Ball
        else -> null
    }

private val officialTitleCodes = listOf(1, 8, 9, 10, 11, 12, 14, 3, 4, 13, 5, 6, 7, 2)

private val battingLeaderCodes = (31..47).toList()

private val pitchingLeaderCodes = (61..76).toList()

private val positionOrder = PlayerPosition.entries.map { it.displayName } + listOf("DH", "不明")

internal fun officialTitleRows(
    honors: List<DetailHonor>,
    yearLabel: (Int) -> String,
): List<DetailAwardRow> = titleRows(honors.filter { it.code in officialTitleCodes }, officialTitleCodes, yearLabel)

internal fun leaderTitleRows(
    honors: List<DetailHonor>,
    yearLabel: (Int) -> String,
): List<DetailAwardRow> {
    val leaders = honors.filter { it.code !in officialTitleCodes }
    val known = battingLeaderCodes + pitchingLeaderCodes
    val extra =
        leaders
            .mapNotNull { it.code }
            .filter { it !in known }
            .distinct()
            .sorted()
    return titleRows(leaders, known + extra, yearLabel, includeYearChips = false)
}

internal fun bestNineRows(
    honors: List<DetailHonor>,
    yearLabel: (Int) -> String,
): List<DetailAwardRow> = facetRows(honors.filter { it.title == "ベストナイン" }, positionOrder, yearLabel, DetailAwardMark.Shield)

internal fun goldenGloveRows(
    honors: List<DetailHonor>,
    yearLabel: (Int) -> String,
): List<DetailAwardRow> = facetRows(honors.filter { it.title == "ゴールデングラブ" }, positionOrder, yearLabel, DetailAwardMark.Glove)

internal fun monthlyAwardRows(
    honors: List<DetailHonor>,
    yearLabel: (Int) -> String,
): List<DetailAwardRow> = facetRows(honors, listOf("投手", "野手"), yearLabel, DetailAwardMark.Medal)

internal fun allStarRows(
    honors: List<DetailHonor>,
    yearLabel: (Int) -> String,
): List<DetailAwardRow> = singleAwardRows(honors, "オールスター選出", yearLabel, DetailAwardMark.Star)

internal fun singleAwardRows(
    honors: List<DetailHonor>,
    label: String,
    yearLabel: (Int) -> String,
    mark: DetailAwardMark? = null,
): List<DetailAwardRow> =
    if (honors.isEmpty()) {
        emptyList()
    } else {
        listOf(DetailAwardRow(label, honors.sortedByDescending { it.year }.map { yearChip(it, yearLabel) }, mark))
    }

internal fun specialRecordRows(
    records: List<DetailHonor>,
    yearLabel: (Int) -> String,
): List<DetailAwardRow> =
    records
        .groupBy { it.title }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<DetailHonor>>> { entry ->
                entry.value.maxOf { honor -> honor.year }
            }.thenBy { it.key },
        ).map { (title, entries) ->
            val ordered = entries.sortedByDescending { it.year }
            val years = ordered.map { yearLabel(it.year) }
            if (entries.any { it.context == "達成" }) {
                DetailAwardRow(title, years)
            } else {
                val value =
                    entries
                        .first()
                        .context
                        .removePrefix("記録値 ")
                        .ifBlank { "—" }
                DetailAwardRow(title, years, boldText = value, inlineYears = true)
            }
        }

private fun titleRows(
    honors: List<DetailHonor>,
    order: List<Int>,
    yearLabel: (Int) -> String,
    includeYearChips: Boolean = true,
): List<DetailAwardRow> =
    order.flatMap { code ->
        listOf("一軍", "二軍").mapNotNull { squad ->
            val entries = honors.filter { it.code == code && it.context == squad }
            if (entries.isEmpty()) {
                null
            } else {
                DetailAwardRow(
                    "$squad ${entries.first().title}",
                    if (includeYearChips) {
                        entries.sortedByDescending { it.year }.map { yearChip(it, yearLabel) }
                    } else {
                        emptyList()
                    },
                    detailAwardMark(code),
                    boldText = if (includeYearChips) null else "${entries.size}回",
                    monochromeMark = squad == "二軍",
                )
            }
        }
    }

private fun facetRows(
    honors: List<DetailHonor>,
    order: List<String>,
    yearLabel: (Int) -> String,
    mark: DetailAwardMark,
): List<DetailAwardRow> {
    val extra =
        honors
            .map { it.facet }
            .filter { it !in order }
            .distinct()
            .sorted()
    return (order + extra).mapNotNull { facet ->
        val entries = honors.filter { it.facet == facet }
        if (entries.isEmpty()) {
            null
        } else {
            DetailAwardRow(facet, entries.sortedByDescending { it.year }.map { yearChip(it, yearLabel) }, mark)
        }
    }
}

private fun yearChip(
    honor: DetailHonor,
    yearLabel: (Int) -> String,
    keepContext: Boolean = true,
): String {
    val year = yearLabel(honor.year)
    val detail = honor.context.takeIf { keepContext && it.isNotBlank() && it != "一軍" && it != "二軍" }
    return if (detail == null) year else "$year $detail"
}

internal fun detailTitleName(
    code: Int,
    second: Boolean,
): String = if (second && code == 2) "新人賞" else titleNames[code] ?: "不明なタイトル（$code）"

private val titleNames =
    mapOf(
        1 to "MVP",
        2 to "新人王",
        3 to "最優秀防御率",
        4 to "最多勝",
        5 to "最多奪三振",
        6 to "最優秀中継",
        7 to "最多セーブ",
        8 to "首位打者",
        9 to "本塁打王",
        10 to "打点王",
        11 to "盗塁王",
        12 to "最多安打",
        13 to "最優秀勝率",
        14 to "最高出塁率",
        31 to "最多試合",
        32 to "最多打席",
        33 to "最多打数",
        34 to "最多二塁打",
        35 to "最多三塁打",
        36 to "最多得点",
        37 to "最多四死球",
        38 to "最多三振",
        39 to "最多盗塁企図",
        40 to "最多犠打",
        41 to "最多犠飛",
        42 to "最多併殺",
        43 to "最多失策",
        44 to "最高出塁率",
        45 to "最高長打率",
        46 to "最高OPS",
        47 to "最高得点圏打率",
        61 to "最多登板",
        62 to "最多先発",
        63 to "最多中継",
        64 to "最多完投",
        65 to "最多完封",
        66 to "最多投球回",
        67 to "最多打者",
        68 to "最多被打数",
        69 to "最多被安打",
        70 to "最多被本塁打",
        71 to "最多与四球",
        72 to "最多暴投",
        73 to "最多被盗塁",
        74 to "最多失点",
        75 to "最多自責点",
        76 to "最多敗戦",
    )

private val recordNames =
    mapOf(
        1 to "完全試合",
        2 to "ノーヒットノーラン",
        3 to "サイクル安打",
        4 to "被安打",
        5 to "被本塁打",
        6 to "奪三振",
        7 to "与四球",
        8 to "暴投",
        9 to "失点",
        10 to "自責点",
        13 to "安打",
        14 to "二塁打",
        15 to "三塁打",
        16 to "本塁打",
        17 to "打点",
        18 to "得点",
        19 to "四球",
        20 to "三振",
        21 to "盗塁",
        22 to "犠打",
        23 to "犠飛",
        24 to "失策",
    )
