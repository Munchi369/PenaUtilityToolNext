package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/** In-window drag and drop; OS drag and drop is intentionally not used. Positions are in root coordinates. */
internal class LineupDragState(
    private val onDrop: (DragItem, DropSpot) -> Unit,
) {
    var item by mutableStateOf<DragItem?>(null)
        private set
    var label by mutableStateOf("")
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    private val targets = mutableStateMapOf<DropSpot, Rect>()

    val hovered: DropSpot?
        get() {
            if (item == null) return null
            val position = pointer
            return targets.entries
                .filter { it.value.contains(position) }
                .minByOrNull { it.value.width * it.value.height }
                ?.key
        }

    fun register(
        spot: DropSpot,
        bounds: Rect,
    ) {
        targets[spot] = bounds
    }

    fun unregister(spot: DropSpot) {
        targets.remove(spot)
    }

    fun start(
        dragged: DragItem,
        text: String,
        position: Offset,
    ) {
        item = dragged
        label = text
        pointer = position
    }

    fun dragTo(position: Offset) {
        pointer = position
    }

    fun end() {
        val dragged = item
        val spot = hovered
        item = null
        if (dragged != null && spot != null) onDrop(dragged, spot)
    }

    fun cancel() {
        item = null
    }
}

internal fun Modifier.dragSource(
    state: LineupDragState,
    item: DragItem,
    label: String,
    enabled: Boolean = true,
): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val origin = remember { arrayOf(Offset.Zero) }
            onGloballyPositioned { origin[0] = it.positionInRoot() }
                .pointerInput(item, label) {
                    detectDragGestures(
                        onDragStart = { state.start(item, label, origin[0] + it) },
                        onDragEnd = state::end,
                        onDragCancel = state::cancel,
                        onDrag = { change, _ ->
                            change.consume()
                            state.dragTo(origin[0] + change.position)
                        },
                    )
                }
        }
    }

internal fun Modifier.dropSpot(
    state: LineupDragState,
    spot: DropSpot,
): Modifier =
    composed {
        DisposableEffect(state, spot) { onDispose { state.unregister(spot) } }
        onGloballyPositioned { state.register(spot, it.boundsInRoot()) }
    }
