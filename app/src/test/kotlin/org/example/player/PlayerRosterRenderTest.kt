package org.example.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.example.rosterColorScheme
import org.example.savedata.CopiedDerbyTestDatabase
import org.example.savedata.DerbySaveDatabaseAccess
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class PlayerRosterRenderTest {
    @Test
    fun sortingKeepsTheVerticalScrollbarThumbInPlace() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val roster = (DerbyPlayerRosterLoader(DerbySaveDatabaseAccess()).load(db.path) as PlayerRosterLoadResult.Loaded).roster
            SwingUtilities.invokeAndWait {
                for (grouping in RosterGrouping.entries) {
                    var options by mutableStateOf(PlayerRosterOptions.forTeam(6).copy(grouping = grouping))
                    val scene =
                        ImageComposeScene(1200, 760) {
                            MaterialTheme(colorScheme = rosterColorScheme) {
                                Surface(Modifier.fillMaxSize()) {
                                    PlayerRosterScreen(
                                        PlayerRosterViewState(phase = PlayerRosterPhase.LOADED, roster = roster),
                                        options,
                                        { options = it },
                                        {},
                                    )
                                }
                            }
                        }
                    try {
                        repeat(3) { scene.render().close() }
                        scene.sendPointerEvent(PointerEventType.Press, Offset(1172f, 220f))
                        scene.sendPointerEvent(PointerEventType.Move, Offset(1172f, 480f))
                        scene.sendPointerEvent(PointerEventType.Release, Offset(1172f, 480f))
                        repeat(3) { scene.render().close() }
                        val beforeSort = scrollbarPixels(scene)

                        scene.click("選手ID")
                        repeat(3) { scene.render().close() }

                        assertEquals("ID", options.current.sortKey)
                        assertEquals(true, options.current.descending)
                        assertArrayEquals(beforeSort, scrollbarPixels(scene), grouping.name)

                        scene.click("選手ID")
                        repeat(3) { scene.render().close() }

                        assertEquals(false, options.current.descending)
                        assertArrayEquals(beforeSort, scrollbarPixels(scene), grouping.name)
                    } finally {
                        scene.close()
                    }
                }
            }
        }
    }

    @Test
    fun allDisplaysRenderAndTabsSwitchAtDesktopSize() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val roster = (DerbyPlayerRosterLoader(DerbySaveDatabaseAccess()).load(db.path) as PlayerRosterLoadResult.Loaded).roster
            SwingUtilities.invokeAndWait {
                var options by mutableStateOf(PlayerRosterOptions.forTeam(6))
                val scene =
                    ImageComposeScene(1200, 760) {
                        MaterialTheme(colorScheme = rosterColorScheme) {
                            Surface(Modifier.fillMaxSize()) {
                                PlayerRosterScreen(
                                    PlayerRosterViewState(phase = PlayerRosterPhase.LOADED, roster = roster),
                                    options,
                                    { options = it },
                                    {},
                                )
                            }
                        }
                    }
                try {
                    val output = Path.of("build/roster-previews")
                    Files.createDirectories(output)
                    for (display in RosterDisplay.entries) {
                        options = options.copy(display = display)
                        repeat(3) { scene.render().close() }
                        scene.render().use { image ->
                            image.encodeToData()!!.use { data -> Files.write(output.resolve("${display.name}.png"), data.bytes) }
                        }
                    }
                    scene.sendPointerEvent(PointerEventType.Press, Offset(400f, 742f))
                    scene.render().close()
                    scene.sendPointerEvent(PointerEventType.Move, Offset(1040f, 742f))
                    scene.render().close()
                    scene.sendPointerEvent(PointerEventType.Release, Offset(1040f, 742f))
                    repeat(3) { scene.render().close() }
                    scene.render().use { image ->
                        image.encodeToData()!!.use { data -> Files.write(output.resolve("SCROLLED.png"), data.bytes) }
                    }
                    scene.sendPointerEvent(PointerEventType.Press, Offset(64f, 82f))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(64f, 82f))
                    scene.render().close()
                    assertEquals(RosterDisplay.BASIC, options.display)
                    options = options.selectTeamScope(RosterTeamScope.Major).copy(display = RosterDisplay.BATTING_STATS)
                    repeat(3) { scene.render().close() }
                    scene.render().use { image ->
                        image.encodeToData()!!.use { data -> Files.write(output.resolve("MAJOR.png"), data.bytes) }
                    }
                } finally {
                    scene.close()
                }
            }
        }
    }

    private fun scrollbarPixels(scene: ImageComposeScene): IntArray =
        scene.render().use { image ->
            image.encodeToData()!!.use { data ->
                val raster = ImageIO.read(ByteArrayInputStream(data.bytes))
                IntArray((1177 - 1167) * (736 - 169)) { offset ->
                    val width = 1177 - 1167
                    raster.getRGB(1167 + offset % width, 169 + offset / width)
                }
            }
        }

    private fun ImageComposeScene.click(label: String) {
        val nodes = semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        val node =
            nodes.singleOrNull {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.startsWith(label) } == true
            } ?: error(
                "Semantics text '$label' not found. Available: " +
                    nodes.mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString { text -> text.text } },
            )
        sendPointerEvent(PointerEventType.Press, node.boundsInWindow.center)
        sendPointerEvent(PointerEventType.Release, node.boundsInWindow.center)
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
}
