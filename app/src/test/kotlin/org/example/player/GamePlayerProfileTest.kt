package org.example.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GamePlayerProfileTest {
    @Test
    fun profileValuesUseNormalizedLabelsAndNumericSortValues() {
        val cells =
            playerProfileCells(
                mapOf(
                    "NENME" to 6,
                    "SHOZOKU" to 3,
                    "DORAJUN" to 102,
                    "SHUSSIN" to 53,
                    "HEIGHT" to 180,
                    "WEIGHT" to 80,
                    "KIKIUDE" to 1,
                    "KIKIDASEKI" to 2,
                    "FORM" to 12,
                ),
            )

        assertEquals(PlayerCell("6", 6.0), cells["NENME"])
        assertEquals(PlayerCell("社会人", 3.0), cells["SHOZOKU"])
        assertEquals(PlayerCell("育成2位", 102.0), cells["DORAJUN"])
        assertEquals(PlayerCell("オーストラリア", 53.0), cells["SHUSSIN"])
        assertEquals(PlayerCell("180", 180.0), cells["HEIGHT"])
        assertEquals(PlayerCell("80", 80.0), cells["WEIGHT"])
        assertEquals(PlayerCell("右投左打", 12.0), cells["THROWS_BATS"])
        assertEquals(PlayerCell("スリークォーター", 12.0), cells["FORM"])
    }

    @Test
    fun absentAndUnknownProfileValuesRemainDistinct() {
        val absent =
            playerProfileCells(
                mapOf(
                    "NENME" to null,
                    "SHOZOKU" to 0,
                    "DORAJUN" to 0,
                    "SHUSSIN" to null,
                    "HEIGHT" to null,
                    "WEIGHT" to -1,
                    "KIKIUDE" to 1,
                    "KIKIDASEKI" to null,
                    "FORM" to null,
                ),
            )
        assertEquals(null, absent["NENME"])
        assertEquals(null, absent["SHOZOKU"])
        assertEquals(null, absent["DORAJUN"])
        assertEquals(null, absent["SHUSSIN"])
        assertEquals(null, absent["HEIGHT"])
        assertEquals(PlayerCell("-1", -1.0), absent["WEIGHT"])
        assertEquals(null, absent["THROWS_BATS"])
        assertEquals(null, absent["FORM"])

        val unknown =
            playerProfileCells(
                mapOf(
                    "SHOZOKU" to 99,
                    "DORAJUN" to -1,
                    "SHUSSIN" to 99,
                    "KIKIUDE" to 1,
                    "KIKIDASEKI" to 99,
                    "FORM" to 2,
                ),
            )
        for (key in listOf("SHOZOKU", "DORAJUN", "SHUSSIN", "THROWS_BATS", "FORM")) {
            assertEquals(unknownCell(), unknown[key], key)
        }
    }

    @Test
    fun domesticAndInternationalOriginsUseReadableJapanese() {
        assertEquals("北海道", playerProfileCells(mapOf("SHUSSIN" to 1)).getValue("SHUSSIN").text)
        assertEquals("沖縄", playerProfileCells(mapOf("SHUSSIN" to 47)).getValue("SHUSSIN").text)
        assertEquals("ベネズエラ", playerProfileCells(mapOf("SHUSSIN" to 55)).getValue("SHUSSIN").text)
        assertEquals("台湾", playerProfileCells(mapOf("SHUSSIN" to 65)).getValue("SHUSSIN").text)
    }
}
