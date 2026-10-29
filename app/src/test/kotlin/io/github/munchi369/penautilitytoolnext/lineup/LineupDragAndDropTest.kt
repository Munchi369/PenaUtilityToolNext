package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class LineupDragAndDropTest {
    @Test
    fun stationaryPointerKeepsHighlightAndDropAlignedWhenListScrollsBothWays() {
        SwingUtilities.invokeAndWait {
            var dropped: DropSpot? = null
            val drag = LineupDragState { _, spot -> dropped = spot }
            val highlighted = mutableMapOf<DropSpot, Boolean>()
            var scrollTo: ((Int) -> Unit)? = null
            val scene =
                ImageComposeScene(100, 100) {
                    val scroll = rememberScrollState()
                    SideEffect { scrollTo = { amount -> runBlocking { scroll.scrollTo(amount) } } }
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        repeat(20) { index ->
                            val spot = DropSpot.ListedPlayer(index)
                            val hovered = drag.hovered == spot
                            SideEffect { highlighted[spot] = hovered }
                            Box(Modifier.fillMaxWidth().height(20.dp).dropSpot(drag, spot))
                        }
                    }
                }
            try {
                repeat(3) { scene.render().close() }
                drag.start(DragItem.ListedPlayer(0), "player", Offset(50f, 50f))
                repeat(3) { scene.render().close() }
                assertEquals(DropSpot.ListedPlayer(2), highlighted.entries.single { it.value }.key)

                listOf(20 to 3, 0 to 2).forEach { (amount, expectedId) ->
                    requireNotNull(scrollTo).invoke(amount)
                    repeat(5) { scene.render().close() }
                    val expected = DropSpot.ListedPlayer(expectedId)
                    assertEquals(expected, drag.hovered)
                    assertEquals(expected, highlighted.entries.single { it.value }.key)
                }

                drag.end()
                repeat(3) { scene.render().close() }
                assertEquals(DropSpot.ListedPlayer(2), dropped)
                assertEquals(emptySet<DropSpot>(), highlighted.filterValues { it }.keys)
            } finally {
                scene.close()
            }
        }
    }
}
