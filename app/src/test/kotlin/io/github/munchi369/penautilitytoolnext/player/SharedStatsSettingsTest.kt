package io.github.munchi369.penautilitytoolnext.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executor

class SharedStatsSettingsTest {
    @Test
    fun detailAndRosterUseTheSamePersistedVisibilityAndOrderWithoutChangingSortOrWidths(
        @TempDir root: Path,
    ) {
        val direct = Executor { it.run() }
        val path = root.resolve("save")
        val store = JsonPlayerRosterSettingsStore(root.resolve("roster.json"))
        val roster = PlayerRoster(emptyList(), emptyList(), currentTeam = 1)
        val controller = PlayerRosterController({ PlayerRosterLoadResult.Loaded(roster) }, direct, direct, store)
        controller.load(path)
        val initial = controller.options
        val batting =
            initial.displays.getValue(RosterDisplay.BATTING_STATS).copy(
                visibleColumns = setOf("NUMBER", "POSITION", "DASEKI", "HONRUIDA", "NENPOU", "DAJUN1"),
                columnOrder = listOf("HONRUIDA", "DASEKI", "NUMBER", "NENPOU", "DAJUN1"),
                sortKey = "AVG",
                descending = false,
                columnWidths = mapOf("DASEKI" to 120f),
            )
        controller.updateOptions(initial.copy(displays = initial.displays + (RosterDisplay.BATTING_STATS to batting)))
        val detail =
            PlayerDetailController(
                { _, _ -> PlayerDetailLoadResult.Failed("unused", "") },
                direct,
                direct,
                rosterController = controller,
            )
        assertEquals(batting, detail.statsOptions.current)
        val edited = detail.statsOptions.moveColumn("HONRUIDA", "DASEKI").hideColumn("NUMBER")
        detail.updateStatsColumns(edited)
        val shared = controller.options.displays.getValue(RosterDisplay.BATTING_STATS)
        assertFalse("NUMBER" in shared.visibleColumns)
        assertEquals(edited.current.columnOrder, shared.columnOrder)
        assertEquals("AVG", shared.sortKey)
        assertFalse(shared.descending)
        assertEquals(mapOf("DASEKI" to 120f), shared.columnWidths)
        assertEquals(RosterDisplay.BASIC, controller.options.display)
        assertEquals(initial.current, controller.options.current)
        assertEquals(
            initial.displays.getValue(RosterDisplay.PITCHING_STATS),
            controller.options.displays.getValue(RosterDisplay.PITCHING_STATS),
        )

        val loaded = store.load() as PlayerRosterSettingsLoadResult.Loaded
        val restored = loaded.settings.restore(PlayerRosterOptions.forTeam(1))
        assertEquals(shared, restored.displays.getValue(RosterDisplay.BATTING_STATS))
        detail.updateOptions(detail.options.copy(pitching = true))
        assertEquals(controller.options.displays.getValue(RosterDisplay.PITCHING_STATS), detail.statsOptions.current)
    }

    @Test
    fun unavailableMonthlyAndMajorColumnsAreOnlyTemporarilyHidden() {
        val layout =
            PlayerRosterOptions(display = RosterDisplay.BATTING_STATS).updateDisplay {
                it.copy(
                    visibleColumns = setOf("AVG", "DASEKI", "NENPOU", "DAJUN1", "POJI2", "ITIGUN", "POSITION"),
                    columnOrder = listOf("NENPOU", "AVG", "DASEKI", "ITIGUN", "DAJUN1", "POJI2"),
                )
            }
        val monthly = detailStatsColumns(layout, PlayerDetailOptions(period = DetailStatsPeriod.MONTHS)).map { it.key }
        assertFalse("NENPOU" in monthly)
        assertFalse("DAJUN1" in monthly)
        assertFalse("POJI2" in monthly)
        assertFalse("ITIGUN" in monthly)
        assertFalse("POSITION" in monthly)
        assertTrue("AVG" in monthly)
        val years = detailStatsColumns(layout, PlayerDetailOptions()).map { it.key }
        assertEquals(listOf("NENPOU", "AVG", "DASEKI", "ITIGUN", "DAJUN1", "POJI2"), years.filter { it in layout.current.visibleColumns })
        assertFalse("ITIGUN" in detailStatsColumns(layout, PlayerDetailOptions(division = 3)).map { it.key })
        assertTrue("NENPOU" in layout.current.visibleColumns)
    }
}
