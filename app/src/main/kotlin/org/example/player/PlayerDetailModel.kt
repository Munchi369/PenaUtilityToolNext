package org.example.player

import java.nio.file.Path

enum class PlayerDetailTab(
    val title: String,
) {
    STATS("成績"),
    ABILITY("能力"),
    HONORS("タイトル・表彰"),
}

enum class DetailStatsPeriod(
    val title: String,
) {
    CURRENT("今季"),
    YEARS("年度別・通算"),
    MONTHS("月別"),
}

data class DetailSection<T>(
    val value: T? = null,
    val error: String? = null,
)

data class DetailStatRecord(
    val year: Int,
    val playerYear: Int,
    val division: Int,
    val values: Map<String, Int?>,
)

data class DetailAbilityRecord(
    val playerYear: Int,
    val cells: Map<String, PlayerCell>,
)

data class DetailHonor(
    val year: Int,
    val title: String,
    val context: String = "",
    val code: Int? = null,
    val facet: String = "",
)

data class DetailProfile(
    val name: String,
    val team: String,
    val number: Int,
    val age: Int?,
    val position: String,
    val playerYear: Int?,
    val cells: Map<String, PlayerCell>,
)

data class PlayerDetailData(
    val playerId: Int,
    val season: Int?,
    val startYear: Int?,
    val profile: DetailSection<DetailProfile>,
    val currentAbility: DetailSection<Map<String, PlayerCell>>,
    val abilities: DetailSection<List<DetailAbilityRecord>>,
    val batting: DetailSection<List<DetailStatRecord>>,
    val pitching: DetailSection<List<DetailStatRecord>>,
    val monthlyBatting: DetailSection<List<DetailStatRecord>>,
    val monthlyPitching: DetailSection<List<DetailStatRecord>>,
    val appearances: DetailSection<List<DetailStatRecord>>,
    val honors: Map<String, DetailSection<List<DetailHonor>>>,
    val records: DetailSection<List<DetailHonor>>,
    val recordHighs: DetailSection<DetailSeasonRecordHighs> = DetailSection(DetailSeasonRecordHighs()),
    val contextErrors: List<String> = emptyList(),
) {
    fun yearLabel(year: Int): String = startYear?.let { "${it + year}年" } ?: "ゲーム${year}年目"

    fun abilityLabel(playerYear: Int): String =
        when (playerYear) {
            -1 -> "獲得前評価"
            0 -> "入団時"
            else -> {
                val currentPlayerYear = profile.value?.playerYear
                if (season != null && currentPlayerYear != null) {
                    "${yearLabel(season - currentPlayerYear + playerYear)}（${playerYear}年目）"
                } else {
                    "${playerYear}年目"
                }
            }
        }
}

sealed interface PlayerDetailLoadResult {
    data class Loaded(
        val data: PlayerDetailData,
    ) : PlayerDetailLoadResult

    data class Failed(
        val message: String,
        val details: String,
    ) : PlayerDetailLoadResult
}

fun interface PlayerDetailLoader {
    fun load(
        path: Path,
        playerId: Int,
    ): PlayerDetailLoadResult
}

internal object DetailColumns {
    val batting = RosterColumns.battingStats.filterNot { it.key == "POSITION" }
    val pitching = RosterColumns.pitchingStats.filterNot { it.key == "POSITION" }
    val abilities =
        (RosterColumns.battingAbility + RosterColumns.pitchingAbility)
            .filterNot { it.key == "POSITION" }
            .distinctBy { it.key } +
            listOf(
                RosterColumn("VS_LEFT", "対左"),
                RosterColumn("K_SANSIN", "三振傾向", 88),
                RosterColumn("K_GORO", "ゴロ傾向", 88),
                RosterColumn("K_NAIYAHURAI", "内飛傾向", 88),
                RosterColumn("K_GAIYAHURAI", "外飛傾向", 88),
            )
    val graphAbilities =
        abilities.filterNot { column ->
            column.key in
                setOf(
                    "POJI_HOSYU",
                    "POJI_ITIRUI",
                    "POJI_NIRUI",
                    "POJI_SANRUI",
                    "POJI_YUUGEKI",
                    "POJI_LEFT",
                    "POJI_CENTER",
                    "POJI_RIGHT",
                    "SENPATUTEKISEI",
                    "NAKATUGITEKISEI",
                    "VS_LEFT",
                    "K_SANSIN",
                    "K_GORO",
                    "K_NAIYAHURAI",
                    "K_GAIYAHURAI",
                )
        }

    fun stats(pitching: Boolean): List<RosterColumn> = if (pitching) this.pitching else batting
}

internal fun detailAbilityCells(values: Map<String, Int?>): Map<String, PlayerCell> =
    DetailColumns.abilities.associate { column ->
        val key = column.key
        val value = values[key]
        key to
            when {
                key.startsWith("PITCH") -> {
                    val index = key.removePrefix("PITCH")
                    val pitch = values["HEN$index"]
                    when {
                        pitch == null || pitch == 0 -> PlayerCell("")
                        pitch !in 1 until pitchNames.size -> unknownCell()
                        else -> pitchAbilityCell(pitchNames[pitch], values["HEN${index}_DATA"])
                    }
                }
                value == null -> missingCell()
                key == "SOUGOU" || key == "MAXMAX" || key.startsWith("K_") -> numberCell(value)
                else -> abilityCell(value)
            }
    }

/** Aggregate source counts before computing rates; an unknown input remains unknown. */
internal fun detailTotals(records: List<DetailStatRecord>): Map<String, Int?> {
    if (records.isEmpty()) return emptyMap()
    val excluded = setOf("NUM", "ID", "NENME", "NENDO", "ITINIGUN", "SHOZOKU", "SEBANGOU", "KUBUN", "NENPOU")
    return records.flatMap { it.values.keys }.toSet().minus(excluded).associateWith { key ->
        val values = records.map { it.values[key] }
        if (values.any { it == null }) null else Math.toIntExact(values.sumOf { it!!.toLong() })
    }
}

internal fun detailMetrics(
    values: Map<String, Int?>,
    pitching: Boolean,
): Map<String, PlayerCell> = if (pitching) GameSeasonMetrics.pitching(values) else GameSeasonMetrics.batting(values)

/** Pitchers see pitching abilities first. Everyone else, including an unknown position, sees batting abilities first. */
internal fun detailAbilityColumns(position: String?): List<RosterColumn> {
    val first =
        if (position == PlayerPosition.PITCHER.displayName) {
            RosterColumns.pitchingAbility
        } else {
            RosterColumns.battingAbility
        }
    return (first.map { it.key } + DetailColumns.abilities.map { it.key })
        .distinct()
        .mapNotNull { key -> DetailColumns.abilities.find { it.key == key } }
}

internal fun detailNavigationPlayers(
    roster: PlayerRoster,
    options: PlayerRosterOptions,
): List<PlayerRosterPlayer> {
    val players = options.players(roster)
    return when (options.grouping) {
        RosterGrouping.NONE -> players
        RosterGrouping.SQUAD -> PlayerSquad.entries.flatMap { squad -> players.filter { it.squad == squad } }
        RosterGrouping.POSITION -> PlayerPosition.entries.flatMap { position -> players.filter { it.position == position } }
    }
}
