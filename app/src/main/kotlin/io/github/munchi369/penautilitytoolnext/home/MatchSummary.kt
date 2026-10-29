package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MatchSummary(
    data: HomeData,
    day: SeasonDay,
    game: SeasonGame,
    matches: List<SeasonGame> = listOf(game),
    onOpenPlayer: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(0) }
    var selectedMatch by rememberSaveable(matches.size) { mutableStateOf(0) }
    val displayedGame = matches.getOrElse(selectedMatch) { game }
    val ownMatch = data.team == displayedGame.team || data.team == displayedGame.opponent
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 460.dp
        Column(Modifier.fillMaxSize().padding(if (narrow) 6.dp else 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CalendarAction("← 戻る", onClick = onBack)
                Text(
                    "${day.month}月${day.date}日 ${data.venue(day, displayedGame.team).orEmpty()}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (matches.size > 1) {
                HomeSegmentedControl(
                    matches.map { if (it.team <= 6) "1stリーグ" else "2ndリーグ" },
                    selectedMatch,
                ) {
                    selectedMatch = it
                    section = 0
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MatchTeamName(data, displayedGame.team, Modifier.weight(1f))
                Text(displayedGame.result, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MatchTeamName(data, displayedGame.opponent, Modifier.weight(1f))
            }
            HomeSegmentedControl(
                listOf("サマリー", if (narrow) "先発・打順" else "先発投手・打順") + if (ownMatch) listOf("救援登板") else emptyList(),
                section,
            ) { section = it }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            key(displayedGame.team) {
                when (section) {
                    0 -> MatchOverview(data, day, displayedGame, Modifier.weight(1f), onOpenPlayer)
                    1 -> MatchBattingOrders(data, displayedGame, Modifier.weight(1f), onOpenPlayer)
                    else -> ReliefSummary(data, day, Modifier.weight(1f), onOpenPlayer)
                }
            }
        }
    }
}

@Composable
private fun MatchTeamName(
    data: HomeData,
    team: Int,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.width(3.dp).height(20.dp).background(data.teamAccent(team), RoundedCornerShape(2.dp)))
        Text(data.teamName(team), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MatchOverview(
    data: HomeData,
    day: SeasonDay,
    game: SeasonGame,
    modifier: Modifier,
    onOpenPlayer: (Int) -> Unit,
) {
    val trend = remember(data, day, game) { data.matchTrend(day, game) }
    val injuries = remember(data, day, game) { data.matchInjuries(day, game) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxHeight >= 200.dp) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchTrends(trend, Modifier.fillMaxWidth())
                HorizontalDivider()
                MatchInjuries(data, injuries, Modifier.weight(1f), onOpenPlayer = onOpenPlayer)
            }
        } else {
            var page by rememberSaveable { mutableStateOf(0) }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (page == 0) "成績" else "当日の故障・違和感", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    CalendarAction(if (page == 0) "故障情報 →" else "← 成績") { page = 1 - page }
                }
                if (page == 0) {
                    MatchTrends(trend, Modifier.fillMaxWidth())
                } else {
                    MatchInjuries(data, injuries, Modifier.weight(1f), showHeading = false, onOpenPlayer = onOpenPlayer)
                }
            }
        }
    }
}

