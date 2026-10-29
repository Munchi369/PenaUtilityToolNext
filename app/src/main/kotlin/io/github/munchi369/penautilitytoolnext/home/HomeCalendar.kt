package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val weekdays = listOf("月", "火", "水", "木", "金", "土", "日")
private const val CALENDAR_WEEK_ROWS = 6

internal enum class CalendarCompetition(
    val label: String,
    val background: Color,
) {
    INTERLEAGUE("交流戦", Color(0xFFE1F2F8)),
    CLIMAX_SERIES("CS", Color(0xFFE9F2E4)),
    JAPAN_SERIES("日本S", Color(0xFFFFF0D8)),
}

internal fun calendarCompetition(day: SeasonDay): CalendarCompetition? =
    when {
        day.game == null -> null
        day.event == 1 && day.day in 155..174 -> CalendarCompetition.INTERLEAGUE
        day.event in 3..4 -> CalendarCompetition.CLIMAX_SERIES
        day.event == 5 -> CalendarCompetition.JAPAN_SERIES
        else -> null
    }

@Composable
internal fun HomeCalendar(
    controller: HomeController,
    data: HomeData,
    modifier: Modifier = Modifier.height(510.dp),
    onOpenPlayer: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        val day = data.days.find { it.day == controller.selectedDay }
        val matches = day?.let(data::calendarGamesOn).orEmpty()
        val game = matches.firstOrNull()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (controller.showingMatch && day != null && game != null) {
                key(day.day) {
                    MatchSummary(data, day, game, matches, onOpenPlayer = onOpenPlayer, onBack = controller::backToCalendar)
                }
            } else {
                CalendarMonth(controller, data, onOpenPlayer)
            }
        }
    }
}

