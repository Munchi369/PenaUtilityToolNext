package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal enum class SeasonFeedKind { GAME, INTERLEAGUE, CLIMAX, JAPAN_SERIES, MONTH, INJURY }

internal enum class SeasonFeedTone { POSITIVE, NEGATIVE, EVEN }

internal data class SeasonFeedEntry(
    val day: SeasonDay,
    val title: String,
    val detail: String,
    val kind: SeasonFeedKind,
    val calendarDay: SeasonDay? = null,
    val injuryKind: Int? = null,
    val tone: SeasonFeedTone = SeasonFeedTone.EVEN,
    val playerId: Int? = null,
    val isHundredMilestone: Boolean = false,
)

/** Build only dated events supported by the saved schedule, results and injury history. */
internal fun HomeData.seasonFeed(): List<SeasonFeedEntry> {
    val results = games.filter { it.team == team }.associateBy { it.game }
    val entries = mutableListOf<SeasonFeedEntry>()
    var wins = 0
    var losses = 0
    var maxSavings = 0
    var firstCs = true
    var firstJapanSeries = true
    val monthly = mutableMapOf<Int, MutableList<SeasonGame>>()
    val playedDays = days.filter { it.day <= currentDay }.sortedBy { it.day }
    var winRun = 0
    var lossRun = 0
    var longestWins = 0
    var longestLosses = 0
    var longestWinDay: Int? = null
    var longestLossDay: Int? = null
    playedDays.filter { it.event == 1 }.forEach { day ->
        val game = day.game?.let(results::get) ?: return@forEach
        when {
            game.runs > game.allowed -> {
                winRun++
                lossRun = 0
                if (winRun > longestWins) {
                    longestWins = winRun
                    longestWinDay = day.day
                }
            }
            game.runs < game.allowed -> {
                lossRun++
                winRun = 0
                if (lossRun > longestLosses) {
                    longestLosses = lossRun
                    longestLossDay = day.day
                }
            }
        }
    }

    playedDays.forEach { day ->
        val game = day.game?.let(results::get) ?: return@forEach
        val score = "${teamName(game.opponent)}戦 ${game.runs}−${game.allowed}"
        val highlights = mutableListOf<String>()
        var isHundredMilestone = false
        if (day.event == 1) {
            monthly.getOrPut(day.month) { mutableListOf() }.add(game)
            when {
                game.runs > game.allowed -> {
                    wins++
                    if (wins == 1) highlights += "今季初勝利"
                    if (wins == 100) {
                        highlights += "シーズン100勝"
                        isHundredMilestone = true
                    }
                    if (day.day == longestWinDay && longestWins >= 5) {
                        highlights += "${longestWins}連勝"
                    }
                    val savings = wins - losses
                    if (savings > maxSavings && savings >= 10 && savings % 10 == 0) {
                        highlights += "貯金${savings}に到達"
                    }
                    maxSavings = maxOf(maxSavings, savings)
                    if (game.allowed == 0) highlights += "完封勝利"
                }
                game.runs < game.allowed -> {
                    losses++
                    if (losses == 100) {
                        highlights += "シーズン100敗"
                        isHundredMilestone = true
                    }
                    if (day.day == longestLossDay && longestLosses >= 5) highlights += "${longestLosses}連敗"
                }
            }
        } else if (day.event in 3..4 && firstCs) {
            highlights += "CS初戦"
            firstCs = false
        } else if (day.event == 5 && firstJapanSeries) {
            highlights += "日本シリーズ初戦"
            firstJapanSeries = false
        }
        val margin = game.runs - game.allowed
        if ((day.event == 1 || day.event in 3..5) && abs(margin) >= 8) {
            highlights += "${abs(margin)}点差で${if (margin > 0) "大勝" else "大敗"}"
        }
        if (highlights.isNotEmpty()) {
            val tone =
                when {
                    game.runs > game.allowed -> SeasonFeedTone.POSITIVE
                    game.runs < game.allowed -> SeasonFeedTone.NEGATIVE
                    else -> SeasonFeedTone.EVEN
                }
            entries +=
                SeasonFeedEntry(
                    day,
                    highlights.joinToString("・"),
                    score,
                    SeasonFeedKind.GAME,
                    day,
                    tone = tone,
                    isHundredMilestone = isHundredMilestone,
                )
        }
    }

    monthly.forEach { (month, gamesInMonth) ->
        val gameNumbers = gamesInMonth.map(SeasonGame::game).toSet()
        val lastDay = playedDays.last { it.month == month && it.event == 1 && it.game in gameNumbers }
        val monthWins = gamesInMonth.count { it.runs > it.allowed }
        val monthLosses = gamesInMonth.count { it.runs < it.allowed }
        val draws = gamesInMonth.size - monthWins - monthLosses
        val currentMonth = today?.month == month
        val title = if (currentMonth) "${month}月の戦績（ここまで）" else "${month}月の戦績"
        entries += SeasonFeedEntry(lastDay, title, "${monthWins}勝 ${monthLosses}敗 ${draws}分", SeasonFeedKind.MONTH)
    }

    val interleagueDays = days.filter { it.day in 155..174 && it.event == 1 }
    val ownInterleague = interleagueStats.singleOrNull { it.team == team }
    if (
        interleagueDays.isNotEmpty() &&
        currentDay >= interleagueDays.last().day &&
        interleagueStats.size == 12 &&
        interleagueStats.all { it.games == interleagueDays.size } &&
        interleagueDays.all { it.game?.let(results::containsKey) == true } &&
        ownInterleague != null
    ) {
        val lastDay = interleagueDays.lastOrNull { it.game?.let(results::containsKey) == true && it.day <= currentDay }
        if (lastDay != null) {
            val rate = ownInterleague.winningRate ?: -1.0
            val rank = 1 + interleagueStats.count { (it.winningRate ?: -1.0) > rate }
            entries +=
                SeasonFeedEntry(
                    lastDay,
                    "交流戦${rank}位",
                    "${ownInterleague.wins}勝 ${ownInterleague.losses}敗 ${ownInterleague.draws}分",
                    SeasonFeedKind.INTERLEAGUE,
                    lastDay,
                )
        }
    }

    fun seriesResult(
        event: Int,
        participants: Set<Int>,
        winner: Int,
        label: String,
        kind: SeasonFeedKind,
        advanceWin: Boolean = false,
    ) {
        if (team !in participants || winner !in participants) return
        val played =
            playedDays
                .filter { it.event == event }
                .mapNotNull { day -> day.game?.let(results::get)?.let { day to it } }
        val last = played.lastOrNull() ?: return
        val opponent = played.first().second.opponent
        val wins = played.count { (_, game) -> game.runs > game.allowed }
        val losses = played.count { (_, game) -> game.runs < game.allowed }
        val draws = played.size - wins - losses
        val record = "${wins}勝${losses}敗" + if (draws > 0) "${draws}分" else ""
        val seedNote = if (advanceWin) "（先行1勝）" else ""
        val success = winner == team
        val title =
            when (kind) {
                SeasonFeedKind.JAPAN_SERIES -> if (success) "日本一" else "日本シリーズ敗北"
                else -> "$label${if (success) "突破" else "敗北"}"
            }
        entries +=
            SeasonFeedEntry(
                last.first,
                title,
                "${teamName(opponent)}に$record$seedNote",
                kind,
                last.first,
                tone = if (success) SeasonFeedTone.POSITIVE else SeasonFeedTone.NEGATIVE,
            )
    }
    postseason?.let { result ->
        val firstLeague = team in 1..6
        val firstParticipants = if (firstLeague) setOf(result.first2, result.first3) else setOf(result.second2, result.second3)
        val firstWinner = if (firstLeague) result.firstCs1 else result.secondCs1
        seriesResult(3, firstParticipants, firstWinner, "CSファースト", SeasonFeedKind.CLIMAX)

        val finalSeed = if (firstLeague) result.first1 else result.second1
        val finalWinner = if (firstLeague) result.firstCs2 else result.secondCs2
        seriesResult(
            4,
            setOf(finalSeed, firstWinner),
            finalWinner,
            "CSファイナル",
            SeasonFeedKind.CLIMAX,
            advanceWin = team == finalSeed,
        )
        seriesResult(
            5,
            setOf(result.firstCs2, result.secondCs2),
            result.japanWinner,
            "日本シリーズ",
            SeasonFeedKind.JAPAN_SERIES,
        )
    }

    val daysByNumber = days.associateBy(SeasonDay::day)
    injuries
        .filter { it.team == team && it.day <= currentDay }
        .forEach { injury ->
            val day = daysByNumber[injury.day] ?: return@forEach
            entries +=
                SeasonFeedEntry(
                    day,
                    "${playerName(injury.player)}：${injury.description}",
                    "記録上の期間 ${injury.duration}日",
                    SeasonFeedKind.INJURY,
                    day,
                    injuryKind = injury.kind,
                    playerId = injury.player,
                )
        }
    return entries.sortedWith(compareBy<SeasonFeedEntry> { it.day.day }.thenBy { it.kind.ordinal }.thenBy { it.title })
}

