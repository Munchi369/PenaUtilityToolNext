package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineupContractsTest {
    private fun development(
        id: Int,
        number: Int = 105,
        restDays: Int = 0,
    ): LineupPlayer =
        fielder(id, PlayerPosition.CATCHER, batting = 99, power = 99, squad = PlayerSquad.SECOND, restDays = restDays)
            .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = number)

    private fun changed(
        contracts: LineupContracts,
        draft: LineupDraft,
        id: Int,
    ): LineupDraft = (contracts.change(draft, id) as EditResult.Applied).draft

    @Test
    fun periodBoundariesMatchTheGameInBothDirections() {
        listOf(212 to true, 213 to false, 308 to false, 309 to true).forEach { (day, allowed) ->
            val snapshot = snapshotOf(fullTeam(listOf(development(201))), day = day)
            val contracts = LineupContracts(snapshot)
            assertEquals(allowed, contracts.preview(snapshot.initial, 201).reason == null)
            val demoted = (LineupEditing(snapshot).demote(snapshot.initial, 116) as EditResult.Applied).draft
            assertEquals(allowed, contracts.preview(demoted, 116).reason == null)
            val bypass = snapshot.initial.withState(201) { it.copy(contract = PlayerContract(PlayerRegistration.CONTROLLED, 29)) }
            assertEquals(allowed, contracts.validate(bypass).isEmpty())
        }
    }

    @Test
    fun allocationUsesSmallestUnusedNumberAcrossAllPendingChangesAndResetReleasesIt() {
        val team =
            fullTeam(listOf(development(201), development(202, 108))).map {
                if (it.id == 2) it.copy(uniformNumber = 80) else it
            }
        val snapshot = snapshotOf(team)
        val contracts = LineupContracts(snapshot)
        assertEquals(1, contracts.preview(snapshot.initial, 201).contract?.uniformNumber)
        val first = changed(contracts, snapshot.initial, 201)
        assertEquals(PlayerSquad.SECOND, first.squad(201))
        assertEquals(29, contracts.preview(first, 202).contract?.uniformNumber)
        val second = changed(contracts, first, 202)
        assertTrue(contracts.validate(second).isEmpty())
        assertEquals(2, LineupEditing(snapshot).changeCount(second))
        val restored = (LineupEditing(snapshot).reset(second, LineupTab.FIELDERS) as EditResult.Applied).draft
        assertEquals(snapshot.initial, restored)
        assertEquals(1, contracts.preview(restored, 202).contract?.uniformNumber)
    }

    @Test
    fun controlledAndDevelopmentLimitsAreEnforced() {
        val farm = (201..240).map { fielder(it, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND) }
        val snapshot = snapshotOf(fullTeam(farm + listOf(development(301), development(302, 108))))
        val contracts = LineupContracts(snapshot)
        val atSeventy = changed(contracts, snapshot.initial, 301)
        assertEquals(70, contracts.counts(atSeventy).controlled)
        assertInstanceOf(EditResult.Rejected::class.java, contracts.change(atSeventy, 302))

        val developmentTeam = (0 until 98).map { development(400 + it, 101 + it) }
        val secondSnapshot = snapshotOf(fullTeam(listOf(farm.first()) + developmentTeam))
        val secondContracts = LineupContracts(secondSnapshot)
        val atNinetyNine = changed(secondContracts, secondSnapshot.initial, 201)
        assertEquals(199, atNinetyNine.contract(201).uniformNumber)
        assertEquals(99, secondContracts.counts(atNinetyNine).development)
        val demoted = (LineupEditing(secondSnapshot).demote(atNinetyNine, 116) as EditResult.Applied).draft
        assertInstanceOf(EditResult.Rejected::class.java, secondContracts.change(demoted, 116))
    }

    @Test
    fun occupiedNumbersAndInvalidContractsCannotBeSaved() {
        val snapshot = snapshotOf(fullTeam(listOf(development(201))))
        val contracts = LineupContracts(snapshot)
        val duplicate = snapshot.initial.withState(201) { it.copy(contract = snapshot.initial.contract(1)) }
        assertTrue(contracts.validate(duplicate).any { it.contains("重複") })
        val invalid = snapshot.initial.withState(201) { it.copy(contract = PlayerContract(PlayerRegistration.DEVELOPMENT, 100)) }
        assertTrue(contracts.validate(invalid).any { it.contains("背番号") })
        val occupied =
            (0..99).associate { 1000 + it to snapshot.initial.state(1).copy(contract = PlayerContract(PlayerRegistration.CONTROLLED, it)) }
        val exhausted = snapshot.initial.copy(players = occupied + snapshot.initial.players)
        assertEquals(null, contracts.preview(exhausted, 201).contract)
    }

    @Test
    fun developmentIsExcludedFromPromotionAndAutomaticSelectionUntilRegistered() {
        val snapshot = snapshotOf(fullTeam(listOf(development(201))))
        val editing = LineupEditing(snapshot)
        assertInstanceOf(EditResult.Rejected::class.java, editing.promote(snapshot.initial, 201))
        assertInstanceOf(EditResult.Rejected::class.java, editing.placeInLineup(snapshot.initial, LineupMode.DH, 0, 201))
        val arranger = LineupAutoArranger(snapshot)
        val arranged = (arranger.arrangeFielders(snapshot.initial, LineupMode.DH, emptyMap()) as EditResult.Applied).draft
        assertFalse(arranged.inAnyLineup(201))
        val registered = changed(LineupContracts(snapshot), snapshot.initial, 201)
        val promoted = (editing.placeInLineup(registered, LineupMode.DH, 0, 201) as EditResult.Applied).draft
        assertEquals(PlayerSquad.FIRST, promoted.squad(201))
        assertTrue(editing.validate(promoted).isEmpty())
    }

    @Test
    fun developmentPitcherIsExcludedFromRolesUntilRegistered() {
        val developmentPitcher =
            pitcher(201, 9999, 100, 99, squad = PlayerSquad.SECOND)
                .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = 101)
        val snapshot = snapshotOf(fullTeam(listOf(developmentPitcher)))
        val editing = LineupEditing(snapshot)
        assertInstanceOf(EditResult.Rejected::class.java, editing.placeInRole(snapshot.initial, PitchingRole.STARTER, 0, 201))
        val arranger = LineupAutoArranger(snapshot)
        val arranged = (arranger.arrangePitchers(snapshot.initial, emptyMap()) as EditResult.Applied).draft
        assertFalse(arranged.inAnyRole(201))
        val registered = changed(LineupContracts(snapshot), snapshot.initial, 201)
        val promoted = (editing.placeInRole(registered, PitchingRole.STARTER, 0, 201) as EditResult.Applied).draft
        assertEquals(PlayerSquad.FIRST, promoted.squad(201))
        assertTrue(editing.validate(promoted).isEmpty())
    }

    @Test
    fun contractRoundTripCannotClearThePromotionWait() {
        val snapshot = snapshotOf(fullTeam(listOf(development(201, restDays = 5))))
        val contracts = LineupContracts(snapshot)
        val registered = changed(contracts, snapshot.initial, 201)
        val roundTrip = changed(contracts, changed(contracts, registered, 201), 201)
        assertInstanceOf(EditResult.Rejected::class.java, LineupEditing(snapshot).promote(roundTrip, 201))
        assertEquals(5, snapshot.player(201).restDays)
    }

    @Test
    fun changingToDevelopmentRequiresSecondTeamAndRemovalFromBothModesAndRoles() {
        val snapshot = snapshotOf(fullTeam())
        val contracts = LineupContracts(snapshot)
        assertInstanceOf(EditResult.Rejected::class.java, contracts.change(snapshot.initial, 116))
        val demoted =
            snapshot.initial
                .withState(101) { it.copy(squad = PlayerSquad.SECOND) }
                .withState(1) { it.copy(squad = PlayerSquad.SECOND) }
        assertInstanceOf(EditResult.Rejected::class.java, contracts.change(demoted, 101))
        assertInstanceOf(EditResult.Rejected::class.java, contracts.change(demoted, 1))
        val onlyDh = demoted.copy(normal = demoted.normal.map { if (it.playerId == 101) it.copy(playerId = 110) else it })
        assertInstanceOf(EditResult.Rejected::class.java, contracts.change(onlyDh, 101))
        val cleared = onlyDh.copy(dh = onlyDh.dh.map { if (it.playerId == 101) it.copy(playerId = 110) else it })
        assertEquals(101, changed(contracts, cleared, 101).contract(101).uniformNumber)
    }

    @Test
    fun tabResetRefusesNumberCollisionWithChangesOnTheOtherTab() {
        val farmPitcher = pitcher(20, 800, 80, 50, squad = PlayerSquad.SECOND)
        val snapshot = snapshotOf(fullTeam(listOf(farmPitcher, development(201))))
        val contracts = LineupContracts(snapshot)
        val freed = changed(contracts, snapshot.initial, 20)
        val registered = changed(contracts, freed, 201)
        assertEquals(snapshot.player(20).uniformNumber, registered.contract(201).uniformNumber)
        assertInstanceOf(EditResult.Rejected::class.java, LineupEditing(snapshot).reset(registered, LineupTab.PITCHERS))
        val fieldersReset = (LineupEditing(snapshot).reset(registered, LineupTab.FIELDERS) as EditResult.Applied).draft
        assertInstanceOf(EditResult.Applied::class.java, LineupEditing(snapshot).reset(fieldersReset, LineupTab.PITCHERS))
    }
}
