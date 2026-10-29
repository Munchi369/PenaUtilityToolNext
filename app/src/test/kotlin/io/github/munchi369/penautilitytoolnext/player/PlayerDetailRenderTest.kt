package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.munchi369.penautilitytoolnext.rosterColorScheme
import io.github.munchi369.penautilitytoolnext.savedata.CopiedDerbyTestDatabase
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class PlayerDetailRenderTest {
    private var frameTime = System.nanoTime()

    @Test
    fun opensFromNameRendersEveryTabAndReturnsToScrolledRoster() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { copy ->
            val access = DerbySaveDatabaseAccess()
            val roster = (DerbyPlayerRosterLoader(access).load(copy.path) as PlayerRosterLoadResult.Loaded).roster
            val controller = PlayerDetailController(DerbyPlayerDetailLoader(access), Executor { it.run() }, Executor { it.run() })
            SwingUtilities.invokeAndWait {
                var options by mutableStateOf(PlayerRosterOptions.forTeam(6))
                val scene =
                    ImageComposeScene(1200, 760) {
                        val saved = rememberSaveableStateHolder()
                        MaterialTheme(colorScheme = rosterColorScheme) {
                            Surface(Modifier.fillMaxSize()) {
                                if (controller.state.player == null) {
                                    saved.SaveableStateProvider("roster") {
                                        PlayerRosterScreen(
                                            state = PlayerRosterViewState(phase = SaveDataLoadPhase.LOADED, roster = roster),
                                            options = options,
                                            onOptionsChange = { options = it },
                                            onOpenSettings = {},
                                            onOpenPlayer = { controller.open(copy.path, it, roster, options) },
                                        )
                                    }
                                } else {
                                    PlayerDetailScreen(controller, controller::close)
                                }
                            }
                        }
                    }
                try {
                    scene.settle()
                    scene.sendPointerEvent(PointerEventType.Press, Offset(1172f, 480f))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(1172f, 480f))
                    scene.sendPointerEvent(PointerEventType.Move, Offset(1100f, 32f))
                    scene.settle()
                    val before = scene.scrollbarPixels()
                    val name =
                        scene.nodes().first { node ->
                            val text =
                                node.config
                                    .getOrNull(SemanticsProperties.Text)
                                    ?.firstOrNull()
                                    ?.text
                            text in roster.players.map { it.name } && node.boundsInRoot.top > 170 && node.boundsInRoot.bottom < 720
                        }
                    scene.clickNode(name)
                    assertNotNull(controller.state.player)
                    assertNotNull(controller.state.data)
                    val profile =
                        requireNotNull(
                            controller.state.data
                                ?.profile
                                ?.value,
                        )
                    val texts =
                        scene.nodes().flatMap { node ->
                            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
                        }
                    assertTrue(profile.team in texts)
                    assertEquals(1, texts.count { it == "所属球団" })
                    assertTrue("#${profile.number}" in texts)
                    assertTrue(profile.name in texts)
                    assertEquals(1, texts.count { it == "ポジション" })
                    assertEquals(1, texts.count { it == "年齢" })
                    assertEquals(1, texts.count { it == "選手ID" })
                    val output = Path.of("build/player-detail-previews")
                    Files.createDirectories(output)
                    for (tab in PlayerDetailTab.entries) {
                        scene.click(tab.title)
                        assertEquals(tab, controller.options.tab)
                        scene.snapshot(output.resolve("${tab.name}.png"))
                    }
                    scene.click("成績")
                    assertTrue(scene.textNode("所属チーム").boundsInRoot.right <= scene.textNode("年度").boundsInRoot.left)
                    controller.updateOptions(controller.options.copy(pitching = false))
                    controller.updateStatsColumns(
                        controller.statsOptions.updateDisplay {
                            it.copy(visibleColumns = setOf("NUMBER", "DASEKI", "SIAI", "HONRUIDA"))
                        },
                    )
                    scene.settle()
                    val start = scene.textNode("打席").boundsInRoot.center
                    val end = scene.textNode("本塁").boundsInRoot.center + Offset(24f, 0f)
                    scene.sendPointerEvent(PointerEventType.Press, start)
                    repeat(10) { step ->
                        scene.sendPointerEvent(PointerEventType.Move, start + (end - start) * ((step + 1) / 10f))
                        scene.settle()
                    }
                    scene.sendPointerEvent(PointerEventType.Release, end)
                    scene.settle()
                    val order = controller.statsOptions.current.columnOrder
                    assertTrue(order.indexOf("DASEKI") > order.indexOf("HONRUIDA"))
                    val header = scene.textNode("本塁").boundsInRoot.center
                    scene.sendPointerEvent(PointerEventType.Press, header, button = PointerButton.Secondary)
                    scene.sendPointerEvent(PointerEventType.Release, header, button = PointerButton.Secondary)
                    scene.settle()
                    scene.click("この列を非表示")
                    assertFalse("HONRUIDA" in controller.statsOptions.current.visibleColumns)
                    scene.click("表示設定")
                    assertNotNull(scene.textNode("表示項目・列順は現役選手一覧と共通"))
                    scene.click("すべて表示")
                    assertTrue("HONRUIDA" in controller.statsOptions.current.visibleColumns)
                    assertEquals(order, controller.statsOptions.current.columnOrder)
                    scene.click("標準に戻す")
                    assertEquals(emptyList<String>(), controller.statsOptions.current.columnOrder)
                    scene.snapshot(output.resolve("DISPLAY_SETTINGS.png"))
                    scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 20f))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(20f, 20f))
                    scene.settle()
                    scene.click("年度別・通算 ▾")
                    scene.click("月別")
                    assertEquals(DetailStatsPeriod.MONTHS, controller.options.period)
                    scene.snapshot(output.resolve("MONTHLY.png"))
                    scene.click("表示設定")
                    assertTrue(
                        scene.nodes().any {
                            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "標準に戻す" } ==
                                true
                        },
                    )
                    scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 20f))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(20f, 20f))
                    scene.settle()
                    scene.click("← 一覧へ戻る")
                    assertNull(controller.state.player)
                    scene.sendPointerEvent(PointerEventType.Move, Offset(1100f, 32f))
                    scene.settle()
                    scene.snapshot(output.resolve("RETURNED_ROSTER.png"))
                    assertArrayEquals(before, scene.scrollbarPixels())
                } finally {
                    scene.close()
                }
            }
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

    private fun ImageComposeScene.textNode(text: String): SemanticsNode =
        nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true }

    private fun ImageComposeScene.click(text: String) {
        clickNode(
            nodes().firstOrNull {
                it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true ||
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
            }
                ?: error("Missing text $text"),
        )
    }

    private fun ImageComposeScene.clickNode(node: SemanticsNode) {
        val center = node.boundsInRoot.center
        sendPointerEvent(PointerEventType.Press, center)
        sendPointerEvent(PointerEventType.Release, center)
        settle()
    }

    private fun ImageComposeScene.settle() {
        repeat(4) {
            frameTime += 100_000_000L
            render(frameTime).close()
        }
    }

    private fun ImageComposeScene.snapshot(path: Path) {
        render(frameTime).use { image -> image.encodeToData()!!.use { Files.write(path, it.bytes) } }
    }

    private fun ImageComposeScene.scrollbarPixels(): IntArray =
        render(frameTime).use { image ->
            image.encodeToData()!!.use { data ->
                val raster = ImageIO.read(ByteArrayInputStream(data.bytes))
                IntArray(10 * (736 - 169)) { offset -> raster.getRGB(1167 + offset % 10, 169 + offset / 10) }
            }
        }
}
