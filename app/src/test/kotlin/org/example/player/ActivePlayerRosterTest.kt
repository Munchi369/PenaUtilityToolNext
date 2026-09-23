package org.example.player

import androidx.compose.ui.graphics.Color
import org.example.savedata.CopiedDerbyTestDatabase
import org.example.savedata.DerbySaveDatabaseAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executor

class ActivePlayerRosterTest {
    @Test
    fun copiedReferenceIncludesOtherTeamsMajorAbilitiesAndBothSeasonSquads() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val result = DerbyPlayerRosterLoader(DerbySaveDatabaseAccess()).load(db.path)
            assertTrue(result is PlayerRosterLoadResult.Loaded, result.toString())
            val roster = (result as PlayerRosterLoadResult.Loaded).roster
            assertEquals(12, roster.teams.size)
            assertEquals(6, roster.currentTeam)
            assertEquals(898, roster.players.size)
            assertEquals(
                898,
                roster.players
                    .map { it.playerId }
                    .distinct()
                    .size,
            )
            val allDomestic = PlayerRosterOptions(teamScope = RosterTeamScope.AllDomestic).players(roster)
            assertEquals(893, allDomestic.size)
            val individualTeamSizes =
                (1..12).map { teamNumber ->
                    val teamPlayers = PlayerRosterOptions(teamScope = RosterTeamScope.Team(teamNumber)).players(roster)
                    assertTrue(teamPlayers.isNotEmpty(), "球団$teamNumber")
                    assertTrue(teamPlayers.all { !it.major && it.teamNumber == teamNumber }, "球団$teamNumber")
                    teamPlayers.size
                }
            assertEquals(allDomestic.size, individualTeamSizes.sum())
            assertEquals(445, PlayerRosterOptions(teamScope = RosterTeamScope.FirstLeague).players(roster).size)
            assertEquals(448, PlayerRosterOptions(teamScope = RosterTeamScope.SecondLeague).players(roster).size)
            assertEquals(898, PlayerRosterOptions(teamScope = RosterTeamScope.All).players(roster).size)
            val major = PlayerRosterOptions(teamScope = RosterTeamScope.Major).players(roster)
            assertEquals(5, major.size)
            assertTrue(major.all { it.information["READY"] == null })
            assertTrue(major.any { 3 in it.batting || 3 in it.pitching })
            val pitcher = roster.players.first { !it.major && it.position == PlayerPosition.PITCHER }
            assertTrue(pitcher.information.getValue("SUTAMINA").abilityStyle != null)
            assertTrue(pitcher.information.getValue("READY").number != null)
            assertTrue(roster.players.any { 1 in it.batting && 2 in it.batting })
            assertTrue(roster.players.any { it.batting[1]?.get("DAJUN1") != null })
            assertTrue(allDomestic.zipWithNext().all { (a, b) -> a.overall >= b.overall })
        }
    }

    @Test
    fun filteringAndStatsArmyAreIndependentAndMissingStatsSortLast() {
        val first = player(1).copy(squad = PlayerSquad.FIRST, batting = mapOf(1 to mapOf("DASEKI" to numberCell(12))))
        val second = player(2).copy(squad = PlayerSquad.SECOND, batting = mapOf(1 to mapOf("DASEKI" to numberCell(120))))
        val absent = player(3).copy(squad = PlayerSquad.SECOND)
        val roster = PlayerRoster(listOf(first), listOf(second, absent))
        val options = PlayerRosterOptions(display = RosterDisplay.BATTING_STATS, squad = PlayerSquad.SECOND)
        assertEquals(listOf(2, 3), options.players(roster).map { it.playerId })
        assertEquals(listOf(2, 3), options.sort("DASEKI").players(roster).map { it.playerId })
        assertEquals("120", second.cell(RosterDisplay.BATTING_STATS, "DASEKI", 1).text)
        assertEquals("—", second.cell(RosterDisplay.BATTING_STATS, "DASEKI", 2).text)
        assertEquals(listOf(2), options.copy(search = "選手2").players(roster).map { it.playerId })
    }

    @Test
    fun allScopeIncludesMajorUntilADomesticOnlyFilterIsApplied() {
        val domestic =
            player(1).copy(
                squad = PlayerSquad.FIRST,
                registration = PlayerRegistration.CONTROLLED,
                batting =
                    mapOf(
                        1 to mapOf("DASEKI" to numberCell(10)),
                        2 to mapOf("DASEKI" to numberCell(20)),
                    ),
            )
        val major =
            player(2).copy(
                teamNumber = 13,
                teamName = "メジャー",
                major = true,
                batting = mapOf(3 to mapOf("ITIGUN" to numberCell(99), "DASEKI" to numberCell(12))),
            )
        val roster = PlayerRoster(listOf(domestic, major), emptyList())

        val all = PlayerRosterOptions(teamScope = RosterTeamScope.All)
        assertEquals(listOf(1, 2), all.players(roster).map { it.playerId })
        assertEquals(listOf(1), all.copy(squad = PlayerSquad.FIRST).players(roster).map { it.playerId })
        assertEquals(listOf(1), all.copy(registration = PlayerRegistration.CONTROLLED).players(roster).map { it.playerId })
        assertEquals(
            listOf(2),
            all
                .copy(positionFilter = RosterPositionFilter.MainPosition(PlayerPosition.CATCHER), search = "2")
                .players(roster)
                .map { it.playerId },
        )
        assertEquals("—", major.cell(RosterDisplay.BATTING_STATS, "ITIGUN", 1).text)
        assertEquals("10", domestic.cell(RosterDisplay.BATTING_STATS, "DASEKI", 1).text)
        assertEquals("20", domestic.cell(RosterDisplay.BATTING_STATS, "DASEKI", 2).text)
        assertEquals("12", major.cell(RosterDisplay.BATTING_STATS, "DASEKI", 1).text)
        assertEquals("12", major.cell(RosterDisplay.BATTING_STATS, "DASEKI", 2).text)
    }

    @Test
    fun positionFilterIsMainPositionOnlyAndUnlinkedDisplayDoesNotChangeWhoIsListed() {
        val pitcher = player(1).copy(position = PlayerPosition.PITCHER)
        val catcher = player(2).copy(position = PlayerPosition.CATCHER)
        val shortstop = player(3).copy(position = PlayerPosition.SHORTSTOP)
        val roster = PlayerRoster(listOf(pitcher, catcher, shortstop), emptyList())
        val options = PlayerRosterOptions(teamScope = RosterTeamScope.AllDomestic)
        assertEquals(listOf(1, 2, 3), options.players(roster).map { it.playerId })
        assertEquals(
            listOf(1, 2, 3),
            options.copy(display = RosterDisplay.PITCHING_ABILITY).players(roster).map { it.playerId },
        )
        assertEquals(
            listOf(2, 3),
            options.copy(positionFilter = RosterPositionFilter.Fielders).players(roster).map { it.playerId },
        )
        assertEquals(
            listOf(1),
            options.copy(positionFilter = RosterPositionFilter.MainPosition(PlayerPosition.PITCHER)).players(roster).map { it.playerId },
        )
        assertEquals(
            listOf(2),
            options.copy(positionFilter = RosterPositionFilter.MainPosition(PlayerPosition.CATCHER)).players(roster).map { it.playerId },
        )
        assertEquals(
            listOf(2, 3),
            options
                .copy(positionFilter = RosterPositionFilter.Fielders, display = RosterDisplay.PITCHING_ABILITY)
                .players(roster)
                .map { it.playerId },
        )
    }

    @Test
    fun automaticDisplaySwitchKeepsTheDisplayFamilyAndClearingThePositionKeepsTheDisplay() {
        val pitcher = RosterPositionFilter.MainPosition(PlayerPosition.PITCHER)
        val fielder = RosterPositionFilter.MainPosition(PlayerPosition.CATCHER)

        val pitchingAbility =
            PlayerRosterOptions(display = RosterDisplay.BATTING_ABILITY)
                .toggleAutomaticDisplaySwitch()
                .selectPosition(pitcher)
        assertEquals(RosterDisplay.PITCHING_ABILITY, pitchingAbility.display)
        assertEquals(RosterDisplay.BATTING_ABILITY, pitchingAbility.selectPosition(fielder).display)

        val pitchingStats = pitchingAbility.copy(display = RosterDisplay.BATTING_STATS).selectPosition(pitcher)
        assertEquals(RosterDisplay.PITCHING_STATS, pitchingStats.display)
        assertEquals(RosterDisplay.BATTING_STATS, pitchingStats.selectPosition(RosterPositionFilter.Fielders).display)

        val basic = pitchingStats.copy(display = RosterDisplay.BASIC).selectPosition(pitcher)
        assertEquals(RosterDisplay.BASIC, basic.display)
        assertEquals(
            RosterDisplay.PITCHING_ABILITY,
            pitchingAbility
                .copy(display = RosterDisplay.PITCHING_ABILITY)
                .selectPosition(null)
                .display,
        )

        val cleared = pitchingAbility.selectPosition(null)
        assertEquals(RosterDisplay.PITCHING_ABILITY, cleared.display)
        assertEquals(null, cleared.positionFilter)
        assertEquals(cleared, cleared.selectDisplay(RosterDisplay.PITCHING_ABILITY))
    }

    @Test
    fun automaticDisplaySwitchLinksTabSelectionsBackToPositionFilters() {
        val catcher = RosterPositionFilter.MainPosition(PlayerPosition.CATCHER)
        val pitcher = RosterPositionFilter.MainPosition(PlayerPosition.PITCHER)
        val linked =
            PlayerRosterOptions(
                display = RosterDisplay.BASIC,
                positionFilter = catcher,
                automaticDisplaySwitch = true,
            )

        val batting = linked.selectDisplay(RosterDisplay.BATTING_STATS)
        assertEquals(catcher, batting.positionFilter)

        val battingWithoutAFilter =
            PlayerRosterOptions(automaticDisplaySwitch = true).selectDisplay(RosterDisplay.BATTING_STATS)
        assertEquals(RosterPositionFilter.Fielders, battingWithoutAFilter.positionFilter)

        val pitching = batting.selectDisplay(RosterDisplay.PITCHING_STATS)
        assertEquals(pitcher, pitching.positionFilter)

        val fielding = pitching.selectDisplay(RosterDisplay.BATTING_ABILITY)
        assertEquals(RosterPositionFilter.Fielders, fielding.positionFilter)

        val basic = fielding.selectDisplay(RosterDisplay.BASIC)
        assertEquals(RosterPositionFilter.Fielders, basic.positionFilter)

        val unlinked = linked.copy(automaticDisplaySwitch = false).selectDisplay(RosterDisplay.PITCHING_ABILITY)
        assertEquals(catcher, unlinked.positionFilter)
    }

    @Test
    fun enablingAutomaticDisplaySwitchImmediatelyUsesTheSelectedPosition() {
        val options =
            PlayerRosterOptions(
                display = RosterDisplay.BATTING_ABILITY,
                positionFilter = RosterPositionFilter.MainPosition(PlayerPosition.PITCHER),
            )
        val enabled = options.toggleAutomaticDisplaySwitch()
        assertTrue(enabled.automaticDisplaySwitch)
        assertEquals(RosterDisplay.PITCHING_ABILITY, enabled.display)
    }

    @Test
    fun gameMetricsUseOutsAndDistinguishZeroFromUnavailableRates() {
        val pitching = GameSeasonMetrics.pitching(mapOf("KAISUU" to 20, "JISEKI" to 2, "HIAN" to 5, "YOSI" to 2))
        assertEquals("6 2/3", pitching.getValue("KAISUU").text)
        assertEquals("2.700", pitching.getValue("ERA").text)
        assertEquals("1.050", pitching.getValue("WHIP").text)
        val empty = GameSeasonMetrics.batting(mapOf("DASUU" to 0, "ANDA" to 0, "SISI" to 3, "GIHI" to 0))
        assertEquals("0", empty.getValue("ANDA").text)
        assertEquals("---", empty.getValue("OBP").text)
        assertEquals(".333", GameSeasonMetrics.batting(mapOf("DASUU" to 3, "ANDA" to 1)).getValue("AVG").text)
        assertFalse("AVG" in GameSeasonMetrics.batting(mapOf("DASUU" to 3)))
    }

    @Test
    fun everyProfileColumnSortsByItsDomainOrderWithUnknownAndAbsentValuesLast() {
        val defaults = PlayerRosterOptions()
        val scenarios =
            listOf(
                Triple("NENME", mapOf("NENME" to 1), mapOf("NENME" to 2)),
                Triple("SHOZOKU", mapOf("SHOZOKU" to 1), mapOf("SHOZOKU" to 4)),
                Triple("DORAJUN", mapOf("DORAJUN" to 8), mapOf("DORAJUN" to 101)),
                Triple("SHUSSIN", mapOf("SHUSSIN" to 1), mapOf("SHUSSIN" to 55)),
                Triple("HEIGHT", mapOf("HEIGHT" to 170), mapOf("HEIGHT" to 190)),
                Triple("WEIGHT", mapOf("WEIGHT" to 70), mapOf("WEIGHT" to 90)),
                Triple("THROWS_BATS", mapOf("KIKIUDE" to 1, "KIKIDASEKI" to 1), mapOf("KIKIUDE" to 2, "KIKIDASEKI" to 2)),
                Triple("FORM", mapOf("FORM" to 11), mapOf("FORM" to 21)),
            )

        for ((key, lowerValues, higherValues) in scenarios) {
            val roster =
                PlayerRoster(
                    listOf(
                        player(1).copy(information = playerProfileCells(lowerValues)),
                        player(2).copy(information = playerProfileCells(higherValues)),
                        player(3).copy(information = mapOf(key to unknownCell())),
                        player(4),
                    ),
                    emptyList(),
                )

            fun sortedIds(descending: Boolean): List<Int> =
                defaults
                    .copy(
                        displays =
                            defaults.displays +
                                (
                                    RosterDisplay.BASIC to
                                        defaults.current.copy(sortKey = key, descending = descending)
                                ),
                    ).players(roster)
                    .map { it.playerId }

            assertEquals(listOf(1, 2), sortedIds(descending = false).take(2), key)
            assertEquals(listOf(2, 1), sortedIds(descending = true).take(2), key)
            assertEquals(setOf(3, 4), sortedIds(descending = false).takeLast(2).toSet(), key)
            assertEquals(setOf(3, 4), sortedIds(descending = true).takeLast(2).toSet(), key)
        }
    }

    @Test
    fun readinessAndFaFollowGameRules() {
        assertEquals(36, readyStamina(80, 7, PlayerSquad.FIRST, 309))
        assertEquals(32, readyStamina(80, 1, PlayerSquad.SECOND, 309))
        assertEquals(26, readyStamina(80, 7, PlayerSquad.FIRST, 316))
        assertEquals("残り41試合", faCell(3, 960, 100, false).text)
        assertEquals("今季取得圏内", faCell(3, 1050, 100, false).text)
        assertEquals("残り523試合", faCell(3, 1050, 10, false).text)
        assertEquals("設定依存", faCell(0, 960, 100, false).text)
        assertEquals("—", faCell(3, 960, 100, true).text)
        assertEquals(
            listOf("S90", "A80", "B70", "C60", "D50", "E40", "F30", "G29"),
            listOf(90, 80, 70, 60, 50, 40, 30, 29).map { abilityCell(it).text },
        )
        assertEquals(PlayerCell("", 0.0), abilityCell(0))
        assertEquals(PlayerCell("スライダー A85", 85.0, AbilityStyle(AbilityRank.A, 6)), pitchAbilityCell("スライダー", 85))
        assertEquals(missingCell("スライダー —"), pitchAbilityCell("スライダー", null))
        assertEquals(PlayerCell("スライダー", 0.0), pitchAbilityCell("スライダー", 0))
        assertEquals(Color(0xFFFF1493), gameAbilityColor(AbilityRank.S))
        assertEquals(Color(0xFFFF1493), gameAbilityColor(AbilityRank.A))
        assertEquals(Color(0xFFFF0000), gameAbilityColor(AbilityRank.B))
        assertEquals(Color(0xFFFF4500), gameAbilityColor(AbilityRank.C))
        assertEquals(Color(0xFFEBB200), gameAbilityColor(AbilityRank.D))
        assertEquals(Color(0xFF3BD70A), gameAbilityColor(AbilityRank.E))
        assertEquals(Color(0xFF007FEB), gameAbilityColor(AbilityRank.F))
        assertEquals(Color(0xFF858585), gameAbilityColor(AbilityRank.G))
    }

    @Test
    fun reloadKeepsOptionsAndSwitchingSaveResetsThem(
        @TempDir root: Path,
    ) {
        val controller =
            PlayerRosterController(
                PlayerRosterLoader { PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6)) },
                Executor(Runnable::run),
                Executor(Runnable::run),
            )
        controller.load(root.resolve("first"))
        controller.updateOptions(
            controller.options.copy(
                search = "選手",
                display = RosterDisplay.PITCHING_STATS,
                statsSquad = 2,
                automaticDisplaySwitch = true,
            ),
        )
        controller.reload()
        assertEquals("選手", controller.options.search)
        assertEquals(2, controller.options.statsSquad)
        assertTrue(controller.options.automaticDisplaySwitch)
        controller.load(root.resolve("second"))
        assertEquals("", controller.options.search)
        assertEquals(RosterDisplay.BASIC, controller.options.display)
        assertEquals(RosterTeamScope.Team(6), controller.options.teamScope)
        assertFalse(controller.options.automaticDisplaySwitch)
    }

    private fun player(id: Int) =
        PlayerRosterPlayer(
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
            teamName = "A",
        )
}
