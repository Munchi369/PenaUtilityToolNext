package io.github.munchi369.penautilitytoolnext.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class RosterColumnOrderTest {
    @Test
    fun customColumnIndicatorComparesTheCurrentVisibleColumnsWithTheirStandardOrder() {
        val original = PlayerRosterOptions.forTeam(6)
        assertFalse(original.hasCustomColumns())
        assertFalse(original.sort("AGE").hasCustomColumns())
        assertTrue(original.hideColumn("ID").hasCustomColumns())
        val moved = original.moveColumn("ID", "POSITION")
        assertTrue(moved.hasCustomColumns())
        assertFalse(moved.moveColumn("ID", "SOUGOU").hasCustomColumns())
        assertFalse(moved.selectDisplay(RosterDisplay.PITCHING_STATS).hasCustomColumns())
        assertFalse(original.selectTeamScope(RosterTeamScope.Major).hasCustomColumns())
        val explicitStandardOrder = original.updateDisplay { it.copy(columnOrder = RosterColumns.basic.map { column -> column.key }) }
        assertFalse(explicitStandardOrder.hasCustomColumns())
    }

    @Test
    fun movingAndHidingColumnsPreservesSortingAndOtherDisplays() {
        val original = PlayerRosterOptions.forTeam(6)
        val moved = original.moveColumn("ID", "POSITION").hideColumn("SOUGOU")
        assertEquals(listOf("NUMBER", "SOUGOU", "POSITION", "ID"), moved.orderedColumns().take(4).map { it.key })
        assertFalse("SOUGOU" in moved.current.visibleColumns)
        assertEquals(original.current.sortKey, moved.current.sortKey)
        assertEquals(original.current.descending, moved.current.descending)
        assertEquals(original.displays.getValue(RosterDisplay.BATTING_STATS), moved.displays.getValue(RosterDisplay.BATTING_STATS))
        val shown = moved.updateDisplay { it.copy(visibleColumns = it.visibleColumns + "SOUGOU") }
        assertEquals("SOUGOU", shown.orderedColumns()[1].key)
        assertEquals(original, original.moveColumn("NAME", "ID"))
        assertFalse("NUMBER" in original.hideColumn("NUMBER").current.visibleColumns)
        assertEquals(original, original.hideColumn("TEAM"))
    }

    @Test
    fun restorationDiscardsUnknownAndDuplicateKeysAndAppendsMissingColumns() {
        val original = PlayerRosterOptions.forTeam(6)
        val stored =
            PlayerRosterSettings.from(
                Path.of("build/column-order-save"),
                original.updateDisplay { it.copy(columnOrder = listOf("AGE", "REMOVED", "AGE", "ID")) },
            )
        val restored = stored.restore(original)
        assertEquals(listOf("AGE", "ID"), restored.current.columnOrder)
        assertEquals(listOf("AGE", "NUMBER", "ID", "SOUGOU"), restored.orderedColumns().take(4).map { it.key })
        assertEquals(RosterColumns.basic.size, restored.orderedColumns().size)
        assertTrue(restored.orderedColumns().map { it.key }.containsAll(RosterColumns.basic.map { it.key }))
    }

    @Test
    fun dropTargetsUseColumnCentersAndSupportDifferentWidthsAndBothDirections() {
        val columns = listOf(RosterColumn("A", "A", 68), RosterColumn("B", "B", 200), RosterColumn("C", "C", 80))
        assertEquals("A", rosterColumnDropTarget(columns, "A", 20f))
        assertEquals("B", rosterColumnDropTarget(columns, "A", 140f))
        assertEquals("C", rosterColumnDropTarget(columns, "A", 1000f))
        assertEquals("B", rosterColumnDropTarget(columns, "C", -145f))
        assertEquals("A", rosterColumnDropTarget(columns, "C", -1000f))
    }
}
