package org.example.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

internal enum class DetailAbilityChartKind(
    val title: String,
    val scaleFloor: Double,
) {
    SPEED("球速（km/h）", 1.0),
    OVERALL("総合（保存値）", 1.0),
    BASIC("基本能力", 100.0),
}

internal fun detailChartAxisLabel(label: String): String {
    Regex("""(\d{4})年""").find(label)?.let { return it.groupValues[1] }
    if (label == "現在") return label
    if (label == "入団時") return "入団"
    Regex("""ゲーム(\d+)年目""").find(label)?.let { return "${it.groupValues[1]}年目" }
    return label.substringBefore("（")
}

internal fun detailChartPointLabel(cell: PlayerCell): String? {
    if (cell.text.isEmpty() || cell.number == null) return null
    return cell.number.toInt().toString()
}

internal fun detailChartLatestLabel(
    rows: List<DetailTableRow>,
    key: String,
): String? = rows.asReversed().firstNotNullOfOrNull { row -> row.cells[key]?.let(::detailChartPointLabel) }

/** Indexes that keep the first and last point and stay within [capacity]. */
internal fun detailChartLabelIndexes(
    count: Int,
    capacity: Int,
): List<Int> {
    if (count <= 0) return emptyList()
    if (count <= capacity) return (0 until count).toList()
    val slots = capacity.coerceAtLeast(2)
    val step = ceil((count - 1).toDouble() / (slots - 1)).toInt().coerceAtLeast(1)
    val indexes = (0 until count step step).toMutableList()
    if (indexes.last() != count - 1) indexes += count - 1
    return indexes
}

@Composable
internal fun DetailAbilityChart(
    kind: DetailAbilityChartKind,
    columns: List<RosterColumn>,
    rows: List<DetailTableRow>,
) {
    val values =
        rows.flatMap { row ->
            columns.mapNotNull { column ->
                row.cells[column.key]?.takeIf { detailChartPointLabel(it) != null }?.number
            }
        }
    val max = (values.maxOrNull() ?: kind.scaleFloor).coerceAtLeast(kind.scaleFloor)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val palette = listOf(Color(0xFF285EAB), Color(0xFFB54B28), Color(0xFF25816F), Color(0xFF8154A8), Color(0xFF947110), Color(0xFFBC4680))
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall
    val description =
        buildString {
            append(kind.title)
            columns.forEach { column ->
                val points =
                    rows.mapNotNull { row ->
                        val value = row.cells[column.key]?.let(::detailChartPointLabel) ?: return@mapNotNull null
                        "${detailChartAxisLabel(row.label)} $value"
                    }
                if (points.isNotEmpty()) append("。${column.title}: ${points.joinToString("、")}")
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(kind.title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            columns.forEachIndexed { index, column ->
                val latest = detailChartLatestLabel(rows, column.key)
                Text(
                    if (latest == null) column.title else "${column.title} $latest",
                    color = palette[index % palette.size],
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (values.isEmpty()) {
            Text("グラフに表示できる記録がありません", style = MaterialTheme.typography.bodySmall)
        } else {
            Canvas(
                Modifier.fillMaxWidth().height(168.dp).padding(horizontal = 4.dp).semantics {
                    contentDescription = description
                },
            ) {
                val axis = 36.dp.toPx()
                val bottom = 18.dp.toPx()
                val plotWidth = (size.width - axis).coerceAtLeast(1f)
                val plotHeight = (size.height - bottom).coerceAtLeast(1f)

                fun xOf(index: Int): Float =
                    axis +
                        if (rows.size == 1) {
                            plotWidth / 2
                        } else {
                            plotWidth * index / (rows.size - 1)
                        }

                fun yOf(value: Double): Float = (plotHeight * (1 - value / max)).toFloat()
                for (step in 0..4) {
                    val y = plotHeight * step / 4
                    drawLine(grid, Offset(axis, y), Offset(size.width, y), 1f)
                    val tick = (max * (4 - step) / 4).toInt().toString()
                    val layout = measurer.measure(tick, axisStyle)
                    drawText(
                        layout,
                        axisColor,
                        Offset(
                            (axis - layout.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                            (y - layout.size.height / 2).coerceIn(0f, plotHeight),
                        ),
                    )
                }
                columns.forEachIndexed { index, column ->
                    var previous: Offset? = null
                    rows.forEachIndexed { rowIndex, row ->
                        val cell = row.cells[column.key]?.takeIf { detailChartPointLabel(it) != null }
                        val value = cell?.number
                        val point = value?.let { Offset(xOf(rowIndex), yOf(it)) }
                        val color = palette[index % palette.size]
                        if (point != null && cell != null) {
                            val pitchChanged =
                                column.key.startsWith("PITCH") &&
                                    rowIndex > 0 &&
                                    rows[rowIndex - 1].cells[column.key]?.text?.substringBefore(' ') !=
                                    cell.text.substringBefore(' ')
                            if (previous != null && !pitchChanged) {
                                drawLine(
                                    color,
                                    previous,
                                    point,
                                    2f,
                                    pathEffect =
                                        if (row.label == "現在") {
                                            PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                        } else {
                                            null
                                        },
                                )
                            }
                            drawCircle(color, 4f, point)
                        }
                        previous = point
                    }
                }
                val placed = mutableListOf<Rect>()
                columns.forEachIndexed { index, column ->
                    val color = palette[index % palette.size]
                    rows.forEachIndexed { rowIndex, row ->
                        val cell = row.cells[column.key] ?: return@forEachIndexed
                        val valueLabel = detailChartPointLabel(cell) ?: return@forEachIndexed
                        val number = cell.number ?: return@forEachIndexed
                        val layout = measurer.measure(valueLabel, axisStyle)
                        val minX = axis
                        val maxX = (size.width - layout.size.width).coerceAtLeast(minX)
                        val topLeft =
                            Offset(
                                (xOf(rowIndex) - layout.size.width / 2).coerceIn(minX, maxX),
                                (yOf(number) - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                            )
                        val bounds = Rect(topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
                        if (placed.none { it.overlaps(bounds) }) {
                            drawText(layout, color, topLeft)
                            placed += bounds
                        }
                    }
                }
                val yearLayout = measurer.measure("0000", axisStyle)
                val capacity = (plotWidth / (yearLayout.size.width + 8.dp.toPx())).toInt().coerceAtLeast(2)
                detailChartLabelIndexes(rows.size, capacity).forEach { index ->
                    val label = detailChartAxisLabel(rows[index].label)
                    val layout = measurer.measure(label, axisStyle)
                    val minX = axis
                    val maxX = (size.width - layout.size.width).coerceAtLeast(minX)
                    drawText(
                        layout,
                        axisColor,
                        Offset(
                            (xOf(index) - layout.size.width / 2).coerceIn(minX, maxX),
                            plotHeight + 2.dp.toPx(),
                        ),
                    )
                }
            }
        }
    }
}
