package org.example.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun RosterToolbar(
    roster: PlayerRoster,
    playerCount: Int,
    options: PlayerRosterOptions,
    columns: List<RosterColumn>,
    onChange: (PlayerRosterOptions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                roster.season?.let {
                    Text("${it}年目", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${playerCount}人", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.width(372.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RosterSearch(options.search, { onChange(options.copy(search = it)) }, Modifier.weight(1f))
                RosterViewControls(roster, options, columns, onChange)
            }
        }
        Column {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup()) {
                    RosterDisplay.entries.forEach { display ->
                        val selected = options.display == display
                        val bringIntoView = remember { BringIntoViewRequester() }
                        LaunchedEffect(selected) {
                            if (selected) bringIntoView.bringIntoView()
                        }
                        val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            Modifier
                                .bringIntoViewRequester(bringIntoView)
                                .selectable(selected, role = Role.Tab, onClick = { onChange(options.selectDisplay(display)) })
                                .height(48.dp)
                                .drawBehind {
                                    if (selected) {
                                        drawLine(
                                            color,
                                            Offset(12.dp.toPx(), size.height - 1.dp.toPx()),
                                            Offset(size.width - 12.dp.toPx(), size.height - 1.dp.toPx()),
                                            2.dp.toPx(),
                                        )
                                    }
                                }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                display.title,
                                color = color,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Box(Modifier.width(164.dp), contentAlignment = Alignment.CenterEnd) {
                    if (options.display.isStats && !options.teamScope.isMajorOnly) {
                        RosterMenu(
                            "表示成績：${if (options.statsSquad == 1) "一軍" else "二軍"}",
                            listOf(1 to "一軍成績", 2 to "二軍成績"),
                        ) { onChange(options.copy(statsSquad = it)) }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun RosterSearch(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.semantics { contentDescription = "選手検索" },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interaction,
        decorationBox = { input ->
            Row(
                Modifier
                    .height(36.dp)
                    .background(colors.surfaceContainerLow, RoundedCornerShape(6.dp))
                    .border(1.dp, if (focused) colors.primary else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Canvas(Modifier.size(16.dp)) {
                    drawCircle(
                        colors.onSurfaceVariant,
                        radius = 5.dp.toPx(),
                        center = Offset(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(1.5.dp.toPx()),
                    )
                    drawLine(colors.onSurfaceVariant, Offset(10.dp.toPx(), 10.dp.toPx()), Offset(15.dp.toPx(), 15.dp.toPx()), 1.5.dp.toPx())
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("名前・背番号・選手ID", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    input()
                }
            }
        },
    )
}

@Composable
private fun <T> RosterMenu(
    label: String,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = {
                    onSelect(value)
                    expanded = false
                })
            }
        }
    }
}