@Composable
internal fun HomeSeasonFeed(
    controller: HomeController,
    data: HomeData,
    modifier: Modifier = Modifier,
    onOpenPlayer: (Int) -> Unit = {},
) {
    val entries = remember(data) { data.seasonFeed() }
    val injuryFailed = data.notices.any { it.startsWith("故障・違和感履歴") }
    val scroll = rememberScrollState()
    LaunchedEffect(data) {
        scroll.scrollTo(scroll.maxValue)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (injuryFailed) {
            Text("怪我の履歴を取得できません", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        if (entries.isEmpty()) {
            Text("試合の節目や怪我の記録がまだありません", style = MaterialTheme.typography.bodySmall)
        } else {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp)) {
                    entries.forEach { entry ->
                        val clickable =
                            when {
                                entry.kind == SeasonFeedKind.MONTH ->
                                    Modifier.clickable(role = Role.Button) { controller.focusMonth(entry.day.month) }
                                entry.calendarDay != null ->
                                    Modifier.clickable(role = Role.Button) { controller.focusDay(entry.calendarDay) }
                                else -> Modifier
                            }
                        Row(
                            Modifier.fillMaxWidth().then(clickable).padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(data.dayText(entry.day.day), Modifier.width(48.dp), style = MaterialTheme.typography.bodyMedium)
                            SeasonFeedIcon(entry.kind, entry.injuryKind, entry.tone, entry.isHundredMilestone)
                            Column(Modifier.weight(1f)) {
                                if (entry.playerId != null) {
                                    CalendarPlayerText(
                                        data,
                                        entry.playerId,
                                        entry.title,
                                        onOpenPlayer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                } else {
                                    Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    entry.detail,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (entry.kind == SeasonFeedKind.MONTH || entry.calendarDay != null) {
                                Text("›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SeasonFeedIcon(
    kind: SeasonFeedKind,
    injuryKind: Int?,
    tone: SeasonFeedTone,
    isHundredMilestone: Boolean,
) {
    val color =
        when (kind) {
            SeasonFeedKind.GAME ->
                when (tone) {
                    SeasonFeedTone.POSITIVE -> Color(0xFF15803D)
                    SeasonFeedTone.NEGATIVE -> Color(0xFF1D4ED8)
                    SeasonFeedTone.EVEN -> MaterialTheme.colorScheme.primary
                }
            SeasonFeedKind.MONTH -> Color(0xFF64748B)
            SeasonFeedKind.INTERLEAGUE -> Color(0xFF0E7490)
            SeasonFeedKind.CLIMAX -> if (tone == SeasonFeedTone.POSITIVE) Color(0xFF15803D) else Color(0xFF1D4ED8)
            SeasonFeedKind.JAPAN_SERIES -> if (tone == SeasonFeedTone.POSITIVE) Color(0xFFB45309) else Color(0xFF1D4ED8)
            SeasonFeedKind.INJURY ->
                when (injuryKind) {
                    1 -> Color(0xFFB45309)
                    2 -> Color(0xFFC62828)
                    else -> Color(0xFF64748B)
                }
        }
    Canvas(
        Modifier
            .padding(top = 1.dp)
            .size(20.dp)
            .padding(3.dp),
    ) {
        fun line(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
        ) {
            drawLine(
                color,
                Offset(size.width * x1, size.height * y1),
                Offset(size.width * x2, size.height * y2),
                1.5.dp.toPx(),
                StrokeCap.Round,
            )
        }
        when (kind) {
            SeasonFeedKind.GAME -> {
                if (isHundredMilestone) {
                    line(0.2f, 0.06f, 0.2f, 0.94f)
                    line(0.2f, 0.12f, 0.85f, 0.12f)
                    line(0.85f, 0.12f, 0.7f, 0.46f)
                    line(0.7f, 0.46f, 0.2f, 0.46f)
                } else {
                    line(0.5f, 0.06f, 0.94f, 0.5f)
                    line(0.94f, 0.5f, 0.5f, 0.94f)
                    line(0.5f, 0.94f, 0.06f, 0.5f)
                    line(0.06f, 0.5f, 0.5f, 0.06f)
                    line(0.5f, 0.94f, 0.5f, 0.5f)
                }
            }
            SeasonFeedKind.MONTH -> {
                line(0.12f, 0.2f, 0.88f, 0.2f)
                line(0.88f, 0.2f, 0.88f, 0.9f)
                line(0.88f, 0.9f, 0.12f, 0.9f)
                line(0.12f, 0.9f, 0.12f, 0.2f)
                line(0.12f, 0.42f, 0.88f, 0.42f)
                line(0.32f, 0.08f, 0.32f, 0.3f)
                line(0.68f, 0.08f, 0.68f, 0.3f)
                line(0.32f, 0.62f, 0.46f, 0.62f)
                line(0.58f, 0.62f, 0.72f, 0.62f)
            }
            SeasonFeedKind.INTERLEAGUE -> {
                line(0.12f, 0.22f, 0.88f, 0.78f)
                line(0.88f, 0.22f, 0.12f, 0.78f)
                line(0.12f, 0.22f, 0.38f, 0.22f)
                line(0.88f, 0.78f, 0.62f, 0.78f)
                line(0.88f, 0.22f, 0.62f, 0.22f)
                line(0.12f, 0.78f, 0.38f, 0.78f)
            }
            SeasonFeedKind.CLIMAX -> {
                line(0.12f, 0.2f, 0.42f, 0.2f)
                line(0.12f, 0.8f, 0.42f, 0.8f)
                line(0.42f, 0.2f, 0.42f, 0.8f)
                line(0.42f, 0.5f, 0.9f, 0.5f)
            }
            SeasonFeedKind.JAPAN_SERIES -> {
                line(0.2f, 0.15f, 0.8f, 0.15f)
                line(0.2f, 0.15f, 0.3f, 0.62f)
                line(0.8f, 0.15f, 0.7f, 0.62f)
                line(0.3f, 0.62f, 0.7f, 0.62f)
                line(0.5f, 0.62f, 0.5f, 0.82f)
                line(0.3f, 0.85f, 0.7f, 0.85f)
                line(0.2f, 0.2f, 0.08f, 0.2f)
                line(0.08f, 0.2f, 0.14f, 0.42f)
                line(0.8f, 0.2f, 0.92f, 0.2f)
                line(0.92f, 0.2f, 0.86f, 0.42f)
            }
            SeasonFeedKind.INJURY -> {
                line(0.14f, 0.64f, 0.64f, 0.14f)
                line(0.64f, 0.14f, 0.86f, 0.36f)
                line(0.86f, 0.36f, 0.36f, 0.86f)
                line(0.36f, 0.86f, 0.14f, 0.64f)
                line(0.38f, 0.48f, 0.52f, 0.62f)
                line(0.48f, 0.38f, 0.62f, 0.52f)
            }
        }
    }
}
