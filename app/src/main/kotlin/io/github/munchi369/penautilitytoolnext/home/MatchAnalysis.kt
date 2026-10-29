package io.github.munchi369.penautilitytoolnext.home

internal data class MatchInjury(
    val team: Int,
    val player: Int,
    val day: Int,
    val kind: Int,
    val duration: Int,
) {
    val description: String
        get() =
            when (kind) {
                1 -> "違和感"
                2 -> "故障"
                else -> "区分 $kind"
            }
}

internal data class MatchTrend(
    val competition: String,
    val streak: String,
    val wins: Int,
    val losses: Int,
    val draws: Int,
) {
    val matchup: String get() = "${wins}勝 ${losses}敗 ${draws}分"
}

/** Counts only the selected competition and stops at the selected date, including that game. */
internal fun HomeData.matchTrend(
    day: SeasonDay,
    game: SeasonGame,
): MatchTrend {
    val resultsByNumber = games.filter { it.team == game.team }.associateBy { it.game }
    val history =
        days
            .filter { it.day <= day.day && it.event == day.event }
            .sortedBy { it.day }
            .mapNotNull { date -> resultsByNumber[date.game] }
    val decisions = history.map { it.runs.compareTo(it.allowed) }.filter { it != 0 }
    val last = decisions.lastOrNull()
    val streak =
        if (last == null) {
            "連勝・連敗なし"
        } else {
            "${decisions.asReversed().takeWhile { it == last }.size}${if (last > 0) "連勝" else "連敗"}"
        }
    val matchup = history.filter { it.opponent == game.opponent }
    return MatchTrend(
        competition =
            when (day.event) {
                1 -> "公式戦"
                3 -> "CSファースト"
                4 -> "CSファイナル"
                5 -> "日本シリーズ"
                else -> "試合"
            },
        streak = streak,
        wins = matchup.count { it.runs > it.allowed },
        losses = matchup.count { it.runs < it.allowed },
        draws = matchup.count { it.runs == it.allowed },
    )
}

internal data class ReliefInterval(
    val previousDay: Int?,
    val consecutiveDays: Int,
) {
    fun text(day: Int): String =
        when {
            previousDay == null -> "前回記録なし"
            consecutiveDays > 1 -> "${consecutiveDays}連投"
            else -> "中${day - previousDay - 1}日"
        }
}

/** Uses recorded starts and relief appearances up to the selected day, including off days in the interval. */
internal fun HomeData.reliefInterval(appearance: ReliefAppearance): ReliefInterval {
    val day = appearance.day
    val history = relief.filter { it.player == appearance.player && it.day <= day }
    val starts = games.filter { it.team == team && it.starter == appearance.player }.map { it.game }.toSet()
    val dates = (history.map { it.day } + days.filter { it.day <= day && it.game in starts }.map { it.day }).toSet()
    val previous = dates.filter { it < day }.maxOrNull()
    var consecutive = 1
    while (day - consecutive in dates) consecutive++
    return ReliefInterval(previous, consecutive)
}

internal fun HomeData.matchInjuries(
    day: SeasonDay,
    game: SeasonGame,
): List<MatchInjury> =
    injuries
        .filter { it.day == day.day && (it.team == game.team || it.team == game.opponent) }
        .sortedWith(compareBy<MatchInjury> { it.team != game.team }.thenBy { it.player }.thenBy { it.kind })

internal fun HomeData.dayText(day: Int): String = days.find { it.day == day }?.let { "${it.month}/${it.date}" } ?: "日付記録なし"