@Composable
private fun CalendarMonth(
    controller: HomeController,
    data: HomeData,
    onOpenPlayer: (Int) -> Unit,
) {
    val shortNames = shortTeamNames(data.teams, 1..12)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showFeed = maxHeight >= 400.dp
        val availableHeight = maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${data.year?.let { "${it}年" } ?: "${data.season}年目"} ${controller.month}月",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                CalendarAction("‹", controller.month > 1) { controller.changeMonth(controller.month - 1) }
                CalendarAction("今月", onClick = controller::today)
                CalendarAction("›", controller.month < 12) { controller.changeMonth(controller.month + 1) }
            }
            Row {
                weekdays.forEach {
                    Text(
                        it,
                        Modifier.weight(1f).padding(start = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val days = data.days.filter { it.month == controller.month }.sortedBy { it.date }
            if (days.isEmpty()) {
                Text("日程がありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val cells = List<SeasonDay?>(days.first().weekday) { null } + days
                val weeks = cells.chunked(7)
                val gridModifier =
                    if (showFeed) {
                        Modifier.height(minOf(58.dp * CALENDAR_WEEK_ROWS, availableHeight * 0.54f))
                    } else {
                        Modifier.weight(1f)
                    }
                Column(gridModifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(CALENDAR_WEEK_ROWS) { row ->
                        val week = weeks.getOrNull(row).orEmpty()
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            (week + List(7 - week.size) { null }).forEach { day ->
                                if (day == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    val selected = controller.selectedDay == day.day
                                    val today = day.day == data.currentDay
                                    val shape = RoundedCornerShape(4.dp)
                                    val gameText = calendarGameText(data, day)
                                    val hasGame = gameText != null
                                    val competition = calendarCompetition(day)
                                    val background = competition?.background ?: Color.Transparent
                                    val selectionBorder =
                                        if (selected) {
                                            Modifier.border(2.dp, Color(0xFF2563EB), shape)
                                        } else {
                                            Modifier
                                        }
                                    val accessibilityText =
                                        listOfNotNull(competition?.label, gameText?.takeUnless { it == competition?.label })
                                            .ifEmpty { listOf("日程") }
                                            .joinToString(" ")
                                    BoxWithConstraints(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .background(background, shape)
                                            .then(selectionBorder)
                                            .clickable(
                                                role = Role.Button,
                                                onClick = { controller.openMatch(day) },
                                            ).semantics { contentDescription = "${day.month}月${day.date}日 $accessibilityText" }
                                            .padding(horizontal = 3.dp),
                                    ) {
                                        val compact = maxWidth >= 90.dp
                                        if (compact || maxHeight < 52.dp) {
                                            Box(Modifier.fillMaxWidth()) {
                                                Column {
                                                    Text(
                                                        "${day.date}",
                                                        style =
                                                            MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 12.sp,
                                                                fontFeatureSettings = "tnum",
                                                            ),
                                                        color =
                                                            if (today) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurface
                                                            },
                                                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                                                    )
                                                    if (hasGame) CalendarGameLine(data, day, gameText.orEmpty(), shortNames)
                                                }
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Box(Modifier.fillMaxWidth()) {
                                                    Box(
                                                        Modifier.size(22.dp).background(
                                                            if (today) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                            CircleShape,
                                                        ),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            "${day.date}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                                            color =
                                                                if (today) {
                                                                    MaterialTheme.colorScheme.onPrimary
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                },
                                                        )
                                                    }
                                                }
                                                if (hasGame) {
                                                    CalendarGameLine(data, day, gameText.orEmpty(), shortNames)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            if (showFeed) HomeSeasonFeed(controller, data, Modifier.weight(1f).fillMaxWidth(), onOpenPlayer)
        }
    }
}

@Composable
private fun CalendarGameLine(
    data: HomeData,
    day: SeasonDay,
    gameText: String,
    shortNames: Map<Int, String>,
) {
    val postseason = if (day.event in 3..5) data.calendarGamesOn(day) else emptyList()
    if (postseason.isNotEmpty()) {
        Column {
            postseason.forEach { game ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val home = shortNames[game.team] ?: data.teamName(game.team)
                    val away = shortNames[game.opponent] ?: data.teamName(game.opponent)
                    PostseasonTeam(data, game.team, home, Modifier.weight(1f))
                    Text("${game.runs}−${game.allowed}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    PostseasonTeam(data, game.opponent, away, Modifier.weight(1f))
                }
            }
        }
        return
    }
    val competition = calendarCompetition(day)
    if (competition != null && data.calendarGamesOn(day).isEmpty()) {
        Text(competition.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        return
    }
    val opponent = data.opponent(day)
    if (opponent == null) {
        Text(gameText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Column {
            LeaderTeam(data.teamName(opponent), shortNames[opponent] ?: data.teamName(opponent), data.teamAccent(opponent), compact = true)
            data.gameOn(day)?.let { game ->
                Text(
                    game.result,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PostseasonTeam(
    data: HomeData,
    team: Int,
    label: String,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Spacer(Modifier.size(4.dp).background(data.teamAccent(team), CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun CalendarAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        Modifier
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel =
                    when (label) {
                        "‹" -> "前月"
                        "›" -> "翌月"
                        else -> label
                    },
                onClick = onClick,
            ).padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
    )
}

internal fun calendarGameText(
    data: HomeData,
    day: SeasonDay,
): String? =
    data.calendarGamesOn(day).takeIf { day.event in 3..5 && it.isNotEmpty() }?.joinToString(" / ") {
        "${data.teamName(it.team)} ${it.runs}−${it.allowed} ${data.teamName(it.opponent)}"
    } ?: data.gameOn(day)?.result
        ?: calendarCompetition(day)?.label
        ?: data.opponent(day)?.let(data::teamName)
        ?: day.game?.let { if (day.event in listOf(1, 3, 4, 5)) eventName(day.event) else null }

internal fun outsText(outs: Int): String = "${outs / 3}" + if (outs % 3 == 0) "" else " ${outs % 3}/3"

private fun eventName(value: Int): String =
    when (value) {
        1 -> "試合予定"
        3, 4 -> "CS"
        5 -> "日本S"
        else -> "試合なし"
    }