@Composable
private fun MatchTrends(
    trend: MatchTrend,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(trend.streak, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("${trend.competition}・試合終了時点", style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f)) {
            Text(trend.matchup, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("この相手との対戦成績", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MatchInjuries(
    data: HomeData,
    injuries: List<MatchInjury>,
    modifier: Modifier,
    showHeading: Boolean = true,
    onOpenPlayer: (Int) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rowHeight = if (maxWidth < 400.dp) 62.dp else 44.dp
        val reserved = if (showHeading) 54.dp else 30.dp
        val pageSize = ((maxHeight - reserved) / rowHeight).toInt().coerceAtLeast(1)
        val pages = injuries.chunked(pageSize)
        var page by rememberSaveable(pageSize, injuries.size) { mutableStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            if (showHeading) Text("当日の故障・違和感", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            when {
                data.notices.any { it.startsWith("故障・違和感履歴") } -> Text("履歴を取得できません", style = MaterialTheme.typography.bodySmall)
                injuries.isEmpty() -> Text("両チームの当日の記録なし", style = MaterialTheme.typography.bodySmall)
                else ->
                    pages.getOrNull(page).orEmpty().forEach { injury ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            CalendarPlayerText(
                                data,
                                injury.player,
                                "${data.playerName(injury.player)}・${injury.description}",
                                onOpenPlayer,
                            )
                            Text("${data.teamName(injury.team)} / 記録上の期間 ${injury.duration}日", style = MaterialTheme.typography.labelSmall)
                        }
                    }
            }
            Spacer(Modifier.weight(1f))
            MatchPages(page, pages.size) { page = it }
        }
    }
}

@Composable
internal fun ReliefSummary(
    data: HomeData,
    day: SeasonDay,
    modifier: Modifier,
    onOpenPlayer: (Int) -> Unit = {},
) {
    val appearances = remember(data, day) { data.relief.filter { it.day == day.day }.sortedBy { it.player } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rowHeight = if (maxWidth < 420.dp) 80.dp else 64.dp
        val pageSize = ((maxHeight - 50.dp) / rowHeight).toInt().coerceAtLeast(1)
        val pages = appearances.chunked(pageSize)
        var page by rememberSaveable(pageSize, appearances.size) { mutableStateOf(0) }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("自チームの救援登板", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            when {
                data.notices.any { it.startsWith("救援登板履歴") } -> Text("救援登板履歴を取得できません", style = MaterialTheme.typography.bodySmall)
                appearances.isEmpty() -> Text("登板記録なし", style = MaterialTheme.typography.bodySmall)
                else ->
                    pages.getOrNull(page).orEmpty().forEach { appearance ->
                        val interval = remember(data, appearance) { data.reliefInterval(appearance) }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            CalendarPlayerText(
                                data,
                                appearance.player,
                                "${data.playerName(appearance.player)} ${decisionText(appearance.decision)}",
                                onOpenPlayer,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "当日 ${outsText(appearance.outs)}回 / 打者${appearance.batters} / 失点${appearance.runs}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                interval.text(day.day) + (interval.previousDay?.let { "・前回 ${data.dayText(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider()
                    }
            }
            Spacer(Modifier.weight(1f))
            MatchPages(page, pages.size) { page = it }
        }
    }
}

@Composable
private fun MatchPages(
    page: Int,
    count: Int,
    onPage: (Int) -> Unit,
) {
    if (count > 1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarAction("前へ", page > 0) { onPage(page - 1) }
            Text("${page + 1} / $count", style = MaterialTheme.typography.labelSmall)
            CalendarAction("次へ", page < count - 1) { onPage(page + 1) }
        }
    }
}

@Composable
private fun MatchBattingOrders(
    data: HomeData,
    game: SeasonGame,
    modifier: Modifier,
    onOpenPlayer: (Int) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 420.dp
        var opponentSelected by rememberSaveable { mutableStateOf(false) }
        val availableHeight = maxHeight - if (narrow) 36.dp else 0.dp
        val pageSize = if (availableHeight >= 290.dp) 9 else ((availableHeight - 72.dp) / 24.dp).toInt().coerceIn(1, 9)
        val pages = (0..8).toList().chunked(pageSize)
        var page by rememberSaveable(pageSize) { mutableStateOf(0) }
        val other = data.games.find { it.game == game.game && it.team == game.opponent }
        Column(Modifier.fillMaxSize()) {
            if (narrow) {
                val labels =
                    if (game.team == data.team) {
                        listOf("自チーム", "相手チーム")
                    } else {
                        listOf(data.teamName(game.team), data.teamName(game.opponent))
                    }
                HomeSegmentedControl(labels, if (opponentSelected) 1 else 0, dense = true) { opponentSelected = it == 1 }
            }
            val teams = listOf(game.team to game, game.opponent to other)
            val visible = if (narrow) listOf(teams[if (opponentSelected) 1 else 0]) else teams
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                visible.forEach { (team, record) ->
                    Column(Modifier.weight(1f).fillMaxSize()) {
                        MatchTeamName(data, team, Modifier.fillMaxWidth())
                        CalendarPlayerText(
                            data,
                            record?.starter,
                            "先発 ${data.playerName(record?.starter)}",
                            onOpenPlayer,
                            maxLines = 1,
                        )
                        if (record == null) {
                            Text("先発打順の記録なし", style = MaterialTheme.typography.bodySmall)
                        } else {
                            pages[page].forEach { index ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    CalendarPlayerText(
                                        data,
                                        record.battingOrder.getOrNull(index),
                                        "${index + 1}　${data.playerName(record.battingOrder.getOrNull(index))}",
                                        onOpenPlayer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalendarAction("前へ", page > 0) { page-- }
                    Text("打順 ${pages[page].first() + 1}–${pages[page].last() + 1}", style = MaterialTheme.typography.labelSmall)
                    CalendarAction("次へ", page < pages.lastIndex) { page++ }
                }
            }
        }
    }
}

private fun decisionText(value: Int): String =
    when (value) {
        1 -> "勝利"
        2 -> "敗戦"
        3 -> "ホールド"
        4 -> "セーブ"
        else -> ""
    }
