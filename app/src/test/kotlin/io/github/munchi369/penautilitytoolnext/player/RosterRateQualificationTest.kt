package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.rosterColorScheme
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class RosterRateQualificationTest {
    @Test
    fun qualifiedPlayersComeFirstInBothSortDirectionsAndOtherColumnsUseNormalSorting() {
        val roster = roster(player(1, 31, 0.200), player(2, 30, 0.900), player(3, 40, 0.300))
        val options = options(RosterDisplay.BATTING_STATS, "AVG")
        assertEquals(listOf(3, 1, 2), options.players(roster).map { it.playerId })
        assertEquals(listOf(1, 3, 2), options.sort("AVG").players(roster).map { it.playerId })
        assertNull(options.sort("HONRUIDA").rateQualification(roster))
        assertEquals(listOf(2, 3, 1), options.sort("HONRUIDA").players(roster).map { it.playerId })
    }

    @Test
    fun thresholdsUseTheSelectedStatisticsSquadAndOutCountsWithoutStarterRestrictions() {
        val roster = roster(player(1, 31, 0.200))
        val batter = roster.players.single()
        val batting = options(RosterDisplay.BATTING_STATS, "AVG")
        assertEquals(RosterQualificationStatus.QUALIFIED, batting.rateQualification(roster)!!.status(batter))
        assertEquals(RosterQualificationStatus.BELOW, batting.copy(statsSquad = 2).rateQualification(roster)!!.status(batter))
        val pitching = options(RosterDisplay.PITCHING_STATS, "ERA")
        val reliever = batter.copy(position = PlayerPosition.PITCHER)
        assertEquals(RosterQualificationStatus.QUALIFIED, pitching.rateQualification(roster)!!.status(reliever))
        assertEquals(RosterQualificationStatus.BELOW, pitching.copy(statsSquad = 2).rateQualification(roster)!!.status(reliever))
        val fractional = reliever.copy(pitching = mapOf(1 to mapOf("ERA" to cell(1.0), "KAISUU" to cell(29.0))))
        assertEquals(RosterQualificationStatus.BELOW, pitching.rateQualification(roster)!!.status(fractional))
        val truncated = roster.copy(completedGames = mapOf(1 to 12))
        val atThreshold = batter.copy(batting = mapOf(1 to mapOf("AVG" to cell(0.2), "DASEKI" to cell(37.0))))
        assertEquals(RosterQualificationStatus.QUALIFIED, batting.rateQualification(truncated)!!.status(atThreshold))
    }

    @Test
    fun missingStatisticsAndMajorPlayersAreNotMistakenForUnqualifiedDomesticPlayers() {
        val eligible = player(1, 31, 0.200)
        val majorBase = player(2, 100, 0.900)
        val major = majorBase.copy(major = true, batting = majorBase.batting + (3 to majorBase.batting.getValue(1)))
        val missing = player(3, 100, 0.800).copy(batting = emptyMap())
        val below = player(4, 30, 0.700)
        val roster = roster(eligible, major, missing, below)
        val options = options(RosterDisplay.BATTING_STATS, "AVG").selectTeamScope(RosterTeamScope.All)
        val qualification = options.rateQualification(roster)!!
        assertEquals(RosterQualificationStatus.UNKNOWN, qualification.status(major))
        assertEquals(RosterQualificationStatus.UNKNOWN, qualification.status(missing))
        assertEquals(listOf(1, 4, 2, 3), options.players(roster).map { it.playerId })
        assertNull(options.selectTeamScope(RosterTeamScope.Major).rateQualification(roster))
        assertNull(options.rateQualification(roster.copy(completedGames = emptyMap())))
        assertNull(options.rateQualification(roster.copy(completedGames = mapOf(1 to 0))))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rateHeaderClicksShowSeparatorsWithinEachExpandedGroupAndCountingStatsRemoveThem() {
        SwingUtilities.invokeAndWait {
            val roster =
                roster(
                    player(1, 31, 0.200),
                    player(2, 30, 0.900),
                    player(3, 31, 0.300).copy(squad = PlayerSquad.SECOND, position = PlayerPosition.FIRST_BASEMAN),
                    player(4, 30, 0.800).copy(squad = PlayerSquad.SECOND, position = PlayerPosition.FIRST_BASEMAN),
                )
            var options by mutableStateOf(options(RosterDisplay.BATTING_STATS, "HONRUIDA"))
            val scene =
                ImageComposeScene(1200, 760) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        Surface(Modifier.fillMaxSize()) {
                            PlayerRosterScreen(
                                PlayerRosterViewState(phase = SaveDataLoadPhase.LOADED, roster = roster),
                                options,
                                { options = it },
                                {},
                            )
                        }
                    }
                }
            try {
                fun render() = repeat(3) { scene.render().close() }

                fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

                fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }

                fun clickHeader(label: String) {
                    val node =
                        nodes().single {
                            it.config.contains(SemanticsActions.OnClick) &&
                                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.startsWith(label) } == true
                        }
                    scene.sendPointerEvent(PointerEventType.Press, node.boundsInWindow.center)
                    scene.sendPointerEvent(PointerEventType.Release, node.boundsInWindow.center)
                    render()
                }

                fun separators(): Int =
                    nodes().count { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "規定打席未到達" } == true
                    }
                for (grouping in RosterGrouping.entries) {
                    options = options.copy(grouping = grouping)
                    render()
                    assertEquals(0, separators())
                    clickHeader("打率")
                    assertEquals(if (grouping == RosterGrouping.NONE) 1 else 2, separators())
                    clickHeader("本塁")
                    assertEquals(0, separators())
                }
            } finally {
                scene.close()
            }
        }
    }

    private fun options(
        display: RosterDisplay,
        sortKey: String,
    ): PlayerRosterOptions =
        PlayerRosterOptions.forTeam(1).copy(display = display, grouping = RosterGrouping.NONE).updateDisplay {
            it.copy(sortKey = sortKey, visibleColumns = setOf("AVG", "HONRUIDA", "DASEKI"))
        }

    private fun roster(vararg players: PlayerRosterPlayer): PlayerRoster =
        PlayerRoster(emptyList(), emptyList(), players.toList(), completedGames = mapOf(1 to 10, 2 to 20))

    private fun player(
        id: Int,
        plateAppearances: Int,
        average: Double,
    ): PlayerRosterPlayer {
        val batting = mapOf("DASEKI" to cell(plateAppearances.toDouble()), "AVG" to cell(average), "HONRUIDA" to cell(average * 10))
        val pitching = mapOf("KAISUU" to cell(30.0), "ERA" to cell(average))
        return PlayerRosterPlayer(
            id,
            id,
            "選手$id",
            500,
            PlayerPosition.CATCHER,
            25,
            PlayerSquad.FIRST,
            PlayerRegistration.CONTROLLED,
            PlayerInjury.None,
            teamNumber = 1,
            batting = mapOf(1 to batting, 2 to batting),
            pitching = mapOf(1 to pitching, 2 to pitching),
        )
    }

    private fun cell(number: Double): PlayerCell = PlayerCell(number.toString(), number)
}
