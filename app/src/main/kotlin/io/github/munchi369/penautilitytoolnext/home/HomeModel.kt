package io.github.munchi369.penautilitytoolnext.home

internal data class SeasonDay(
    val day: Int,
    val month: Int,
    val date: Int,
    val weekday: Int,
    val event: Int,
    val game: Int?,
)

internal data class SeasonGame(
    val team: Int,
    val opponent: Int,
    val game: Int,
    val runs: Int,
    val allowed: Int,
    val starter: Int?,
    val battingOrder: List<Int?>,
) {
    val result: String get() = "$resultMark $runs−$allowed"
    private val resultMark: String
        get() =
            when {
                runs > allowed -> "○"
                runs < allowed -> "●"
                else -> "△"
            }
}

internal data class ReliefAppearance(
    val day: Int,
    val player: Int,
    val outs: Int,
    val batters: Int,
    val runs: Int,
    val decision: Int,
)

internal data class TeamSeasonStats(
    val team: Int,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val runs: Int,
    val runsAllowed: Int,
    val hits: Int,
    val atBats: Int,
    val homeRuns: Int,
    val steals: Int,
    val earnedRuns: Int,
    val pitchingOuts: Int,
    val completeGames: Int,
    val shutouts: Int,
    val holds: Int,
    val saves: Int,
    val walks: Int = 0,
    val sacrificeBunts: Int = 0,
    val starterEarnedRuns: Int = 0,
    val starterOuts: Int = 0,
    val reliefEarnedRuns: Int = 0,
    val reliefOuts: Int = 0,
) {
    val winningRate: Double? get() = (wins + losses).takeIf { it > 0 }?.let { wins.toDouble() / it }
    val battingAverage: Double? get() = atBats.takeIf { it > 0 }?.let { hits.toDouble() / it }
    val earnedRunAverage: Double? get() = pitchingOuts.takeIf { it > 0 }?.let { earnedRuns * 27.0 / it }
    val starterEarnedRunAverage: Double? get() = starterOuts.takeIf { it > 0 }?.let { starterEarnedRuns * 27.0 / it }
    val reliefEarnedRunAverage: Double? get() = reliefOuts.takeIf { it > 0 }?.let { reliefEarnedRuns * 27.0 / it }
}

internal data class InterleagueTeamStats(
    val team: Int,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val runs: Int?,
    val runsAllowed: Int?,
) {
    val winningRate: Double? get() = (wins + losses).takeIf { it > 0 }?.let { wins.toDouble() / it }
}

internal data class PostseasonResults(
    val first1: Int,
    val first2: Int,
    val first3: Int,
    val second1: Int,
    val second2: Int,
    val second3: Int,
    val firstCs1: Int,
    val secondCs1: Int,
    val firstCs2: Int,
    val secondCs2: Int,
    val japanWinner: Int,
)

internal data class HomeData(
    val team: Int,
    val season: Int,
    val year: Int?,
    val currentDay: Int,
    val money: Int?,
    val color: Int?,
    val teams: Map<Int, String>,
    val players: Map<Int, String>,
    val days: List<SeasonDay>,
    val games: List<SeasonGame>,
    val slots: Map<Pair<Int, Int>, Int>,
    val relief: List<ReliefAppearance>,
    val notices: List<String> = emptyList(),
    val injuries: List<MatchInjury> = emptyList(),
    val teamColors: Map<Int, Int> = emptyMap(),
    val teamStats: List<TeamSeasonStats> = emptyList(),
    val interleagueStats: List<InterleagueTeamStats> = emptyList(),
    val interleagueStatsUnavailable: Boolean = false,
    val postseason: PostseasonResults? = null,
) {
    val today: SeasonDay? get() = days.find { it.day == currentDay }
    val remaining: Int get() = (days.count { it.event == 1 } - games.count { it.team == team && it.game in regularGames }).coerceAtLeast(0)
    private val regularGames: Set<Int> = days.filter { it.event == 1 }.mapNotNull { it.game }.toSet()

    fun teamName(id: Int): String = teams[id] ?: "球団$id"

    fun playerName(id: Int?): String = if (id == null || id <= 0) "記録なし" else players[id] ?: "選手ID $id（登録情報なし）"

    fun gameOn(day: SeasonDay): SeasonGame? = games.find { it.team == team && it.game == day.game }

    fun calendarGamesOn(day: SeasonDay): List<SeasonGame> {
        val number = day.game ?: return emptyList()
        if (day.event !in 3..5) return listOfNotNull(gameOn(day))
        val matches =
            games
                .filter { it.game == number && it.team in 1..12 && it.opponent in 1..12 }
                .distinctBy { minOf(it.team, it.opponent) to maxOf(it.team, it.opponent) }
                .sortedWith(compareBy<SeasonGame> { minOf(it.team, it.opponent) }.thenBy { maxOf(it.team, it.opponent) })
        val own = matches.find { it.team == team || it.opponent == team }
        return if (own == null) matches else listOf(own) + matches.filterNot { it == own }
    }

    fun regularResults(team: Int): List<SeasonGame> = games.filter { it.team == team && it.game in regularGames }

    fun venue(
        day: SeasonDay,
        team: Int = this.team,
    ): String? = slots[day.game to team]?.let { if (it % 2 == 0) "ホーム" else "ビジター" }

    fun opponent(day: SeasonDay): Int? {
        gameOn(day)?.let { return it.opponent }
        val number = day.game ?: return null
        val slot = slots[number to team] ?: return null
        val opponentSlot = if (slot % 2 == 0) slot - 1 else slot + 1
        return slots.entries
            .find { it.key.first == number && it.value == opponentSlot }
            ?.key
            ?.second
    }
}

/** Game weekdays are Monday=0 through Sunday=6; postseason game numbers have separate ranges. */
internal fun seasonGameNumbers(days: List<SeasonDay>): List<SeasonDay> {
    val postseason =
        mapOf(
            284 to 151,
            285 to 152,
            286 to 153,
            289 to 161,
            290 to 162,
            291 to 163,
            292 to 164,
            293 to 165,
            294 to 166,
            299 to 171,
            300 to 172,
            302 to 173,
            303 to 174,
            304 to 175,
            306 to 176,
            307 to 177,
            308 to 178,
        )
    var number = 0
    return days.sortedBy { it.day }.map { day ->
        val game =
            when {
                day.event == 1 -> ++number
                day.event in 3..5 -> postseason[day.day]
                else -> null
            }
        day.copy(game = game)
    }
}
