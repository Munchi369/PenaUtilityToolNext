package org.example.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private enum class RosterFilter(
    val title: String,
) {
    TEAM_SCOPE("球団・リーグ"),
    POSITION("ポジション"),
    SQUAD("現在の軍"),
    REGISTRATION("支配下・育成"),
}

@Composable
internal fun RosterViewControls(
    roster: PlayerRoster,
    options: PlayerRosterOptions,
    columns: List<RosterColumn>,
    onChange: (PlayerRosterOptions) -> Unit,
) {
    var filtersOpen by remember(options.teamScope, options.display) { mutableStateOf(false) }
    var columnsOpen by remember(options.teamScope, options.display) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            RosterIconButton("フィルター", RosterIcon.FILTER, filtersOpen) {
                columnsOpen = false
                filtersOpen = !filtersOpen
            }
            if (filtersOpen) {
                FilterPopup(roster, options, onChange, { filtersOpen = false })
            }
        }
        Box {
            RosterIconButton("表示設定", RosterIcon.SLIDERS, columnsOpen) {
                filtersOpen = false
                columnsOpen = !columnsOpen
            }
            if (columnsOpen) {
                RosterPopup({ columnsOpen = false }) {
                    Column(Modifier.width(480.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${options.display.title}の表示項目", style = MaterialTheme.typography.titleSmall)
                        RosterGroupingMenu(options, onChange)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row {
                            TextButton(onClick = {
                                onChange(
                                    options.updateDisplay {
                                        it.copy(
                                            visibleColumns = columns.filter { it.standard }.map { it.key }.toSet(),
                                        )
                                    },
                                )
                            }) { Text("標準に戻す") }
                            TextButton(onClick = {
                                onChange(options.updateDisplay { it.copy(visibleColumns = columns.map { it.key }.toSet()) })
                            }) { Text("すべて表示") }
                        }
                        ScrollableChoices(Modifier.weight(1f, fill = false)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                columns.forEach { column ->
                                    ChoiceButton(column.title, column.key in options.current.visibleColumns) {
                                        onChange(
                                            options.updateDisplay {
                                                it.copy(
                                                    visibleColumns =
                                                        if (column.key in
                                                            it.visibleColumns
                                                        ) {
                                                            it.visibleColumns - column.key
                                                        } else {
                                                            it.visibleColumns + column.key
                                                        },
                                                )
                                            },
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
}

@Composable
internal fun RosterFilterChips(
    roster: PlayerRoster,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (options.teamScope != RosterTeamScope.All) {
            FilterChip(
                "球団・リーグ：${options.teamScope.label(roster)}",
                RosterFilter.TEAM_SCOPE,
                roster,
                options,
                onChange,
            ) {
                onChange(options.selectTeamScope(RosterTeamScope.All))
            }
        }
        options.positionFilter?.let {
            FilterChip("ポジション：${it.label}", RosterFilter.POSITION, roster, options, onChange) {
                onChange(options.copy(positionFilter = null))
            }
        }
        if (!options.teamScope.isMajorOnly) {
            options.squad?.let {
                FilterChip(
                    "現在の軍：${if (it == PlayerSquad.FIRST) "一軍" else "二軍"}",
                    RosterFilter.SQUAD,
                    roster,
                    options,
                    onChange,
                ) {
                    onChange(options.copy(squad = null))
                }
            }
            options.registration?.let {
                FilterChip("登録区分：${it.displayName}", RosterFilter.REGISTRATION, roster, options, onChange) {
                    onChange(options.copy(registration = null))
                }
            }
        }
    }
}

@Composable
private fun RosterGroupingMenu(
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.grouping
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                "一覧グルーピング：${current.label} ▾",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RosterGrouping.choicesForScope(options.teamScope).forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.label) },
                    onClick = {
                        onChange(options.selectGrouping(value))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    filter: RosterFilter,
    roster: PlayerRoster,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember(options.teamScope) { mutableStateOf(false) }
    Box {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    Modifier.clickable(role = Role.Button) { open = true }.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                RosterIconButton("$label を解除", RosterIcon.CLOSE, false, onRemove)
            }
        }
        if (open) {
            RosterPopup({ open = false }) {
                Column(Modifier.width(224.dp).padding(8.dp)) {
                    FilterValues(filter, roster, options, onChange) { open = false }
                }
            }
        }
    }
}

@Composable
private fun FilterPopup(
    roster: PlayerRoster,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<RosterFilter?>(null) }
    RosterPopup(onDismiss) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected?.let { filter ->
                Column(Modifier.width(216.dp)) { FilterValues(filter, roster, options, onChange, onDismiss) }
            }
            Column(Modifier.width(184.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RosterFilter.entries
                    .filter {
                        !options.teamScope.isMajorOnly || it == RosterFilter.TEAM_SCOPE || it == RosterFilter.POSITION
                    }.forEach { filter ->
                        ChoiceButton(
                            label = "${filter.title}  ›",
                            selected = selected == filter,
                            modifier = Modifier.fillMaxWidth(),
                            showSelectionMark = false,
                        ) {
                            selected = filter
                        }
                    }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                ChoiceButton(
                    label = "ポジション連動",
                    selected = options.automaticDisplaySwitch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onChange(options.toggleAutomaticDisplaySwitch())
                }
            }
        }
    }
}

@Composable
private fun FilterValues(
    filter: RosterFilter,
    roster: PlayerRoster,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    ScrollableChoices {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (filter) {
                RosterFilter.TEAM_SCOPE ->
                    roster.teamScopeChoices().forEach { choice ->
                        ChoiceButton(
                            choice.scope.label(roster),
                            options.teamScope == choice.scope,
                            Modifier.fillMaxWidth().padding(start = if (choice.indented) 16.dp else 0.dp),
                        ) {
                            onChange(options.selectTeamScope(choice.scope))
                            onDismiss()
                        }
                    }
                RosterFilter.POSITION -> {
                    val values =
                        listOf(RosterPositionFilter.MainPosition(PlayerPosition.PITCHER), RosterPositionFilter.Fielders) +
                            PlayerPosition.entries.filter { it != PlayerPosition.PITCHER }.map { RosterPositionFilter.MainPosition(it) }
                    values.forEach { value ->
                        val indent = value is RosterPositionFilter.MainPosition && value.position != PlayerPosition.PITCHER
                        ChoiceButton(
                            value.label,
                            options.positionFilter == value,
                            Modifier.fillMaxWidth().padding(start = if (indent) 16.dp else 0.dp),
                        ) {
                            onChange(options.selectPosition(value))
                            onDismiss()
                        }
                    }
                }
                RosterFilter.SQUAD ->
                    PlayerSquad.entries.forEach { value ->
                        ChoiceButton(if (value == PlayerSquad.FIRST) "一軍" else "二軍", options.squad == value, Modifier.fillMaxWidth()) {
                            onChange(options.copy(squad = value))
                            onDismiss()
                        }
                    }
                RosterFilter.REGISTRATION ->
                    PlayerRegistration.entries.forEach { value ->
                        ChoiceButton(value.displayName, options.registration == value, Modifier.fillMaxWidth()) {
                            onChange(options.copy(registration = value))
                            onDismiss()
                        }
                    }
            }
        }
    }
}

private data class TeamScopeChoice(
    val scope: RosterTeamScope,
    val indented: Boolean = false,
)

private fun PlayerRoster.teamScopeChoices(): List<TeamScopeChoice> =
    buildList {
        add(TeamScopeChoice(RosterTeamScope.All))
        add(TeamScopeChoice(RosterTeamScope.AllDomestic))
        add(TeamScopeChoice(RosterTeamScope.FirstLeague))
        teams.toSortedMap().filterKeys { it in RosterTeamScope.FirstLeague.domesticTeamNumbers }.forEach { (number, _) ->
            add(TeamScopeChoice(RosterTeamScope.Team(number), indented = true))
        }
        add(TeamScopeChoice(RosterTeamScope.SecondLeague))
        teams.toSortedMap().filterKeys { it in RosterTeamScope.SecondLeague.domesticTeamNumbers }.forEach { (number, _) ->
            add(TeamScopeChoice(RosterTeamScope.Team(number), indented = true))
        }
        add(TeamScopeChoice(RosterTeamScope.Major))
    }

private fun RosterTeamScope.label(roster: PlayerRoster): String =
    when (this) {
        RosterTeamScope.All -> "すべて"
        RosterTeamScope.AllDomestic -> "国内全球団"
        RosterTeamScope.FirstLeague -> "1stリーグ"
        RosterTeamScope.SecondLeague -> "2ndリーグ"
        RosterTeamScope.Major -> "メジャー"
        is RosterTeamScope.Team -> roster.teams[number] ?: "球団$number"
    }

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showSelectionMark: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface)
            .selectable(selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (showSelectionMark) {
            Text(if (selected) "✓" else "", Modifier.width(12.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ScrollableChoices(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(scroll).padding(end = 12.dp)) { content() }
        Box(Modifier.matchParentSize()) {
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun RosterPopup(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Popup(
        popupPositionProvider = RosterPopupPosition,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val density = LocalDensity.current
        val windowHeightPx = LocalWindowInfo.current.containerSize.height
        Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Box(
                if (windowHeightPx > 0) {
                    Modifier.heightIn(max = with(density) { windowHeightPx.toDp() })
                } else {
                    Modifier
                },
            ) {
                content()
            }
        }
    }
}

private object RosterPopupPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            anchorBounds.bottom.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
}

private enum class RosterIcon { FILTER, SLIDERS, CLOSE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RosterIconButton(
    label: String,
    icon: RosterIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TooltipArea(tooltip = {
        Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 4.dp) {
            Text(label, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
    }) {
        IconButton(
            onClick = onClick,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                    .semantics { contentDescription = label },
        ) {
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(18.dp)) {
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
                when (icon) {
                    RosterIcon.FILTER -> {
                        line(0.1f, 0.25f, 0.9f, 0.25f)
                        line(0.25f, 0.5f, 0.75f, 0.5f)
                        line(0.4f, 0.75f, 0.6f, 0.75f)
                    }
                    RosterIcon.SLIDERS -> {
                        listOf(0.25f, 0.5f, 0.75f).forEachIndexed { index, y ->
                            line(0.1f, y, 0.9f, y)
                            val x = if (index == 1) 0.65f else 0.35f
                            line(x, y - 0.1f, x, y + 0.1f)
                        }
                    }
                    RosterIcon.CLOSE -> {
                        line(0.3f, 0.3f, 0.7f, 0.7f)
                        line(0.7f, 0.3f, 0.3f, 0.7f)
                    }
                }
            }
        }
    }
}
