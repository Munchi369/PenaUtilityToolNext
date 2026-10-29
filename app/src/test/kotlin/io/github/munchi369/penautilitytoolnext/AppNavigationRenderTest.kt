package io.github.munchi369.penautilitytoolnext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.team.TeamIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class AppNavigationRenderTest {
    @Test
    fun hoverMenuAllowsMovingToMultipleActionsAndKeepsDisabledActionsUnavailable() {
        SwingUtilities.invokeAndWait {
            var firstClicks = 0
            var secondClicks = 0
            var disabledClicks = 0
            val scene =
                ImageComposeScene(480, 300) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Box(Modifier.padding(10.dp)) {
                            NavigationMenu(
                                label = "チーム",
                                actions =
                                    listOf(
                                        NavigationAction("現役選手一覧", true, true) { firstClicks++ },
                                        NavigationAction("起用", false, true) { secondClicks++ },
                                        NavigationAction("利用不可", false, false) { disabledClicks++ },
                                    ),
                            ) { Text("T") }
                        }
                    }
                }
            try {
                fun render() {
                    repeat(3) { scene.render().close() }
                }

                fun move(
                    x: Float,
                    y: Float,
                ) {
                    scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))
                    render()
                }

                fun click(
                    x: Float,
                    y: Float,
                ) {
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                    render()
                }

                render()
                move(30f, 30f)
                move(55f, 30f)
                move(100f, 80f)
                val output = Path.of("build/navigation-previews")
                Files.createDirectories(output)
                scene.render().use { image ->
                    image.encodeToData()!!.use { Files.write(output.resolve("sidebar-hover-menu.png"), it.bytes) }
                }
                click(100f, 80f)
                assertEquals(1, secondClicks)
                assertEquals(0, firstClicks)
                move(300f, 200f)
                move(30f, 30f)
                move(100f, 130f)
                click(100f, 130f)
                assertEquals(0, disabledClicks)
                move(300f, 200f)
                click(30f, 30f)
                click(100f, 30f)
                assertEquals(1, firstClicks)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun iconNavigationKeepsFixedWidthAndActions() {
        SwingUtilities.invokeAndWait {
            listOf(true, false).forEach { available ->
                var contentLeft = 0f
                var settingsClicks = 0
                var playersClicks = 0
                var lineupClicks = 0
                val scene =
                    ImageComposeScene(480, 300) {
                        MaterialTheme(colorScheme = rosterColorScheme) {
                            Row {
                                AppNavigation(
                                    destination = AppDestination.LINEUP,
                                    playersEnabled = available,
                                    team = if (available) TeamIdentity("東京スカイホークス", 0xFFCC00) else null,
                                    teamStatus = "セーブデータ未選択",
                                    onPlayers = { playersClicks++ },
                                    onSettings = { settingsClicks++ },
                                    onLineup = { lineupClicks++ },
                                )
                                Box(Modifier.size(1.dp).onGloballyPositioned { contentLeft = it.positionInRoot().x })
                            }
                        }
                    }
                try {
                    repeat(3) { scene.render().close() }
                    assertEquals(60f, contentLeft)
                    val output = Path.of("build/navigation-previews")
                    Files.createDirectories(output)
                    scene.render().use { image ->
                        image.encodeToData()!!.use { Files.write(output.resolve("sidebar-compact-$available.png"), it.bytes) }
                    }

                    fun click(
                        x: Float,
                        y: Float,
                    ) {
                        scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                        scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                        repeat(3) { scene.render().close() }
                    }

                    click(30f, 36f)
                    click(30f, 136f)
                    click(30f, 176f)
                    assertEquals(1, settingsClicks)
                    assertEquals(if (available) 1 else 0, playersClicks)
                    assertEquals(if (available) 1 else 0, lineupClicks)
                    assertEquals(60f, contentLeft)
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun teamIconOpensSettingsIncludingWhenTeamIsUnavailable() {
        SwingUtilities.invokeAndWait {
            val cases =
                listOf(
                    TeamIdentity("東京スカイホークス", 0xFFCC00) to "",
                    TeamIdentity("とても長いチーム名の表示確認", 0xFFFFFF) to "",
                    null to "セーブデータ未選択",
                    null to "セーブデータを利用できません",
                    null to "チーム情報を取得できません",
                    TeamIdentity("F", 0xAD24FF) to "",
                )
            cases.forEachIndexed { index, (team, status) ->
                var settingsClicks = 0
                var playersClicks = 0
                val scene =
                    ImageComposeScene(320, 480) {
                        MaterialTheme(colorScheme = rosterColorScheme) {
                            AppNavigation(
                                destination = if (team == null) AppDestination.SETTINGS else AppDestination.PLAYERS,
                                playersEnabled = team != null,
                                team = team,
                                teamStatus = status,
                                onPlayers = { playersClicks++ },
                                onSettings = { settingsClicks++ },
                            )
                        }
                    }
                try {
                    repeat(3) { scene.render().close() }
                    val output = Path.of("build/navigation-previews")
                    Files.createDirectories(output)
                    scene.render().use { image ->
                        image.encodeToData()!!.use { Files.write(output.resolve("sidebar-$index.png"), it.bytes) }
                    }
                    listOf(16f, 30f, 44f).forEach { x ->
                        scene.sendPointerEvent(PointerEventType.Press, Offset(x, 40f))
                        scene.sendPointerEvent(PointerEventType.Release, Offset(x, 40f))
                        scene.render().close()
                    }
                    assertEquals(3, settingsClicks)
                    scene.sendPointerEvent(PointerEventType.Press, Offset(30f, 136f))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(30f, 136f))
                    scene.render().close()
                    assertEquals(if (team == null) 0 else 1, playersClicks)
                } finally {
                    scene.close()
                }
            }
        }
    }
}
