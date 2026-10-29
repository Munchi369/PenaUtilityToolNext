package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.player.RosterColumns
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import kotlin.math.roundToInt

private val fielderAbilityKeys =
    listOf("KOUDA", "POWER", "SOURYOKU", "KENRYOKU", "HOKYUU", "SOUKYUU", "SENKYUU", "KOWAZA", "TAIRYOKU", "RIDO", "TOURUIGIJUTU")
private val pitcherAbilityKeys = listOf("MAXMAX", "SEIKYUU", "SUTAMINA", "KAIHUKU", "SEISIN", "QUICK")
private val abilityTitles = (RosterColumns.battingAbility + RosterColumns.pitchingAbility).associate { it.key to it.title }

@Composable
internal fun LineupPlayerPanel(
    player: LineupPlayer,
    draft: LineupDraft,
    modifier: Modifier,
    onAptitude: (FieldSlot, Int) -> Unit,
    onMainPosition: (FieldSlot) -> Unit,
    onPitchingAptitude: (PitchingRole, Int) -> Unit,
    onCancelAptitude: (LineupPlayerState) -> Unit,
) {
    var aptitudeBeforeEdit by remember(player.id) { mutableStateOf<LineupPlayerState?>(null) }
    val editingAptitude = aptitudeBeforeEdit != null
    Column(modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 4.dp, top = 4.dp)) {
        Text(player.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(
                "#${draft.contract(player.id).uniformNumber}",
                draft.mainPosition(player.id).displayName,
                player.cells["THROWS_BATS"]?.text,
                "${player.age}歳",
                if (draft.squad(player.id) == PlayerSquad.FIRST) "1軍" else "2軍",
            ).joinToString("　"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val resting =
            player.restDays.takeIf {
                it > 0 &&
                    player.initialSquad == PlayerSquad.SECOND &&
                    draft.squad(player.id) == PlayerSquad.SECOND
            }
        val pitching = draft.pitchingAptitude(player.id)
        val mismatch =
            when {
                player.id in draft.starters && pitching.reliever > pitching.starter -> "中継型"
                player.id in draft.relievers && pitching.starter > pitching.reliever -> "先発型"
                else -> null
            }
        FlowRow(
            Modifier.padding(top = 6.dp).heightIn(min = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlayerBadges(player, draft, blocked = resting?.let { "休止 ${it}日" }, extraBadge = mismatch)
        }

        PanelSection(if (player.isPitcher) "投手能力" else "野手能力")
        val keys = if (player.isPitcher) pitcherAbilityKeys else fielderAbilityKeys
        val items =
            listOf<Pair<String, PlayerCell?>>("総合" to PlayerCell(player.overall.toString())) +
                keys.map { abilityTitles.getValue(it) to player.cells[it] } +
                listOf("疲労" to player.cells["HIROU"])
        AbilityGrid(items)

        if (player.isPitcher) {
            KeyValue("先発時スタミナ", player.cells["READY"], Modifier.fillMaxWidth())
            PanelSection("球種")
            (1..5).forEach { index ->
                val cell = player.cells["PITCH$index"]
                val name = cell?.text?.substringBefore(' ')?.takeIf(String::isNotEmpty)
                val amount =
                    cell?.let {
                        it.copy(
                            text = it.text.substringAfter(' ', ""),
                            abilityStyle = it.abilityStyle?.copy(colorStart = 0),
                        )
                    }
                KeyValue(name ?: "（なし）", amount?.takeIf { name != null }, Modifier.fillMaxWidth())
            }
            PanelSection("起用適性") {
                EditToggle(
                    editing = editingAptitude,
                    onEdit = { aptitudeBeforeEdit = draft.state(player.id) },
                    onDone = { aptitudeBeforeEdit = null },
                    onCancel = {
                        aptitudeBeforeEdit?.let(onCancelAptitude)
                        aptitudeBeforeEdit = null
                    },
                )
            }
            val aptitude = draft.pitchingAptitude(player.id)
            PitchingRole.entries.forEach { role ->
                AptitudeBar(
                    label = role.label,
                    labelWidth = 48,
                    main = aptitude.starterType == (role == PitchingRole.STARTER),
                    value = aptitude.of(role),
                    initial = player.initialPitchingAptitude.of(role),
                    editing = editingAptitude,
                    onChange = { onPitchingAptitude(role, it) },
                )
            }
        } else {
            PanelSection("守備適性") {
                EditToggle(
                    editing = editingAptitude,
                    onEdit = { aptitudeBeforeEdit = draft.state(player.id) },
                    onDone = { aptitudeBeforeEdit = null },
                    onCancel = {
                        aptitudeBeforeEdit?.let(onCancelAptitude)
                        aptitudeBeforeEdit = null
                    },
                )
            }
            FieldSlot.defense.forEach { slot ->
                val main = draft.isMain(player.id, slot)
                val value = draft.aptitude(player.id, slot)
                AptitudeBar(
                    label = slot.shortLabel,
                    labelWidth = 28,
                    main = main,
                    barColor =
                        if (main) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color(0xFF465A73)
                        },
                    value = value,
                    initial = player.initialAptitudes[slot] ?: 0,
                    editing = editingAptitude,
                    onLabelClick = { onMainPosition(slot) },
                    onChange = { onAptitude(slot, it) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EditToggle(
    editing: Boolean,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    Row {
        if (editing) {
            Text(
                "キャンセル",
                Modifier.clip(RoundedCornerShape(5.dp)).clickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (editing) "完了" else "編集",
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .clickable(onClick = if (editing) onDone else onEdit)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (editing) FontWeight.SemiBold else FontWeight.Normal,
            color = if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PanelSection(
    title: String,
    action: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action()
    }
}

@Composable
private fun AbilityGrid(items: List<Pair<String, PlayerCell?>>) {
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            row.forEach { (label, cell) -> KeyValue(label, cell, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyValue(
    label: String,
    cell: PlayerCell?,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AbilityValue(cell, 64)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AptitudeBar(
    label: String,
    labelWidth: Int,
    main: Boolean,
    value: Int,
    initial: Int,
    editing: Boolean,
    barColor: Color = MaterialTheme.colorScheme.primary,
    onLabelClick: (() -> Unit)? = null,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        val selectable = editing && onLabelClick != null
        Box(Modifier.width(labelWidth.dp)) {
            Text(
                label,
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(if (selectable && main) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                    .then(if (selectable) Modifier.clickable(onClick = onLabelClick) else Modifier)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (main) FontWeight.Bold else FontWeight.Normal,
                color =
                    when {
                        selectable && main -> MaterialTheme.colorScheme.onPrimary
                        main -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        if (editing) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).height(28.dp),
                colors =
                    SliderDefaults.colors(
                        thumbColor = barColor,
                        activeTrackColor = barColor,
                    ),
            )
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.fillMaxWidth(value / 100f).height(16.dp).background(barColor))
            }
        }
        Text(
            buildAnnotatedString {
                if (value != initial) {
                    withStyle(
                        SpanStyle(color = MaterialTheme.colorScheme.outline, textDecoration = TextDecoration.LineThrough),
                    ) { append("$initial ") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("$value") }
                } else {
                    append("$value")
                }
            },
            Modifier.width(64.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}
