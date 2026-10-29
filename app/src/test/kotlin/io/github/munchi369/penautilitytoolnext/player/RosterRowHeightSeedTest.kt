package io.github.munchi369.penautilitytoolnext.player

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RosterRowHeightSeedTest {
    @Test
    fun publishesKnownHeightsUntilEveryRowIsFilled() {
        val first = rosterRowHeightSeed(listOf(44, 32, 24), emptyMap(), null) { it * 2 }
        assertArrayEquals(intArrayOf(88, 64, 48), first)

        val filled = mapOf(0 to 88, 1 to 64, 2 to 48)
        assertNull(rosterRowHeightSeed(listOf(44, 32, 24), filled, listOf(44, 32, 24)) { it * 2 })

        val cleared = rosterRowHeightSeed(listOf(44, 32, 24), emptyMap(), listOf(44, 32, 24)) { it * 2 }
        assertArrayEquals(intArrayOf(88, 64, 48), cleared)
    }

    @Test
    fun republishesWhenARowKindMoves() {
        val known = mapOf(0 to 88, 1 to 88)
        val next = rosterRowHeightSeed(listOf(44, 24), known, listOf(44, 44)) { it * 2 }
        assertArrayEquals(intArrayOf(88, 48), next)
    }
}
