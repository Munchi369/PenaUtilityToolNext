package io.github.munchi369.penautilitytoolnext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.munchi369.penautilitytoolnext.team.TeamIdentity
import kotlinx.coroutines.delay

@Composable
internal fun AppNavigation(
    destination: AppDestination,
    playersEnabled: Boolean,
    team: TeamIdentity?,
    teamStatus: String,
    onPlayers: () -> Unit,
    onSettings: () -> Unit,
    onLineup: () -> Unit = {},
    onHome: () -> Unit = {},
) {
    Surface(
        color = appShellBackground,
        modifier = Modifier.width(60.dp).fillMaxHeight(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 16.dp)) {
            TeamSettings(team, teamStatus, onSettings)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                NavigationMenu(
                    label = "ホーム",
                    actions = listOf(NavigationAction("ホーム", destination == AppDestination.HOME, playersEnabled, onHome)),
                ) {
                    HomeGlyph(androidx.compose.material3.LocalContentColor.current)
                }
                NavigationMenu(
                    label = "現役選手一覧",
                    actions = listOf(NavigationAction("現役選手一覧", destination == AppDestination.PLAYERS, playersEnabled, onPlayers)),
                ) {
                    NavigationGlyph(lineup = false, color = androidx.compose.material3.LocalContentColor.current)
                }
                NavigationMenu(
                    label = "起用",
                    actions = listOf(NavigationAction("起用", destination == AppDestination.LINEUP, playersEnabled, onLineup)),
                ) {
                    NavigationGlyph(lineup = true, color = androidx.compose.material3.LocalContentColor.current)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamSettings(
    team: TeamIdentity?,
    teamStatus: String,
    onSettings: () -> Unit,
) {
    val teamColor = team?.let { Color(0xFF000000L or it.rgb.toLong()) }
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 4.dp) {
                Text("${team?.name ?: teamStatus}：設定を開く", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
            }
        },
    ) {
        TextButton(
            onClick = onSettings,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.size(40.dp).semantics { contentDescription = "設定を開く" },
        ) {
            Spacer(Modifier.size(12.dp).clip(CircleShape).background(teamColor ?: MaterialTheme.colorScheme.outline))
        }
    }
}

internal data class NavigationAction(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NavigationMenu(
    label: String,
    actions: List<NavigationAction>,
    icon: @Composable () -> Unit,
) {
    require(actions.isNotEmpty())
    var anchorHovered by remember { mutableStateOf(false) }
    var menuHovered by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var openedByClick by remember { mutableStateOf(false) }
    val enabled = actions.any { it.enabled }
    val selected = actions.any { it.selected }

    LaunchedEffect(anchorHovered, menuHovered, openedByClick) {
        if (anchorHovered || menuHovered) {
            open = true
        } else if (!openedByClick) {
            delay(160)
            open = false
        }
    }

    Box(
        Modifier
            .onPointerEvent(PointerEventType.Enter) { anchorHovered = true }
            .onPointerEvent(PointerEventType.Exit) { anchorHovered = false },
    ) {
        TextButton(
            onClick = {
                if (actions.size == 1) {
                    open = false
                    actions.single().onClick()
                } else {
                    openedByClick = true
                    open = true
                }
            },
            enabled = enabled,
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(8.dp),
            colors =
                ButtonDefaults.textButtonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            modifier =
                Modifier.size(40.dp).semantics {
                    contentDescription = label
                    this.selected = selected
                },
        ) {
            icon()
        }
        if (open) {
            Popup(
                popupPositionProvider = NavigationMenuPosition,
                onDismissRequest = {
                    open = false
                    openedByClick = false
                    menuHovered = false
                },
                properties = PopupProperties(focusable = openedByClick),
            ) {
                Box(
                    Modifier
                        .onPointerEvent(PointerEventType.Enter) { menuHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { menuHovered = false }
                        .padding(start = 10.dp),
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp) {
                        Column(
                            Modifier
                                .width(220.dp)
                                .height(48.dp * actions.size.coerceAtMost(6))
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Top,
                        ) {
                            actions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    enabled = action.enabled,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (action.selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                            ).semantics { this.selected = action.selected },
                                    onClick = {
                                        open = false
                                        openedByClick = false
                                        menuHovered = false
                                        action.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private object NavigationMenuPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            anchorBounds.right.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            anchorBounds.top.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
}

@Composable
private fun NavigationGlyph(
    lineup: Boolean,
    color: Color,
) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.4.dp.toPx()
        if (lineup) {
            val corners =
                listOf(
                    Offset(size.width / 2, size.height * 0.12f),
                    Offset(size.width * 0.88f, size.height / 2),
                    Offset(size.width / 2, size.height * 0.88f),
                    Offset(size.width * 0.12f, size.height / 2),
                )
            corners.indices.forEach { drawLine(color, corners[it], corners[(it + 1) % corners.size], stroke) }
            drawCircle(color, radius = stroke, center = center)
        } else {
            repeat(3) { index ->
                val y = size.height * (0.25f + index * 0.25f)
                drawCircle(color, radius = stroke / 2, center = Offset(size.width * 0.15f, y))
                drawLine(color, Offset(size.width * 0.35f, y), Offset(size.width * 0.9f, y), stroke)
            }
        }
    }
}

@Composable
private fun HomeGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val points =
            listOf(
                Offset(size.width * 0.12f, size.height * 0.12f),
                Offset(size.width * 0.88f, size.height * 0.12f),
                Offset(size.width * 0.88f, size.height * 0.5f),
                Offset(size.width * 0.5f, size.height * 0.88f),
                Offset(size.width * 0.12f, size.height * 0.5f),
            )
        points.indices.forEach { drawLine(color, points[it], points[(it + 1) % points.size], 1.4.dp.toPx()) }
    }
}
