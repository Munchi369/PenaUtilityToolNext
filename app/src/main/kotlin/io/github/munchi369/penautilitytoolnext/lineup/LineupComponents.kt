package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import io.github.munchi369.penautilitytoolnext.ui.gameAbilityColor
import kotlin.math.roundToInt

internal data class ArrangementHighlight(
    val spots: Set<DropSpot> = emptySet(),
    val alpha: Float = 0f,
)

internal val LocalArrangementHighlight = staticCompositionLocalOf { ArrangementHighlight() }

internal object LineupColors {
    val hover = Color(0xFFEEF3FA)
    val selected = Color(0xFFEEF2F7)
    val warningBackground = Color(0xFFFFF6E5)
    val warning = Color(0xFFAA6900)
    val injuryBackground = Color(0xFFFBEAEA)
    val injury = Color(0xFFBF3333)
    val foreignBackground = Color(0xFFE8EEF9)
    val foreign = Color(0xFF285EAB)
    val restBackground = Color(0xFFF3EDF9)
    val rest = Color(0xFF7E3FA0)
    val developmentBackground = Color(0xFFE9F6EE)
    val development = Color(0xFF287A46)
    val strongAptitude = Color(0xFFDCE7F5)
    val weakAptitude = Color(0xFFEEF3FA)
    val subAptitude = Color(0xFF5B7DB0)
    val pinHead = Color(0xFFE5484D)
}

internal fun blockedReason(
    player: LineupPlayer,
    draft: LineupDraft,
    foreignRoom: Int,
): String? =
    when {
        draft.squad(player.id) == PlayerSquad.FIRST -> null
        player.initialSquad == PlayerSquad.SECOND && player.restDays > 0 -> "休止 ${player.restDays}日"
        player.foreign && foreignRoom <= 0 -> "枠外"
        else -> null
    }

@Composable
internal fun DropArea(
    drag: LineupDragState,
    spot: DropSpot,
    title: String,
    count: Int,
    content: @Composable () -> Unit,
) {
    val hovered = drag.hovered == spot
    Column(
        Modifier
            .fillMaxWidth()
            .dropSpot(drag, spot)
            .clip(RoundedCornerShape(8.dp))
            .then(if (hovered) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .padding(4.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        content()
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    vararg columns: String,
    widths: List<Int>,
) {
    Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        columns.forEachIndexed { index, column -> HeaderText(column, Modifier.width(widths[index].dp), TextAlign.End) }
        widths.drop(columns.size).forEach { Spacer(Modifier.width(it.dp)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun HeaderText(
    text: String,
    modifier: Modifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
    )
}

@Composable
internal fun LineupRow(
    spot: DropSpot,
    drag: LineupDragState,
    dragItem: DragItem,
    dragLabel: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    height: Int,
    dragEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val hovered = drag.hovered == spot
    val dragging = drag.item == dragItem
    val highlight = LocalArrangementHighlight.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .dropSpot(drag, spot)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    hovered -> LineupColors.hover
                    spot in highlight.spots && highlight.alpha > 0f ->
                        lerp(
                            if (selected) LineupColors.selected else Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            highlight.alpha,
                        )
                    selected -> LineupColors.selected
                    else -> Color.Transparent
                },
            ).then(if (hovered) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier)
            .dragSource(drag, dragItem, dragLabel, dragEnabled)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "⋮⋮",
            Modifier.width(18.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (dragEnabled) MaterialTheme.colorScheme.outline else Color.Transparent,
        )
        Row(Modifier.weight(1f).alpha(if (dragging) 0.35f else 1f), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun PlayerName(
    player: LineupPlayer,
    draft: LineupDraft,
    modifier: Modifier,
) {
    val color =
        when {
            player.injured -> LineupColors.injury
            player.initialSquad == PlayerSquad.SECOND && player.restDays > 0 && draft.squad(player.id) == PlayerSquad.SECOND ->
                LineupColors.rest
            player.foreign -> LineupColors.foreign
            !draft.isControlled(player.id) -> LineupColors.development
            player.injury is PlayerInjury.Discomfort -> LineupColors.warning
            else -> MaterialTheme.colorScheme.onSurface
        }
    Text(
        player.name,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun PlayerBadges(
    player: LineupPlayer,
    draft: LineupDraft,
    blocked: String? = null,
    extraBadge: String? = null,
) {
    if (player.foreign) Badge("外", LineupColors.foreignBackground, LineupColors.foreign)
    when (val injury = player.injury) {
        is PlayerInjury.Injured -> Badge("故障", LineupColors.injuryBackground, LineupColors.injury)
        is PlayerInjury.Discomfort -> Badge("違和感", LineupColors.warningBackground, LineupColors.warning)
        PlayerInjury.None -> Unit
    }
    val contract = draft.contract(player.id)
    if (!draft.isControlled(player.id)) Badge("育成", LineupColors.developmentBackground, LineupColors.development)
    if (contract != player.initialContract) {
        Badge("契約変更", LineupColors.warningBackground, LineupColors.warning)
    }
    val squad = draft.squad(player.id)
    if (squad != player.initialSquad) {
        Badge(
            if (squad ==
                PlayerSquad.FIRST
            ) {
                "昇格"
            } else {
                "降格"
            },
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.primary,
        )
    }
    blocked?.let { Badge(it, LineupColors.restBackground, LineupColors.rest) }
    extraBadge?.let { Badge(it, LineupColors.warningBackground, LineupColors.warning) }
}

@Composable
internal fun Badge(
    text: String,
    background: Color,
    foreground: Color,
) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = foreground,
        maxLines = 1,
    )
}

@Composable
internal fun AbilityValue(
    cell: PlayerCell?,
    width: Int,
    dimmed: Boolean = false,
    align: TextAlign = TextAlign.End,
) {
    val rank = cell?.abilityStyle?.rank
    val text =
        buildAnnotatedString {
            when {
                cell == null -> append("—")
                rank == null -> append(cell.text)
                else -> {
                    withStyle(
                        SpanStyle(color = gameAbilityColor(rank).copy(alpha = if (dimmed) 0.45f else 1f), fontWeight = FontWeight.Bold),
                    ) {
                        append(rank.symbol)
                    }
                    append(" ")
                    append(cell.text.removePrefix(rank.symbol))
                }
            }
        }
    Text(
        text,
        Modifier.width(width.dp),
        style = MaterialTheme.typography.bodySmall,
        textAlign = align,
        color = if (dimmed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
internal fun NumberValue(
    text: String,
    width: Int,
    dimmed: Boolean = false,
) {
    Text(
        text,
        Modifier.width(width.dp),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.End,
        color = if (dimmed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
internal fun PinToggle(
    pinned: Boolean,
    onToggle: () -> Unit,
) {
    val color = if (pinned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .width(28.dp)
            .height(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (pinned) "固定を解除" else "固定する" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.4.dp.toPx()
            val head = Offset(size.width / 2, size.height * 0.32f)
            if (pinned) {
                drawCircle(LineupColors.pinHead, radius = size.width * 0.28f, center = head)
            } else {
                drawCircle(color, radius = size.width * 0.28f, center = head, style = Stroke(stroke))
            }
            drawLine(color, Offset(size.width / 2, size.height * 0.6f), Offset(size.width / 2, size.height), stroke)
        }
    }
}

@Composable
internal fun DragGhost(
    drag: LineupDragState,
    rootOrigin: Offset,
) {
    if (drag.item == null) return
    val position = drag.pointer - rootOrigin
    Surface(
        modifier = Modifier.offset { IntOffset((position.x + 14).roundToInt(), (position.y - 14).roundToInt()) },
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(drag.label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
