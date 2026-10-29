package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.munchi369.penautilitytoolnext.player.PlayerRoster
import io.github.munchi369.penautilitytoolnext.player.PlayerRosterPlayer
import io.github.munchi369.penautilitytoolnext.player.PlayerRosterViewState
import io.github.munchi369.penautilitytoolnext.player.RosterPositionFilter
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import java.util.Locale

private val homeBackground = Color(0xFFF8F8FA)
private val teamStatBestColor = Color(0xFF1D4ED8)
private val teamStatWorstColor = Color(0xFFB91C1C)

@Composable
internal fun HomeScreen(
    controller: HomeController,
    rosterState: PlayerRosterViewState,
    onOpenCalendarPlayer: (Int) -> Unit = {},
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
) {
    val data = controller.data
    when {
        controller.loading -> CircularProgressIndicator(Modifier.padding(28.dp))
        controller.error != null -> Text(controller.error.orEmpty(), Modifier.padding(28.dp), color = MaterialTheme.colorScheme.error)
        data != null -> {
            var league by rememberSaveable(data.team) { mutableStateOf(if (data.team <= 6) 0 else 1) }
            var leagueView by rememberSaveable(data.team) { mutableStateOf(0) }
            var teamStatsPage by rememberSaveable(data.team) { mutableStateOf(0) }
            var teamStatsDetailPage by rememberSaveable(data.team) { mutableStateOf(0) }
            var calendarPage by rememberSaveable { mutableStateOf(false) }
            var pitching by rememberSaveable { mutableStateOf(false) }
            var leadersLeague by rememberSaveable(data.team) { mutableStateOf(if (data.team <= 6) 0 else 1) }
            LaunchedEffect(controller.showingMatch) {
                if (controller.showingMatch) calendarPage = true
            }
            BoxWithConstraints(Modifier.fillMaxSize().background(homeBackground).padding(12.dp)) {
                val wide = maxWidth >= 1100.dp
                val compact = maxHeight < 700.dp
                val stacked = !wide && maxWidth >= 600.dp && maxHeight >= 1100.dp && !controller.showingMatch
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeOverview(data)
                    data.notices.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (wide) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1.55f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomePanel(compact = compact) {
                                    LeagueMatrix(
                                        data,
                                        league,
                                        leagueView,
                                        teamStatsPage,
                                        teamStatsDetailPage,
                                        compact,
                                        { league = it },
                                        { leagueView = it },
                                        {
                                            teamStatsPage = it
                                            teamStatsDetailPage = 0
                                        },
                                        { teamStatsDetailPage = it },
                                    )
                                }
                                HomePanel(Modifier.weight(1f).fillMaxWidth(), compact = compact, fillContent = true) {
                                    HomeLeaders(
                                        data,
                                        rosterState,
                                        leadersLeague,
                                        { leadersLeague = it },
                                        pitching,
                                        { pitching = it },
                                        onOpenPlayer,
                                        Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            HomePanel(Modifier.weight(1f).fillMaxSize(), compact = compact) {
                                HomeCalendar(controller, data, Modifier.fillMaxSize(), onOpenCalendarPlayer)
                            }
                        }
                    } else if (stacked) {
                        HomePanel(Modifier.fillMaxWidth()) {
                            LeagueMatrix(
                                data,
                                league,
                                leagueView,
                                teamStatsPage,
                                teamStatsDetailPage,
                                onLeague = { league = it },
                                onView = { leagueView = it },
                                onStatsPage = {
                                    teamStatsPage = it
                                    teamStatsDetailPage = 0
                                },
                                onDetailPage = { teamStatsDetailPage = it },
                            )
                        }
                        HomePanel(Modifier.weight(1f).fillMaxWidth(), fillContent = true) {
                            HomeLeaders(
                                data,
                                rosterState,
                                leadersLeague,
                                { leadersLeague = it },
                                pitching,
                                { pitching = it },
                                onOpenPlayer,
                                Modifier.fillMaxSize(),
                            )
                        }
                        HomePanel(Modifier.weight(1f).fillMaxWidth(), fillContent = true) {
                            HomeCalendar(controller, data, Modifier.fillMaxSize(), onOpenCalendarPlayer)
                        }
                    } else {
                        HomeViewTabs(listOf("リーグ戦・成績", "カレンダー"), if (calendarPage) 1 else 0) { calendarPage = it == 1 }
                        if (calendarPage) {
                            HomePanel(Modifier.weight(1f).fillMaxWidth(), compact = compact, fillContent = true) {
                                HomeCalendar(controller, data, Modifier.fillMaxSize(), onOpenCalendarPlayer)
                            }
                        } else {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomePanel(Modifier.fillMaxWidth(), compact = compact) {
                                    LeagueMatrix(
                                        data,
                                        league,
                                        leagueView,
                                        teamStatsPage,
                                        teamStatsDetailPage,
                                        compact = compact,
                                        onLeague = { league = it },
                                        onView = { leagueView = it },
                                        onStatsPage = {
                                            teamStatsPage = it
                                            teamStatsDetailPage = 0
                                        },
                                        onDetailPage = { teamStatsDetailPage = it },
                                    )
                                }
                                HomePanel(Modifier.weight(1f).fillMaxWidth(), compact = compact, fillContent = true) {
                                    HomeLeaders(
                                        data,
                                        rosterState,
                                        leadersLeague,
                                        { leadersLeague = it },
                                        pitching,
                                        { pitching = it },
                                        onOpenPlayer,
                                        Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fillContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    HomeCard(modifier) {
        Column(
            Modifier
                .then(if (fillContent) Modifier.fillMaxSize() else Modifier)
                .padding(if (compact) 8.dp else 12.dp),
            content = content,
        )
    }
}

@Composable
internal fun HomeCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color(0xFFE8E8ED)),
        content = content,
    )
}

@Composable
private fun LeagueMatrix(
    data: HomeData,
    league: Int,
    view: Int,
    statsPage: Int,
    detailPage: Int,
    compact: Boolean = false,
    onLeague: (Int) -> Unit,
    onView: (Int) -> Unit,
    onStatsPage: (Int) -> Unit,
    onDetailPage: (Int) -> Unit,
) {
    BoxWithConstraints {
        val tableWidth = maxWidth
        val paged = tableWidth < 500.dp
        val rowHeight = if (compact) 30.dp else 36.dp
        val tableHeight = rowHeight * 7 + if (paged) 40.dp else 0.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterEnd) {
                if (view != 2) {
                    HomeSegmentedControl(listOf(leagueName(0), leagueName(1)), league, onSelect = onLeague)
                }
            }
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeViewTabs(listOf("球団成績", "対戦表", "交流戦"), view, dense = paged, onSelect = onView)
                if (view == 0) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        HomeSegmentedControl(listOf("基本", "野手", "投手"), statsPage, dense = paged, onSelect = onStatsPage)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(tableHeight)) {
                when (view) {
                    0 -> TeamStatsTable(data, league, statsPage, detailPage, paged, compact, onDetailPage)
                    2 -> InterleagueTable(data, paged, compact)
                    else -> LeagueMatrixTable(data, league, tableWidth, compact)
                }
            }
        }
    }
}

@Composable
private fun LeagueMatrixTable(
    data: HomeData,
    league: Int,
    width: Dp,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val teams =
            (if (league == 0) 1..6 else 7..12).sortedWith(
                compareByDescending<Int> { team -> winningPercentage(data.regularResults(team)) ?: -1.0 }.thenBy { it },
            )
        val stacked = width < 760.dp
        val tiny = width < 420.dp
        val headerLength =
            when {
                width < 420.dp -> 2
                width < 600.dp -> 3
                stacked -> 4
                else -> 5
            }
        val cellHeight = (if (compact) 30.dp else 36.dp) + if (width < 500.dp) 5.dp else 0.dp
        Column {
            Row(Modifier.height(cellHeight), verticalAlignment = Alignment.CenterVertically) {
                MatrixCell("球団", 1.4f, textAlign = TextAlign.Start)
                teams.forEach {
                    Row(
                        Modifier.weight(1f).padding(horizontal = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(5.dp).background(data.teamAccent(it), CircleShape))
                        Text(
                            data.teamName(it).take(headerLength),
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MatrixCell("勝−敗−分", 1.7f)
                MatrixCell("勝率", 1.3f)
            }
            teams.forEachIndexed { index, team ->
                val results = data.regularResults(team)
                val opponents =
                    teams.filter { it != team }.associateWith { opponent ->
                        matchupCounts(results.filter { it.opponent == opponent })
                    }
                val highlights = matchupHighlights(opponents.values)
                val accent = data.teamAccent()
                val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                Row(
                    Modifier
                        .height(cellHeight)
                        .background(if (team == data.team) data.teamTint() else Color.Transparent)
                        .drawBehind {
                            drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                            if (team == data.team) drawRect(accent, size = size.copy(width = 3.dp.toPx()))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamNameCell(data, team, index + 1, 1.4f)
                    teams.forEach { opponent ->
                        val counts = opponents[opponent]
                        MatchupScoreCell(
                            if (counts == null) {
                                AnnotatedString("—")
                            } else {
                                matchupScoreText(
                                    counts,
                                    stacked,
                                    highlightWins = counts.wins == highlights.wins,
                                    highlightLosses = counts.losses == highlights.losses,
                                )
                            },
                            stacked = stacked,
                            tiny = tiny,
                        )
                    }
                    MatchupScoreCell(matchupScoreText(matchupCounts(results), stacked), 1.7f, stacked, tiny)
                    MatrixCell(
                        winningPercentage(results)?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—",
                        1.3f,
                        bold = true,
                        horizontalPadding = if (stacked) 3.dp else 6.dp,
                    )
                }
            }
        }
    }
}

internal fun teamStandings(
    stats: List<TeamSeasonStats>,
    league: Int,
): List<TeamSeasonStats> =
    stats
        .filter { it.team in if (league == 0) 1..6 else 7..12 }
        .sortedWith(compareByDescending<TeamSeasonStats> { it.winningRate ?: -1.0 }.thenBy { it.team })

internal fun interleagueStandings(stats: List<InterleagueTeamStats>): List<InterleagueTeamStats> =
    stats.sortedWith(compareByDescending<InterleagueTeamStats> { it.winningRate ?: -1.0 }.thenBy { it.team })

@Composable
private fun InterleagueTable(
    data: HomeData,
    paged: Boolean,
    compact: Boolean,
) {
    val standings = interleagueStandings(data.interleagueStats)
    if (standings.isEmpty()) {
        val message =
            when {
                data.interleagueStatsUnavailable -> "交流戦成績を取得できません"
                data.currentDay < 155 -> "交流戦はまだ始まっていません"
                else -> "交流戦の試合結果はまだありません"
            }
        Text(message, style = MaterialTheme.typography.bodySmall)
        return
    }
    val rowHeight = if (compact) 30.dp else 36.dp
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(rowHeight).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MatrixCell("順位・球団", 2f, textAlign = TextAlign.Start)
            if (!paged) MatrixCell("試合", 0.8f)
            MatrixCell("勝−敗−分", 1.8f)
            MatrixCell("勝率", 1.2f)
            MatrixCell("得−失", 1.2f)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp)) {
                standings.forEachIndexed { index, stats ->
                    val own = stats.team == data.team
                    val accent = data.teamAccent()
                    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .background(if (own) data.teamTint() else Color.Transparent)
                            .drawBehind {
                                drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                                if (own) drawRect(accent, size = size.copy(width = 3.dp.toPx()))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamNameCell(data, stats.team, index + 1, 2f)
                        if (!paged) MatrixCell("${stats.games}", 0.8f)
                        MatrixCell("${stats.wins}−${stats.losses}−${stats.draws}", 1.8f)
                        MatrixCell(stats.winningRate?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—", 1.2f, bold = true)
                        MatrixCell(
                            if (stats.runs == null || stats.runsAllowed == null) "—" else "${stats.runs}−${stats.runsAllowed}",
                            1.2f,
                        )
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun TeamStatsTable(
    data: HomeData,
    league: Int,
    page: Int,
    detailPage: Int,
    paged: Boolean,
    compact: Boolean,
    onDetailPage: (Int) -> Unit,
) {
    val standings = teamStandings(data.teamStats, league)
    if (standings.isEmpty()) {
        Text("球団成績を取得できません", style = MaterialTheme.typography.bodySmall)
        return
    }
    val leader = standings.first()
    val columns =
        when (page) {
            1 -> listOf("打率", "安打", "本塁打", "盗塁", "得点", "四球", "犠打")
            2 -> listOf("先発防率", "救援防率", "失点", "完投", "完封", "H / S")
            else -> listOf("試合", "勝−敗−分", "勝率", "ゲーム差", "得−失")
        }
    val visibleIndices = teamStatsVisibleColumns(page, detailPage, paged)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (paged && page != 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("表示項目", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                HomeSegmentedControl(listOf("1/2", "2/2"), detailPage, onSelect = onDetailPage)
            }
        }
        Column {
            Row(Modifier.height(if (compact) 30.dp else 36.dp), verticalAlignment = Alignment.CenterVertically) {
                MatrixCell("順位・球団", 2f, textAlign = TextAlign.Start)
                visibleIndices.forEach { column ->
                    MatrixCell(columns[column], if (columns[column] == "勝−敗−分") 1.8f else 1.2f)
                }
            }
            standings.forEachIndexed { index, stats ->
                val own = stats.team == data.team
                val accent = data.teamAccent()
                val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                Row(
                    Modifier
                        .height(if (compact) 30.dp else 36.dp)
                        .background(if (own) data.teamTint() else Color.Transparent)
                        .drawBehind {
                            drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                            if (own) drawRect(accent, size = size.copy(width = 3.dp.toPx()))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamNameCell(data, stats.team, index + 1, 2f)
                    val values = teamStatsValues(stats, leader, page)
                    visibleIndices.forEach { column ->
                        val color = teamStatRankColor(teamStatRank(stats, standings, page, column))
                        val holdsAndSaves =
                            if (page == 2 && column == 5) {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = teamStatRankColor(teamStatRank(stats, standings, page, column, 0)))) {
                                        append("${stats.holds}")
                                    }
                                    append(" / ")
                                    withStyle(SpanStyle(color = teamStatRankColor(teamStatRank(stats, standings, page, column, 1)))) {
                                        append("${stats.saves}")
                                    }
                                }
                            } else {
                                null
                            }
                        MatrixCell(
                            values[column],
                            if (columns[column] == "勝−敗−分") 1.8f else 1.2f,
                            bold = column == 2 && page == 0,
                            color = if (holdsAndSaves == null) color else Color.Unspecified,
                            formattedText = holdsAndSaves,
                        )
                    }
                }
            }
        }
    }
}

internal fun teamStatsVisibleColumns(
    page: Int,
    detailPage: Int,
    paged: Boolean,
): List<Int> =
    when {
        !paged || page == 0 ->
            when (page) {
                1 -> (0..6).toList()
                2 -> (0..5).toList()
                else -> (0..4).toList()
            }
        page == 1 && detailPage == 0 -> (0..3).toList()
        page == 1 -> (4..6).toList()
        detailPage == 0 -> (0..2).toList()
        else -> (3..5).toList()
    }

internal fun teamStatsValues(
    stats: TeamSeasonStats,
    leader: TeamSeasonStats,
    page: Int,
): List<String> =
    when (page) {
        1 ->
            listOf(
                stats.battingAverage?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—",
                "${stats.hits}",
                "${stats.homeRuns}",
                "${stats.steals}",
                "${stats.runs}",
                "${stats.walks}",
                "${stats.sacrificeBunts}",
            )
        2 ->
            listOf(
                stats.starterEarnedRunAverage?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—",
                stats.reliefEarnedRunAverage?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—",
                "${stats.runsAllowed}",
                "${stats.completeGames}",
                "${stats.shutouts}",
                "${stats.holds} / ${stats.saves}",
            )
        else -> {
            val gap = ((leader.wins - stats.wins) + (stats.losses - leader.losses)) / 2.0
            listOf(
                "${stats.games}",
                "${stats.wins}−${stats.losses}−${stats.draws}",
                stats.winningRate?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—",
                if (stats.team == leader.team) "—" else String.format(Locale.ROOT, "%.1f", gap),
                "${stats.runs}−${stats.runsAllowed}",
            )
        }
    }

internal enum class TeamStatRank { BEST, WORST, OTHER }

internal fun teamStatRank(
    stats: TeamSeasonStats,
    league: List<TeamSeasonStats>,
    page: Int,
    column: Int,
    part: Int = 0,
): TeamStatRank {
    val value = teamStatMetric(stats, page, column, part) ?: return TeamStatRank.OTHER
    val values = league.mapNotNull { teamStatMetric(it, page, column, part) }
    if (values.size < 2 || values.min() == values.max()) return TeamStatRank.OTHER
    val lowerIsBetter = page == 2 && column in 0..2
    val best = if (lowerIsBetter) values.min() else values.max()
    val worst = if (lowerIsBetter) values.max() else values.min()
    return when (value) {
        best -> TeamStatRank.BEST
        worst -> TeamStatRank.WORST
        else -> TeamStatRank.OTHER
    }
}

private fun teamStatMetric(
    stats: TeamSeasonStats,
    page: Int,
    column: Int,
    part: Int,
): Double? =
    when (page) {
        1 ->
            when (column) {
                0 -> stats.battingAverage?.let { String.format(Locale.ROOT, "%.3f", it).toDouble() }
                1 -> stats.hits.toDouble()
                2 -> stats.homeRuns.toDouble()
                3 -> stats.steals.toDouble()
                4 -> stats.runs.toDouble()
                5 -> stats.walks.toDouble()
                6 -> stats.sacrificeBunts.toDouble()
                else -> null
            }
        2 ->
            when (column) {
                0 -> stats.starterEarnedRunAverage?.let { String.format(Locale.ROOT, "%.2f", it).toDouble() }
                1 -> stats.reliefEarnedRunAverage?.let { String.format(Locale.ROOT, "%.2f", it).toDouble() }
                2 -> stats.runsAllowed.toDouble()
                3 -> stats.completeGames.toDouble()
                4 -> stats.shutouts.toDouble()
                5 -> (if (part == 0) stats.holds else stats.saves).toDouble()
                else -> null
            }
        else -> null
    }

private fun teamStatRankColor(rank: TeamStatRank): Color =
    when (rank) {
        TeamStatRank.BEST -> teamStatBestColor
        TeamStatRank.WORST -> teamStatWorstColor
        TeamStatRank.OTHER -> Color.Unspecified
    }

@Composable
private fun RowScope.MatrixCell(
    text: String,
    weight: Float = 1.2f,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    formattedText: AnnotatedString? = null,
    horizontalPadding: Dp = 6.dp,
    textAlign: TextAlign = TextAlign.End,
) {
    Text(
        formattedText ?: AnnotatedString(text),
        Modifier.weight(weight).padding(horizontal = horizontalPadding),
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowScope.MatchupScoreCell(
    text: AnnotatedString,
    weight: Float = 1f,
    stacked: Boolean,
    tiny: Boolean,
) {
    Text(
        text,
        Modifier.weight(weight).padding(horizontal = if (stacked) 2.dp else 3.dp),
        style =
            MaterialTheme.typography.labelSmall.copy(
                fontSize =
                    when {
                        tiny -> 10.sp
                        stacked -> 12.sp
                        else -> 13.sp
                    },
                lineHeight = if (stacked) 13.sp else MaterialTheme.typography.labelSmall.lineHeight,
                fontFeatureSettings = "tnum",
            ),
        textAlign = TextAlign.End,
        maxLines = if (stacked) 2 else 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal data class MatchupCounts(
    val wins: Int,
    val losses: Int,
    val draws: Int,
)

internal data class MatchupHighlights(
    val wins: Int?,
    val losses: Int?,
)

internal fun matchupCounts(games: List<SeasonGame>): MatchupCounts =
    MatchupCounts(
        games.count { it.runs > it.allowed },
        games.count { it.runs < it.allowed },
        games.count { it.runs == it.allowed },
    )

internal fun matchupHighlights(values: Collection<MatchupCounts>): MatchupHighlights =
    MatchupHighlights(
        values.maxOfOrNull { it.wins }?.takeIf { it > 0 },
        values.maxOfOrNull { it.losses }?.takeIf { it > 0 },
    )

internal fun matchupScoreText(
    counts: MatchupCounts,
    stacked: Boolean,
    highlightWins: Boolean = false,
    highlightLosses: Boolean = false,
): AnnotatedString =
    buildAnnotatedString {
        if (highlightWins) {
            withStyle(SpanStyle(color = teamStatBestColor)) { append(counts.wins.toString()) }
        } else {
            append(counts.wins.toString())
        }
        append("−")
        if (highlightLosses) {
            withStyle(SpanStyle(color = teamStatWorstColor)) { append(counts.losses.toString()) }
        } else {
            append(counts.losses.toString())
        }
        append(if (stacked) "\n${counts.draws}分" else "−${counts.draws}")
    }

internal fun winLoss(games: List<SeasonGame>): String =
    "${games.count { it.runs > it.allowed }}−${games.count { it.runs < it.allowed }}−${games.count { it.runs == it.allowed }}"

private fun winningPercentage(games: List<SeasonGame>): Double? {
    val decisions = games.count { it.runs != it.allowed }
    return if (decisions == 0) null else games.count { it.runs > it.allowed }.toDouble() / decisions
}

internal fun leagueName(league: Int): String = if (league == 0) "1stリーグ" else "2ndリーグ"

@Composable
private fun HomeLeaders(
    data: HomeData,
    state: PlayerRosterViewState,
    league: Int,
    onLeague: (Int) -> Unit,
    pitching: Boolean,
    onPitching: (Boolean) -> Unit,
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(league, pitching) { scroll.scrollTo(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterEnd) {
            HomeSegmentedControl(listOf(leagueName(0), leagueName(1)), league, onSelect = onLeague)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            HomeSegmentedControl(listOf("野手", "投手"), if (pitching) 1 else 0) { onPitching(it == 1) }
        }
        val roster = state.roster
        if (state.isLoading || roster == null || roster.season != data.season) {
            Text(if (state.isLoading) "成績を読み込み中" else "成績を取得できません。選手一覧で再読み込みしてください。")
        } else {
            val categories =
                if (pitching) {
                    listOf("ERA" to "防御率", "KATI" to "勝利", "DASSAN" to "奪三振", "S" to "セーブ")
                } else {
                    listOf("AVG" to "打率", "HONRUIDA" to "本塁打", "DATEN" to "打点", "TOURUI" to "盗塁")
                }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val columns = if (maxWidth >= 500.dp) 4 else 2
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.chunked(columns).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.forEach { (key, label) ->
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    val leaders = homeLeaders(data, roster, league, pitching, key)
                                    if (leaders.isEmpty()) {
                                        Text("対象選手なし", style = MaterialTheme.typography.bodySmall)
                                    }
                                    leaders.forEach { (rank, player, cell) ->
                                        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "$rank",
                                                Modifier.width(20.dp),
                                                style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 14.sp,
                                                        fontFeatureSettings = "tnum",
                                                    ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (rank == 1) FontWeight.Bold else FontWeight.Normal,
                                            )
                                            LeaderTeamInitial(
                                                name = data.teamName(player.teamNumber),
                                                color = data.teamAccent(player.teamNumber),
                                            )
                                            Text(
                                                player.name,
                                                Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 6.dp)
                                                    .clickable(role = Role.Button) { onOpenPlayer(player) },
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                cell.text,
                                                style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 14.sp,
                                                        fontFeatureSettings = "tnum",
                                                    ),
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

internal fun shortTeamNames(
    teams: Map<Int, String>,
    league: IntRange,
): Map<Int, String> {
    val names = league.associateWith { teams[it] ?: "球団$it" }
    return names.mapValues { (team, name) ->
        val length =
            (2..name.length).firstOrNull { size ->
                names.none { (otherTeam, otherName) -> otherTeam != team && otherName.startsWith(name.take(size)) }
            } ?: name.length
        name.take(length)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LeaderTeam(
    name: String,
    label: String,
    color: Color,
    compact: Boolean,
) {
    TooltipArea(tooltip = {
        Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 4.dp) {
            Text(name, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
    }) {
        Row(
            Modifier.semantics { contentDescription = name },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(
                label,
                Modifier.widthIn(max = if (compact) 42.dp else 96.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeaderTeamInitial(
    name: String,
    color: Color,
) {
    TooltipArea(tooltip = {
        Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 4.dp) {
            Text(name, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
    }) {
        Row(
            Modifier.width(28.dp).semantics { contentDescription = name },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(
                name.take(1),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun homeLeaders(
    data: HomeData,
    roster: PlayerRoster,
    league: Int,
    pitching: Boolean,
    metric: String,
): List<Triple<Int, PlayerRosterPlayer, PlayerCell>> {
    val range = if (league == 0) 1..6 else 7..12
    val completed = data.days.count { it.event == 1 && it.day < data.currentDay }
    val candidates =
        roster.players
            .filter { !it.major && it.teamNumber in range && (metric != "ERA" || RosterPositionFilter.Starters.matches(it)) }
            .mapNotNull { player ->
                val values = (if (pitching) player.pitching else player.batting)[1].orEmpty()
                val cell = values[metric]?.takeIf { it.number != null } ?: return@mapNotNull null
                val qualified =
                    when (metric) {
                        "AVG" -> (values["DASEKI"]?.number ?: -1.0) >= (completed * 3.1).toInt() && completed > 0
                        "ERA" -> (values["KAISUU"]?.number ?: -1.0) >= completed * 3 && completed > 0
                        else -> (cell.number ?: 0.0) > 0
                    }
                if (qualified) player to cell else null
            }.sortedWith(
                compareBy<Pair<PlayerRosterPlayer, PlayerCell>> {
                    (it.second.number ?: 0.0) * if (metric == "ERA") 1 else -1
                }.thenBy { it.first.playerId },
            )
    return candidates.take(10).map { (player, cell) ->
        Triple(candidates.indexOfFirst { it.second.number == cell.number } + 1, player, cell)
    }
}

@Composable
private fun RowScope.TeamNameCell(
    data: HomeData,
    team: Int,
    rank: Int,
    weight: Float,
) {
    Row(
        Modifier.weight(weight).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(5.dp).background(data.teamAccent(team), CircleShape))
        Text(
            data.teamName(team),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            fontWeight = if (team == data.team) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
