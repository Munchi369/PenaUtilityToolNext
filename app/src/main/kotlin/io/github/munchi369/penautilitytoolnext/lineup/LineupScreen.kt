package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.home.HomeCard
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import io.github.munchi369.penautilitytoolnext.team.TeamIdentity
import io.github.munchi369.penautilitytoolnext.ui.TechnicalDetails

@Composable
fun LineupScreen(
    controller: LineupController,
    team: TeamIdentity? = null,
) {
    val state = controller.state
    val snapshot = state.snapshot
    val draft = state.draft
    val (tab, mode, selectedId) = controller.view
    if (state.phase != SaveDataLoadPhase.LOADED || snapshot == null || draft == null) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.message, style = MaterialTheme.typography.titleMedium)
            state.notice?.let { NoticeBar(it, controller::dismissNotice) }
            state.technicalDetails?.let { TechnicalDetails(it) }
        }
        return
    }
    val selected =
        snapshot.playerOrNull(selectedId)?.takeIf { it.isPitcher == (tab == LineupTab.PITCHERS) }
            ?: snapshot.playerOrNull(firstPlayer(draft, tab, mode))
    LineupEditor(
        controller = controller,
        state = state,
        snapshot = snapshot,
        team = team,
        draft = draft,
        tab = tab,
        mode = mode,
        selected = selected,
        onTab = controller::showTab,
        onMode = controller::showMode,
        onSelect = controller::select,
    )
}

@Composable
private fun LineupEditor(
    controller: LineupController,
    state: LineupViewState,
    snapshot: LineupSnapshot,
    team: TeamIdentity?,
    draft: LineupDraft,
    tab: LineupTab,
    mode: LineupMode,
    selected: LineupPlayer?,
    onTab: (LineupTab) -> Unit,
    onMode: (LineupMode) -> Unit,
    onSelect: (Int) -> Unit,
) {
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val editing = remember(snapshot) { LineupEditing(snapshot) }
    val drag = remember(controller) { LineupDragState(controller::drop) }
    val selectedId = selected?.id
    DisposableEffect(controller) {
        onDispose { controller.clearHighlights() }
    }
    val rowHighlight = ArrangementHighlight(state.highlightedSpots, 1f)
    CompositionLocalProvider(LocalArrangementHighlight provides rowHighlight) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                                controller.clearHighlights()
                            }
                        }
                    }
                }.onPreviewKeyEvent {
                    controller.clearHighlights()
                    false
                }.onGloballyPositioned { rootOrigin = it.positionInRoot() },
        ) {
            Column(Modifier.fillMaxSize().background(Color(0xFFF8F8FA)).padding(12.dp)) {
                LineupOverview(
                    teamNumber = snapshot.teamNumber,
                    team = team,
                    counts = editing.counts(draft),
                    contracts = LineupContracts(snapshot).counts(draft),
                    notices = snapshot.notices,
                )
                Spacer(Modifier.height(12.dp))
                HomeCard(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        LineupToolbar(
                            tab = tab,
                            mode = mode,
                            changes = editing.changeCount(draft),
                            saving = state.saving,
                            onTab = onTab,
                            onMode = onMode,
                            onAuto = { controller.autoArrange(tab, mode) },
                            autoCompleted = state.autoCompleted,
                            considerSubPositions = controller.view.considerSubPositions,
                            onSubPositions = controller::considerSubPositions,
                            onReset = { controller.reset(tab) },
                            onCopy = { controller.copyLineup(mode) },
                            onSave = controller::save,
                        )
                        Box(Modifier.fillMaxWidth().height(48.dp).verticalScroll(rememberScrollState())) {
                            state.notice?.let { NoticeBar(it, controller::dismissNotice) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            when (tab) {
                                LineupTab.FIELDERS -> {
                                    Column(
                                        Modifier
                                            .weight(1.15f)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .padding(end = 16.dp),
                                    ) {
                                        FielderLineup(controller, snapshot, draft, mode, state.pins, drag, selectedId, onSelect)
                                    }
                                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    PlayerListsColumn(
                                        drag,
                                        Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 8.dp),
                                    ) {
                                        FielderLists(snapshot, draft, editing, mode, drag, selectedId, onSelect)
                                    }
                                }

                                LineupTab.PITCHERS -> {
                                    Column(
                                        Modifier
                                            .weight(1.15f)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .padding(end = 16.dp),
                                    ) {
                                        PitchingRoles(controller, snapshot, draft, state.pins, drag, selectedId, onSelect)
                                    }
                                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    PlayerListsColumn(
                                        drag,
                                        Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 8.dp),
                                    ) {
                                        PitcherLists(snapshot, draft, editing, drag, selectedId, onSelect)
                                    }
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            if (selected != null) {
                                LineupPlayerPanel(
                                    player = selected,
                                    draft = draft,
                                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                                    onAptitude = { slot, value -> controller.setAptitude(selected.id, slot, value) },
                                    onMainPosition = { controller.setMainPosition(selected.id, it) },
                                    onPitchingAptitude = { role, value -> controller.setPitchingAptitude(selected.id, role, value) },
                                    onCancelAptitude = { controller.restoreAptitudeEdit(selected.id, it) },
                                )
                            } else {
                                Spacer(Modifier.width(300.dp))
                            }
                        }
                    }
                }
            }
            DragGhost(drag, rootOrigin)
        }
    }
}

@Composable
private fun PlayerListsColumn(
    drag: LineupDragState,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val edge = with(LocalDensity.current) { 48.dp.toPx() }
    val step = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(drag.item, drag.pointer, bounds) {
        if (drag.item == null || !bounds.contains(drag.pointer)) return@LaunchedEffect
        val amount =
            when {
                drag.pointer.y < bounds.top + edge -> -step
                drag.pointer.y > bounds.bottom - edge -> step
                else -> return@LaunchedEffect
            }
        while (true) {
            withFrameNanos { }
            if (scroll.scrollBy(amount) == 0f) break
        }
    }
    Column(
        modifier.onGloballyPositioned { bounds = it.boundsInRoot() }.verticalScroll(scroll),
        content = content,
    )
}

private fun firstPlayer(
    draft: LineupDraft,
    tab: LineupTab,
    mode: LineupMode,
): Int? =
    when (tab) {
        LineupTab.FIELDERS -> draft.lineup(mode).firstNotNullOfOrNull { it.playerId }
        LineupTab.PITCHERS -> (draft.starters + draft.relievers).firstNotNullOfOrNull { it }
    }
