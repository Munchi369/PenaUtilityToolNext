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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import org.example.rosterColorScheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class PlayerRosterControlsTest {
    @Test
    fun filterMenusApplyReplaceAndRemoveIndividualConditions() =
        withRosterScene { ui ->
            ui.click("フィルター")
            ui.click("ポジション  ›")
            ui.snapshot("FILTER_MENU")
            assertFalse(ui.hasText("✓"))
            ui.click("野手")
            assertEquals(RosterPositionFilter.Fielders, ui.options.positionFilter)
            assertFalse(ui.hasText("ポジション  ›"))
            ui.click("ポジション：野手")
            assertTrue(ui.hasText("✓"))
            ui.click("捕手")
            assertEquals(RosterPositionFilter.MainPosition(PlayerPosition.CATCHER), ui.options.positionFilter)
            ui.click("フィルター")
            ui.click("現在の軍  ›")
            ui.click("一軍")
            ui.click("フィルター")
            ui.click("支配下・育成  ›")
            ui.click("育成")
            ui.snapshot("FILTER_CHIPS")
            assertEquals(PlayerSquad.FIRST, ui.options.squad)
            assertEquals(PlayerRegistration.DEVELOPMENT, ui.options.registration)
            assertFalse(ui.hasText("条件をクリア"))
            ui.options = ui.options.copy(search = "検索を保持")
            ui.click("ポジション：捕手 を解除")
            assertNull(ui.options.positionFilter)
            assertEquals("検索を保持", ui.options.search)
            assertEquals(PlayerSquad.FIRST, ui.options.squad)
            ui.options = ui.options.selectTeamScope(RosterTeamScope.Major)
            ui.settle()
            assertFalse(ui.hasText("現在の軍：一軍"))
            assertFalse(ui.hasText("登録区分：育成"))
            ui.click("フィルター")
            assertFalse(ui.hasText("現在の軍  ›"))
            assertFalse(ui.hasText("支配下・育成  ›"))
            ui.outsideClick()
            ui.options = ui.options.selectTeamScope(RosterTeamScope.Team(6))
            ui.settle()
            assertTrue(ui.hasText("現在の軍：一軍"))
            assertTrue(ui.hasText("登録区分：育成"))
            ui.click("現在の軍：一軍 を解除")
            assertNull(ui.options.squad)
            assertEquals(PlayerRegistration.DEVELOPMENT, ui.options.registration)
            ui.click("登録区分：育成 を解除")
            assertNull(ui.options.registration)
        }

    @Test
    fun teamScopeMovesIntoFiltersAndItsChipClearsToAll() =
        withRosterScene { ui ->
            assertFalse(ui.hasText("F ▾"))
            assertTrue(ui.hasText("球団・リーグ：F"))

            ui.click("フィルター")
            ui.click("球団・リーグ  ›")
            ui.snapshot("TEAM_SCOPE_FILTER")
            assertTrue(ui.hasText("すべて"))
            assertTrue(ui.hasText("国内全球団"))
            assertTrue(ui.hasText("1stリーグ"))
            assertTrue(ui.hasText("2ndリーグ"))
            assertTrue(ui.hasText("メジャー"))
            ui.click("1stリーグ")

            assertEquals(RosterTeamScope.FirstLeague, ui.options.teamScope)
            assertTrue(ui.hasText("球団・リーグ：1stリーグ"))
            ui.click("球団・リーグ：1stリーグ を解除")
            assertEquals(RosterTeamScope.All, ui.options.teamScope)
            assertFalse(ui.hasText("球団・リーグ：1stリーグ"))
        }

    @Test
    fun positionIsInitiallyVisibleAndCanBeHiddenAndRestoredInPitchingAndStatsDisplays() =
        withRosterScene { ui ->
            for (display in listOf(RosterDisplay.PITCHING_ABILITY, RosterDisplay.BATTING_STATS, RosterDisplay.PITCHING_STATS)) {
                ui.click(display.title)
                assertTrue(ui.hasText("ポジション"), display.title)
                ui.click("表示設定")
                ui.click("ポジション")
                ui.outsideClick()
                assertFalse(ui.hasText("ポジション"), display.title)
                ui.click("表示設定")
                ui.click("標準に戻す")
                ui.outsideClick()
                assertTrue(ui.hasText("ポジション"), display.title)
            }
        }

    @Test
    fun displayButtonsStayOpenAndKeepIndependentSelections() =
        withRosterScene { ui ->
            ui.click("表示設定")
            ui.snapshot("DISPLAY_SETTINGS")
            ui.click("選手ID")
            assertFalse("ID" in ui.options.current.visibleColumns)
            assertTrue(ui.hasText("基本情報の表示項目"))
            ui.click("年齢")
            assertFalse("AGE" in ui.options.current.visibleColumns)
            ui.click("一覧グルーピング：軍別 ▾")
            ui.click("なし")
            assertEquals(RosterGrouping.NONE, ui.options.grouping)
            ui.outsideClick()
            assertFalse(ui.hasText("基本情報の表示項目"))
            assertTrue(ui.hasText("背番号"))
            assertTrue(ui.hasText("名前"))
            assertFalse(ui.hasText("選手ID"))
            ui.click("打撃成績")
            ui.click("表示設定")
            ui.snapshot("BATTING_DISPLAY_SETTINGS")
            ui.click("すべて表示")
            assertEquals(RosterColumns.forDisplay(RosterDisplay.BATTING_STATS).map { it.key }.toSet(), ui.options.current.visibleColumns)
            ui.click("標準に戻す")
            assertEquals(
                RosterColumns
                    .forDisplay(RosterDisplay.BATTING_STATS)
                    .filter {
                        it.standard
                    }.map { it.key }
                    .toSet(),
                ui.options.current.visibleColumns,
            )
            ui.outsideClick()
            ui.click("基本情報")
            assertFalse("ID" in ui.options.current.visibleColumns)
            assertFalse("AGE" in ui.options.current.visibleColumns)
            ui.click("表示設定")
            ui.click("標準に戻す")
            assertTrue("ID" in ui.options.current.visibleColumns)
            assertTrue("AGE" in ui.options.current.visibleColumns)
        }

    @Test
    fun basicConditionColumnsStartHiddenAndParticipateInDisplayControls() =
        withRosterScene { ui ->
            val optional = RosterColumns.basic.filterNot { it.standard }
            assertTrue(optional.none { it.key in ui.options.current.visibleColumns })

            ui.click("表示設定")
            optional.forEach { column -> assertTrue(ui.hasText(column.title), column.title) }
            ui.click("すべて表示")
            assertEquals(RosterColumns.basic.map { it.key }.toSet(), ui.options.current.visibleColumns)

            ui.click("標準に戻す")
            assertEquals(
                RosterColumns.basic
                    .filter { it.standard }
                    .map { it.key }
                    .toSet(),
                ui.options.current.visibleColumns,
            )
        }

    @Test
    fun displaySwitchLivesInFiltersAndFollowsPositionSelections() =
        withRosterScene { ui ->
            ui.options = ui.options.copy(display = RosterDisplay.BATTING_ABILITY)
            ui.click("表示設定")
            assertFalse(ui.hasText("ポジション連動"))
            ui.outsideClick()

            ui.click("フィルター")
            ui.click("ポジション連動")
            assertTrue(ui.options.automaticDisplaySwitch)
            assertTrue(ui.hasText("フィルター"))
            ui.click("ポジション  ›")
            ui.click("投手")
            assertEquals(RosterDisplay.PITCHING_ABILITY, ui.options.display)

            ui.click("打撃成績")
            assertEquals(RosterDisplay.BATTING_STATS, ui.options.display)
            assertEquals(RosterPositionFilter.Fielders, ui.options.positionFilter)
            ui.click("フィルター")
            ui.click("ポジション  ›")
            ui.click("捕手")
            assertEquals(RosterDisplay.BATTING_STATS, ui.options.display)
            assertEquals(RosterPositionFilter.MainPosition(PlayerPosition.CATCHER), ui.options.positionFilter)

            ui.click("フィルター")
            ui.click("ポジション連動")
            assertFalse(ui.options.automaticDisplaySwitch)
            assertEquals(RosterDisplay.BATTING_STATS, ui.options.display)
        }

    @Test
    fun shortWindowKeepsGroupingVisibleAndClickable() =
        withRosterScene(height = 400) { ui ->
            ui.options = ui.options.copy(display = RosterDisplay.BATTING_STATS, grouping = RosterGrouping.NONE)
            ui.click("表示設定")
            ui.snapshot("COMPACT_DISPLAY_SETTINGS")
            ui.click("一覧グルーピング：なし ▾")
            ui.click("軍別")
            assertEquals(RosterGrouping.SQUAD, ui.options.grouping)
            ui.click("試合")
            ui.click("一覧グルーピング：軍別 ▾")
            ui.click("なし")
            assertEquals(RosterGrouping.NONE, ui.options.grouping)
            assertTrue(ui.hasText("打撃成績の表示項目"))
        }

    @Test
    fun majorShowsGroupingWithoutSquadChoice() =
        withRosterScene { ui ->
            ui.options = ui.options.selectTeamScope(RosterTeamScope.Major)
            ui.settle()
            ui.click("表示設定")
            assertTrue(ui.hasText("一覧グルーピング：なし ▾"))
            ui.click("一覧グルーピング：なし ▾")
            assertTrue(ui.hasText("ポジション別"))
            assertFalse(ui.hasText("軍別"))
            ui.click("ポジション別")
            assertEquals(RosterGrouping.POSITION, ui.options.grouping)
        }

    @Test
    fun displayChangesKeepTableAlignedAndConditionsOnlyUseSpaceWhenPresent() =
        withRosterScene { ui ->
            ui.options = ui.options.selectTeamScope(RosterTeamScope.All)
            ui.settle()
            val tableTop = ui.bounds("背番号").top
            assertTrue(ui.bounds("選手検索").top < ui.bounds("基本情報").top)
            for (display in RosterDisplay.entries) {
                ui.click(display.title)
                assertEquals(tableTop, ui.bounds("背番号").top)
            }
            ui.click("表示成績：一軍 ▾")
            ui.click("二軍成績")
            assertEquals(2, ui.options.statsSquad)
            assertNull(ui.options.squad)
            ui.options = ui.options.selectTeamScope(RosterTeamScope.Major)
            ui.settle()
            assertFalse(ui.hasText("表示成績：二軍 ▾"))
            assertTrue(ui.bounds("背番号").top > tableTop)
            ui.options =
                ui.options
                    .selectTeamScope(RosterTeamScope.All)
                    .copy(positionFilter = RosterPositionFilter.Fielders)
            ui.settle()
            assertTrue(ui.bounds("背番号").top > tableTop)
            ui.click("ポジション：野手 を解除")
            assertEquals(tableTop, ui.bounds("背番号").top)
            ui.search("17")
            assertEquals("17", ui.options.search)
        }

    @Test
    fun narrowWindowKeepsSearchAndStatsControlsUsable() =
        withRosterScene(width = 480) { ui ->
            ui.settle()
            assertTrue(ui.bounds("選手検索").width > 0)
            ui.search("18")
            assertEquals("18", ui.options.search)
            val tableTop = ui.bounds("背番号").top
            ui.options = ui.options.copy(display = RosterDisplay.PITCHING_STATS)
            ui.settle()
            assertEquals(tableTop, ui.bounds("背番号").top)
            ui.click("表示成績：一軍 ▾")
            ui.click("二軍成績")
            assertEquals(2, ui.options.statsSquad)
            ui.snapshot("NARROW_ROSTER")
            ui.click("フィルター")
            ui.click("ポジション  ›")
            ui.click("野手")
            ui.snapshot("NARROW_FILTER_CHIPS")
            ui.click("ポジション：野手 を解除")
            assertNull(ui.options.positionFilter)
        }

    private fun withRosterScene(
        height: Int = 760,
        width: Int = 1200,
        test: (RosterScene) -> Unit,
    ) {
        SwingUtilities.invokeAndWait {
            RosterScene(height, width).use(test)
        }
    }

    private class RosterScene(
        private val height: Int,
        private val width: Int,
    ) : AutoCloseable {
        var options by mutableStateOf(PlayerRosterOptions.forTeam(6))
        private var frameTime = System.nanoTime()
        private val scene =
            ImageComposeScene(width, height) {
                MaterialTheme(colorScheme = rosterColorScheme) {
                    Surface(Modifier.fillMaxSize()) {
                        PlayerRosterScreen(
                            PlayerRosterViewState(
                                phase = PlayerRosterPhase.LOADED,
                                roster =
                                    PlayerRoster(
                                        emptyList(),
                                        emptyList(),
                                        teams =
                                            mapOf(
                                                1 to "A",
                                                2 to "B",
                                                3 to "C",
                                                4 to "D",
                                                5 to "E",
                                                6 to "F",
                                                7 to "G",
                                                8 to "H",
                                                9 to "J",
                                                10 to "K",
                                                11 to "L",
                                                12 to "M",
                                            ),
                                    ),
                            ),
                            options,
                            { options = it },
                            {},
                        )
                    }
                }
            }

        fun settle() {
            repeat(5) {
                frameTime += 100_000_000L
                scene.render(frameTime).close()
            }
        }

        fun click(label: String) {
            settle()
            val matches = nodes().filter { it.matches(label) && it.config.contains(SemanticsActions.OnClick) }
            val node = matches.singleOrNull { it.config.contains(SemanticsProperties.Selected) } ?: matches.single()
            val bounds = node.boundsInWindow
            assertTrue(bounds.width > 0 && bounds.height > 0 && bounds.top >= 0 && bounds.bottom <= height, "$label bounds: $bounds")
            pointerClick(bounds.center)
        }

        fun hasText(label: String): Boolean = nodes().any { it.matches(label) }

        fun bounds(label: String): Rect = nodes().first { it.matches(label) }.boundsInWindow

        fun search(value: String) {
            settle()
            val input = nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
            settle()
        }

        fun outsideClick() = pointerClick(Offset(600f, 700f))

        fun snapshot(name: String) {
            settle()
            val output = Path.of("build/roster-previews")
            Files.createDirectories(output)
            scene.render(frameTime).use { image ->
                image.encodeToData()!!.use { data -> Files.write(output.resolve("$name.png"), data.bytes) }
            }
        }

        private fun pointerClick(position: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, position)
            scene.sendPointerEvent(PointerEventType.Release, position)
            settle()
        }

        private fun nodes(): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { descendants(it) }
            return scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        }

        private fun SemanticsNode.matches(label: String): Boolean =
            config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
                config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true

        override fun close() = scene.close()
    }
}
