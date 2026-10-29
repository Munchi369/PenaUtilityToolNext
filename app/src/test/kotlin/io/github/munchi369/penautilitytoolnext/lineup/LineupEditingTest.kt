package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineupEditingTest {
    @Test
    fun restDaysFollowTheGameDemotionRule() {
        assertEquals(10, LineupRules.restDaysAfterDemotion(100))
        assertNull(LineupRules.restDaysAfterDemotion(86))
        assertNull(LineupRules.restDaysAfterDemotion(87))
        assertNull(LineupRules.restDaysAfterDemotion(279))
        assertEquals(10, LineupRules.restDaysAfterDemotion(278))
    }

    @Test
    fun swappingBattingOrderKeepsEachPlayersPosition() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)

        val draft = editing.swapLineup(snapshot.initial, LineupMode.NORMAL, 0, 3)

        assertEquals(LineupEntry(104, FieldSlot.THIRD), draft.normal[0])
        assertEquals(LineupEntry(101, FieldSlot.CATCHER), draft.normal[3])
        assertEquals(snapshot.initial.dh, draft.dh)
    }

    @Test
    fun benchPlayerDroppedOnLineupTakesOverThePosition() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)

        val result = editing.placeInLineup(snapshot.initial, LineupMode.NORMAL, 2, 111) as EditResult.Applied

        assertEquals(LineupEntry(111, FieldSlot.SECOND), result.draft.normal[2])
        assertTrue(editing.bench(result.draft, LineupMode.NORMAL).any { it.id == 103 })
    }

    @Test
    fun changingPositionSwapsWithThePlayerWhoHadIt() {
        val snapshot = snapshotOf(fullTeam())
        val draft = LineupEditing(snapshot).changePosition(snapshot.initial, LineupMode.NORMAL, 0, FieldSlot.SHORT)

        assertEquals(FieldSlot.SHORT, draft.normal[0].slot)
        assertEquals(FieldSlot.CATCHER, draft.normal[4].slot)
    }

    @Test
    fun promotionIntoAFullFirstTeamDemotesTheWeakestSameTypePlayerOffTheLineup() {
        val farm = fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND)
        val snapshot = snapshotOf(fullTeam(listOf(farm)))
        val editing = LineupEditing(snapshot)

        val result = editing.placeInLineup(snapshot.initial, LineupMode.NORMAL, 0, 201) as EditResult.Applied

        assertEquals(PlayerSquad.FIRST, result.draft.squad(201))
        assertEquals(PlayerSquad.SECOND, result.draft.squad(116))
        assertEquals(29, editing.counts(result.draft).total)
        assertEquals(16, editing.counts(result.draft).fielders)
    }

    @Test
    fun resetRestoresOnlyTheTabsOwnEdits() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)
        val fielders = editing.swapLineup(snapshot.initial, LineupMode.NORMAL, 0, 1)
        val both = editing.swapRoles(fielders, PitchingRole.STARTER, 0, PitchingRole.STARTER, 1)

        val reset = (editing.reset(both, LineupTab.FIELDERS) as EditResult.Applied).draft

        assertEquals(snapshot.initial.normal, reset.normal)
        assertEquals(both.starters, reset.starters)
    }

    @Test
    fun resetIsRejectedWhenItWouldOverfillTheFirstTeam() {
        val team =
            fullTeam().filterNot { it.id == 13 } + pitcher(301, 500, 50, 80, squad = PlayerSquad.SECOND).copy(uniformNumber = 80) +
                pitcher(302, 490, 50, 80, squad = PlayerSquad.SECOND).copy(uniformNumber = 81)
        val snapshot = snapshotOf(team)
        val editing = LineupEditing(snapshot)
        var draft = (editing.demote(snapshot.initial, 116) as EditResult.Applied).draft
        draft = (editing.promote(draft, 301) as EditResult.Applied).draft
        draft = (editing.promote(draft, 302) as EditResult.Applied).draft

        val result = editing.reset(draft, LineupTab.FIELDERS)

        assertTrue((result as EditResult.Rejected).reason.contains("投手タブ"))
        assertInstanceOf(EditResult.Applied::class.java, editing.reset(draft, LineupTab.PITCHERS))
    }

    @Test
    fun playersInTheirRestPeriodCannotBePromoted() {
        val resting = fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND, restDays = 4)
        val snapshot = snapshotOf(fullTeam(listOf(resting)))

        val result = LineupEditing(snapshot).promote(snapshot.initial, 201)

        assertTrue((result as EditResult.Rejected).reason.contains("残り4日"))
    }

    @Test
    fun foreignLimitsBlockPromotion() {
        val team =
            fullTeam().map { if (it.id in listOf(101, 102, 103)) it.copy(foreign = true) else it } +
                fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND, foreign = true)
        val snapshot = snapshotOf(team)

        val result = LineupEditing(snapshot).promote(snapshot.initial, 201)

        assertInstanceOf(EditResult.Rejected::class.java, result)
    }

    @Test
    fun startersAndRoleHoldersCannotBeDemoted() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)

        assertInstanceOf(EditResult.Rejected::class.java, editing.demote(snapshot.initial, 101))
        assertInstanceOf(EditResult.Rejected::class.java, editing.demote(snapshot.initial, 1))
        assertInstanceOf(EditResult.Applied::class.java, editing.demote(snapshot.initial, 116))
    }

    @Test
    fun minimumFieldersBlockDemotion() {
        val team = fullTeam().filterNot { it.id in 113..116 }
        val snapshot = snapshotOf(team)

        assertInstanceOf(EditResult.Rejected::class.java, LineupEditing(snapshot).demote(snapshot.initial, 112))
    }

    @Test
    fun rolesSwapAcrossStarterAndReliever() {
        val snapshot = snapshotOf(fullTeam())
        val draft = LineupEditing(snapshot).swapRoles(snapshot.initial, PitchingRole.STARTER, 0, PitchingRole.RELIEVER, 0)

        assertEquals(7, draft.starters[0])
        assertEquals(1, draft.relievers[0])
    }

    @Test
    fun aptitudeCanBeZeroEvenAtTheMainPositionAndIsCappedAtHundred() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)

        val draft = editing.setAptitude(snapshot.initial, 101, FieldSlot.CATCHER, 0)
        val sub = editing.setAptitude(draft, 101, FieldSlot.FIRST, 140)

        assertEquals(0, sub.aptitude(101, FieldSlot.CATCHER))
        assertEquals(100, sub.aptitude(101, FieldSlot.FIRST))
        assertEquals(2, editing.changeCount(sub))
    }

    @Test
    fun mainPositionMovesOnlyForFielders() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)

        val draft = editing.setMainPosition(snapshot.initial, 101, FieldSlot.LEFT)
        val unchanged = editing.setMainPosition(draft, 1, FieldSlot.LEFT)

        assertEquals(PlayerPosition.LEFT_FIELDER, draft.mainPosition(101))
        assertTrue(draft.isMain(101, FieldSlot.LEFT))
        assertEquals(PlayerPosition.PITCHER, unchanged.mainPosition(1))
        assertEquals(1, editing.changeCount(unchanged))
    }

    @Test
    fun pitchingAptitudeChangesTheRoleType() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)

        val draft = editing.setPitchingAptitude(snapshot.initial, 1, PitchingRole.RELIEVER, 95)

        assertEquals(PitchingAptitude(80, 95), draft.pitchingAptitude(1))
        assertFalse(draft.pitchingAptitude(1).starterType)
        assertEquals(1, editing.changeCount(draft))
    }

    @Test
    fun copyingDhLineupToNormalDropsTheDhAndBatsThePitcherNinth() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)
        val dh = editing.swapLineup(snapshot.initial, LineupMode.DH, 0, 8)

        val normal = editing.copyLineup(dh, LineupMode.DH).normal

        assertEquals(dh.dh.filter { it.slot != FieldSlot.DH }, normal.take(8))
        assertEquals(LineupEntry(null, FieldSlot.PITCHER), normal[8])
    }

    @Test
    fun copyingNormalLineupToDhPutsTheDhInThePitchersSlot() {
        val normal =
            listOf(LineupEntry(null, FieldSlot.PITCHER)) + FieldSlot.defense.mapIndexed { index, slot -> LineupEntry(101 + index, slot) }
        val snapshot = snapshotOf(fullTeam(), normal = normal)

        val dh = LineupEditing(snapshot).copyLineup(snapshot.initial, LineupMode.NORMAL).dh

        assertEquals(LineupEntry(109, FieldSlot.DH), dh[0])
        assertEquals(normal.drop(1), dh.drop(1))
    }

    @Test
    fun copyingToDhPicksTheBestBenchHitterWhenTheOldDhIsNowAFielder() {
        val snapshot = snapshotOf(fullTeam())
        val editing = LineupEditing(snapshot)
        val normal = (editing.placeInLineup(snapshot.initial, LineupMode.NORMAL, 0, 109) as EditResult.Applied).draft

        val dh = editing.copyLineup(normal, LineupMode.NORMAL).dh

        assertEquals(LineupEntry(101, FieldSlot.DH), dh[8])
    }

    @Test
    fun validationRejectsDuplicatesMissingStartersAndOverfullRoster() {
        val snapshot = snapshotOf(fullTeam(listOf(fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND))))
        val initial = snapshot.initial
        val broken =
            initial
                .copy(
                    normal = initial.normal.toMutableList().also { it[1] = it[1].copy(playerId = 101) },
                    starters = List(9) { null },
                ).withState(201) { it.copy(squad = PlayerSquad.FIRST) }

        val errors = LineupEditing(snapshot).validate(broken)

        assertTrue(errors.any { it.contains("同じ選手") })
        assertTrue(errors.any { it.contains("先発が1人もいません") })
        assertTrue(errors.any { it.contains("29人") })
        assertTrue(LineupEditing(snapshot).validate(initial).isEmpty())
    }

    @Test
    fun injuriesAndMissingAptitudeDoNotBlockSaving() {
        val team = fullTeam().map { if (it.id == 101) it.copy(injury = PlayerInjury.Injured(5)) else it }
        val snapshot = snapshotOf(team)
        val draft = LineupEditing(snapshot).changePosition(snapshot.initial, LineupMode.NORMAL, 1, FieldSlot.CATCHER)

        assertTrue(LineupEditing(snapshot).validate(draft).isEmpty())
    }
}
