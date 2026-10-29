package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.ui.FilterChoiceButton
import io.github.munchi369.penautilitytoolnext.ui.FilterIcon
import io.github.munchi369.penautilitytoolnext.ui.FilterIconButton
import io.github.munchi369.penautilitytoolnext.ui.FilterPopup

internal fun detailStatsColumns(
    layout: PlayerRosterOptions,
    detail: PlayerDetailOptions,
): List<RosterColumn> {
    val monthly = detail.period == DetailStatsPeriod.MONTHS
    val available =
        DetailColumns
            .stats(detail.pitching)
            .filterNot { column ->
                (column.key == "ITIGUN" && (monthly || detail.division == 3)) ||
                    (
                        monthly &&
                            (
                                column.key == "NENPOU" ||
                                    column.key.startsWith("DAJUN") ||
                                    column.key.startsWith("POJI") ||
                                    column.key in setOf("DH", "DAIDA", "DAISOU", "SYUBI")
                            )
                    )
            }.map { it.key }
            .toSet()
    return layout.orderedColumns().filter { it.key in available }
}

@Composable
internal fun DetailStatsDisplaySettings(
    columns: List<RosterColumn>,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
) {
    var expanded by remember(options.display) { mutableStateOf(false) }
    val defaultColumns = RosterColumns.forDisplay(options.display)
    val availableKeys = columns.map { it.key }.toSet()
    val displayed = columns.filter { it.key in options.current.visibleColumns }.map { it.key }
    val defaults = defaultColumns.filter { it.standard && it.key in availableKeys }.map { it.key }
    Box {
        FilterIconButton("表示設定", FilterIcon.SLIDERS, expanded) { expanded = !expanded }
        if (displayed != defaults) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(7.dp)
                    .background(Color(0xFF2563EB), CircleShape)
                    .semantics { contentDescription = "表示項目を変更済み" },
            )
        }
        if (expanded) {
            FilterPopup({ expanded = false }) {
                Column(Modifier.width(480.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${options.display.title}の表示項目", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "表示項目・列順は現役選手一覧と共通",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Row {
                        TextButton(onClick = {
                            onChange(
                                options.updateDisplay {
                                    it.copy(
                                        visibleColumns = defaultColumns.filter { it.standard }.map { it.key }.toSet(),
                                        columnOrder = emptyList(),
                                    )
                                },
                            )
                        }) { Text("標準に戻す") }
                        TextButton(onClick = {
                            onChange(options.updateDisplay { it.copy(visibleColumns = it.visibleColumns + availableKeys) })
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
                                                    if (column.key in it.visibleColumns) {
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
