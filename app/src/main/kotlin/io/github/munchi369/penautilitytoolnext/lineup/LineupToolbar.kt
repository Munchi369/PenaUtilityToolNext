package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.ui.TechnicalDetails

@Composable
internal fun LineupToolbar(
    tab: LineupTab,
    mode: LineupMode,
    changes: Int,
    saving: Boolean,
    onTab: (LineupTab) -> Unit,
    onMode: (LineupMode) -> Unit,
    onAuto: () -> Unit,
    autoCompleted: Boolean,
    considerSubPositions: Boolean,
    onSubPositions: (Boolean) -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LineupTab.entries.forEach { entry -> Pill(entry.label, entry == tab) { onTab(entry) } }
        }
        if (tab == LineupTab.FIELDERS) {
            Spacer(Modifier.width(16.dp))
            Row(
                Modifier.clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surface).padding(2.dp),
            ) {
                LineupMode.entries.forEach { entry ->
                    val on = entry == mode
                    Text(
                        entry.label,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { onMode(entry) }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val other = LineupMode.entries.first { it != mode }
            ToolbarButton("${other.label}へコピー", enabled = !saving, onClick = onCopy)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (changes > 0) {
                Text("変更 $changes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
            }
            if (tab == LineupTab.FIELDERS) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .toggleable(value = considerSubPositions, enabled = !saving, role = Role.Checkbox, onValueChange = onSubPositions)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = considerSubPositions, onCheckedChange = null, enabled = !saving, modifier = Modifier.size(16.dp))
                    Text("サブ考慮", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ToolbarButton("自動並び替え", enabled = !saving, completion = autoCompleted, onClick = onAuto)
            ToolbarButton("元に戻す", enabled = !saving && changes > 0, onClick = onReset)
            ToolbarButton(if (saving) "保存中" else "保存", enabled = !saving && changes > 0, primary = true, onClick = onSave)
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    enabled: Boolean,
    primary: Boolean = false,
    completion: Boolean? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .then(
                    if (primary && enabled) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    },
                ).clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            color =
                when {
                    !enabled -> MaterialTheme.colorScheme.outline
                    primary -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
        if (completion != null) {
            Text(
                "✓",
                Modifier.width(18.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelMedium,
                color = if (completion) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
        }
    }
}

@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier =
            Modifier
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
internal fun NoticeBar(
    notice: LineupNotice,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (notice.error) LineupColors.warningBackground else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                notice.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (notice.error) LineupColors.warning else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            notice.technicalDetails?.let { TechnicalDetails(it) }
        }
        Text(
            "✕",
            Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
