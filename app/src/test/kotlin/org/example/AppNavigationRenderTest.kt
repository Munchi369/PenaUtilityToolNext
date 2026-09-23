package org.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import org.example.team.TeamIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class AppNavigationRenderTest {
    @Test
    fun entireTeamRowOpensSettingsIncludingWhenTeamIsUnavailable() {
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
                    ImageComposeScene(232, 480) {
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
                    listOf(34f, 110f, 204f).forEach { x ->
                        scene.sendPointerEvent(PointerEventType.Press, Offset(x, 40f))
                        scene.sendPointerEvent(PointerEventType.Release, Offset(x, 40f))
                        scene.render().close()
                    }
                    assertEquals(3, settingsClicks)
                    scene.sendPointerEvent(PointerEventType.Press, Offset(110f, 108f))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(110f, 108f))
                    scene.render().close()
                    assertEquals(if (team == null) 0 else 1, playersClicks)
                } finally {
                    scene.close()
                }
            }
        }
    }
}
