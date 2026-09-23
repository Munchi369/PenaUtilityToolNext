package org.example.player

import org.example.savedata.CopiedDerbyTestDatabase
import org.example.savedata.DerbySaveDatabaseAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor

class PlayerDetailTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun referenceLoadsHistoryBothKindsAndAllHonorsAndReleasesDatabase() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { copy ->
            val loader = DerbyPlayerDetailLoader(DerbySaveDatabaseAccess())
            for (id in listOf(1, 11, 1008)) {
                val result = loader.load(copy.path, id) as PlayerDetailLoadResult.Loaded
                val data = result.data
                assertNotNull(data.profile.value, data.profile.error)
                assertEquals(5, data.season)
                assertNotNull(data.startYear)
                assertTrue(data.contextErrors.isEmpty(), data.contextErrors.toString())
                val sections =
                    listOf(
                        data.currentAbility,
                        data.abilities,
                        data.batting,
                        data.pitching,
                        data.monthlyBatting,
                        data.monthlyPitching,
                        data.appearances,
                        data.records,
                    ) +
                        data.honors.values
                sections.forEach { assertNull(it.error) }
                assertFalse(data.abilities.value.isNullOrEmpty())
                assertTrue(data.abilities.value!!.all { it.cells["POJI_HOSYU"]?.text == "—" })
                assertFalse(Files.exists(copy.path.resolve("db.lck")))
            }
            val data = (loader.load(copy.path, 1) as PlayerDetailLoadResult.Loaded).data
            assertTrue(data.batting.value!!.any { it.year < 0 })
            assertTrue(data.monthlyBatting.value!!.all { it.division in 4..9 })
            assertTrue(
                data.honors
                    .getValue("ベストナイン・ゴールデングラブ")
                    .value!!
                    .any { it.title == "ゴールデングラブ" },
            )
            assertTrue(
                data.honors
                    .getValue("オールスター")
                    .value!!
                    .isNotEmpty(),
            )
        }
    }

    @Test
    fun careerRatesUseSummedCountsAndDoNotTreatUnknownsAsZero() {
        val records =
            listOf(
                DetailStatRecord(1, 1, 1, mapOf("ANDA" to 1, "DASUU" to 2, "NENPOU" to 1000, "ITIGUN" to 5)),
                DetailStatRecord(2, 2, 1, mapOf("ANDA" to 1, "DASUU" to 8, "NENPOU" to 2000, "ITIGUN" to null)),
            )
        val total = detailTotals(records)
        assertEquals(".200", detailMetrics(total, false).getValue("AVG").text)
        assertNull(total["NENPOU"])
        assertNull(total["ITIGUN"])
        assertNull(detailMetrics(total, false)["ITIGUN"])
        val pitching =
            detailMetrics(
                detailTotals(
                    listOf(
                        DetailStatRecord(1, 1, 1, mapOf("KAISUU" to 4, "JISEKI" to 1)),
                        DetailStatRecord(2, 2, 1, mapOf("KAISUU" to 5, "JISEKI" to 0)),
                    ),
                ),
                true,
            )
        assertEquals("3", pitching.getValue("KAISUU").text)
        assertEquals("3.000", pitching.getValue("ERA").text)
    }

    @Test
    fun settingsPersistOnlyDisplayChoicesAndDiscardUnknownKeys() {
        val file = temporary.resolve("detail.json")
        val store = PlayerDetailSettingsStore(file)
        val settings = PlayerDetailSettings(setOf("AVG"), setOf("ERA"), setOf("POWER", "MAXMAX"))
        store.save(settings)
        assertEquals(settings, PlayerDetailSettingsStore(file).load())
        Files.writeString(file, """{"batting":["AVG","unknown"],"graphs":[]} """)
        val restored = store.load()
        assertEquals(setOf("AVG"), restored.battingColumns)
        assertEquals(emptySet<String>(), restored.graphColumns)
        Files.writeString(
            file,
            """{"graphs":["POWER","K_GORO","POJI_HOSYU","SENPATUTEKISEI","NAKATUGITEKISEI","VS_LEFT"]}""",
        )
        assertEquals(setOf("POWER"), store.load().graphColumns)
        assertEquals(PlayerDetailSettings().pitchingColumns, restored.pitchingColumns)
        store.clear()
        assertEquals(PlayerDetailSettings(), store.load())
    }

    @Test
    fun titleNamesCoverOfficialAndExtendedLeaders() {
        assertEquals("最多犠飛", detailTitleName(41, false))
        assertEquals("最高長打率", detailTitleName(45, false))
        assertEquals("最多登板", detailTitleName(61, false))
        assertEquals("新人賞", detailTitleName(2, true))
        assertEquals("新人王", detailTitleName(2, false))
        assertEquals("不明なタイトル（99）", detailTitleName(99, false))
    }

    @Test
    fun awardRowsKeepOfficialOrderAndSplitLeaders() {
        val titles =
            listOf(
                DetailHonor(4, "MVP", "一軍", code = 1),
                DetailHonor(5, "MVP", "一軍", code = 1),
                DetailHonor(3, "MVP", "二軍", code = 1),
                DetailHonor(5, "新人賞", "二軍", code = 2),
                DetailHonor(5, "最多犠飛", "一軍", code = 41),
                DetailHonor(2, "最多登板", "二軍", code = 61),
            )
        val official = officialTitleRows(titles) { "${it}年" }
        assertEquals(listOf("一軍 MVP", "二軍 MVP", "二軍 新人賞"), official.map { it.label })
        assertEquals(
            listOf(DetailAwardMark.Crown, DetailAwardMark.Crown, DetailAwardMark.Sprout),
            official.map { it.mark },
        )
        assertEquals(listOf(false, true, true), official.map { it.monochromeMark })
        assertEquals(listOf("5年", "4年"), official.first().chips)
        assertEquals(listOf(2, 1, 1), official.map { it.chips.size })
        val leaders = leaderTitleRows(titles) { "${it}年" }
        assertEquals(listOf("一軍 最多犠飛", "二軍 最多登板"), leaders.map { it.label })
        assertTrue(leaders.all { it.mark == null })
        assertTrue(leaders.all { it.chips.isEmpty() })
        assertEquals(listOf("1回", "1回"), leaders.map { it.boldText })
        val onBase =
            listOf(
                DetailHonor(5, "最高出塁率", "一軍", code = 14),
                DetailHonor(5, "最高出塁率", "一軍", code = 44),
            )
        assertEquals(DetailAwardMark.Bat, officialTitleRows(onBase) { "${it}年" }.single().mark)
        assertNull(leaderTitleRows(onBase) { "${it}年" }.single().mark)
        val fielding =
            listOf(
                DetailHonor(4, "ベストナイン", "2ndリーグ", facet = "三塁手"),
                DetailHonor(5, "ベストナイン", "1stリーグ", facet = "三塁手"),
                DetailHonor(5, "ゴールデングラブ", "1stリーグ", facet = "捕手"),
            )
        val bestNine = bestNineRows(fielding) { "${it}年" }
        assertEquals(listOf("三塁手"), bestNine.map { it.label })
        assertEquals(DetailAwardMark.Shield, bestNine.single().mark)
        assertEquals(listOf("5年 1stリーグ", "4年 2ndリーグ"), bestNine.single().chips)
        assertEquals(DetailAwardMark.Glove, goldenGloveRows(fielding) { "${it}年" }.single().mark)
        val records =
            listOf(
                DetailHonor(1, "完全試合", "達成"),
                DetailHonor(4, "安打", "記録値 200"),
                DetailHonor(2, "安打", "記録値 200"),
                DetailHonor(3, "完全試合", "達成"),
                DetailHonor(5, "失策", "記録値 3"),
            )
        val recordRows = specialRecordRows(records) { "${it}年" }
        assertEquals(listOf("失策", "安打", "完全試合"), recordRows.map { it.label })
        assertEquals("3", recordRows[0].boldText)
        assertTrue(recordRows[0].inlineYears)
        assertEquals(listOf("5年"), recordRows[0].chips)
        assertEquals("200", recordRows[1].boldText)
        assertEquals(listOf("4年", "2年"), recordRows[1].chips)
        assertNull(recordRows[2].boldText)
        assertFalse(recordRows[2].inlineYears)
        assertEquals(listOf("3年", "1年"), recordRows[2].chips)
    }

    @Test
    fun recordHighKeysMatchCountMaxAndRateLeadersLikeTheGame() {
        val highs =
            DetailSeasonRecordHighs(
                battingMax = mapOf(1 to mapOf("SIAI" to 143, "HONRUIDA" to 50, "ANDA" to 200)),
                pitchingMax = mapOf(1 to mapOf("KAISUU" to 600, "KATI" to 20, "MAKE" to 15)),
                battingAvg = mapOf(1 to mapOf("DASUU" to 500, "ANDA" to 180)),
                battingObp = emptyMap(),
                battingOps = emptyMap(),
                pitchingEra = mapOf(1 to mapOf("KAISUU" to 540, "JISEKI" to 60)),
            )
        assertEquals(
            setOf("HONRUIDA", "AVG"),
            detailRecordHighKeys(
                false,
                1,
                mapOf("HONRUIDA" to 50, "ANDA" to 180, "DASUU" to 500, "SIAI" to 143),
                highs,
            ),
        )
        assertEquals(
            setOf("KATI", "ERA"),
            detailRecordHighKeys(
                true,
                1,
                mapOf("KATI" to 20, "KAISUU" to 540, "JISEKI" to 60, "MAKE" to 3),
                highs,
            ),
        )
        assertTrue(markDetailRecordHighs(mapOf("HONRUIDA" to numberCell(50)), setOf("HONRUIDA")).getValue("HONRUIDA").recordHigh)
        assertFalse(
            detailRecordHighKeys(false, 1, mapOf("HONRUIDA" to 49), highs).contains("HONRUIDA"),
        )
    }

    @Test
    fun navigationPreservesOptionsAndDiscardsOutOfOrderAndClosedResults() {
        val background = QueuedExecutor()
        val calls = mutableListOf<Int>()
        val controller =
            PlayerDetailController(
                PlayerDetailLoader { _, id ->
                    calls.add(id)
                    PlayerDetailLoadResult.Failed("player$id", "details")
                },
                background,
                Executor { it.run() },
            )
        val first = player(1)
        val second = player(2)
        val roster = PlayerRoster(listOf(first, second), emptyList())
        val options = PlayerRosterOptions(display = RosterDisplay.PITCHING_ABILITY, statsSquad = 2)
        controller.open(temporary, first, roster, options)
        assertEquals(PlayerDetailTab.ABILITY, controller.options.tab)
        assertEquals(2, controller.options.division)
        controller.updateOptions(controller.options.copy(tab = PlayerDetailTab.HONORS, year = 3))
        controller.move(1)
        background.runLast()
        background.runLast()
        assertEquals(2, controller.state.player?.playerId)
        assertEquals("player2", controller.state.error?.message)
        assertEquals(PlayerDetailTab.HONORS, controller.options.tab)
        assertEquals(3, controller.options.year)
        controller.reload()
        controller.close()
        background.runLast()
        assertNull(controller.state.player)
        assertNull(controller.state.error)
    }

    @Test
    fun navigationUsesGroupedRosterOrderAndMajorInitialDivision() {
        val pitcher = player(1)
        val fielder = player(2).copy(position = PlayerPosition.CATCHER, overall = 200)
        val roster = PlayerRoster(listOf(pitcher, fielder), emptyList())
        val options = PlayerRosterOptions(grouping = RosterGrouping.POSITION)
        assertEquals(listOf(1, 2), detailNavigationPlayers(roster, options).map { it.playerId })
        val controller =
            PlayerDetailController(
                PlayerDetailLoader {
                    _,
                    _,
                    ->
                    PlayerDetailLoadResult.Failed("", "")
                },
                Executor { it.run() },
                Executor { it.run() },
            )
        val major = pitcher.copy(major = true)
        controller.open(temporary, major, PlayerRoster(emptyList(), emptyList(), listOf(major)), PlayerRosterOptions())
        assertEquals(3, controller.options.division)
        assertTrue(controller.options.pitching)
    }

    @Test
    fun abilityColumnsFollowMainPositionAndChartLabelsUseTheYear() {
        val pitcher = detailAbilityColumns("投手").map { it.key }
        val fielder = detailAbilityColumns("捕手").map { it.key }
        assertTrue(pitcher.indexOf("MAXMAX") < pitcher.indexOf("KOUDA"))
        assertTrue(fielder.indexOf("KOUDA") < fielder.indexOf("MAXMAX"))
        assertEquals(fielder, detailAbilityColumns("不明").map { it.key })
        assertEquals("2024", detailChartAxisLabel("2024年（12年目）"))
        assertEquals("現在", detailChartAxisLabel("現在"))
        assertEquals("入団", detailChartAxisLabel("入団時"))
        assertEquals("5年目", detailChartAxisLabel("ゲーム5年目（3年目）"))
        assertEquals("85", detailChartPointLabel(abilityCell(85)))
        assertNull(detailChartPointLabel(abilityCell(0)))
        assertEquals(listOf(0, 2, 4, 5), detailChartLabelIndexes(6, 4))
    }

    private fun player(id: Int) =
        PlayerRosterPlayer(
            id,
            id,
            "選手$id",
            100,
            PlayerPosition.PITCHER,
            22,
            PlayerSquad.FIRST,
            PlayerRegistration.CONTROLLED,
            PlayerInjury.None,
            teamNumber = 1,
        )

    private class QueuedExecutor : Executor {
        private val tasks = mutableListOf<Runnable>()

        override fun execute(command: Runnable) {
            tasks.add(command)
        }

        fun runLast() {
            tasks.removeAt(tasks.lastIndex).run()
        }
    }
}
