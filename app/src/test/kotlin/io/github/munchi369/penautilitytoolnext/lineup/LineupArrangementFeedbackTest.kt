package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineupArrangementFeedbackTest {
    private val before = snapshotOf(fullTeam()).initial

    @Test
    fun fielderFeedbackIncludesChangedPositionsAndDemotionsButNotUnchangedSlots() {
        val entries = before.dh.toMutableList()
        entries[0] = entries[0].copy(slot = entries[1].slot)
        entries[1] = entries[1].copy(slot = before.dh[0].slot)
        val after = before.copy(dh = entries).withState(110) { it.copy(squad = PlayerSquad.SECOND) }

        assertEquals(
            setOf(
                DropSpot.LineupSlot(LineupMode.DH, 0),
                DropSpot.LineupSlot(LineupMode.DH, 1),
                DropSpot.ListedPlayer(101),
                DropSpot.ListedPlayer(102),
                DropSpot.ListedPlayer(110),
            ),
            arrangementChangedSpots(before, after, LineupTab.FIELDERS, LineupMode.DH),
        )
        assertEquals(
            setOf(DropSpot.ListedPlayer(110)),
            arrangementChangedSpots(before, after, LineupTab.FIELDERS, LineupMode.NORMAL),
        )
    }

    @Test
    fun pitcherFeedbackIncludesEmptiedSlotsAndNewAssignments() {
        val starters = before.starters.toMutableList()
        starters[0] = null
        starters[6] = before.starters[0]
        assertEquals(
            setOf(DropSpot.RoleSlot(PitchingRole.STARTER, 0), DropSpot.RoleSlot(PitchingRole.STARTER, 6), DropSpot.ListedPlayer(1)),
            arrangementChangedSpots(before, before.copy(starters = starters), LineupTab.PITCHERS, LineupMode.DH),
        )
    }

    @Test
    fun unchangedArrangementsHaveNoHighlightedRows() {
        LineupTab.entries.forEach { tab ->
            assertTrue(arrangementChangedSpots(before, before, tab, LineupMode.DH).isEmpty())
        }
    }
}
