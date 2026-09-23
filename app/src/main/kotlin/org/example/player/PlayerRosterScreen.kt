package org.example.player

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.ui.TechnicalDetails

@Composable
fun PlayerRosterScreen(
    state: PlayerRosterViewState,
    options: PlayerRosterOptions,
    onOptionsChange: (PlayerRosterOptions) -> Unit,
    onOpenSettings: () -> Unit,
    settingsNotice: PlayerRosterSettingsNotice? = null,
    onDismissSettingsNotice: () -> Unit = {},
    onOpenPlayer: (PlayerRosterPlayer) -> Unit = {},
) {
    when (state.phase) {
        PlayerRosterPhase.IDLE, PlayerRosterPhase.LOADING ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(state.message)
                }
            }
        PlayerRosterPhase.LOADED ->
            state.roster?.let {
                LoadedRoster(it, options, onOptionsChange, settingsNotice, onDismissSettingsNotice, onOpenPlayer)
            }
        else ->
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.message, style = MaterialTheme.typography.titleLarge)
                Text("再読み込みするか、セーブデータの設定を確認してください。")
                TextButton(onClick = onOpenSettings) { Text("設定を開く") }
                state.technicalDetails?.let { TechnicalDetails(it) }
            }
    }
}

@Composable
private fun LoadedRoster(
    roster: PlayerRoster,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
    settingsNotice: PlayerRosterSettingsNotice?,
    onDismissSettingsNotice: () -> Unit,
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
) {
    val players = remember(roster, options) { options.players(roster) }
    val availableColumns =
        RosterColumns.forDisplay(options.display).filterNot { options.teamScope.isMajorOnly && it.key == "ITIGUN" }
    val columns = availableColumns.filter { it.key in options.current.visibleColumns }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        settingsNotice?.let { RosterSettingsNoticeBanner(it, onDismissSettingsNotice) }
        RosterToolbar(roster, players.size, options, availableColumns, onChange)
        if (
            options.teamScope != RosterTeamScope.All ||
            options.positionFilter != null ||
            (!options.teamScope.isMajorOnly && (options.squad != null || options.registration != null))
        ) {
            RosterFilterChips(roster, options, onChange, Modifier.fillMaxWidth())
        }
        RosterTable(players, columns, options, onChange, Modifier.weight(1f), onOpenPlayer)
    }
}

@Composable
private fun RosterSettingsNoticeBanner(
    notice: PlayerRosterSettingsNotice,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notice.message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    }
}

