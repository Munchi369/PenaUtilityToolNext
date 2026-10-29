package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.rosterColorScheme
import io.github.munchi369.penautilitytoolnext.savedata.CopiedDerbyTestDatabase
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.LatestSaveDatabaseBackup
import io.github.munchi369.penautilitytoolnext.team.TeamIdentity
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.Executor
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class LineupScreenRenderTest {
    private var testDatabase: CopiedDerbyTestDatabase? = null

    @Test
    fun overviewShowsOwnTeamAndRosterCounts() {
        val snapshot = snapshotOf(fullTeam())
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller, TeamIdentity("テスト球団", 0x1268B1)) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }
                val nodes = scene.nodes()
                val team = nodes.single { it.hasText("テスト球団") }
                val league = nodes.single { it.hasText("1stリーグ") }
                val firstTeam = nodes.single { it.hasText("1軍") }
                assertTrue(team.boundsInRoot.right < firstTeam.boundsInRoot.left)
                assertTrue(league.boundsInRoot.top < team.boundsInRoot.top)
                listOf("投手", "野手", "外国人", "支配下", "育成").forEach { label ->
                    assertTrue(nodes.any { it.hasText(label) })
                }
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun overviewAlwaysShowsTeamAndMetricsAndDoesNotCollapseWhenClicked() {
        val snapshot = snapshotOf(fullTeam()).copy(notices = listOf("起用の読み込みを補正しました"))
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(900, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller, TeamIdentity("テスト球団", 0x1268B1)) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }
                val expandedTop =
                    scene
                        .nodes()
                        .single { it.hasText("スタメン") }
                        .boundsInRoot.top
                val team = scene.nodes().filter { it.hasText("テスト球団") }.minBy { it.boundsInRoot.top }
                val league = scene.nodes().filter { it.hasText("1stリーグ") }.minBy { it.boundsInRoot.top }
                val firstTeamMetric = scene.nodes().filter { it.hasText("1軍") }.minBy { it.boundsInRoot.top }
                val lastMetric = scene.nodes().filter { it.hasText("育成") }.minBy { it.boundsInRoot.top }
                assertTrue(team.boundsInRoot.right < firstTeamMetric.boundsInRoot.left)
                assertTrue(kotlin.math.abs(league.boundsInRoot.center.y - firstTeamMetric.boundsInRoot.center.y) < 8f)
                assertTrue(kotlin.math.abs(firstTeamMetric.boundsInRoot.center.y - lastMetric.boundsInRoot.center.y) < 8f)
                scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 20f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(100f, 20f))
                repeat(3) { scene.render().close() }
                assertEquals(
                    expandedTop,
                    scene
                        .nodes()
                        .single { it.hasText("スタメン") }
                        .boundsInRoot.top,
                )
                assertTrue(scene.nodes().any { it.hasText("支配下") })
                assertTrue(scene.nodes().any { it.hasText("育成") })
                assertTrue(scene.nodes().any { it.hasText("起用の読み込みを補正しました") })
                assertFalse(scene.nodes().any { it.hasText("⌃") || it.hasText("⌄") })
                controller.reload()
                repeat(3) { scene.render().close() }
                assertTrue(scene.nodes().any { it.hasText("育成") })
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun noticeDisplayAndDismissalKeepTheTablesInPlace() {
        val snapshot = snapshotOf(fullTeam())
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                LineupTab.entries.forEach { tab ->
                    controller.showTab(tab)
                    repeat(3) { scene.render().close() }
                    val title = if (tab == LineupTab.FIELDERS) "スタメン" else "先発"

                    fun tableBounds() = scene.nodes().single { it.hasText(title) && it.boundsInRoot.left < 100f }.boundsInRoot

                    val before = tableBounds()
                    val item =
                        if (tab == LineupTab.FIELDERS) {
                            DragItem.LineupSlot(LineupMode.DH, 0)
                        } else {
                            DragItem.RoleSlot(PitchingRole.STARTER, 0)
                        }
                    controller.drop(item, DropSpot.DevelopmentArea(tab == LineupTab.PITCHERS))
                    repeat(3) { scene.render().close() }
                    assertTrue(requireNotNull(controller.state.notice).error)
                    assertEquals(before, tableBounds())
                    val dismiss = scene.nodes().single { it.hasText("✕") }
                    click(scene, dismiss.boundsInRoot.center)
                    assertEquals(null, controller.state.notice)
                    assertEquals(before, tableBounds())
                    controller.load(snapshot.path, LineupNotice("1行目\n2行目\n3行目\n4行目", error = true))
                    repeat(3) { scene.render().close() }
                    assertEquals(before, tableBounds())
                    controller.dismissNotice()
                    repeat(3) { scene.render().close() }
                    assertEquals(before, tableBounds())
                }
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun subPositionCheckboxChangesOnlyTheNextArrangementAndIsHiddenForPitchers() {
        val snapshot =
            snapshotOf(
                fullTeam().map { player ->
                    when (player.id) {
                        104 -> player.copy(batting = 90, power = 90)
                        112 -> player.copy(batting = 70, power = 70, initialAptitudes = player.initialAptitudes + (FieldSlot.FIRST to 60))
                        else -> player
                    }
                },
            )
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        controller.showMode(LineupMode.NORMAL)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }
                val option = scene.nodes().single { it.config.getOrNull(SemanticsProperties.ToggleableState) != null }
                assertEquals(ToggleableState.Off, option.config[SemanticsProperties.ToggleableState])
                val auto = scene.nodes().single { it.hasText("自動並び替え") }
                assertTrue(option.boundsInRoot.right <= auto.boundsInRoot.left)
                assertTrue(option.boundsInRoot.right < 1200f)
                val label = scene.nodes().single { it.hasText("サブ考慮") }
                val layout = label.textLayout()
                assertEquals("サブ考慮".length, layout.getLineEnd(0))
                click(scene, option.boundsInRoot.center)
                assertTrue(controller.view.considerSubPositions)
                assertEquals(snapshot.initial, controller.state.draft)
                val tableHeader = scene.nodes().single { it.hasText("スタメン") }
                val tableTop = tableHeader.boundsInRoot.top
                click(scene, auto.boundsInRoot.center)
                assertEquals(112, requireNotNull(controller.state.draft).normal.single { it.slot == FieldSlot.FIRST }.playerId)
                val arrangedHeader = scene.nodes().single { it.hasText("スタメン") }
                assertEquals(tableTop, arrangedHeader.boundsInRoot.top)
                val check = scene.nodes().single { it.hasText("✓") }
                val checkLayout = check.textLayout()
                assertEquals(rosterColorScheme.primary, checkLayout.layoutInput.style.color)
                val frameStart = System.nanoTime()
                repeat(150) { frame -> scene.render(frameStart + frame * 16_666_667L).close() }
                val persistentCheck = scene.nodes().single { it.hasText("✓") }.textLayout()
                assertEquals(rosterColorScheme.primary, persistentCheck.layoutInput.style.color)
                click(scene, Offset(10f, 80f))
                assertTrue(controller.state.highlightedSpots.isEmpty())
                assertFalse(controller.state.autoCompleted)
                controller.drop(DragItem.LineupSlot(LineupMode.NORMAL, 0), DropSpot.LineupSlot(LineupMode.NORMAL, 1))
                repeat(3) { scene.render().close() }
                assertTrue(DropSpot.LineupSlot(LineupMode.NORMAL, 1) in controller.state.highlightedSpots)
                click(scene, Offset(10f, 80f))
                assertTrue(controller.state.highlightedSpots.isEmpty())
                controller.showTab(LineupTab.PITCHERS)
                repeat(3) { scene.render().close() }
                val clearedCheck = scene.nodes().single { it.hasText("✓") }.textLayout()
                assertEquals(androidx.compose.ui.graphics.Color.Transparent, clearedCheck.layoutInput.style.color)
                assertFalse(scene.nodes().any { it.hasText("サブ考慮") })
                val pitchersHeader = scene.nodes().single { it.hasText("先発") && it.boundsInRoot.left < 100f }
                val pitchersTop = pitchersHeader.boundsInRoot.top
                val pitcherAuto = scene.nodes().single { it.hasText("自動並び替え") }
                click(scene, pitcherAuto.boundsInRoot.center)
                val arrangedPitchersHeader = scene.nodes().single { it.hasText("先発") && it.boundsInRoot.left < 100f }
                assertEquals(pitchersTop, arrangedPitchersHeader.boundsInRoot.top)
                assertEquals(null, controller.state.notice)
                controller.showTab(LineupTab.FIELDERS)
                repeat(3) { scene.render().close() }
                val restored = scene.nodes().single { it.config.getOrNull(SemanticsProperties.ToggleableState) != null }
                assertEquals(ToggleableState.On, restored.config[SemanticsProperties.ToggleableState])
            } finally {
                scene.close()
            }
        }
    }

    private fun click(
        scene: ImageComposeScene,
        position: Offset,
    ) {
        scene.sendPointerEvent(PointerEventType.Press, position)
        scene.sendPointerEvent(PointerEventType.Release, position)
        repeat(3) { scene.render().close() }
    }

    @AfterEach
    fun removeTestDatabaseCopy() {
        testDatabase?.close()
    }

    @Test
    fun referenceLineupRendersAndRowDragSwapsBattingOrder() {
        val copied = CopiedDerbyTestDatabase.copyPrimaryReference()
        testDatabase = copied
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = DerbyLineupLoader(DerbySaveDatabaseAccess()),
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(copied.path)
        val before = requireNotNull(controller.state.draft).dh
        var after = before

        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }

                fun rowCenter(index: Int): Offset {
                    val player = requireNotNull(controller.state.snapshot).player(requireNotNull(before[index].playerId))
                    return scene
                        .nodes()
                        .filter { it.hasText(player.name) && it.boundsInRoot.left < 500f }
                        .minBy { it.boundsInRoot.top }
                        .boundsInRoot.center
                }

                drag(scene, from = rowCenter(0), to = rowCenter(2))

                after = requireNotNull(controller.state.draft).dh
            } finally {
                scene.close()
            }
        }

        assertNotEquals(before, after)
        assertEquals(before[0], after[2])
        assertEquals(before[2], after[0])
    }

    @Test
    fun contractChangesRenderAndSaveKeepsSelection() {
        val copied = CopiedDerbyTestDatabase.copyPrimaryReference()
        testDatabase = copied
        val access = DerbySaveDatabaseAccess()
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = DerbyLineupLoader(access),
                saver = DerbyLineupWriter(access, LatestSaveDatabaseBackup(copied.path.parent.resolve("backup"))),
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(copied.path)
        controller.select(1548)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 960) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }

                controller.drop(DragItem.ListedPlayer(1548), DropSpot.FarmArea(false))
                assertEquals(PlayerRegistration.CONTROLLED, requireNotNull(controller.state.draft).contract(1548).registration)
                repeat(3) { scene.render().close() }

                controller.save()
                assertEquals(1548, controller.view.selectedId)
                assertEquals(PlayerRegistration.CONTROLLED, requireNotNull(controller.state.draft).contract(1548).registration)
                controller.showTab(LineupTab.PITCHERS)
                controller.select(1317)
                repeat(3) { scene.render().close() }
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun pointerDragChangesContractsOnFarmRowsAndEmptyDevelopmentArea() {
        val farm = fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND)
        val development =
            fielder(202, PlayerPosition.FIRST_BASEMAN, squad = PlayerSquad.SECOND)
                .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = 105)
        val snapshot = snapshotOf(fullTeam(listOf(farm, development)))
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(Path.of("save"))
        controller.select(202)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }

                fun playerCenter() = scene.listNode("野手202").boundsInRoot.center

                fun groupCenter(label: String) = scene.listNode(label, last = true).boundsInRoot.center

                drag(scene, from = playerCenter(), to = groupCenter("2軍"))
                assertEquals(PlayerRegistration.CONTROLLED, requireNotNull(controller.state.draft).contract(202).registration)
                assertEquals(PlayerSquad.SECOND, requireNotNull(controller.state.draft).squad(202))
                repeat(2) { scene.render().close() }
                drag(scene, from = playerCenter(), to = groupCenter("育成"))
                assertEquals(PlayerRegistration.DEVELOPMENT, requireNotNull(controller.state.draft).contract(202).registration)
                assertEquals(101, requireNotNull(controller.state.draft).contract(202).uniformNumber)
                assertEquals(202, controller.view.selectedId)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun holdingDraggedPlayerAtListEdgeScrollsToDevelopmentBelowTheViewport() {
        val farm = (201..225).map { fielder(it, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND) }
        val development =
            fielder(500, PlayerPosition.FIRST_BASEMAN, squad = PlayerSquad.SECOND)
                .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = 105)
        val snapshot = snapshotOf(fullTeam(farm + development))
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(Path.of("save"))
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }
                val from = scene.listNode("野手201").boundsInRoot.center
                val to = Offset(600f, 790f)
                scene.sendPointerEvent(PointerEventType.Press, from)
                (1..10).forEach { step ->
                    scene.sendPointerEvent(PointerEventType.Move, from + (to - from) * (step / 10f))
                    scene.render().close()
                }
                val frameStart = System.nanoTime()
                repeat(150) { frame -> scene.render(frameStart + frame * 16_666_667L).close() }
                scene.sendPointerEvent(PointerEventType.Release, to)
                repeat(3) { scene.render().close() }
                assertEquals(PlayerRegistration.DEVELOPMENT, requireNotNull(controller.state.draft).contract(201).registration)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun aptitudeEditingKeepsPanelPositionAndCancelRestoresOnlyThatEdit() {
        val resting = fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND).copy(restDays = 5)
        val snapshot =
            snapshotOf(
                fullTeam(listOf(resting)).map { player ->
                    if (player.id == 101) player.copy(cells = player.cells + ("HIROU" to PlayerCell("37"))) else player
                },
            )
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        controller.setAptitude(101, FieldSlot.CATCHER, 77)
        controller.select(101)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 960) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }

                fun panelText(value: String) = scene.nodes().single { it.hasText(value) && it.boundsInRoot.left >= 850f }

                val fatigueColumn = scene.nodes().single { it.hasText("疲労") && it.boundsInRoot.left < 500f }
                val powerColumn = scene.nodes().single { it.hasText("長打") && it.boundsInRoot.left < 500f }
                assertTrue(powerColumn.boundsInRoot.right <= fatigueColumn.boundsInRoot.left)
                assertEquals(2, scene.nodes().count { it.hasText("37") })
                assertTrue(panelText("疲労").boundsInRoot.top > panelText("野手能力").boundsInRoot.top)
                assertTrue(panelText("疲労").boundsInRoot.top < panelText("守備適性").boundsInRoot.top)
                assertFalse(scene.nodes().any { it.hasText("状態") })
                val abilityTop = panelText("野手能力").boundsInRoot.top
                val aptitudeTop = panelText("守備適性").boundsInRoot.top
                assertFalse(scene.nodes().any { it.hasText("契約") })
                controller.select(201)
                repeat(3) { scene.render().close() }
                assertEquals(abilityTop, panelText("野手能力").boundsInRoot.top)

                controller.select(101)
                repeat(3) { scene.render().close() }
                click(scene, panelText("編集").boundsInRoot.center)
                assertTrue(scene.nodes().any { it.hasText("キャンセル") })
                assertTrue(panelText("キャンセル").boundsInRoot.right <= panelText("完了").boundsInRoot.left)
                assertEquals(aptitudeTop, panelText("守備適性").boundsInRoot.top)
                controller.setAptitude(101, FieldSlot.CATCHER, 0)
                controller.setMainPosition(101, FieldSlot.LEFT)
                repeat(3) { scene.render().close() }
                click(scene, panelText("キャンセル").boundsInRoot.center)
                assertEquals(
                    77,
                    requireNotNull(controller.state.draft).aptitude(101, FieldSlot.CATCHER),
                )
                assertEquals(snapshot.initial.mainPosition(101), requireNotNull(controller.state.draft).mainPosition(101))
                assertEquals(aptitudeTop, panelText("守備適性").boundsInRoot.top)
                assertFalse(scene.nodes().any { it.hasText("キャンセル") })

                controller.showTab(LineupTab.PITCHERS)
                controller.select(1)
                repeat(3) { scene.render().close() }
                assertTrue(panelText("疲労").boundsInRoot.top < panelText("球種").boundsInRoot.top)
                assertTrue(panelText("先発時スタミナ").boundsInRoot.top < panelText("球種").boundsInRoot.top)
                assertTrue(panelText("球種").boundsInRoot.top < panelText("起用適性").boundsInRoot.top)
                assertFalse(scene.nodes().any { it.hasText("状態") })
                assertFalse(scene.nodes().any { it.hasText("契約") })
                click(scene, panelText("編集").boundsInRoot.center)
                controller.setPitchingAptitude(1, PitchingRole.RELIEVER, 99)
                repeat(3) { scene.render().close() }
                click(scene, panelText("キャンセル").boundsInRoot.center)
                assertEquals(snapshot.initial.pitchingAptitude(1), requireNotNull(controller.state.draft).pitchingAptitude(1))
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun listNamesShowPriorityColorsAndStatusBadgesRemainOnlyInThePanel() {
        val development =
            fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND)
                .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = 105)
        val examples =
            listOf(
                development.copy(name = "長谷川陸司", foreign = true, restDays = 5, injury = PlayerInjury.Injured(5)) to LineupColors.injury,
                development.copy(id = 202, name = "休止選手", uniformNumber = 106, foreign = true, restDays = 5) to LineupColors.rest,
                development.copy(id = 203, name = "外国選手", uniformNumber = 107, foreign = true) to LineupColors.foreign,
                development.copy(id = 204, name = "育成選手", uniformNumber = 108) to LineupColors.development,
            )
        val snapshot = snapshotOf(fullTeam(examples.map { it.first }))
        val direct = Executor(Runnable::run)
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        controller.select(201)
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(1200, 820) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface { LineupScreen(controller) }
                    }
                }
            try {
                repeat(3) { scene.render().close() }
                val nodes = scene.nodes()
                examples.forEach { (player, expectedColor) ->
                    val node = nodes.filter { it.hasText(player.name) }.minBy { it.boundsInRoot.left }
                    val layout = node.textLayout()
                    assertEquals(expectedColor, layout.layoutInput.style.color, player.name)
                    assertEquals(player.name, layout.layoutInput.text.text)
                }
                val longName = nodes.filter { it.hasText("長谷川陸司") }.minBy { it.boundsInRoot.left }.textLayout()
                assertEquals("長谷川陸司".length, longName.getLineEnd(0))
                listOf("故障" to LineupColors.injury, "休止 5日" to LineupColors.rest, "外" to LineupColors.foreign).forEach { (text, color) ->
                    val node = nodes.single { it.hasText(text) }
                    val layout = node.textLayout()
                    assertTrue(node.boundsInRoot.left >= 850f, "状態バッジは右パネルに表示します")
                    assertEquals(color, layout.layoutInput.style.color)
                }
                val greenBadges =
                    nodes
                        .filter { it.hasText("育成") }
                        .filter {
                            val layout = it.textLayout()
                            layout.layoutInput.style.color == LineupColors.development
                        }
                assertEquals(1, greenBadges.size)
                assertTrue(greenBadges.single().boundsInRoot.left >= 850f)

                controller.drop(DragItem.ListedPlayer(204), DropSpot.FarmArea(false))
                repeat(3) { scene.render().close() }
                val registered = scene.nodes().single { it.hasText("育成選手") }
                val registeredLayout = registered.textLayout()
                assertEquals(rosterColorScheme.onSurface, registeredLayout.layoutInput.style.color)
            } finally {
                scene.close()
            }
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
    }

    private fun ImageComposeScene.listNode(
        text: String,
        last: Boolean = false,
    ): SemanticsNode {
        val matches = nodes().filter { it.hasText(text) && it.boundsInRoot.left in 500f..850f }
        return if (last) matches.maxBy { it.boundsInRoot.top } else matches.minBy { it.boundsInRoot.top }
    }

    private fun SemanticsNode.hasText(text: String): Boolean = config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(requireNotNull(config[SemanticsActions.GetTextLayoutResult].action).invoke(results))
        return results.single()
    }

    private fun drag(
        scene: ImageComposeScene,
        from: Offset,
        to: Offset,
    ) {
        scene.sendPointerEvent(PointerEventType.Press, from)
        (1..10).forEach { step ->
            val position = from + (to - from) * (step / 10f)
            scene.sendPointerEvent(PointerEventType.Move, position)
            scene.render().close()
        }
        scene.sendPointerEvent(PointerEventType.Release, to)
        repeat(2) { scene.render().close() }
    }
}
