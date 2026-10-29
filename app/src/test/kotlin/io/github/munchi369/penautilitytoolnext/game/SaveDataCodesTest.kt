package io.github.munchi369.penautilitytoolnext.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveDataCodesTest {
    @Test
    fun playerNumberRoundTripsControlledAndDevelopmentContracts() {
        assertEquals(3, PlayerNumberCode.team(219))
        assertEquals(19, PlayerNumberCode.uniformNumber(219))
        assertEquals(PlayerRegistration.CONTROLLED, PlayerNumberCode.registration(219))
        assertEquals(219, PlayerNumberCode.encode(3, PlayerRegistration.CONTROLLED, 19))

        assertEquals(12, PlayerNumberCode.team(11105))
        assertEquals(105, PlayerNumberCode.uniformNumber(11105))
        assertEquals(PlayerRegistration.DEVELOPMENT, PlayerNumberCode.registration(11105))
        assertEquals(11105, PlayerNumberCode.encode(12, PlayerRegistration.DEVELOPMENT, 105))
    }

    @Test
    fun domesticNumbersCoverTwelveTeamsOfBothContracts() {
        assertTrue(PlayerNumberCode.isDomestic(0))
        assertTrue(PlayerNumberCode.isDomestic(1199))
        assertFalse(PlayerNumberCode.isDomestic(1200))
        assertFalse(PlayerNumberCode.isDomestic(-1))
        assertTrue(PlayerNumberCode.isDomestic(10000))
        assertTrue(PlayerNumberCode.isDomestic(11199))
        assertFalse(PlayerNumberCode.isDomestic(11200))
        assertEquals(
            "(SENSYU_NUM BETWEEN 200 AND 299 OR SENSYU_NUM BETWEEN 10200 AND 10299)",
            PlayerNumberCode.teamCondition(3),
        )
    }

    @Test
    fun injuryAndSquadDecodeOnlyKnownValues() {
        assertEquals(PlayerInjury.None, PlayerInjury.fromDatabaseValue(0))
        assertEquals(PlayerInjury.Discomfort(5), PlayerInjury.fromDatabaseValue(1005))
        assertEquals(PlayerInjury.Injured(120), PlayerInjury.fromDatabaseValue(2120))
        assertNull(PlayerInjury.fromDatabaseValue(3000))
        assertEquals(PlayerSquad.SECOND, PlayerSquad.fromDatabaseValue(2))
        assertNull(PlayerSquad.fromDatabaseValue(3))
    }

    @Test
    fun calendarRulesMatchTheGame() {
        assertTrue(GameCalendar.allowsControlledRegistration(212))
        assertFalse(GameCalendar.allowsControlledRegistration(213))
        assertFalse(GameCalendar.allowsControlledRegistration(308))
        assertTrue(GameCalendar.allowsControlledRegistration(309))
        assertEquals(1..6, GameTeams.firstLeague)
        assertEquals(7..12, GameTeams.secondLeague)
        assertEquals(-13, SaveRows.teamColorMyTeam(3))
    }
}
