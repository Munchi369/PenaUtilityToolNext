package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.rosterColorScheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class RosterTableInteractionTest {
    @Test
    fun resizingAndFittingKeepWidthsPerTabAndResetToDefaults() {
        SwingUtilities.invokeAndWait {
            TableScene().use { ui ->
                val initial = ui.bounds("選手ID").width
                ui.resize("選手ID", 70f)
                val resized = ui.bounds("選手ID").width
                assertTrue(resized > initial + 30f)
                assertTrue(requireNotNull(ui.options.current.columnWidths["ID"]) > initial + 30f)
                assertEquals("SOUGOU", ui.options.current.sortKey)

                ui.options = ui.options.copy(display = RosterDisplay.BATTING_ABILITY)
                ui.settle()
                val otherWidths = ui.options.current.columnWidths
                assertTrue(otherWidths.isEmpty())
                ui.options = ui.options.copy(display = RosterDisplay.BASIC)
                ui.settle()
                assertEquals(resized, ui.bounds("選手ID").width, 1f)

                ui.doubleClickEdge("選手ID")
                assertTrue(ui.bounds("選手ID").width < resized - 10f)
                ui.options = ui.options.resizeColumns(emptyMap())
                ui.settle()
                assertEquals(initial, ui.bounds("選手ID").width, 1f)

                ui.resize("名前", 80f)
                assertTrue(ui.bounds("名前").width > 200f)
                ui.options = ui.options.resizeColumns(emptyMap())
                ui.settle()
                assertEquals(176f, ui.bounds("名前").width, 1f)
            }
        }
    }

    @Test
    fun selectionFollowsThePlayerThroughSortingAndNameStillOpensDetails() {
        SwingUtilities.invokeAndWait {
            TableScene().use { ui ->
                ui.click("101")
                assertNull(ui.openedPlayer)
                assertTrue(ui.selectedTexts().contains("選手A"))
                assertFalse(ui.selectedTexts().contains("選手B"))
                ui.click("選手ID")
                assertTrue(ui.selectedTexts().contains("選手A"))
                ui.click("102")
                assertFalse(ui.selectedTexts().contains("選手A"))
                assertTrue(ui.selectedTexts().contains("選手B"))
                ui.click("選手B")
                assertEquals(102, ui.openedPlayer)
                ui.options = ui.options.copy(search = "選手A")
                ui.settle()
                assertTrue(ui.selectedTexts().isEmpty(), ui.selectedTexts().toString())
            }
        }
    }

    private class TableScene : AutoCloseable {
        var options by mutableStateOf(
            PlayerRosterOptions.forTeam(1).copy(grouping = RosterGrouping.NONE).updateDisplay {
                it.copy(visibleColumns = setOf("ID", "AGE"))
            },
        )
        var openedPlayer: Int? = null
        private var frameTime = System.nanoTime()
        private val roster =
            PlayerRoster(
                listOf(player(101, "選手A", 22), player(102, "選手B", 32)),
                emptyList(),
            )
        private val scene =
            ImageComposeScene(720, 480) {
                MaterialTheme(colorScheme = rosterColorScheme) {
                    RosterTable(
                        options.players(roster),
                        emptyMap(),
                        options.orderedColumns().filter { it.key in options.current.visibleColumns },
                        options,
                        null,
                        { options = it },
                        Modifier.fillMaxSize(),
                        { openedPlayer = it.playerId },
                    )
                }
            }

        fun settle() {
            repeat(6) {
                frameTime += 100_000_000L
                scene.render(frameTime).close()
            }
        }

        fun bounds(label: String): Rect {
            settle()
            val node =
                nodes().first { node ->
                    node.hasText(label) && node.boundsInWindow.width > 0 && node.boundsInWindow.height > 0
                }
            return node.boundsInWindow
        }

        fun click(label: String) {
            val center = bounds(label).center
            scene.sendPointerEvent(PointerEventType.Press, center, timeMillis = frameTime / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, center, timeMillis = frameTime / 1_000_000 + 20)
            settle()
        }

        fun resize(
            label: String,
            amount: Float,
        ) {
            val header = bounds(label)
            val start = Offset(header.right, header.center.y)
            scene.sendPointerEvent(PointerEventType.Press, start)
            repeat(10) { step ->
                scene.sendPointerEvent(PointerEventType.Move, start + Offset(amount * (step + 1) / 10f, 0f))
                settle()
            }
            scene.sendPointerEvent(PointerEventType.Release, start + Offset(amount, 0f))
            settle()
        }

        fun doubleClickEdge(label: String) {
            val header = bounds(label)
            val point = Offset(header.right, header.center.y)
            val time = frameTime / 1_000_000
            repeat(2) { index ->
                scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time + index * 100)
                scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time + index * 100 + 20)
            }
            settle()
        }

        fun selectedTexts(): List<String> =
            nodes()
                .filter {
                    it.config.getOrNull(SemanticsProperties.Selected) == true &&
                        !it.config.contains(SemanticsProperties.CollectionItemInfo) &&
                        it.boundsInWindow.width > 0 &&
                        it.boundsInWindow.height > 0
                }.flatMap { node -> descendants(node) }
                .flatMap { node ->
                    node.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .map { text -> text.text }
                }

        private fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }

        private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

        private fun SemanticsNode.hasText(label: String): Boolean =
            config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(label) } == true

        override fun close() = scene.close()
    }
}

private fun player(
    id: Int,
    name: String,
    age: Int,
): PlayerRosterPlayer =
    PlayerRosterPlayer(
        id,
        id,
        name,
        500,
        PlayerPosition.CATCHER,
        age,
        PlayerSquad.FIRST,
        PlayerRegistration.CONTROLLED,
        PlayerInjury.None,
        teamNumber = 1,
    )
