package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.ui.abilityCell

@Composable
internal fun PitchingRoles(
    controller: LineupController,
    snapshot: LineupSnapshot,
    draft: LineupDraft,
    pins: Map<PinGroup, Set<Int>>,
    drag: LineupDragState,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    PitchingRole.entries.forEach { role ->
        if (role == PitchingRole.RELIEVER) Spacer(Modifier.height(16.dp))
        val readyTitle = if (role == PitchingRole.STARTER) "先発時" else ""
        SectionHeader(role.label, "総合", "適性", readyTitle, "疲労", widths = listOf(40, 48, 40, 32, 24))
        draft.roles(role).forEachIndexed { index, id ->
            val player = snapshot.playerOrNull(id)
            LineupRow(
                spot = DropSpot.RoleSlot(role, index),
                drag = drag,
                dragItem = DragItem.RoleSlot(role, index),
                dragLabel = "${role.roleName(index)}  ${player?.name.orEmpty()}",
                dragEnabled = player != null,
                selected = player != null && player.id == selectedId,
                onClick = player?.let { { onSelect(it.id) } },
                height = 34,
            ) {
                Text(
                    role.roleName(index),
                    Modifier.width(104.dp).padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (player == null) {
                    Text("空き", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(184.dp))
                } else {
                    val aptitude = draft.pitchingAptitude(player.id)
                    PlayerName(player, draft, Modifier.weight(1f))
                    NumberValue(player.overall.toString(), 40)
                    AbilityValue(abilityCell(aptitude.of(role)), 48)
                    NumberValue(if (role == PitchingRole.STARTER) player.cells["READY"]?.text.orEmpty() else "", 40)
                    NumberValue(player.cells["HIROU"]?.text.orEmpty(), 32)
                    PinToggle(player.id in pins[PinGroup.PITCHING].orEmpty()) { controller.togglePin(PinGroup.PITCHING, player.id) }
                }
            }
        }
    }
}

@Composable
internal fun PitcherLists(
    snapshot: LineupSnapshot,
    draft: LineupDraft,
    editing: LineupEditing,
    drag: LineupDragState,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    val pitchers = snapshot.players.filter { it.isPitcher }
    val bench =
        pitchers
            .filter { draft.isControlled(it.id) && draft.squad(it.id) == PlayerSquad.FIRST && !draft.inAnyRole(it.id) }
            .sortedByDescending { it.overall }
    val farm = pitchers.filter { draft.isControlled(it.id) && draft.squad(it.id) == PlayerSquad.SECOND }
    val development = pitchers.filter { !draft.isControlled(it.id) }
    val room = editing.foreignRoom(draft, pitcher = true)
    DropArea(drag, DropSpot.FirstTeamArea(pitchers = true), "ベンチ", bench.size) {
        PitcherListHeader()
        bench.forEach { PitcherListRow(it, draft, room, drag, selectedId, onSelect) }
    }
    Spacer(Modifier.height(16.dp))
    DropArea(drag, DropSpot.FarmArea(pitchers = true), "2軍", farm.size) {
        PitcherListHeader()
        farm.forEach { PitcherListRow(it, draft, room, drag, selectedId, onSelect) }
    }
    Spacer(Modifier.height(16.dp))
    DropArea(drag, DropSpot.DevelopmentArea(pitchers = true), "育成", development.size) {
        PitcherListHeader()
        development.forEach { PitcherListRow(it, draft, room, drag, selectedId, onSelect) }
    }
}

@Composable
private fun PitcherListHeader() {
    Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(18.dp))
        HeaderText("名前", Modifier.weight(1f))
        HeaderText("総合", Modifier.width(40.dp), TextAlign.End)
        HeaderText("先発", Modifier.width(48.dp), TextAlign.End)
        HeaderText("中継", Modifier.width(48.dp), TextAlign.End)
        HeaderText("疲労", Modifier.width(32.dp), TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PitcherListRow(
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
        NumberValue(player.overall.toString(), 40, dimmed = blocked != null)
        AbilityValue(abilityCell(draft.pitchingAptitude(player.id).starter), 48, dimmed = blocked != null)
        AbilityValue(abilityCell(draft.pitchingAptitude(player.id).reliever), 48, dimmed = blocked != null)
        NumberValue(player.cells["HIROU"]?.text.orEmpty(), 32, dimmed = blocked != null)
    }
}
