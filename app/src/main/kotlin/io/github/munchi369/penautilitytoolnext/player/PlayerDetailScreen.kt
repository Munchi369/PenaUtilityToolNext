package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import io.github.munchi369.penautilitytoolnext.ui.TechnicalDetails
import io.github.munchi369.penautilitytoolnext.ui.gameAbilityColor
import io.github.munchi369.penautilitytoolnext.ui.missingCell

@Composable
fun PlayerDetailScreen(
    controller: PlayerDetailController,
    onBack: () -> Unit,
    backLabel: String = "← 一覧へ戻る",
) {
    val state = controller.state
    val options = controller.options
    val data = state.data
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(backLabel) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { controller.move(-1) }, enabled = state.previous) { Text("前の選手") }
            TextButton(onClick = { controller.move(1) }, enabled = state.next) { Text("次の選手") }
        }
        val profile = data?.profile?.value
        PlayerDetailOverview(
            name = profile?.name ?: state.player?.name ?: state.requestedName.orEmpty(),
            teamName = profile?.team ?: state.player?.teamName.orEmpty(),
            number = profile?.number ?: state.player?.uniformNumber,
            position = profile?.position ?: state.player?.position?.displayName,
            age = profile?.age ?: state.player?.age,
            playerId = data?.playerId ?: state.requestedPlayerId,
            teamColor = state.player?.teamNumber?.let(controller.teamColors::get),
        )
        if (profile != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RosterColumns.basic.filter { it.key in profile.cells && it.key !in setOf("ID", "POSITION", "AGE") }.forEach { column ->
                    Text("${column.title} ${profile.cells.getValue(column.key).text}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        controller.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        controller.rosterSettingsNotice?.let { RosterSettingsNoticeBanner(it, controller::dismissRosterSettingsNotice) }
        PrimaryTabRow(selectedTabIndex = options.tab.ordinal) {
            PlayerDetailTab.entries.forEach { tab ->
                Tab(
                    selected = options.tab == tab,
                    onClick = { controller.updateOptions(options.copy(tab = tab)) },
                    text = { Text(tab.title) },
                )
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> {
                Text(state.error.message, color = MaterialTheme.colorScheme.error)
                TechnicalDetails(state.error.details)
            }
            data != null -> {
                data.profile.error?.let { DetailError("基本情報", it) }
                if (data.contextErrors.isNotEmpty()) DetailError("年度情報", data.contextErrors.joinToString("\n"))
                when (options.tab) {
                    PlayerDetailTab.STATS ->
                        DetailStats(
                            data,
                            options,
                            controller.statsOptions,
                            controller::updateOptions,
                            controller::updateStatsColumns,
                        )
                    PlayerDetailTab.ABILITY -> DetailAbilities(data, controller.settings, controller::updateSettings)
                    PlayerDetailTab.HONORS -> DetailHonors(data)
                }
            }
        }
    }
}

@Composable
private fun DetailStats(
    data: PlayerDetailData,
    options: PlayerDetailOptions,
    statsOptions: PlayerRosterOptions,
    onOptions: (PlayerDetailOptions) -> Unit,
    onStatsOptions: (PlayerRosterOptions) -> Unit,
) {
    val monthly = options.period == DetailStatsPeriod.MONTHS
    val section =
        when {
            monthly && options.pitching -> data.monthlyPitching
            monthly -> data.monthlyBatting
            options.pitching -> data.pitching
            else -> data.batting
        }
    val records = section.value.orEmpty()
    val years = records.map { it.year }.distinct().sortedDescending()
    val year = options.year ?: data.season ?: years.firstOrNull()
    val columns = detailStatsColumns(statsOptions, options)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DetailChoice(if (options.pitching) "投手成績" else "打撃成績", listOf(false to "打撃成績", true to "投手成績")) {
                onOptions(options.copy(pitching = it))
            }
            DetailChoice(divisionLabel(options.division), (1..3).map { it to divisionLabel(it) }) { onOptions(options.copy(division = it)) }
            DetailChoice(options.period.title, DetailStatsPeriod.entries.map { it to it.title }) { onOptions(options.copy(period = it)) }
            if (monthly) {
                DetailChoice(
                    year?.let(data::yearLabel) ?: "年度なし",
                    years.map { it to data.yearLabel(it) },
                ) { onOptions(options.copy(year = it)) }
            }
        }
        DetailStatsDisplaySettings(columns, statsOptions, onStatsOptions)
    }
    if (section.error != null) {
        DetailError("成績", section.error)
        return
    }
    if (!options.pitching && !monthly) data.appearances.error?.let { DetailError("打順・守備位置別出場", it) }
    data.teamNames.error?.let { DetailError("所属チーム", it) }
    if (!monthly) data.recordHighs.error?.let { DetailError("歴代最高", it) }
    if (options.period == DetailStatsPeriod.CURRENT && data.season == null) {
        Text("今季の年度を確認できません")
        return
    }
    val selected =
        records.filter {
            if (monthly) {
                options.division == 1 && it.year == year
            } else {
                it.division == options.division &&
                    (options.period != DetailStatsPeriod.CURRENT || it.year == data.season)
            }
        }
    if (selected.isEmpty()) {
        Text(if (monthly) "月別記録なし" else "成績記録なし", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val joined =
        selected.map { record ->
            val appearances =
                if (!monthly && !options.pitching) {
                    data.appearances.value
                        ?.singleOrNull { it.playerYear == record.playerYear && it.division == record.division }
                        ?.values
                        .orEmpty()
                } else {
                    emptyMap()
                }
            record.copy(values = record.values + appearances.filterKeys { it != "NENME" && it != "ITINIGUN" && it != "ID" })
        }
    val rows =
        buildList {
            if (options.period == DetailStatsPeriod.YEARS) {
                add(DetailTableRow("通算", detailMetrics(detailTotals(joined), options.pitching)))
            }
            val highs = data.recordHighs.value
            joined.sortedWith(if (monthly) compareBy { it.division } else compareByDescending { it.year }).forEach { record ->
                val cells = detailMetrics(record.values, options.pitching)
                val marked =
                    if (!monthly && highs != null) {
                        markDetailRecordHighs(
                            cells,
                            detailRecordHighKeys(options.pitching, record.division, record.values, highs),
                        )
                    } else {
                        cells
                    }
                add(
                    DetailTableRow(
                        if (monthly) detailMonthLabel(record.division) else data.yearLabel(record.year),
                        marked + ("TEAM" to detailTeamCell(record.values, data.teamNames.value.orEmpty())),
                    ),
                )
            }
        }
    val visibleColumns = columns.filter { it.key in statsOptions.current.visibleColumns }
    val dragState = remember(visibleColumns) { RosterColumnDragState() }
    DetailTable(
        visibleColumns,
        rows,
        Modifier.fillMaxSize(),
        if (monthly) "月" else "年度",
        numericHeaders = true,
        leadingColumn = RosterColumn("TEAM", "所属チーム", 180),
        columnHeader = { column ->
            RosterColumnHeaders(
                visibleColumns,
                statsOptions,
                {},
                onStatsOptions,
                renderColumns = listOf(column),
                dragState = dragState,
                headerContent = { item ->
                    Text(
                        item.title,
                        Modifier.fillMaxWidth().height(44.dp).padding(8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
    )
}

@Composable
private fun DetailAbilities(
    data: PlayerDetailData,
    settings: PlayerDetailSettings,
    onSettings: (PlayerDetailSettings) -> Unit,
) {
    val scroll = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailColumnMenu(
                    "グラフの項目",
                    DetailColumns.graphAbilities,
                    settings.graphColumns,
                    defaultColumns = PlayerDetailSettings().graphColumns,
                ) { onSettings(settings.copy(graphColumns = it)) }
            }
            data.abilities.error?.let { error -> item { DetailError("能力履歴", error) } }
            data.currentAbility.error?.let { error -> item { DetailError("現在能力", error) } }
            val history = data.abilities.value.orEmpty()
            val chartRows =
                history.filter { it.playerYear >= 0 }.map { DetailTableRow(data.abilityLabel(it.playerYear), it.cells) } +
                    listOfNotNull(data.currentAbility.value?.let { DetailTableRow("現在", it) })
            val selected = DetailColumns.graphAbilities.filter { it.key in settings.graphColumns }
            val groups =
                selected.groupBy {
                    when {
                        it.key == "MAXMAX" -> DetailAbilityChartKind.SPEED
                        it.key == "SOUGOU" -> DetailAbilityChartKind.OVERALL
                        else -> DetailAbilityChartKind.BASIC
                    }
                }
            item {
                BoxWithConstraints {
                    val chartsPerRow = if (maxWidth >= 760.dp) 2 else 1
                    FlowRow(maxItemsInEachRow = chartsPerRow, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        groups.forEach { (kind, columns) ->
                            Box(Modifier.weight(1f)) { DetailAbilityChart(kind, columns, chartRows) }
                        }
                    }
                }
            }
            item { Text("能力の記録", style = MaterialTheme.typography.titleMedium) }
            val rows =
                listOfNotNull(data.currentAbility.value?.let { DetailTableRow("現在", it) }) +
                    history.sortedByDescending { it.playerYear }.map { DetailTableRow(data.abilityLabel(it.playerYear), it.cells) }
            if (rows.isEmpty()) {
                item { Text("能力記録なし") }
            } else {
                item {
                    DetailTable(
                        detailAbilityColumns(data.profile.value?.position),
                        rows,
                        Modifier.fillMaxWidth().height(340.dp),
                        "記録時点",
                        labelWidth = 180,
                    )
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun DetailHonors(data: PlayerDetailData) {
    val scroll = rememberLazyListState()
    var leadersOpen by remember(data.playerId) { mutableStateOf(false) }
    val titles = data.honors["タイトル"]
    val fielding = data.honors["ベストナイン・ゴールデングラブ"]
    val monthly = data.honors["月間MVP"]
    val allStar = data.honors["オールスター"]
    val yearLabel = data::yearLabel
    val official =
        listOf(
            "タイトル" to titles?.value?.let { officialTitleRows(it, yearLabel) },
            "ベストナイン" to fielding?.value?.let { bestNineRows(it, yearLabel) },
            "ゴールデングラブ" to fielding?.value?.let { goldenGloveRows(it, yearLabel) },
            "月間MVP" to monthly?.value?.let { monthlyAwardRows(it, yearLabel) },
            "オールスター" to allStar?.value?.let { allStarRows(it, yearLabel) },
        )
    val leaders = titles?.value?.let { leaderTitleRows(it, yearLabel) }.orEmpty()
    val records = data.records.value?.let { specialRecordRows(it, yearLabel) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val honorsMissing = official.all { it.second.isNullOrEmpty() } && leaders.isEmpty()
            val honorsFailed = official.any { data.honors[honorKey(it.first)]?.error != null }
            if (honorsMissing && !honorsFailed) {
                item { Text("獲得・選出記録なし", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val shownErrors = mutableSetOf<String>()
            official.forEach { (title, rows) ->
                val key = honorKey(title)
                val error = data.honors[key]?.error
                if (error != null && shownErrors.add(key)) {
                    item { DetailError(key, error) }
                } else if (!rows.isNullOrEmpty()) {
                    item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(rows) { row -> DetailAwardLine(row) }
                }
            }
            if (leaders.isNotEmpty()) {
                item {
                    TextButton(onClick = { leadersOpen = !leadersOpen }) {
                        Text("部門別1位 ${leaders.size}" + if (leadersOpen) " ▴" else " ▾")
                    }
                }
                if (leadersOpen) items(leaders) { row -> DetailAwardLine(row) }
            }
            item { HorizontalDivider() }
            item { Text("特別記録", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (data.records.error != null) {
                item { DetailError("特別記録", data.records.error) }
            } else if (records.isNullOrEmpty()) {
                item { Text("特別記録なし") }
            } else {
                items(records) { row -> DetailAwardLine(row) }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
    }
}

private fun honorKey(title: String): String =
    when (title) {
        "ベストナイン", "ゴールデングラブ" -> "ベストナイン・ゴールデングラブ"
        else -> title
    }

@Composable
private fun DetailAwardLine(row: DetailAwardRow) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label)
            when {
                row.mark != null ->
                    repeat(row.chips.size) {
                        DetailAwardMarkIcon(row.mark, monochrome = row.monochromeMark)
                    }
                row.inlineYears -> {
                    Text(row.boldText ?: "—", fontWeight = FontWeight.Bold)
                    Text(row.chips.joinToString("") { "（$it）" })
                }
                else -> Text(row.boldText ?: "${row.chips.size}回", fontWeight = FontWeight.Bold)
            }
        }
        if (!row.inlineYears && row.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                row.chips.forEach { chip ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                        Text(chip, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

internal data class DetailTableRow(
    val label: String,
    val cells: Map<String, PlayerCell>,
)

@Composable
internal fun DetailTable(
    columns: List<RosterColumn>,
    rows: List<DetailTableRow>,
    modifier: Modifier,
    label: String,
    labelWidth: Int = 112,
    numericHeaders: Boolean = false,
    columnHeader: (@Composable (RosterColumn) -> Unit)? = null,
    leadingColumn: RosterColumn? = null,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    Column(modifier) {
        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leadingColumn?.let { column ->
                Text(column.title, Modifier.width(column.width.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
            }
            Text(label, Modifier.width(labelWidth.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                columns.forEach { column ->
                    if (columnHeader != null) {
                        Box(Modifier.width(column.width.dp)) { columnHeader(column) }
                    } else {
                        Text(
                            column.title,
                            Modifier.width(column.width.dp).padding(8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = if (numericHeaders && column.key != "POSITION") TextAlign.End else TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            LazyColumn(state = vertical, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                items(rows) { row ->
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        leadingColumn?.let { column -> DetailStatCell(row.cells[column.key] ?: missingCell(), column) }
                        Text(row.label, Modifier.width(labelWidth.dp).padding(8.dp), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.weight(1f).horizontalScroll(horizontal), verticalAlignment = Alignment.CenterVertically) {
                            columns.forEach { column -> DetailStatCell(row.cells[column.key] ?: missingCell(), column) }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(vertical), Modifier.align(Alignment.CenterEnd))
        }
        Row {
            Spacer(Modifier.width((labelWidth + (leadingColumn?.width ?: 0)).dp))
            HorizontalScrollbar(rememberScrollbarAdapter(horizontal), Modifier.weight(1f).height(12.dp))
        }
    }
}

@Composable
private fun DetailStatCell(
    cell: PlayerCell,
    column: RosterColumn,
) {
    val rankColor = cell.abilityStyle?.rank?.let(::gameAbilityColor)
    val color =
        when {
            cell.recordHigh -> Color(0xFFFF0000)
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
        fontWeight = if (cell.recordHigh) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign =
            if (cell.number != null && cell.abilityStyle == null) {
                TextAlign.End
            } else {
                TextAlign.Start
            },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun DetailError(
    label: String,
    error: String,
) {
    Column {
        Text("$label を読み込めません。再読み込みで再試行できます。", color = MaterialTheme.colorScheme.error)
        TechnicalDetails(error)
    }
}

@Composable
private fun <T> DetailChoice(
    label: String,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = choices.isNotEmpty()) { Text("$label ▾") }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            choices.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = {
                    onSelect(value)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun DetailColumnMenu(
    label: String,
    columns: List<RosterColumn>,
    selected: Set<String>,
    defaultColumns: Set<String> = columns.filter { it.standard }.map { it.key }.toSet(),
    onChange: (Set<String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label ▾") }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 380.dp)) {
            DropdownMenuItem(text = { Text("標準に戻す") }, onClick = {
                onChange(defaultColumns)
            })
            columns.forEach { column ->
                DropdownMenuItem(
                    text = { Text(column.title) },
                    leadingIcon = { Checkbox(column.key in selected, onCheckedChange = null) },
                    onClick = { onChange(if (column.key in selected) selected - column.key else selected + column.key) },
                )
            }
        }
    }
}

private fun divisionLabel(division: Int): String =
    when (division) {
        1 -> "一軍"
        2 -> "二軍"
        else -> "メジャー"
    }
