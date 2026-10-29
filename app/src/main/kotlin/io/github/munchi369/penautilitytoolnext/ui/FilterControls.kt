package io.github.munchi369.penautilitytoolnext.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun FilterChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showSelectionMark: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface)
            .selectable(selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (showSelectionMark) {
            Text(if (selected) "✓" else "", Modifier.width(12.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun FilterPopup(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Popup(
        popupPositionProvider = FilterPopupPosition,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val density = LocalDensity.current
        val windowHeightPx = LocalWindowInfo.current.containerSize.height
        Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Box(
                if (windowHeightPx > 0) {
                    Modifier.heightIn(max = with(density) { windowHeightPx.toDp() })
                } else {
                    Modifier
                },
            ) {
                content()
            }
        }
    }
}

private object FilterPopupPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            anchorBounds.bottom.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
}

internal enum class FilterIcon { FILTER, SLIDERS, CLOSE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilterIconButton(
    label: String,
    icon: FilterIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TooltipArea(tooltip = {
        Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 4.dp) {
            Text(label, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
    }) {
        IconButton(
            onClick = onClick,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                    .semantics { contentDescription = label },
        ) {
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(18.dp)) {
                fun line(
                    x1: Float,
                    y1: Float,
                    x2: Float,
                    y2: Float,
                ) {
                    drawLine(
                        color,
                        Offset(size.width * x1, size.height * y1),
                        Offset(size.width * x2, size.height * y2),
                        1.5.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
                when (icon) {
                    FilterIcon.FILTER -> {
                        line(0.1f, 0.25f, 0.9f, 0.25f)
                        line(0.25f, 0.5f, 0.75f, 0.5f)
                        line(0.4f, 0.75f, 0.6f, 0.75f)
                    }
                    FilterIcon.SLIDERS -> {
                        listOf(0.25f, 0.5f, 0.75f).forEachIndexed { index, y ->
                            line(0.1f, y, 0.9f, y)
                            val x = if (index == 1) 0.65f else 0.35f
                            line(x, y - 0.1f, x, y + 0.1f)
                        }
                    }
                    FilterIcon.CLOSE -> {
                        line(0.3f, 0.3f, 0.7f, 0.7f)
                        line(0.7f, 0.3f, 0.3f, 0.7f)
                    }
                }
            }
        }
    }
}
