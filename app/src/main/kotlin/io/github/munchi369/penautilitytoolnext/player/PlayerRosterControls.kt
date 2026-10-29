package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.ui.FilterChoiceButton
import io.github.munchi369.penautilitytoolnext.ui.FilterIcon
import io.github.munchi369.penautilitytoolnext.ui.FilterIconButton
import io.github.munchi369.penautilitytoolnext.ui.FilterPopup

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
            FilterIconButton("フィルター", FilterIcon.FILTER, filtersOpen) {
                columnsOpen = false
                filtersOpen = !filtersOpen
            }
            if (filtersOpen) {
                FilterPopup(roster, options, onChange, { filtersOpen = false })
            }
        }
        Box {
            FilterIconButton("表示設定", FilterIcon.SLIDERS, columnsOpen) {
                filtersOpen = false
                columnsOpen = !columnsOpen
            }
            if (options.hasCustomColumns()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(7.dp)
                        .background(Color(0xFF2563EB), CircleShape)
                        .semantics { contentDescription = "表示項目を変更済み" },
                )
            }
            if (columnsOpen) {
                FilterPopup({ columnsOpen = false }) {
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
                                            columnOrder = emptyList(),
                                            columnWidths = emptyMap(),
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
                                    FilterChoiceButton(column.title, column.key in options.current.visibleColumns) {
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
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    Modifier.clickable(role = Role.Button) { open = true }.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                FilterIconButton("$label を解除", FilterIcon.CLOSE, false, onRemove)
            }
        }
        if (open) {
            FilterPopup({ open = false }) {
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
    FilterPopup(onDismiss) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected?.let { filter ->
                Column(Modifier.width(216.dp)) { FilterValues(filter, roster, options, onChange, onDismiss) }
            }
            Column(Modifier.width(184.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RosterFilter.entries
                    .filter {
                        !options.teamScope.isMajorOnly || it == RosterFilter.TEAM_SCOPE || it == RosterFilter.POSITION
                    }.forEach { filter ->
                        FilterChoiceButton(
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
                FilterChoiceButton(
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
                        FilterChoiceButton(
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
                        listOf(
                            RosterPositionFilter.MainPosition(PlayerPosition.PITCHER),
                            RosterPositionFilter.Starters,
                            RosterPositionFilter.Relievers,
                            RosterPositionFilter.Fielders,
                        ) +
                            PlayerPosition.entries.filter { it != PlayerPosition.PITCHER }.map { RosterPositionFilter.MainPosition(it) }
                    values.forEach { value ->
                        val indent =
                            when (value) {
                                RosterPositionFilter.Starters, RosterPositionFilter.Relievers -> true
                                is RosterPositionFilter.MainPosition -> value.position != PlayerPosition.PITCHER
                                else -> false
                            }
                        FilterChoiceButton(
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
                        FilterChoiceButton(
                            if (value ==
                                PlayerSquad.FIRST
                            ) {
                                "一軍"
                            } else {
                                "二軍"
                            },
                            options.squad == value,
                            Modifier.fillMaxWidth(),
                        ) {
                            onChange(options.copy(squad = value))
                            onDismiss()
                        }
                    }
                RosterFilter.REGISTRATION ->
                    PlayerRegistration.entries.forEach { value ->
                        FilterChoiceButton(value.displayName, options.registration == value, Modifier.fillMaxWidth()) {
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
internal fun ScrollableChoices(
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
