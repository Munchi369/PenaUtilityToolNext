package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeSegmentedControl(
    labels: List<String>,
    selected: Int,
    dense: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .selectableGroup()
            .padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val background = if (index == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            val foreground =
                if (index == selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                label,
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(background)
                    .selectable(selected = index == selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = if (dense) 4.dp else 12.dp, vertical = 5.dp),
                color = foreground,
                fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun HomeViewTabs(
    labels: List<String>,
    selected: Int,
    dense: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.selectableGroup()) {
        labels.forEachIndexed { index, label ->
            Text(
                label,
                Modifier
                    .drawBehind {
                        if (index == selected) {
                            drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                        }
                    }.selectable(selected = index == selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = if (dense) 8.dp else 12.dp, vertical = 8.dp),
                color = if (index == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (index == selected) FontWeight.Medium else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
