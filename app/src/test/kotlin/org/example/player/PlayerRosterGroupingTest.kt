package org.example.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerRosterGroupingTest {
    @Test
    fun forTeamDefaultsMatchScopeRules() {
        assertEquals(RosterGrouping.NONE, PlayerRosterOptions().grouping)
        assertEquals(RosterGrouping.NONE, PlayerRosterOptions(teamScope = RosterTeamScope.AllDomestic).grouping)
        assertEquals(RosterGrouping.NONE, PlayerRosterOptions(teamScope = RosterTeamScope.Major).grouping)
        assertEquals(RosterGrouping.SQUAD, PlayerRosterOptions.forTeam(6).grouping)
    }

    @Test
    fun selectGroupingIsSharedAcrossDisplaysAndResetsExpansionOnEnter() {
        val none =
            PlayerRosterOptions.forTeam(6).copy(
                grouping = RosterGrouping.NONE,
                expandedSquads = setOf(PlayerSquad.FIRST),
                expandedPositions = setOf(PlayerPosition.CATCHER),
                revealGroups = false,
            )

        val squad = none.selectGrouping(RosterGrouping.SQUAD)
        assertEquals(RosterGrouping.SQUAD, squad.grouping)
        assertEquals(setOf(PlayerSquad.FIRST, PlayerSquad.SECOND), squad.expandedSquads)
        assertTrue(squad.revealGroups)

        val batting = squad.copy(display = RosterDisplay.BATTING_STATS)
        assertEquals(RosterGrouping.SQUAD, batting.grouping)
        assertEquals(setOf(PlayerSquad.FIRST, PlayerSquad.SECOND), batting.expandedSquads)

        val collapsed = batting.toggleSquadExpanded(PlayerSquad.SECOND)
        assertEquals(setOf(PlayerSquad.FIRST), collapsed.expandedSquads)
        assertEquals(setOf(PlayerSquad.FIRST), collapsed.copy(display = RosterDisplay.BASIC).expandedSquads)

        val position = collapsed.selectGrouping(RosterGrouping.POSITION)
        assertEquals(RosterGrouping.POSITION, position.grouping)
        assertEquals(PlayerPosition.entries.toSet(), position.expandedPositions)
        assertTrue(position.revealGroups)
        assertEquals(setOf(PlayerSquad.FIRST), position.expandedSquads)

        val closed = position.selectGrouping(RosterGrouping.NONE)
        assertEquals(RosterGrouping.NONE, closed.grouping)
        assertFalse(closed.revealGroups)
        assertEquals(PlayerPosition.entries.toSet(), closed.expandedPositions)
    }

    @Test
    fun selectAllTeamsOrMajorDropsSquadGroupingWithoutRestoring() {
        val grouped = PlayerRosterOptions.forTeam(6)
        assertEquals(RosterGrouping.SQUAD, grouped.grouping)

        val allTeams = grouped.selectTeamScope(RosterTeamScope.AllDomestic)
        assertEquals(RosterTeamScope.AllDomestic, allTeams.teamScope)
        assertEquals(RosterGrouping.NONE, allTeams.grouping)

        val backToTeam = allTeams.selectTeamScope(RosterTeamScope.Team(6))
        assertEquals(RosterTeamScope.Team(6), backToTeam.teamScope)
        assertEquals(RosterGrouping.NONE, backToTeam.grouping)

        val major = grouped.selectTeamScope(RosterTeamScope.Major)
        assertEquals(RosterGrouping.NONE, major.grouping)
        assertEquals(RosterGrouping.NONE, major.selectTeamScope(RosterTeamScope.Team(6)).grouping)
    }

    @Test
    fun coercedGroupingDropsStoredSquadOnAllTeamsOrMajor() {
        val invalid = PlayerRosterOptions(teamScope = RosterTeamScope.FirstLeague, grouping = RosterGrouping.SQUAD)
        assertEquals(RosterGrouping.NONE, invalid.coercedGrouping().grouping)

        val major = PlayerRosterOptions(teamScope = RosterTeamScope.Major, grouping = RosterGrouping.SQUAD, revealGroups = true)
        val coerced = major.coercedGrouping()
        assertEquals(RosterGrouping.NONE, coerced.grouping)
        assertFalse(coerced.revealGroups)
    }

    @Test
    fun allTeamsAndMajorKeepPositionGroupingAndHideSquadChoice() {
        val positioned =
            PlayerRosterOptions
                .forTeam(6)
                .selectGrouping(RosterGrouping.POSITION)
                .clearRevealGroups()
        assertEquals(
            listOf(RosterGrouping.NONE, RosterGrouping.SQUAD, RosterGrouping.POSITION),
            RosterGrouping.choicesForScope(RosterTeamScope.Team(6)),
        )

        val allTeams = positioned.selectTeamScope(RosterTeamScope.AllDomestic)
        assertEquals(RosterGrouping.POSITION, allTeams.grouping)
        assertEquals(listOf(RosterGrouping.NONE, RosterGrouping.POSITION), RosterGrouping.choicesForScope(RosterTeamScope.AllDomestic))

        val major = positioned.selectTeamScope(RosterTeamScope.Major)
        assertEquals(RosterGrouping.POSITION, major.grouping)
        assertEquals(listOf(RosterGrouping.NONE, RosterGrouping.POSITION), RosterGrouping.choicesForScope(RosterTeamScope.Major))
        assertEquals(RosterGrouping.POSITION, major.selectGrouping(RosterGrouping.SQUAD).grouping)
    }

    @Test
    fun positionExpansionIsIndependentFromSquadExpansion() {
        val options =
            PlayerRosterOptions
                .forTeam(6)
                .selectGrouping(RosterGrouping.POSITION)
                .clearRevealGroups()
                .togglePositionExpanded(PlayerPosition.PITCHER)
        assertFalse(PlayerPosition.PITCHER in options.expandedPositions)
        assertEquals(setOf(PlayerSquad.FIRST, PlayerSquad.SECOND), options.expandedSquads)

        val squad = options.selectGrouping(RosterGrouping.SQUAD).clearRevealGroups()
        assertEquals(setOf(PlayerSquad.FIRST, PlayerSquad.SECOND), squad.expandedSquads)
        assertFalse(PlayerPosition.PITCHER in squad.expandedPositions)
    }
}
