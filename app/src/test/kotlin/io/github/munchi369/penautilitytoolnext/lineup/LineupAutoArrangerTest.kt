package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineupAutoArrangerTest {
    private val noPins = emptyMap<PinGroup, Set<Int>>()

    private fun arrangeWithSubs(
        snapshot: LineupSnapshot,
        mode: LineupMode = LineupMode.NORMAL,
        pins: Map<PinGroup, Set<Int>> = noPins,
        draft: LineupDraft = snapshot.initial,
    ): LineupDraft {
        val result = LineupAutoArranger(snapshot).arrangeFielders(draft, mode, pins, considerSubPositions = true)
        return (result as EditResult.Applied).draft
    }

    @Test
    fun thirdBasemanWhoLosesHisMainPositionCanBeatTheMainFirstBaseman() {
        val team =
            fullTeam().map { player ->
                when (player.id) {
                    104 -> player.copy(batting = 90, power = 90)
                    112 -> player.copy(batting = 70, power = 70, initialAptitudes = player.initialAptitudes + (FieldSlot.FIRST to 60))
                    else -> player
                }
            }
        val snapshot = snapshotOf(team)
        val off = arrange(snapshot, LineupMode.NORMAL)
        val on = arrangeWithSubs(snapshot)

        assertEquals(102, off.normal.single { it.slot == FieldSlot.FIRST }.playerId)
        assertEquals(104, on.normal.single { it.slot == FieldSlot.THIRD }.playerId)
        assertEquals(112, on.normal.single { it.slot == FieldSlot.FIRST }.playerId)
        assertEquals(snapshot.initial.dh, on.dh)
        assertEquals(snapshot.initial.starters, on.starters)
        assertTrue(LineupEditing(snapshot).validate(on).isEmpty())
    }

    @Test
    fun flexiblePlayerIsAssignedWhereTheWholeLineupBenefitsInsteadOfTakingTheFirstPosition() {
        val team =
            fullTeam().map { player ->
                when (player.id) {
                    101 -> player.copy(batting = 85, power = 85)
                    109 -> player.copy(batting = 90, power = 90, initialAptitudes = player.initialAptitudes + (FieldSlot.FIRST to 80))
                    102, 110 -> player.copy(batting = 20, power = 20)
                    else -> player
                }
            }
        val snapshot = snapshotOf(team)
        val on = arrangeWithSubs(snapshot)

        assertEquals(101, on.normal.single { it.slot == FieldSlot.CATCHER }.playerId)
        assertEquals(109, on.normal.single { it.slot == FieldSlot.FIRST }.playerId)
        val selectedIds = on.normal.mapNotNull { it.playerId }
        assertEquals(8, selectedIds.distinct().size)
    }

    @Test
    fun subSelectionRespectsPinsAndUsesEditedAptitudesAndMainPositions() {
        val snapshot = snapshotOf(fullTeam().map { if (it.id == 112) it.copy(batting = 90, power = 90) else it })
        val editing = LineupEditing(snapshot)
        val edited = editing.setAptitude(snapshot.initial, 112, FieldSlot.FIRST, 1)
        val pinned = mapOf(PinGroup.NORMAL to setOf(104))
        val on = arrangeWithSubs(snapshot, pins = pinned, draft = edited)

        assertEquals(snapshot.initial.normal[3], on.normal[3])
        assertEquals(112, on.normal.single { it.slot == FieldSlot.FIRST }.playerId)
        val withoutSub = editing.setAptitude(edited, 112, FieldSlot.FIRST, 0)
        val noSubLineup = arrangeWithSubs(snapshot, pins = pinned, draft = withoutSub).normal
        assertEquals(102, noSubLineup.single { it.slot == FieldSlot.FIRST }.playerId)
        val newMain = editing.setMainPosition(withoutSub, 112, FieldSlot.FIRST)
        assertEquals(112, arrangeWithSubs(snapshot, pins = pinned, draft = newMain).normal.single { it.slot == FieldSlot.FIRST }.playerId)
    }

    @Test
    fun dhSharesTheSelectionAndUnplayablePositionsAreUsedOnlyWhenNecessary() {
        val team = fullTeam().map { if (it.id == 112) it.copy(batting = 99, power = 99, initialAptitudes = emptyMap()) else it }
        val snapshot = snapshotOf(team)
        val dh = arrangeWithSubs(snapshot, LineupMode.DH)
        assertEquals(112, dh.dh.single { it.slot == FieldSlot.DH }.playerId)
        val selectedIds = dh.dh.mapNotNull { it.playerId }
        assertEquals(9, selectedIds.distinct().size)
        assertEquals(snapshot.initial.normal, dh.normal)

        val noCatchers = snapshotOf(fullTeam().map { it.copy(initialAptitudes = it.initialAptitudes + (FieldSlot.CATCHER to 0)) })
        val editing = LineupEditing(noCatchers)
        val noCatcherMain = listOf(101, 109).fold(noCatchers.initial) { draft, id -> editing.setMainPosition(draft, id, FieldSlot.FIRST) }
        val fallback = arrangeWithSubs(noCatchers, draft = noCatcherMain)
        assertTrue(fallback.normal.single { it.slot == FieldSlot.CATCHER }.playerId != null)
        assertTrue(LineupEditing(noCatchers).validate(fallback).isEmpty())
    }

    @Test
    fun subSelectionExcludesUnavailablePlayersAndLimitsForeignPromotionsAcrossTheCombination() {
        val farm =
            listOf(
                fielder(
                    201,
                    PlayerPosition.THIRD_BASEMAN,
                    90,
                    90,
                    PlayerSquad.SECOND,
                    foreign = true,
                    aptitudes = mapOf(FieldSlot.FIRST to 80),
                ),
                fielder(202, PlayerPosition.THIRD_BASEMAN, 99, 99, PlayerSquad.SECOND, foreign = true),
                fielder(203, PlayerPosition.FIRST_BASEMAN, 99, 99, PlayerSquad.SECOND, restDays = 3),
                fielder(204, PlayerPosition.FIRST_BASEMAN, 99, 99, PlayerSquad.SECOND, injury = PlayerInjury.Injured(5)),
                fielder(205, PlayerPosition.FIRST_BASEMAN, 99, 99, PlayerSquad.SECOND)
                    .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = 105),
            )
        val snapshot = snapshotOf(fullTeam(farm).map { if (it.id in 1..3) it.copy(foreign = true) else it })
        val on = arrangeWithSubs(snapshot)

        assertEquals(202, on.normal.single { it.slot == FieldSlot.THIRD }.playerId)
        assertEquals(PlayerSquad.FIRST, on.squad(202))
        (201..205).filter { it != 202 }.forEach { assertFalse(on.inAnyLineup(it)) }
        assertEquals(PlayerSquad.SECOND, on.squad(116))
        assertTrue(LineupEditing(snapshot).validate(on).isEmpty())
    }

    @Test
    fun equalCombinationsPreferDefenseThenMainPositionsThenLowerIds() {
        val team =
            fullTeam().map { player ->
                when (player.id) {
                    109 -> player.copy(batting = 60, power = 60, initialAptitudes = player.initialAptitudes + (FieldSlot.CATCHER to 90))
                    else -> player
                }
            }
        val snapshot = snapshotOf(team)
        assertEquals(109, arrangeWithSubs(snapshot).normal.single { it.slot == FieldSlot.CATCHER }.playerId)
        val tied = snapshot.initial.withState(109) { it.copy(aptitudes = snapshot.player(101).initialAptitudes) }
        assertEquals(101, arrangeWithSubs(snapshot, draft = tied).normal.single { it.slot == FieldSlot.CATCHER }.playerId)
        assertEquals(
            arrangeWithSubs(snapshot, draft = tied),
            arrangeWithSubs(snapshotOf(team.reversed()), draft = tied),
        )
    }

    private fun arrange(
        snapshot: LineupSnapshot,
        mode: LineupMode,
        pins: Map<PinGroup, Set<Int>> = noPins,
    ): LineupDraft = (LineupAutoArranger(snapshot).arrangeFielders(snapshot.initial, mode, pins) as EditResult.Applied).draft

    @Test
    fun mainPositionPlayersWithTheBestHittingStartAndFollowTheStandardOrder() {
        val snapshot = snapshotOf(fullTeam())

        val lineup = arrange(snapshot, LineupMode.NORMAL).normal

        assertEquals(
            listOf(104, 106, 102, 101, 103, 105, 107, 108, null),
            lineup.map { it.playerId },
        )
        assertEquals(FieldSlot.PITCHER, lineup[8].slot)
        lineup.filter { it.playerId != null }.forEach { entry ->
            assertEquals(snapshot.player(entry.playerId!!).position, entry.slot.mainPosition)
        }
    }

    @Test
    fun openPositionsAreFilledByTheHighestSubPositionAptitude() {
        val team =
            fullTeam()
                .filterNot { it.id == 101 || it.id == 109 }
                .map { player ->
                    when (player.id) {
                        110 -> player.copy(initialAptitudes = player.initialAptitudes + (FieldSlot.CATCHER to 70))
                        111 -> player.copy(initialAptitudes = player.initialAptitudes + (FieldSlot.CATCHER to 30))
                        else -> player
                    }
                }
        val snapshot =
            snapshotOf(
                team,
                normal =
                    FieldSlot.defense.mapIndexed { index, slot -> LineupEntry(if (index == 0) 110 else 101 + index, slot) } +
                        LineupEntry(null, FieldSlot.PITCHER),
                dh =
                    FieldSlot.defense.mapIndexed { index, slot -> LineupEntry(if (index == 0) 110 else 101 + index, slot) } +
                        LineupEntry(111, FieldSlot.DH),
            )

        val lineup = arrange(snapshot, LineupMode.NORMAL).normal

        assertEquals(110, lineup.single { it.slot == FieldSlot.CATCHER }.playerId)
        assertEquals(102, lineup.single { it.slot == FieldSlot.FIRST }.playerId)
    }

    @Test
    fun pinnedPlayersKeepTheirBattingSlotAndPosition() {
        val normal =
            listOf(LineupEntry(116, FieldSlot.RIGHT)) +
                FieldSlot.defense.dropLast(1).mapIndexed { index, slot -> LineupEntry(101 + index, slot) } +
                LineupEntry(null, FieldSlot.PITCHER)
        val snapshot = snapshotOf(fullTeam(), normal = normal)

        val lineup = arrange(snapshot, LineupMode.NORMAL, mapOf(PinGroup.NORMAL to setOf(116))).normal

        assertEquals(LineupEntry(116, FieldSlot.RIGHT), lineup[0])
        assertFalse(lineup.any { it.playerId == 108 })
    }

    @Test
    fun injuredPlayersAreSkippedAndDhTakesTheBestRemainingHitter() {
        val team = fullTeam().map { if (it.id == 101) it.copy(injury = PlayerInjury.Injured(10)) else it }
        val snapshot = snapshotOf(team)

        val lineup = arrange(snapshot, LineupMode.DH).dh

        assertEquals(109, lineup.single { it.slot == FieldSlot.CATCHER }.playerId)
        assertEquals(110, lineup.single { it.slot == FieldSlot.DH }.playerId)
        assertFalse(lineup.any { it.slot == FieldSlot.PITCHER })
    }

    @Test
    fun strongerFarmPlayerIsPromotedAndTheWeakestUnusedFielderIsDemoted() {
        val farm = fielder(201, PlayerPosition.CATCHER, batting = 99, power = 99, squad = PlayerSquad.SECOND)
        val resting = fielder(202, PlayerPosition.FIRST_BASEMAN, batting = 99, power = 99, squad = PlayerSquad.SECOND, restDays = 3)
        val snapshot = snapshotOf(fullTeam(listOf(farm, resting)))

        val draft = arrange(snapshot, LineupMode.NORMAL)

        assertEquals(201, draft.normal.single { it.slot == FieldSlot.CATCHER }.playerId)
        assertFalse(draft.normal.any { it.playerId == 202 })
        assertEquals(PlayerSquad.FIRST, draft.squad(201))
        assertEquals(PlayerSquad.SECOND, draft.squad(116))
    }

    @Test
    fun foreignFarmPlayersAreSkippedWhenTheForeignLimitIsFull() {
        val team =
            fullTeam().map { if (it.id in listOf(1, 2, 3, 4)) it.copy(foreign = true) else it } +
                fielder(201, PlayerPosition.CATCHER, batting = 99, power = 99, squad = PlayerSquad.SECOND, foreign = true)
        val snapshot = snapshotOf(team)

        val draft = arrange(snapshot, LineupMode.NORMAL)

        assertEquals(101, draft.normal.single { it.slot == FieldSlot.CATCHER }.playerId)
        assertEquals(PlayerSquad.SECOND, draft.squad(201))
    }

    @Test
    fun pitchersSplitByAptitudeAndSortByOverall() {
        val snapshot = snapshotOf(fullTeam(), starters = List(9) { null }, relievers = List(9) { null })

        val draft = (LineupAutoArranger(snapshot).arrangePitchers(snapshot.initial, noPins) as EditResult.Applied).draft

        assertEquals(listOf(1, 2, 3, 4, 5, 6, null, null, null), draft.starters)
        assertEquals(listOf(8, 9, 10, 11, 12, 13, null, null, null), draft.relievers)
    }

    @Test
    fun injuredAndOverLimitForeignPitchersAreSkipped() {
        val team =
            fullTeam().map { player ->
                when (player.id) {
                    1 -> player.copy(injury = PlayerInjury.Injured(8))
                    in 101..104 -> player.copy(foreign = true)
                    else -> player
                }
            } + pitcher(301, 990, 90, 50, squad = PlayerSquad.SECOND, foreign = true)
        val snapshot = snapshotOf(team, starters = List(9) { null }, relievers = List(9) { null })

        val draft = (LineupAutoArranger(snapshot).arrangePitchers(snapshot.initial, noPins) as EditResult.Applied).draft

        assertFalse(1 in draft.starters)
        assertFalse(301 in draft.starters)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), draft.starters.take(6))
    }

    @Test
    fun tiesGoToTheLowerPlayerId() {
        val team = fullTeam().map { if (it.id == 109) it.copy(batting = 60, power = 60) else it }
        val snapshot = snapshotOf(team)

        val lineup = arrange(snapshot, LineupMode.NORMAL).normal

        assertEquals(101, lineup.single { it.slot == FieldSlot.CATCHER }.playerId)
    }

    @Test
    fun missingStarterTypesAreFilledByStarterAptitudeAndPinsStay() {
        val team =
            fullTeam().map { player ->
                when {
                    player.id in 4..7 -> player.copy(starterAptitude = 40, relieverAptitude = 70)
                    player.id == 13 -> player.copy(starterAptitude = 65)
                    else -> player
                }
            }
        val snapshot = snapshotOf(team, starters = listOf(12) + List(8) { null }, relievers = List(9) { null })

        val draft =
            (
                LineupAutoArranger(
                    snapshot,
                ).arrangePitchers(snapshot.initial, mapOf(PinGroup.PITCHING to setOf(12))) as EditResult.Applied
            ).draft

        assertEquals(12, draft.starters[0])
        assertEquals(listOf(12, 1, 2, 3, 13), draft.starters.take(5))
        assertTrue(draft.relievers.filterNotNull().none { it == 12 || it == 13 })
    }
}
