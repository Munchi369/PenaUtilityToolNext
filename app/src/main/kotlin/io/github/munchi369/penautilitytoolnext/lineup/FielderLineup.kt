package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad

@Composable
internal fun FielderLineup(
    controller: LineupController,
    snapshot: LineupSnapshot,
    draft: LineupDraft,
    mode: LineupMode,
    pins: Map<PinGroup, Set<Int>>,
    drag: LineupDragState,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    SectionHeader("スタメン", "巧打", "長打", "疲労", widths = listOf(52, 52, 32, 28))
    draft.lineup(mode).forEachIndexed { index, entry ->
        val player = snapshot.playerOrNull(entry.playerId)
        val spot = DropSpot.LineupSlot(mode, index)
        LineupRow(
            spot = spot,
            drag = drag,
            dragItem = DragItem.LineupSlot(mode, index),
            dragLabel = "${index + 1}  ${player?.name ?: entry.slot.label}",
            selected = player != null && player.id == selectedId,
            onClick = player?.let { { onSelect(it.id) } },
            height = 40,
        ) {
            Text(
                "${index + 1}",
                modifier = Modifier.width(28.dp).padding(end = 8.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                entry.slot == FieldSlot.PITCHER -> {
                    Text("先発投手", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PositionChip(entry.slot, options = emptyList()) {}
                    Spacer(Modifier.width(164.dp))
                }

                player == null -> {
                    Text("—", Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                    PositionChip(entry.slot, options = emptyList()) {}
                    Spacer(Modifier.width(164.dp))
                }

                else -> {
                    PlayerName(player, draft, Modifier.weight(1f))
                    val options =
                        (FieldSlot.defense + if (mode == LineupMode.DH) listOf(FieldSlot.DH) else emptyList()).map { slot ->
                            slot to slot.aptitudeColumn?.let { "適性 ${draft.aptitude(player.id, slot)}" }.orEmpty()
                        }
                    PositionChip(
                        entry.slot,
                        options = options,
                        sub = entry.slot in FieldSlot.defense && !draft.isMain(player.id, entry.slot),
                        unqualified = entry.slot in FieldSlot.defense && draft.aptitude(player.id, entry.slot) == 0,
                    ) { controller.changePosition(mode, index, it) }
                    AbilityValue(player.cells["KOUDA"], 52)
                    AbilityValue(player.cells["POWER"], 52)
                    NumberValue(player.cells["HIROU"]?.text.orEmpty(), 32)
                    PinToggle(player.id in pins[mode.pinGroup].orEmpty()) { controller.togglePin(mode.pinGroup, player.id) }
                }
            }
        }
    }
}

@Composable
internal fun FielderLists(
    snapshot: LineupSnapshot,
    draft: LineupDraft,
    editing: LineupEditing,
    mode: LineupMode,
    drag: LineupDragState,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    val bench = editing.bench(draft, mode)
    val farm =
        snapshot.players.filter { !it.isPitcher && draft.isControlled(it.id) && draft.squad(it.id) == PlayerSquad.SECOND }
    val development = snapshot.players.filter { !it.isPitcher && !draft.isControlled(it.id) }
    val room = editing.foreignRoom(draft, pitcher = false)
    DropArea(drag, DropSpot.FirstTeamArea(pitchers = false), "1軍控え", bench.size) {
        FielderListHeader()
        bench.forEach { FielderListRow(it, draft, room, drag, selectedId, onSelect) }
    }
    Spacer(Modifier.height(16.dp))
    DropArea(drag, DropSpot.FarmArea(pitchers = false), "2軍", farm.size) {
        FielderListHeader()
        farm.forEach { FielderListRow(it, draft, room, drag, selectedId, onSelect) }
    }
    Spacer(Modifier.height(16.dp))
    DropArea(drag, DropSpot.DevelopmentArea(pitchers = false), "育成", development.size) {
        FielderListHeader()
        development.forEach { FielderListRow(it, draft, room, drag, selectedId, onSelect) }
    }
}

@Composable
private fun FielderListHeader() {
    Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(18.dp))
        HeaderText("名前", Modifier.weight(1f))
        HeaderText("巧打", Modifier.width(44.dp), TextAlign.End)
        HeaderText("長打", Modifier.width(44.dp), TextAlign.End)
        Spacer(Modifier.width(8.dp))
        FieldSlot.defense.forEach { HeaderText(it.shortLabel, Modifier.width(18.dp), TextAlign.Center) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FielderListRow(
    player: LineupPlayer,
    draft: LineupDraft,
    foreignRoom: Int,
    drag: LineupDragState,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    val blocked = blockedReason(player, draft, foreignRoom)
    LineupRow(
        spot = DropSpot.ListedPlayer(player.id),
        drag = drag,
        dragItem = DragItem.ListedPlayer(player.id),
        dragLabel = player.name,
        selected = player.id == selectedId,
        onClick = { onSelect(player.id) },
        height = 34,
    ) {
        PlayerName(player, draft, Modifier.weight(1f))
        AbilityValue(player.cells["KOUDA"], 44, dimmed = blocked != null)
        AbilityValue(player.cells["POWER"], 44, dimmed = blocked != null)
        Spacer(Modifier.width(8.dp))
        FieldSlot.defense.forEach { slot -> AptitudeChip(slot, draft.aptitude(player.id, slot), draft.isMain(player.id, slot)) }
    }
}

@Composable
private fun PositionChip(
    slot: FieldSlot,
    options: List<Pair<FieldSlot, String>>,
    sub: Boolean = false,
    unqualified: Boolean = false,
    onSelect: (FieldSlot) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (sub && !unqualified) "${slot.label}（サブ）" else slot.label
    val positionColor =
        when (slot) {
            FieldSlot.PITCHER, FieldSlot.DH -> Color(0xFFFFBFC1)
            FieldSlot.CATCHER -> Color(0xFF87CCFF)
            FieldSlot.FIRST, FieldSlot.SECOND, FieldSlot.THIRD, FieldSlot.SHORT -> Color(0xFFFFFFCC)
            FieldSlot.LEFT, FieldSlot.CENTER, FieldSlot.RIGHT -> Color(0xFF99FF99)
        }
    val background =
        if (unqualified) LineupColors.injuryBackground else lerp(positionColor, MaterialTheme.colorScheme.surface, 0.60f)
    val border = if (unqualified) LineupColors.injury else lerp(positionColor, MaterialTheme.colorScheme.onSurface, 0.15f)
    Box(Modifier.width(96.dp)) {
        Text(
            label,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(background)
                    .border(1.dp, border, RoundedCornerShape(5.dp))
                    .then(if (options.isNotEmpty()) Modifier.clickable { expanded = true } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .semantics { contentDescription = "守備位置 $label" },
            style = MaterialTheme.typography.labelMedium,
            color = if (unqualified) LineupColors.injury else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, detail) ->
                DropdownMenuItem(
                    text = {
                        Row(Modifier.width(140.dp)) {
                            Text(
                                option.label,
                                Modifier.weight(1f),
                                fontWeight =
                                    if (option ==
                                        slot
                                    ) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                            )
                            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun AptitudeChip(
    slot: FieldSlot,
    value: Int,
    main: Boolean,
) {
    val background =
        when {
            main -> MaterialTheme.colorScheme.primary
            value >= 60 -> LineupColors.strongAptitude
            value > 0 -> LineupColors.weakAptitude
            else -> MaterialTheme.colorScheme.background
        }
    val foreground =
        when {
            main -> MaterialTheme.colorScheme.onPrimary
            value >= 60 -> LineupColors.foreign
            value > 0 -> LineupColors.subAptitude
            else -> MaterialTheme.colorScheme.outline
        }
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (main || value > 0) slot.shortLabel else "-", style = MaterialTheme.typography.labelSmall, color = foreground)
    }
}
