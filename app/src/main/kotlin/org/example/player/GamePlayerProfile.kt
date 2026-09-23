package org.example.player

internal fun playerProfileCells(values: Map<String, Int?>): Map<String, PlayerCell> =
    buildMap {
        for (key in listOf("NENME", "HEIGHT", "WEIGHT")) {
            values[key]?.let { put(key, numberCell(it)) }
        }

        values["SHOZOKU"]?.let { value ->
            when (value) {
                0 -> Unit
                in entryPathNames.indices -> put("SHOZOKU", codedCell(entryPathNames[value], value))
                else -> put("SHOZOKU", unknownCell())
            }
        }
        values["DORAJUN"]?.let { value ->
            val rank = value % 100
            when {
                value == 0 -> Unit
                value in 1..99 -> put("DORAJUN", codedCell("${value}位", value))
                value >= 100 && rank in 1..99 -> put("DORAJUN", codedCell("育成${rank}位", value))
                else -> put("DORAJUN", unknownCell())
            }
        }
        values["SHUSSIN"]?.let { value ->
            put("SHUSSIN", originNames[value]?.let { codedCell(it, value) } ?: unknownCell())
        }

        val throwing = values["KIKIUDE"]
        val batting = values["KIKIDASEKI"]
        if (throwing != null && batting != null) {
            val throwingName = handNames[throwing]
            val battingName = handNames[batting]
            put(
                "THROWS_BATS",
                if (throwingName == null || battingName == null) {
                    unknownCell()
                } else {
                    codedCell("${throwingName}投${battingName}打", throwing * 10 + batting)
                },
            )
        }

        values["FORM"]?.let { value ->
            put("FORM", formNames[value]?.let { codedCell(it, value) } ?: unknownCell())
        }
    }

private fun codedCell(
    text: String,
    code: Int,
): PlayerCell = PlayerCell(text, code.toDouble())

private val entryPathNames = listOf("", "高校", "大学", "社会人", "独立")

private val handNames = mapOf(1 to "右", 2 to "左", 3 to "両")

private val formNames =
    mapOf(
        11 to "オーバー",
        12 to "スリークォーター",
        13 to "サイド",
        14 to "アンダー",
        21 to "スタンダード",
        22 to "オープン",
        23 to "神主",
        24 to "クラウチング",
        25 to "振り子",
        26 to "バスター",
        27 to "一本足",
    )

private val originNames =
    listOf(
        "北海道",
        "青森",
        "岩手",
        "宮城",
        "秋田",
        "山形",
        "福島",
        "茨城",
        "栃木",
        "群馬",
        "埼玉",
        "千葉",
        "東京",
        "神奈川",
        "新潟",
        "富山",
        "石川",
        "福井",
        "山梨",
        "長野",
        "岐阜",
        "静岡",
        "愛知",
        "三重",
        "滋賀",
        "京都",
        "大阪",
        "兵庫",
        "奈良",
        "和歌山",
        "鳥取",
        "島根",
        "岡山",
        "広島",
        "山口",
        "徳島",
        "香川",
        "愛媛",
        "高知",
        "福岡",
        "佐賀",
        "長崎",
        "熊本",
        "大分",
        "宮崎",
        "鹿児島",
        "沖縄",
    ).mapIndexed { index, name -> index + 1 to name }.toMap() +
        mapOf(
            51 to "アメリカ",
            52 to "カナダ",
            53 to "オーストラリア",
            54 to "スペイン",
            55 to "ベネズエラ",
            56 to "ドミニカ共和国",
            57 to "キューバ",
            58 to "メキシコ",
            59 to "パナマ",
            60 to "イタリア",
            61 to "ブラジル",
            62 to "オランダ",
            63 to "中国",
            64 to "韓国",
            65 to "台湾",
        )