@Composable
private fun RosterTable(
    players: List<PlayerRosterPlayer>,
    columns: List<RosterColumn>,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
    modifier: Modifier,
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    var sortRevision by remember { mutableIntStateOf(0) }
    val sortColumn: (String) -> Unit = { key ->
        sortRevision += 1
        onChange(options.sort(key))
    }
    val playerKey: (PlayerRosterPlayer) -> String = { player -> "sort-$sortRevision-player-${player.playerId}" }
    val frozen =
        (if (options.teamScope.showsTeamColumn) listOf(RosterColumn("TEAM", "球団", 76)) else emptyList()) +
            listOf(RosterColumn("NUMBER", "背番号", 76), RosterColumn("NAME", "名前", 176))
    val grouping = options.grouping
    val groups =
        when (grouping) {
            RosterGrouping.SQUAD ->
                listOf(
                    RosterListGroup.Squad(PlayerSquad.FIRST, "一軍", players.filter { it.squad == PlayerSquad.FIRST }),
                    RosterListGroup.Squad(PlayerSquad.SECOND, "二軍", players.filter { it.squad == PlayerSquad.SECOND }),
                )
            RosterGrouping.POSITION ->
                PlayerPosition.entries.mapNotNull { position ->
                    val entries = players.filter { it.position == position }
                    if (entries.isEmpty()) {
                        null
                    } else {
                        RosterListGroup.Position(position, position.displayName, entries)
                    }
                }
            RosterGrouping.NONE -> listOf(RosterListGroup.Flat(players))
        }
    LaunchedEffect(options.revealGroups) {
        if (options.revealGroups) {
            vertical.scrollToItem(0)
            onChange(options.clearRevealGroups())
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp)) {
            frozen.forEach { HeaderCell(it, options, sortColumn) }
            Row(Modifier.weight(1f).horizontalScroll(horizontal)) { columns.forEach { HeaderCell(it, options, sortColumn) } }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (players.isEmpty()) {
                Text("条件に一致する選手がいません", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(state = vertical, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                groups.forEach { group ->
                    when (group) {
                        is RosterListGroup.Flat ->
                            items(group.players, key = playerKey) { player ->
                                PlayerRow(player, frozen, columns, options, horizontal, onOpenPlayer)
                            }
                        is RosterListGroup.Squad -> {
                            val expanded = group.squad in options.expandedSquads
                            item(key = "group-squad-${group.squad}") {
                                RosterGroupHeader(group.title, group.players.size, expanded) {
                                    onChange(options.toggleSquadExpanded(group.squad))
                                }
                            }
                            if (expanded) {
                                items(group.players, key = playerKey) { player ->
                                    PlayerRow(player, frozen, columns, options, horizontal, onOpenPlayer)
                                }
                            }
                        }
                        is RosterListGroup.Position -> {
                            val expanded = group.position in options.expandedPositions
                            item(key = "group-position-${group.position}") {
                                RosterGroupHeader(group.title, group.players.size, expanded) {
                                    onChange(options.togglePositionExpanded(group.position))
                                }
                            }
                            if (expanded) {
                                items(group.players, key = playerKey) { player ->
                                    PlayerRow(player, frozen, columns, options, horizontal, onOpenPlayer)
                                }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(vertical), Modifier.align(Alignment.CenterEnd))
        }
        Row(Modifier.fillMaxWidth().padding(end = 12.dp)) {
            Box(Modifier.width(frozen.sumOf { it.width }.dp))
            HorizontalScrollbar(rememberScrollbarAdapter(horizontal), Modifier.weight(1f).height(12.dp))
        }
    }
}

private sealed interface RosterListGroup {
    val players: List<PlayerRosterPlayer>

    data class Flat(
        override val players: List<PlayerRosterPlayer>,
    ) : RosterListGroup

    data class Squad(
        val squad: PlayerSquad,
        val title: String,
        override val players: List<PlayerRosterPlayer>,
    ) : RosterListGroup

    data class Position(
        val position: PlayerPosition,
        val title: String,
        override val players: List<PlayerRosterPlayer>,
    ) : RosterListGroup
}

@Composable
private fun RosterGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle),
    ) {
        Row(
            Modifier.height(32.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (expanded) "▾" else "▸", fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("${count}人", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerRosterPlayer,
    frozen: List<RosterColumn>,
    columns: List<RosterColumn>,
    options: PlayerRosterOptions,
    horizontal: ScrollState,
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        frozen.forEach { column ->
            if (column.key == "NAME") {
                Box(
                    Modifier.height(44.dp).clickable(role = Role.Button, onClickLabel = "${player.name}の詳細を開く") {
                        onOpenPlayer(player)
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BodyCell(player.cell(options.display, column.key, options.statsSquad), column, true)
                }
            } else {
                BodyCell(player.cell(options.display, column.key, options.statsSquad), column, true)
            }
        }
        Row(Modifier.weight(1f).horizontalScroll(horizontal), verticalAlignment = Alignment.CenterVertically) {
            columns.forEach { column ->
                BodyCell(player.cell(options.display, column.key, options.statsSquad), column, false)
            }
        }
    }
}

@Composable
private fun HeaderCell(
    column: RosterColumn,
    options: PlayerRosterOptions,
    onSort: (String) -> Unit,
) {
    Box(
        Modifier
            .width(column.width.dp)
            .height(44.dp)
            .clickable {
                onSort(column.key)
            }.padding(horizontal = 8.dp),
        contentAlignment = if (column.isNumeric(options.display)) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            column.title +
                if (options.current.sortKey ==
                    column.key
                ) {
                    if (options.current.descending) " ↓" else " ↑"
                } else {
                    ""
                },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun BodyCell(
    cell: PlayerCell,
    column: RosterColumn,
    frozen: Boolean,
) {
    val rankColor = cell.abilityStyle?.rank?.let(::gameAbilityColor)
    val color =
        when {
            column.key == "INJURY" && cell.text.startsWith("故障") -> Color(0xFFBF3333)
            column.key == "INJURY" && cell.text.startsWith("違和感") -> Color(0xFFAA6900)
            cell.abilityStyle?.colorStart == 0 && rankColor != null -> rankColor
            else -> MaterialTheme.colorScheme.onSurface
        }
    val text =
        buildAnnotatedString {
            append(cell.text)
            val start = cell.abilityStyle?.colorStart
            if (rankColor != null && start != null && start > 0) {
                addStyle(SpanStyle(color = rankColor), start, cell.text.length)
            }
        }
    Text(
        text,
        modifier = Modifier.width(column.width.dp).padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight =
            if (frozen &&
                column.key == "NAME"
            ) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
        color = color,
        textAlign =
            if (cell.number != null &&
                cell.abilityStyle == null &&
                column.key != "POSITION" &&
                column.key != "FA"
            ) {
                TextAlign.End
            } else {
                TextAlign.Start
            },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun gameAbilityColor(rank: AbilityRank): Color =
    when (rank) {
        AbilityRank.S, AbilityRank.A -> Color(0xFFFF1493)
        AbilityRank.B -> Color(0xFFFF0000)
        AbilityRank.C -> Color(0xFFFF4500)
        AbilityRank.D -> Color(0xFFEBB200)
        AbilityRank.E -> Color(0xFF3BD70A)
        AbilityRank.F -> Color(0xFF007FEB)
        AbilityRank.G -> Color(0xFF858585)
    }

private fun RosterColumn.isNumeric(display: RosterDisplay): Boolean =
    key in setOf("NUMBER", "ID", "SOUGOU", "AGE", "HIROU", "READY", "MAXMAX") ||
        (display.isStats && key !in setOf("TEAM", "NAME", "POSITION"))
