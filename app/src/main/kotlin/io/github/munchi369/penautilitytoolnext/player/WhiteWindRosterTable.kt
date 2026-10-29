package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.ui.TeamTag
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.distinctUntilChanged
import ua.wwind.table.Table
import ua.wwind.table.config.RowHeightMode
import ua.wwind.table.config.TableCustomization
import ua.wwind.table.config.TableDefaults
import ua.wwind.table.config.TableMotion
import ua.wwind.table.config.TableRowContext
import ua.wwind.table.config.TableRowStyle
import ua.wwind.table.config.TableSettings
import ua.wwind.table.state.TableState
import ua.wwind.table.state.rememberTableState
import ua.wwind.table.tableColumns
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun RosterTable(
    players: List<PlayerRosterPlayer>,
    teamColors: Map<Int, Int>,
    columns: List<RosterColumn>,
    options: PlayerRosterOptions,
    qualification: RosterRateQualification?,
    onChange: (PlayerRosterOptions) -> Unit,
    modifier: Modifier,
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    var sortRevision by remember { mutableIntStateOf(0) }
    var selectedPlayerId by rememberSaveable { mutableStateOf<Int?>(null) }
    val currentOptions by rememberUpdatedState(options)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnOpen by rememberUpdatedState(onOpenPlayer)
    val sortColumn: (String) -> Unit = { key ->
        sortRevision += 1
        currentOnChange(currentOptions.sort(key))
    }
    val textMeasurer = rememberTextMeasurer()
    val tagStyle = MaterialTheme.typography.labelSmall
    val teamNames = players.map { it.teamName }.distinct().sorted()
    val teamNameWidth =
        remember(teamNames, textMeasurer, tagStyle) {
            teamNames.maxOfOrNull { textMeasurer.measure(it, tagStyle, maxLines = 1).size.width } ?: 0
        }
    val teamHeadingWidth = textMeasurer.measure("球団 ↓", MaterialTheme.typography.labelMedium, maxLines = 1).size.width
    val teamWidth =
        with(LocalDensity.current) {
            ceil(maxOf(teamNameWidth.toDp() + 31.dp, teamHeadingWidth.toDp() + 16.dp).value).toInt()
        }
    val frozen =
        (if (options.teamScope.showsTeamColumn) listOf(RosterColumn("TEAM", "球団", teamWidth)) else emptyList()) +
            listOf(RosterColumn("NAME", "名前", 176))
    val allColumns = frozen + columns
    val dragState = remember(columns) { RosterColumnDragState() }
    val rows = remember(players, options, qualification) { rosterTableRows(players, options, qualification) }
    // Sorting stays in PlayerRosterOptions. Leaving the library sort unset also avoids its automatic scroll-to-top.
    val tableState =
        key(options.display) {
            rememberTableState(
                columns = allColumns.map { it.key }.toPersistentList(),
                initialWidths =
                    options.current.columnWidths
                        .mapValues { it.value.dp }
                        .toPersistentMap(),
                settings =
                    TableSettings(
                        pinnedColumnsCount = frozen.size,
                        rowHeightMode = RowHeightMode.Dynamic,
                        showVerticalDividers = false,
                        showRowDividers = false,
                        motion = TableMotion.Reduced,
                    ),
                dimensions =
                    TableDefaults.compactDimensions().copy(
                        rowHeight = 44.dp,
                        headerHeight = 44.dp,
                        cellHorizontalPadding = 0.dp,
                        columnDragHandleSize = 0.dp,
                        dragHandleIconSize = 0.dp,
                        pinnedColumnShadowWidth = 0.dp,
                        pinnedColumnDividerThickness = 1.dp,
                    ),
            )
        }
    val sizedColumns =
        columns.map { column ->
            val width =
                tableState.columns.widths[column.key]
                    ?.value
                    ?.roundToInt() ?: column.width
            column.copy(width = width)
        }
    LaunchedEffect(tableState, options.current.columnWidths) {
        tableState.columns.setWidths(
            (tableState.columns.explicitWidths.keys + options.current.columnWidths.keys)
                .associateWith { options.current.columnWidths[it]?.dp },
        )
    }
    LaunchedEffect(tableState, options.display) {
        val display = options.display
        snapshotFlow { tableState.columns.explicitWidths.toMap() }
            .distinctUntilChanged()
            .collect { widths ->
                if (currentOptions.display != display) return@collect
                val resized =
                    currentOptions.resizeColumns(
                        widths.mapValues { (key, width) -> width.value.coerceIn(rosterColumnMinWidth(key).toFloat(), 1000f) },
                    )
                if (resized != currentOptions) currentOnChange(resized)
            }
    }
    LaunchedEffect(players) {
        if (players.none { it.playerId == selectedPlayerId }) selectedPlayerId = null
    }
    val specs =
        tableColumns<RosterTableRow, String, Unit> {
            allColumns.forEach { column ->
                column(column.key, valueOf = { row -> (row as? RosterTableRow.Player)?.player?.name }) {
                    width(rosterColumnMinWidth(column.key).dp, column.width.dp)
                    // Resizable columns are measured again for every visible cell. Width changes are handled on the edge instead.
                    resizable(false)
                    headerDecorations(false)
                    header {
                        if (column in frozen) {
                            HeaderCell(column, options, sortColumn)
                        } else {
                            RosterColumnHeaders(
                                sizedColumns,
                                options,
                                sortColumn,
                                onChange,
                                renderColumns = sizedColumns.filter { it.key == column.key },
                                dragState = dragState,
                            )
                        }
                    }
                    cell { row, _ ->
                        Box(Modifier.fillMaxWidth().height(row.height.dp), contentAlignment = Alignment.CenterStart) {
                            when (row) {
                                is RosterTableRow.Player ->
                                    RosterPlayerCell(row.player, column, teamColors, options) { player ->
                                        selectedPlayerId = player.playerId
                                        currentOnOpen(player)
                                    }
                                is RosterTableRow.Group ->
                                    if (column.key == "NAME") {
                                        RosterGroupLabel(row)
                                    }
                                is RosterTableRow.Qualification ->
                                    if (column.key == "NAME") {
                                        Text(
                                            row.label,
                                            Modifier.padding(horizontal = 8.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        HorizontalDivider()
                                    }
                            }
                        }
                    }
                }
            }
        }
    LaunchedEffect(options.revealGroups) {
        if (options.revealGroups) {
            vertical.scrollToItem(0)
            currentOnChange(currentOptions.clearRevealGroups())
        }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val seededHeights = remember { SeededRowHeights() }
            val expectedHeights = rows.map { it.height }
            Table(
                itemsCount = rows.size,
                itemAt = { rows.getOrNull(it) },
                columns = specs,
                state = tableState,
                modifier =
                    Modifier
                        .layout { measurable, constraints ->
                            // Group, qualification, and player rows are 32/24/44dp. Publish that before measuring
                            // so the table does not subcompose the next viewport just to discover those heights.
                            val pixels =
                                rosterRowHeightSeed(
                                    expectedHeights,
                                    tableState.rowHeightsPx,
                                    seededHeights.heights,
                                ) { height ->
                                    with(density) { height.dp.roundToPx() }
                                }
                            if (pixels != null) {
                                pixels.forEachIndexed { index, px ->
                                    if (tableState.rowHeightsPx[index] != px) tableState.updateRowHeight(index, px)
                                }
                                seededHeights.heights = expectedHeights
                            }
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }.fillMaxSize()
                        .padding(end = 12.dp),
                verticalState = vertical,
                horizontalState = horizontal,
                // Discard player identity on a new sort so the list keeps its item index and offset, rather than following a player.
                rowKey = { row, index -> "sort-$sortRevision-${row?.key ?: index}" },
                onRowClick = { row ->
                    if (row is RosterTableRow.Player) selectedPlayerId = row.player.playerId
                    if (row is RosterTableRow.Group) {
                        row.squad?.let { currentOnChange(currentOptions.toggleSquadExpanded(it)) }
                        row.position?.let { currentOnChange(currentOptions.togglePositionExpanded(it)) }
                    }
                },
                emptyContent = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("条件に一致する選手がいません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                bodyOverlay = {
                    if (players.isEmpty() && rows.isNotEmpty()) {
                        Text(
                            "条件に一致する選手がいません",
                            Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                customization =
                    object : TableCustomization<RosterTableRow, String> {
                        @Composable
                        override fun resolveRowStyle(ctx: TableRowContext<RosterTableRow, String>): TableRowStyle {
                            val player = (ctx.item as? RosterTableRow.Player)?.player
                            val selected = selectedPlayerId != null && player?.playerId == selectedPlayerId
                            return TableRowStyle(
                                modifier = Modifier.semantics { this.selected = selected },
                                containerColor =
                                    when {
                                        selected ->
                                            MaterialTheme.colorScheme.primary
                                                .copy(alpha = 0.10f)
                                                .compositeOver(MaterialTheme.colorScheme.surface)
                                        ctx.item is RosterTableRow.Group -> MaterialTheme.colorScheme.surfaceContainerLow
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                            )
                        }
                    },
                colors =
                    TableDefaults.colors(
                        headerContainerColor = MaterialTheme.colorScheme.surface,
                        rowContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                border = TableDefaults.NoBorder,
                shape = RectangleShape,
                // All column actions are handled by our headers to keep fixed columns immutable and preserve saved settings.
                columnMenu = { _, _ -> emptyList() },
            )
            RosterColumnEdges(
                allColumns,
                frozen.size,
                rows,
                options,
                tableState,
                horizontal,
                textMeasurer,
                Modifier
                    .padding(end = 12.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.TopStart),
            )
            VerticalScrollbar(
                rememberScrollbarAdapter(vertical),
                Modifier.align(Alignment.CenterEnd).padding(top = 45.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(end = 12.dp)) {
            val frozenWidth =
                frozen.sumOf { column ->
                    tableState.columns.widths[column.key]
                        ?.value
                        ?.roundToInt() ?: column.width
                }
            Box(Modifier.width(frozenWidth.dp))
            HorizontalScrollbar(rememberScrollbarAdapter(horizontal), Modifier.weight(1f).height(12.dp))
        }
    }
}

@Composable
private fun RosterPlayerCell(
    player: PlayerRosterPlayer,
    column: RosterColumn,
    teamColors: Map<Int, Int>,
    options: PlayerRosterOptions,
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
) {
    when (column.key) {
        "NAME" ->
            Box(
                Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "${player.name}の詳細を開く") {
                    onOpenPlayer(player)
                },
                contentAlignment = Alignment.CenterStart,
            ) {
                BodyCell(player.cell(options.display, column.key, options.statsSquad), column, true)
            }
        "TEAM" -> Box(Modifier.padding(horizontal = 4.dp)) { TeamTag(player.teamName, teamColors[player.teamNumber]) }
        else -> BodyCell(player.cell(options.display, column.key, options.statsSquad), column, false)
    }
}

@Composable
private fun RosterGroupLabel(group: RosterTableRow.Group) {
    Row(
        Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (group.expanded) "▾" else "▸", fontWeight = FontWeight.SemiBold)
        Text(group.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text("${group.count}人", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private sealed interface RosterTableRow {
    val key: String
    val height: Int

    data class Player(
        val player: PlayerRosterPlayer,
    ) : RosterTableRow {
        override val key: String = "player-${player.playerId}"
        override val height: Int = 44
    }

    data class Group(
        override val key: String,
        val title: String,
        val count: Int,
        val expanded: Boolean,
        val squad: PlayerSquad? = null,
        val position: PlayerPosition? = null,
    ) : RosterTableRow {
        override val height: Int = 32
    }

    data class Qualification(
        override val key: String,
        val label: String,
    ) : RosterTableRow {
        override val height: Int = 24
    }
}

@Composable
private fun RosterColumnEdges(
    columns: List<RosterColumn>,
    pinnedCount: Int,
    rows: List<RosterTableRow>,
    options: PlayerRosterOptions,
    tableState: TableState<String>,
    horizontal: ScrollState,
    textMeasurer: TextMeasurer,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scroll = with(density) { horizontal.value.toDp() }
    val pinned = if (pinnedCount >= columns.size) 0 else pinnedCount
    var boundary = 0.dp
    Box(modifier) {
        columns.forEachIndexed { index, column ->
            boundary += tableState.columns.widths[column.key] ?: column.width.dp
            val viewport = if (index < pinned) boundary else boundary - scroll
            RosterColumnEdge(
                column,
                rows,
                options,
                tableState,
                textMeasurer,
                Modifier.offset(x = viewport - 8.dp),
            )
            if (index == pinned - 1) boundary += 1.dp
        }
    }
}

@Composable
private fun RosterColumnEdge(
    column: RosterColumn,
    rows: List<RosterTableRow>,
    options: PlayerRosterOptions,
    tableState: TableState<String>,
    textMeasurer: TextMeasurer,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val headerStyle = MaterialTheme.typography.labelMedium
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val tagStyle = MaterialTheme.typography.labelSmall
    val rowsState = rememberUpdatedState(rows)
    val optionsState = rememberUpdatedState(options)
    Box(
        modifier
            .width(16.dp)
            .height(44.dp)
            .pointerInput(tableState, column.key) {
                detectTapGestures(
                    onDoubleTap = {
                        tableState.columns.widths[column.key] =
                            fittedRosterColumnWidth(
                                column,
                                rowsState.value,
                                optionsState.value,
                                textMeasurer,
                                headerStyle,
                                bodyStyle,
                                tagStyle,
                                density,
                            )
                    },
                )
            }.pointerInput(tableState, column.key) {
                var width = 0.dp
                detectHorizontalDragGestures(
                    onDragStart = {
                        width = tableState.columns.widths[column.key] ?: column.width.dp
                    },
                ) { change, dragAmount ->
                    change.consume()
                    width += dragAmount.toDp()
                    tableState.columns.widths[column.key] =
                        width.coerceIn(rosterColumnMinWidth(column.key).dp, 1000.dp)
                }
            },
    )
}

private fun fittedRosterColumnWidth(
    column: RosterColumn,
    rows: List<RosterTableRow>,
    options: PlayerRosterOptions,
    textMeasurer: TextMeasurer,
    headerStyle: TextStyle,
    bodyStyle: TextStyle,
    tagStyle: TextStyle,
    density: Density,
): Dp {
    val header =
        column.title +
            if (options.current.sortKey == column.key) {
                if (options.current.descending) " ↓" else " ↑"
            } else {
                ""
            }
    val headerPx = textMeasurer.measure(header, headerStyle, maxLines = 1).size.width
    var contentPx = 0
    rows.forEach { row ->
        val label = rowContentLabel(row, column, options) ?: return@forEach
        val style = if (column.key == "TEAM") tagStyle else bodyStyle
        val measured = textMeasurer.measure(label, style, maxLines = 1).size.width
        if (measured > contentPx) contentPx = measured
    }
    val contentPadding = if (column.key == "TEAM") 31.dp else 16.dp
    val fitted =
        with(density) {
            maxOf(headerPx.toDp() + 16.dp, contentPx.toDp() + contentPadding)
        }
    return fitted.coerceIn(rosterColumnMinWidth(column.key).dp, 1000.dp)
}

private fun rowContentLabel(
    row: RosterTableRow,
    column: RosterColumn,
    options: PlayerRosterOptions,
): String? =
    when (row) {
        is RosterTableRow.Player ->
            row.player
                .cell(options.display, column.key, options.statsSquad)
                .text
                .takeIf { it.isNotBlank() }
        is RosterTableRow.Group -> row.title.takeIf { column.key == "NAME" }
        is RosterTableRow.Qualification -> row.label.takeIf { column.key == "NAME" }
    }

private class SeededRowHeights {
    var heights: List<Int>? = null
}

/**
 * Pixel heights to publish before the table measures.
 * Returns null when [knownHeightsPx] already has every row and [seededHeightsDp] still matches,
 * so a measured correction is not overwritten on the next pass.
 */
internal fun rosterRowHeightSeed(
    rowHeightsDp: List<Int>,
    knownHeightsPx: Map<Int, Int>,
    seededHeightsDp: List<Int>?,
    toPx: (Int) -> Int,
): IntArray? {
    val missing = rowHeightsDp.indices.any { knownHeightsPx[it] == null }
    if (!missing && seededHeightsDp == rowHeightsDp) return null
    return IntArray(rowHeightsDp.size) { index -> toPx(rowHeightsDp[index]) }
}

private fun rosterTableRows(
    players: List<PlayerRosterPlayer>,
    options: PlayerRosterOptions,
    qualification: RosterRateQualification?,
): List<RosterTableRow> =
    buildList {
        fun addPlayers(
            entries: List<PlayerRosterPlayer>,
            groupKey: String,
        ) {
            entries.forEachIndexed { index, player ->
                val status = qualification?.status(player)
                val previous = entries.getOrNull(index - 1)?.let { qualification?.status(it) }
                if (status != null && status != RosterQualificationStatus.QUALIFIED && status != previous) {
                    add(RosterTableRow.Qualification("$groupKey-$status", requireNotNull(qualification).separatorLabel(status)))
                }
                add(RosterTableRow.Player(player))
            }
        }

        when (options.grouping) {
            RosterGrouping.NONE -> addPlayers(players, "flat")
            RosterGrouping.SQUAD ->
                listOf(PlayerSquad.FIRST to "一軍", PlayerSquad.SECOND to "二軍").forEach { (squad, title) ->
                    val entries = players.filter { it.squad == squad }
                    val expanded = squad in options.expandedSquads
                    val key = "squad-$squad"
                    add(RosterTableRow.Group(key, title, entries.size, expanded, squad = squad))
                    if (expanded) addPlayers(entries, key)
                }
            RosterGrouping.POSITION ->
                PlayerPosition.entries.forEach { position ->
                    val entries = players.filter { it.position == position }
                    if (entries.isNotEmpty()) {
                        val expanded = position in options.expandedPositions
                        val key = "position-$position"
                        add(RosterTableRow.Group(key, position.displayName, entries.size, expanded, position = position))
                        if (expanded) addPlayers(entries, key)
                    }
                }
        }
    }
