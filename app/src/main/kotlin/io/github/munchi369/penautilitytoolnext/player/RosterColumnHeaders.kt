package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal class RosterColumnDragState {
    var dragged by mutableStateOf<String?>(null)
    var distance by mutableFloatStateOf(0f)
}

@Composable
internal fun RosterColumnHeaders(
    columns: List<RosterColumn>,
    options: PlayerRosterOptions,
    onSort: (String) -> Unit,
    onChange: (PlayerRosterOptions) -> Unit,
    modifier: Modifier = Modifier,
    renderColumns: List<RosterColumn> = columns,
    dragState: RosterColumnDragState = remember(columns) { RosterColumnDragState() },
    headerContent: @Composable (RosterColumn) -> Unit = { HeaderCell(it, options, onSort) },
) {
    val density = LocalDensity.current
    val currentOptions by rememberUpdatedState(options)
    val currentOnChange by rememberUpdatedState(onChange)
    val dragged = dragState.dragged
    val distance = dragState.distance
    val target = dragged?.let { rosterColumnDropTarget(columns, it, distance / density.density) }
    Row(modifier) {
        renderColumns.forEach { column ->
            val index = columns.indexOf(column)
            key(column.key) {
                val moving = dragged == column.key
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .zIndex(if (moving) 1f else 0f)
                        .graphicsLayer { translationX = if (moving) distance else 0f }
                        .background(if (moving) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .then(
                            if (target == column.key && target != dragged) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            },
                        ).pointerInput(columns, column.key) {
                            detectDragGestures(
                                onDragStart = {
                                    dragState.distance = 0f
                                    dragState.dragged = column.key
                                },
                                onDragCancel = {
                                    dragState.dragged = null
                                    dragState.distance = 0f
                                },
                                onDragEnd = {
                                    val destination = rosterColumnDropTarget(columns, column.key, dragState.distance / density.density)
                                    currentOnChange(currentOptions.moveColumn(column.key, destination))
                                    dragState.dragged = null
                                    dragState.distance = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                dragState.distance += amount.x
                            }
                        }.semantics {
                            customActions =
                                listOfNotNull(
                                    columns.getOrNull(index - 1)?.let { previous ->
                                        CustomAccessibilityAction("${column.title}を左へ移動") {
                                            onChange(options.moveColumn(column.key, previous.key))
                                            true
                                        }
                                    },
                                    columns.getOrNull(index + 1)?.let { next ->
                                        CustomAccessibilityAction("${column.title}を右へ移動") {
                                            onChange(options.moveColumn(column.key, next.key))
                                            true
                                        }
                                    },
                                    CustomAccessibilityAction("${column.title}を非表示") {
                                        onChange(options.hideColumn(column.key))
                                        true
                                    },
                                )
                        },
                ) {
                    ContextMenuArea(
                        items = {
                            listOf(ContextMenuItem("この列を非表示") { onChange(options.hideColumn(column.key)) })
                        },
                    ) {
                        headerContent(column)
                    }
                }
            }
        }
    }
}

internal fun rosterColumnDropTarget(
    columns: List<RosterColumn>,
    key: String,
    distance: Float,
): String {
    val source = columns.indexOfFirst { it.key == key }
    if (source < 0) return key
    val center = columns.take(source).sumOf { it.width } + columns[source].width / 2f + distance
    var left = 0f
    val before =
        columns.count { column ->
            val midpoint = left + column.width / 2f
            left += column.width
            column.key != key && midpoint < center
        }
    return columns[before].key
}
