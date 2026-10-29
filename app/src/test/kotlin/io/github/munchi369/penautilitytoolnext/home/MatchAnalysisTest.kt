package io.github.munchi369.penautilitytoolnext.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MatchAnalysisTest {
    @Test
    fun trendsExcludeFutureGamesAndOtherCompetitionsAndIgnoreDrawsInStreaks() {
        val data =
            sample().copy(
                days = (1..8).map { SeasonDay(it, 4, it, it % 7, if (it == 8) 5 else 1, it) },
                games =
                    listOf(
                        game(1, 1, 4),
                        game(2, 4, 1),
                        game(3, 2, 2),
                        game(4, 6, 1),
                        game(5, 3, 0, opponent = 3),
                        game(6, 2, 1),
                        game(7, 0, 9),
                        game(8, 0, 5),
                    ).reversed(),
            )
        val trend = data.matchTrend(data.days[5], data.games.first { it.game == 6 })
        assertEquals("4連勝", trend.streak)
        assertEquals("3勝 1敗 1分", trend.matchup)
        val postseason = data.matchTrend(data.days[7], data.games.first { it.game == 8 })
        assertEquals("日本シリーズ", postseason.competition)
        assertEquals("1連敗", postseason.streak)
        assertEquals("0勝 1敗 0分", postseason.matchup)
        val drawn = data.copy(games = listOf(game(1, 2, 2)))
        assertEquals("連勝・連敗なし", drawn.matchTrend(data.days[0], drawn.games.single()).streak)
    }

    @Test
    fun intervalIgnoresFutureAppearancesAndOtherPlayers() {
        val selected = ReliefAppearance(10, 11, 3, 4, 0, 3)
        val data =
            sample().copy(
                relief =
                    listOf(
                        ReliefAppearance(3, 11, 9, 15, 0, 0),
                        ReliefAppearance(4, 11, 5, 8, 0, 0),
                        ReliefAppearance(8, 11, 3, 4, 0, 0),
                        ReliefAppearance(9, 11, 3, 5, 0, 0),
                        selected,
                        ReliefAppearance(11, 11, 6, 12, 0, 0),
                        ReliefAppearance(10, 99, 3, 3, 0, 0),
                    ),
            )
        val interval = data.reliefInterval(selected)
        assertEquals(9, interval.previousDay)
        assertEquals("3連投", interval.text(10))
    }

    @Test
    fun intervalIncludesRecordedStartsAndDistinguishesMissingHistoryFromFirstAppearance() {
        val selected = ReliefAppearance(10, 11, 3, 4, 0, 0)
        val data =
            sample().copy(
                days = listOf(SeasonDay(8, 4, 8, 1, 1, 1)),
                games = listOf(game(1, 4, 0).copy(starter = 11)),
                relief = listOf(selected),
            )
        val interval = data.reliefInterval(selected)
        assertEquals(8, interval.previousDay)
        assertEquals("中1日", interval.text(10))
        val missing = data.copy(games = emptyList()).reliefInterval(selected)
        assertNull(missing.previousDay)
        assertEquals("前回記録なし", missing.text(10))
    }

    @Test
    fun injuriesUseTheEventDateAndRecordedTeamsRatherThanCurrentPlayerAffiliation() {
        val own = MatchInjury(1, 44, 10, 1, 7)
        val opponent = MatchInjury(2, 45, 10, 2, 21)
        val data =
            sample().copy(
                injuries = listOf(opponent, own, own.copy(day = 9), own.copy(day = 11), own.copy(team = 3)),
            )
        val result = data.matchInjuries(SeasonDay(10, 4, 10, 3, 1, 1), game(1, 4, 2))
        assertEquals(listOf(own, opponent), result)
        assertEquals(listOf("違和感", "故障"), result.map { it.description })
        assertEquals("区分 3", own.copy(kind = 3).description)
    }

    private fun game(
        number: Int,
        runs: Int,
        allowed: Int,
        opponent: Int = 2,
    ): SeasonGame = SeasonGame(1, opponent, number, runs, allowed, 10, List(9) { 11 })

    private fun sample(): HomeData =
        HomeData(
            team = 1,
            season = 5,
            year = 2028,
            currentDay = 12,
            money = null,
            color = null,
            teams = mapOf(1 to "東京", 2 to "大阪"),
            players = emptyMap(),
            days = emptyList(),
            games = emptyList(),
            slots = emptyMap(),
            relief = emptyList(),
        )
}
