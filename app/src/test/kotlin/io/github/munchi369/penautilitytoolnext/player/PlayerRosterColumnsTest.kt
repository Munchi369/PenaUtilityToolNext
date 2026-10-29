package io.github.munchi369.penautilitytoolnext.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerRosterColumnsTest {
    @Test
    fun compactWidthsFitTheAgreedAbilityAndStatsColumnsInTheDefaultWindow() {
        assertTrue(RosterColumns.battingAbility.take(12).sumOf { it.width } <= MULTI_TEAM_SCROLLABLE_WIDTH)
        assertTrue(RosterColumns.battingAbility.take(13).sumOf { it.width } <= SINGLE_TEAM_SCROLLABLE_WIDTH)

        assertTrue(RosterColumns.battingStats.take(12).sumOf { it.width } <= MULTI_TEAM_SCROLLABLE_WIDTH)
        assertTrue(RosterColumns.battingStats.take(13).sumOf { it.width } <= SINGLE_TEAM_SCROLLABLE_WIDTH)

        assertTrue(RosterColumns.pitchingStats.take(12).sumOf { it.width } <= MULTI_TEAM_SCROLLABLE_WIDTH)
        assertTrue(RosterColumns.pitchingStats.take(13).sumOf { it.width } <= SINGLE_TEAM_SCROLLABLE_WIDTH)
    }

    @Test
    fun pitchingAbilityKeepsPitchColumnsReadable() {
        assertTrue(RosterColumns.pitchingAbility.take(11).sumOf { it.width } <= MULTI_TEAM_SCROLLABLE_WIDTH)
        RosterColumns.pitchingAbility.filter { it.key.startsWith("PITCH") }.forEach { column ->
            assertEquals(200, column.width)
        }
    }

    @Test
    fun basicInformationDefaultsShowThroughFormAndHideConditionColumns() {
        assertEquals(
            listOf(
                "NUMBER",
                "ID",
                "SOUGOU",
                "POSITION",
                "AGE",
                "NENME",
                "SHOZOKU",
                "DORAJUN",
                "SHUSSIN",
                "HEIGHT",
                "WEIGHT",
                "THROWS_BATS",
                "FORM",
            ),
            RosterColumns.basic.filter { it.standard }.map { it.key },
        )
        assertEquals(
            listOf("FA", "HIROU", "READY", "INJURY"),
            RosterColumns.basic.filterNot { it.standard }.map { it.key },
        )
        assertEquals(
            RosterColumns.basic
                .filter { it.standard }
                .map { it.key }
                .toSet(),
            PlayerRosterOptions().current.visibleColumns,
        )
    }

    private companion object {
        const val SINGLE_TEAM_SCROLLABLE_WIDTH = 972
        const val MULTI_TEAM_SCROLLABLE_WIDTH = 896
    }
}
