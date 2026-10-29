package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.game.SaveRows
import io.github.munchi369.penautilitytoolnext.player.DerbyPlayerRosterLoader
import io.github.munchi369.penautilitytoolnext.player.PlayerRoster
import io.github.munchi369.penautilitytoolnext.player.PlayerRosterLoadResult
import io.github.munchi369.penautilitytoolnext.player.PlayerRosterPlayer
import io.github.munchi369.penautilitytoolnext.player.PlayerRosterViewState
import io.github.munchi369.penautilitytoolnext.rosterColorScheme
import io.github.munchi369.penautilitytoolnext.savedata.CopiedDerbyTestDatabase
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import io.github.munchi369.penautilitytoolnext.ui.appTypography
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import javax.swing.SwingUtilities

class HomeTest {
    private val direct = Executor(Runnable::run)

    @Test
    fun matchupExtremesColorOnlyTheWinAndLossNumbers() {
        val values =
            listOf(
                MatchupCounts(9, 2, 1),
                MatchupCounts(9, 4, 0),
                MatchupCounts(1, 7, 0),
                MatchupCounts(0, 7, 2),
                MatchupCounts(1, 0, 0),
            )
        val highlights = matchupHighlights(values)
        assertEquals(MatchupHighlights(9, 7), highlights)
        assertEquals(2, values.count { it.wins == highlights.wins })
        assertEquals(2, values.count { it.losses == highlights.losses })
        assertEquals(MatchupHighlights(null, null), matchupHighlights(List(5) { MatchupCounts(0, 0, 0) }))

        val colored = matchupScoreText(MatchupCounts(9, 16, 8), false, highlightWins = true, highlightLosses = true)
        assertEquals("9−16−8", colored.text)
        assertEquals(listOf(0 until 1, 2 until 4), colored.spanStyles.map { it.start until it.end })
        assertEquals(listOf(Color(0xFF1D4ED8), Color(0xFFB91C1C)), colored.spanStyles.map { it.item.color })
        val narrow = matchupScoreText(MatchupCounts(9, 16, 8), true, highlightWins = true, highlightLosses = true)
        assertEquals("9−16\n8分", narrow.text)
        assertEquals(listOf(0 until 1, 2 until 4), narrow.spanStyles.map { it.start until it.end })
        assertTrue(matchupScoreText(MatchupCounts(9, 16, 8), false).spanStyles.isEmpty())
    }

    @Test
    fun earnedRunAverageLeadersOnlyIncludeStarterTypePitchers() {
        val starter =
            PlayerRosterPlayer(
                1,
                1,
                "先発",
                500,
                PlayerPosition.PITCHER,
                25,
                PlayerSquad.FIRST,
                PlayerRegistration.CONTROLLED,
                PlayerInjury.None,
                teamNumber = 1,
                information = mapOf("SENPATUTEKISEI" to PlayerCell("80", 80.0), "NAKATUGITEKISEI" to PlayerCell("40", 40.0)),
                pitching =
                    mapOf(
                        1 to
                            mapOf(
                                "ERA" to PlayerCell("3.000", 3.0),
                                "KAISUU" to PlayerCell("9", 27.0),
                                "KATI" to PlayerCell("1", 1.0),
                            ),
                    ),
            )
        val reliever =
            starter.copy(
                playerId = 2,
                name = "中継ぎ",
                information = mapOf("SENPATUTEKISEI" to PlayerCell("40", 40.0), "NAKATUGITEKISEI" to PlayerCell("80", 80.0)),
                pitching = starter.pitching.mapValues { (_, stats) -> stats + ("ERA" to PlayerCell("1.000", 1.0)) },
            )
        val equalAptitude =
            starter.copy(
                playerId = 3,
                name = "同値",
                information = mapOf("SENPATUTEKISEI" to PlayerCell("50", 50.0), "NAKATUGITEKISEI" to PlayerCell("50", 50.0)),
                pitching = starter.pitching.mapValues { (_, stats) -> stats + ("ERA" to PlayerCell("2.000", 2.0)) },
            )
        val roster = PlayerRoster(listOf(starter, reliever, equalAptitude), emptyList())

        assertEquals(listOf(3, 1), homeLeaders(sample(), roster, 0, true, "ERA").map { it.second.playerId })
        assertEquals(listOf(1, 2, 3), homeLeaders(sample(), roster, 0, true, "KATI").map { it.second.playerId })
    }

    @Test
    fun calendarOnlyShowsResultsOrScheduledGamesAndKeepsPostseasonOffDaysEmpty() {
        val data = sample()
        assertEquals("○ 5−2", calendarGameText(data, data.days[0]))
        assertNull(calendarGameText(data, data.days[1]))
        assertEquals("試合予定", calendarGameText(data, data.days[2]))
        assertEquals("日本S", calendarGameText(data, SeasonDay(306, 11, 2, 6, 5, 176)))
        assertNull(calendarGameText(data, SeasonDay(305, 11, 1, 5, 5, null)))
    }

    @Test
    fun seasonFeedCombinesDatedTeamHighlightsMonthlyRecordsAndOwnInjuries() {
        val dates =
            (1..12).map { number ->
                SeasonDay(number, if (number <= 6) 3 else 4, number, 0, 1, number)
            } +
                SeasonDay(13, 10, 13, 0, 3, 151) +
                SeasonDay(14, 10, 14, 0, 5, 171) +
                SeasonDay(15, 10, 15, 0, 0, null)
        val results =
            (1..12).map { number ->
                SeasonGame(
                    1,
                    2,
                    number,
                    if (number == 11) 1 else 5,
                    when (number) {
                        2 -> 0
                        11 -> 5
                        else -> 1
                    },
                    null,
                    emptyList(),
                )
            } +
                SeasonGame(1, 2, 151, 4, 3, null, emptyList()) +
                SeasonGame(1, 2, 171, 3, 4, null, emptyList())
        val data =
            sample().copy(
                currentDay = 14,
                days = dates,
                games = results,
                injuries =
                    listOf(
                        MatchInjury(1, 10, 9, 2, 8),
                        MatchInjury(2, 11, 10, 1, 3),
                        MatchInjury(1, 11, 3, 1, 4),
                        MatchInjury(1, 10, 15, 2, 7),
                    ),
            )
        val feed = data.seasonFeed()
        assertEquals(feed.map { it.day.day }.sorted(), feed.map { it.day.day })
        assertEquals(listOf(1), feed.filter { it.title == "今季初勝利" }.map { it.day.day })
        assertEquals(listOf(10), feed.filter { it.title.contains("連勝") }.map { it.day.day })
        assertEquals(listOf(10), feed.filter { it.title.contains("貯金10に到達") }.map { it.day.day })
        assertEquals(listOf(2), feed.filter { it.title == "完封勝利" }.map { it.day.day })
        assertEquals(listOf("6勝 0敗 0分", "5勝 1敗 0分"), feed.filter { it.kind == SeasonFeedKind.MONTH }.map { it.detail })
        assertEquals(listOf(3, 9), feed.filter { it.kind == SeasonFeedKind.INJURY }.map { it.day.day })
        assertEquals(listOf(1, 2), feed.filter { it.kind == SeasonFeedKind.INJURY }.map { it.injuryKind })
        assertEquals(listOf("CS初戦", "日本シリーズ初戦"), feed.filter { it.day.day in 13..14 }.map { it.title })
        assertEquals(listOf(SeasonFeedTone.POSITIVE, SeasonFeedTone.NEGATIVE), feed.filter { it.day.day in 13..14 }.map { it.tone })
        assertTrue(feed.filter { it.kind != SeasonFeedKind.MONTH }.all { it.calendarDay == it.day })
        assertTrue(feed.filter { it.kind == SeasonFeedKind.MONTH }.all { it.calendarDay == null })
    }

    @Test
    fun seasonFeedShowsLongestLossAndEightRunMarginInOneGameRow() {
        val dates =
            (1..11).map { number -> SeasonDay(number, 3, number, 0, 1, number) } +
                SeasonDay(12, 10, 12, 0, 3, 151)
        val results =
            (1..11).map { number ->
                SeasonGame(
                    1,
                    2,
                    number,
                    1,
                    when (number) {
                        1 -> 8
                        6 -> 1
                        11 -> 9
                        else -> 2
                    },
                    null,
                    emptyList(),
                )
            } + SeasonGame(1, 2, 151, 10, 1, null, emptyList())
        val data = sample().copy(currentDay = 12, days = dates, games = results)
        val games = data.seasonFeed().filter { it.kind == SeasonFeedKind.GAME }

        assertEquals(listOf(11, 12), games.map { it.day.day })
        assertEquals("10連敗・8点差で大敗", games[0].title)
        assertEquals("CS初戦・9点差で大勝", games[1].title)
        assertEquals("大阪戦 1−9", games[0].detail)
        assertEquals(listOf(SeasonFeedTone.NEGATIVE, SeasonFeedTone.POSITIVE), games.map { it.tone })
    }

    @Test
    fun seasonFeedShowsCompletedInterleagueRankIncludingTiedFirst() {
        val days =
            (155..174).map { day ->
                SeasonDay(day, 6, day - 151, 0, if (day in setOf(161, 168)) 0 else 1, day)
            }
        val results = days.filter { it.event == 1 }.map { day -> SeasonGame(1, 7, day.day, 5, 2, null, emptyList()) }
        val stats =
            (1..12).map { team ->
                InterleagueTeamStats(team, 18, if (team <= 2) 12 else 8, if (team <= 2) 5 else 10, if (team <= 2) 1 else 0, null, null)
            }
        val data = sample().copy(currentDay = 175, days = days, games = results, interleagueStats = stats)
        val entry = data.seasonFeed().single { it.kind == SeasonFeedKind.INTERLEAGUE }

        assertEquals("交流戦1位", entry.title)
        assertEquals("12勝 5敗 1分", entry.detail)
        assertEquals(174, entry.day.day)
        assertEquals(entry.day, entry.calendarDay)
        assertTrue(data.copy(interleagueStats = stats.dropLast(1)).seasonFeed().none { it.kind == SeasonFeedKind.INTERLEAGUE })
        assertTrue(data.copy(currentDay = 173).seasonFeed().none { it.kind == SeasonFeedKind.INTERLEAGUE })
        assertTrue(data.copy(games = results.dropLast(1)).seasonFeed().none { it.kind == SeasonFeedKind.INTERLEAGUE })
        val lastPlace = stats.map { if (it.team == 1) it.copy(wins = 7, losses = 11) else it }
        val lastEntry =
            data
                .copy(interleagueStats = lastPlace)
                .seasonFeed()
                .single { it.kind == SeasonFeedKind.INTERLEAGUE }
        assertEquals("交流戦12位", lastEntry.title)
    }

    @Test
    fun seasonFeedShowsSeriesOutcomesOnlyAfterWinnersAreKnown() {
        val dates =
            (0..2).map { offset -> SeasonDay(284 + offset, 10, 11 + offset, 0, 3, 151 + offset) } +
                (0..3).map { offset -> SeasonDay(289 + offset, 10, 16 + offset, 0, 4, 161 + offset) } +
                (0..5).map { offset -> SeasonDay(299 + offset, 10, 26 + offset, 0, 5, 171 + offset) }
        val games =
            dates.map { day ->
                val opponent =
                    when (day.event) {
                        5 -> 7
                        4 -> 3
                        else -> 2
                    }
                val win = day.game in setOf(151, 152, 161, 162, 163, 164, 171, 172, 173, 174)
                SeasonGame(1, opponent, requireNotNull(day.game), if (win) 5 else 1, if (win) 1 else 5, null, emptyList())
            }
        val postseason = PostseasonResults(3, 1, 2, 7, 8, 9, 1, 8, 1, 7, 1)
        val data = sample().copy(currentDay = 305, days = dates, games = games, postseason = postseason)
        val entries =
            data
                .seasonFeed()
                .filter { it.kind == SeasonFeedKind.CLIMAX || it.kind == SeasonFeedKind.JAPAN_SERIES }

        assertEquals(listOf("CSファースト突破", "CSファイナル突破", "日本一"), entries.map { it.title })
        assertEquals(listOf("大阪に2勝1敗", "球団3に4勝0敗", "球団7に4勝2敗"), entries.map { it.detail })
        assertEquals(listOf(286, 292, 304), entries.map { it.day.day })
        val unfinished = data.copy(postseason = postseason.copy(firstCs1 = 0, firstCs2 = 0, japanWinner = 0))
        assertTrue(
            unfinished.seasonFeed().none {
                it.kind == SeasonFeedKind.CLIMAX || it.kind == SeasonFeedKind.JAPAN_SERIES
            },
        )

        val finalLoss =
            sample()
                .copy(
                    team = 3,
                    currentDay = 293,
                    days = dates.filter { it.event == 4 },
                    games = games.filter { it.game in 161..164 }.map { it.copy(team = 3, opponent = 1, runs = 1, allowed = 5) },
                    postseason = postseason,
                ).seasonFeed()
                .single { it.kind == SeasonFeedKind.CLIMAX }
        assertEquals("CSファイナル敗北", finalLoss.title)
        assertEquals("東京に0勝4敗（先行1勝）", finalLoss.detail)
        assertEquals(SeasonFeedTone.NEGATIVE, finalLoss.tone)
    }

    @Test
    fun seasonFeedReplacesFifteenWinsWithSixteenWhenRecordGrows() {
        val data =
            sample().copy(
                currentDay = 16,
                days = (1..16).map { day -> SeasonDay(day, 4, day, 0, 1, day) },
                games = (1..16).map { game -> SeasonGame(1, 2, game, 5, 1, null, emptyList()) },
            )
        val atFifteen = data.copy(currentDay = 15).seasonFeed().filter { it.title.contains("連勝") }
        val atSixteen = data.seasonFeed().filter { it.title.contains("連勝") }

        assertEquals(listOf("15連勝"), atFifteen.map { it.title })
        assertEquals(listOf(15), atFifteen.map { it.day.day })
        assertEquals(listOf("16連勝"), atSixteen.map { it.title })
        assertEquals(listOf(16), atSixteen.map { it.day.day })
        assertTrue(data.copy(currentDay = 4).seasonFeed().none { it.title.contains("連勝") })
    }

    @Test
    fun seasonFeedMarksHundredthRegularWinOrLossOnceAndExcludesPostseason() {
        val days =
            (1..102).map { day ->
                val event = if (day == 100) 3 else 1
                val game =
                    when {
                        day == 100 -> 151
                        day > 100 -> day - 1
                        else -> day
                    }
                SeasonDay(day, 3 + (day - 1) / 30, 1 + (day - 1) % 30, 0, event, game)
            }
        listOf(
            Triple(5 to 2, "シーズン100勝", SeasonFeedTone.POSITIVE),
            Triple(2 to 5, "シーズン100敗", SeasonFeedTone.NEGATIVE),
        ).forEach { (score, title, tone) ->
            val games = days.map { day -> SeasonGame(1, 2, requireNotNull(day.game), score.first, score.second, null, emptyList()) }
            val data = sample().copy(currentDay = 102, days = days, games = games)
            val milestones = data.seasonFeed().filter { title in it.title }

            assertEquals(listOf(101), milestones.map { it.day.day })
            assertTrue(milestones.single().isHundredMilestone)
            assertEquals(SeasonFeedKind.GAME, milestones.single().kind)
            assertEquals(tone, milestones.single().tone)
            assertTrue(data.copy(currentDay = 100).seasonFeed().none { title in it.title })
        }
    }

    @Test
    fun competitionColorsAndLabelsRemainTheSameBeforeAndAfterResults() {
        val interleague = SeasonDay(155, 6, 2, 1, 1, 1)
        val climax = SeasonDay(284, 10, 11, 5, 3, 151)
        val japanSeries = SeasonDay(299, 10, 26, 6, 5, 171)
        assertEquals(CalendarCompetition.INTERLEAGUE, calendarCompetition(interleague))
        assertEquals(CalendarCompetition.CLIMAX_SERIES, calendarCompetition(climax))
        assertEquals(CalendarCompetition.JAPAN_SERIES, calendarCompetition(japanSeries))
        assertEquals(Color(0xFFE1F2F8), calendarCompetition(interleague)?.background)
        assertEquals(Color(0xFFE9F2E4), calendarCompetition(climax)?.background)
        assertEquals(Color(0xFFFFF0D8), calendarCompetition(japanSeries)?.background)
        assertNull(calendarCompetition(SeasonDay(305, 11, 1, 5, 5, null)))
        val before = sample().copy(games = emptyList())
        assertEquals("交流戦", calendarGameText(before, interleague))
        assertEquals("CS", calendarGameText(before, climax))
        assertEquals("日本S", calendarGameText(before, japanSeries))
        assertEquals("○ 5−2", calendarGameText(sample(), interleague))
    }

    @Test
    fun postseasonCalendarShowsBothLeaguesAndJapanSeriesWhenOwnTeamIsAbsent() {
        val cs = SeasonDay(284, 10, 11, 5, 3, 151)
        val series = SeasonDay(299, 10, 26, 6, 5, 171)
        val offDay = SeasonDay(305, 11, 1, 5, 5, null)
        val data =
            sample().copy(
                team = 6,
                currentDay = 309,
                days = listOf(cs, series, offDay),
                teams = mapOf(1 to "東京", 2 to "大阪", 3 to "名古屋", 7 to "札幌", 10 to "福岡", 12 to "沖縄"),
                games =
                    listOf(
                        SeasonGame(1, 2, 151, 9, 7, null, emptyList()),
                        SeasonGame(2, 1, 151, 7, 9, null, emptyList()),
                        SeasonGame(7, 12, 151, 1, 0, null, emptyList()),
                        SeasonGame(12, 7, 151, 0, 1, null, emptyList()),
                        SeasonGame(3, 10, 171, 3, 13, null, emptyList()),
                        SeasonGame(10, 3, 171, 13, 3, null, emptyList()),
                    ),
            )
        assertEquals(listOf(1, 7), data.calendarGamesOn(cs).map { it.team })
        assertEquals(listOf(3), data.calendarGamesOn(series).map { it.team })
        assertTrue(calendarGameText(data, cs)!!.contains("札幌 1−0 沖縄"))
        assertTrue(calendarGameText(data, series)!!.contains("名古屋 3−13 福岡"))
        assertNull(calendarGameText(data, offDay))
        val controller = HomeController({ Result.success(data) }, direct, direct)
        controller.load(Path.of("postseason"))
        controller.openMatch(cs)
        assertTrue(controller.showingMatch)
        controller.openMatch(series)
        assertTrue(controller.showingMatch)
    }

    @Test
    fun gameNumbersSkipOffDaysAndUseSeparatePostseasonNumbers() {
        val days =
            seasonGameNumbers(
                listOf(
                    SeasonDay(88, 3, 29, 4, 1, null),
                    SeasonDay(89, 3, 30, 5, 0, null),
                    SeasonDay(90, 3, 31, 6, 1, null),
                    SeasonDay(284, 10, 11, 0, 3, null),
                    SeasonDay(299, 10, 26, 1, 5, null),
                ),
            )
        assertEquals(listOf(1, null, 2, 151, 171), days.map { it.game })
    }

    @Test
    fun backPreservesMonthAndSelectedDateAndFutureDatesNeverOpenAResult() {
        val data = sample()
        val controller = HomeController({ Result.success(data) }, direct, direct)
        controller.load(Path.of("save-a"))
        controller.changeMonth(3)
        controller.select(data.days[0])
        assertEquals(88, controller.selectedDay)
        assertFalse(controller.showingMatch)
        controller.openMatch(data.days[0])
        assertTrue(controller.showingMatch)
        controller.backToCalendar()
        assertEquals(3, controller.month)
        assertEquals(88, controller.selectedDay)
        assertFalse(controller.showingMatch)
        controller.openMatch(data.days[2])
        assertFalse(controller.showingMatch)
        assertEquals("ビジター", data.venue(data.days[0]))
        assertEquals(2, data.opponent(data.days[0]))
        assertEquals(1, data.remaining)
        assertEquals("1 2/3", outsText(5))
    }

    @Test
    fun focusingFeedDateSelectsCalendarWithoutOpeningMatch() {
        val data = sample().copy(days = listOf(SeasonDay(88, 2, 29, 4, 1, 1)) + sample().days.drop(1))
        val controller = HomeController({ Result.success(data) }, direct, direct)
        controller.load(Path.of("save-a"))
        controller.openMatch(data.days[0])
        assertTrue(controller.showingMatch)

        controller.focusDay(data.days[0])
        assertEquals(2, controller.month)
        assertEquals(88, controller.selectedDay)
        assertFalse(controller.showingMatch)

        controller.focusDay(data.days[1])
        assertEquals(3, controller.month)
        assertEquals(89, controller.selectedDay)
        assertFalse(controller.showingMatch)

        controller.focusMonth(2)
        assertEquals(2, controller.month)
        assertNull(controller.selectedDay)
        assertFalse(controller.showingMatch)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun clickingSeasonFeedRowsFocusesDayOrMonthWithoutOpeningMatch() {
        val data =
            sample().copy(
                days =
                    listOf(
                        SeasonDay(88, 4, 1, 4, 1, 1),
                        SeasonDay(89, 4, 2, 5, 0, null),
                        SeasonDay(90, 4, 3, 6, 1, 2),
                    ),
                injuries = listOf(MatchInjury(1, 11, 89, 1, 3)),
            )
        val controller = HomeController({ Result.success(data) }, direct, direct)
        controller.load(Path.of("feed-click"))
        controller.focusMonth(3)
        var openedPlayer: Int? = null
        SwingUtilities.invokeAndWait {
            val scene =
                ImageComposeScene(400, 250) {
                    MaterialTheme(colorScheme = rosterColorScheme, typography = appTypography) {
                        Surface { HomeSeasonFeed(controller, data, Modifier.fillMaxSize(), onOpenPlayer = { openedPlayer = it }) }
                    }
                }
            try {
                fun click(
                    y: Float,
                    x: Float = 100f,
                ) {
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                    repeat(3) { scene.render().close() }
                }

                repeat(3) { scene.render().close() }
                click(45f)
                assertEquals(4, controller.month)
                assertEquals(88, controller.selectedDay)
                assertFalse(controller.showingMatch)
                controller.focusMonth(3)
                click(90f)
                assertEquals(4, controller.month)
                assertNull(controller.selectedDay)
                assertFalse(controller.showingMatch)
                click(140f)
                assertEquals(11, openedPlayer)
                assertNull(controller.selectedDay)
                click(140f, 20f)
                assertEquals(4, controller.month)
                assertEquals(89, controller.selectedDay)
                assertFalse(controller.showingMatch)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun changingSavesDropsOldResultsAndFailuresEndLoading() {
        val background = ArrayDeque<Runnable>()
        val ui = ArrayDeque<Runnable>()
        val controller = HomeController({ Result.success(sample()) }, Executor(background::add), Executor(ui::add))
        controller.load(Path.of("old"))
        background.removeFirst().run()
        controller.load(Path.of("new"))
        ui.removeFirst().run()
        assertNull(controller.data)
        background.removeFirst().run()
        ui.removeFirst().run()
        assertNotNull(controller.data)
        controller.clear()
        assertNull(controller.path)
        assertFalse(controller.loading)
        val failing = HomeController({ error("broken") }, direct, direct)
        failing.load(Path.of("broken"))
        assertFalse(failing.loading)
        assertNotNull(failing.error)
    }

    @Test
    fun referenceDatabaseProvidesCurrentSeasonGamesLineupsAndReliefAndIsReleased() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val result = DerbyHomeLoader(DerbySaveDatabaseAccess()).load(db.path)
            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            val data = result.getOrThrow()
            assertEquals(5, data.season)
            assertEquals(12, data.teams.size)
            assertNotNull(data.color)
            assertEquals(data.color, data.teamColors[data.team])
            assertEquals(0, data.remaining)
            assertTrue(data.games.isNotEmpty())
            assertEquals(12, data.teamStats.size)
            assertEquals((1..12).toSet(), data.teamStats.map { it.team }.toSet())
            assertTrue(data.teamStats.all { it.games == it.wins + it.losses + it.draws })
            assertEquals(6, teamStandings(data.teamStats, 0).size)
            assertEquals(6, teamStandings(data.teamStats, 1).size)
            assertTrue(data.teamStats.all { it.earnedRuns == it.starterEarnedRuns + it.reliefEarnedRuns })
            assertTrue(data.teamStats.all { it.pitchingOuts == it.starterOuts + it.reliefOuts })
            assertTrue(data.teamStats.all { it.walks > 0 && it.sacrificeBunts > 0 })
            assertEquals(12, data.interleagueStats.size)
            assertEquals((1..12).toSet(), data.interleagueStats.map { it.team }.toSet())
            assertTrue(data.interleagueStats.all { it.games == it.wins + it.losses + it.draws })
            assertEquals(9, interleagueStandings(data.interleagueStats).first().team)
            assertEquals(InterleagueTeamStats(1, 18, 7, 9, 2, 60, 86), data.interleagueStats.single { it.team == 1 })
            assertTrue(data.relief.isNotEmpty())
            assertTrue(data.injuries.isNotEmpty())
            assertTrue(data.injuries.all { it.day in 1..365 && it.duration >= 0 && it.kind in 1..2 })
            val day = data.days.first { data.gameOn(it) != null }
            val game = requireNotNull(data.gameOn(day))
            assertEquals(9, game.battingOrder.size)
            assertNotNull(game.starter)
            assertEquals(game.opponent, data.opponent(day))
            assertTrue(Files.notExists(db.path.resolve("db.lck")))
        }
    }

    @Test
    fun interleagueRowsMissingBeforeStartAreNotAnErrorButMissingResultsAfterStartAre() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val access = DerbySaveDatabaseAccess()
            val changed =
                access.write(db.path, "テスト用コピーの交流戦成績を除去") { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeUpdate("DELETE FROM TEAM_SEISEKI WHERE NENDO = 5 AND ITINIGUN = 1 AND TEAMNO BETWEEN 13 AND 24")
                        statement.executeUpdate("UPDATE SAVE SET SYSDAY = 100 WHERE ${SaveRows.CURRENT_CONDITION}")
                    }
                }
            assertTrue(changed is SaveDatabaseResult.Success)
            val before = DerbyHomeLoader(access).load(db.path).getOrThrow()
            assertTrue(before.interleagueStats.isEmpty())
            assertFalse(before.interleagueStatsUnavailable)
            assertTrue(before.notices.none { it.startsWith("交流戦成績") })

            val advanced =
                access.write(db.path, "テスト用コピーの日付を交流戦後へ変更") { connection ->
                    connection.createStatement().use {
                        it.executeUpdate("UPDATE SAVE SET SYSDAY = 200 WHERE ${SaveRows.CURRENT_CONDITION}")
                    }
                }
            assertTrue(advanced is SaveDatabaseResult.Success)
            val after = DerbyHomeLoader(access).load(db.path).getOrThrow()
            assertTrue(after.interleagueStats.isEmpty())
            assertTrue(after.interleagueStatsUnavailable)
            assertTrue(after.notices.none { it.startsWith("交流戦成績") })
        }
    }

    @Test
    fun missingInjuryHistoryDoesNotHideGameResultsOrPretendThereWereNoInjuries() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val access = DerbySaveDatabaseAccess()
            val dropped =
                access.write(db.path, "テスト用コピーの故障履歴を除去") { connection ->
                    connection.createStatement().use { it.executeUpdate("DROP TABLE RIREKI") }
                }
            assertTrue(dropped is SaveDatabaseResult.Success)
            val data = DerbyHomeLoader(access).load(db.path).getOrThrow()
            assertTrue(data.games.isNotEmpty())
            assertTrue(data.injuries.isEmpty())
            assertTrue(data.notices.any { it.startsWith("故障・違和感履歴") })
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun clickingResultReplacesCalendarAndBackRestoresSelection() {
        SwingUtilities.invokeAndWait {
            val controller = HomeController({ Result.success(sample()) }, direct, direct)
            controller.load(Path.of("sample"))
            var openedPlayer: Int? = null
            val scene =
                ImageComposeScene(480, 530) {
                    MaterialTheme(colorScheme = rosterColorScheme, typography = appTypography) {
                        Surface {
                            HomeCalendar(controller, requireNotNull(controller.data), onOpenPlayer = { openedPlayer = it })
                        }
                    }
                }
            try {
                fun render(name: String): ByteArray {
                    repeat(3) { scene.render().close() }
                    val directory = Path.of("build/home-previews")
                    Files.createDirectories(directory)
                    return scene.render().use { image ->
                        image.encodeToData()!!.use {
                            val bytes = it.bytes
                            Files.write(directory.resolve("$name.png"), bytes)
                            bytes
                        }
                    }
                }

                var pointerTime = 1000L

                fun click(
                    x: Float,
                    y: Float,
                ) {
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), timeMillis = pointerTime)
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), timeMillis = pointerTime + 10)
                    pointerTime += 100
                    repeat(3) { scene.render().close() }
                }

                fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

                fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }

                fun nodeWithText(text: String) =
                    nodes().first { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
                    }

                fun clickNode(node: SemanticsNode) = click(node.boundsInRoot.center.x, node.boundsInRoot.center.y)

                render("calendar")
                controller.focusMonth(3)
                repeat(3) { scene.render().close() }
                clickNode(
                    nodes().first { node ->
                        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains("3月29日") } == true
                    },
                )
                assertEquals(88, controller.selectedDay)
                assertTrue(controller.showingMatch)
                val summary = render("match")
                clickNode(nodeWithText("先発投手・打順"))
                val orders = render("match-orders")
                assertFalse(summary.contentEquals(orders), "先発・打順タブで表示が切り替わること")

                val starter =
                    scene.semanticsOwners
                        .flatMap { descendants(it.rootSemanticsNode) }
                        .first { node ->
                            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "先発 先発 太郎" } == true
                        }
                val center = starter.boundsInRoot.center
                click(center.x, center.y)
                assertEquals(10, openedPlayer)
                clickNode(nodeWithText("救援登板"))
                val relief = render("match-relief")
                assertFalse(orders.contentEquals(relief), "救援登板タブで表示が切り替わること")
                val back =
                    scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }.first { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "← 戻る" } == true
                    }
                click(back.boundsInRoot.center.x, back.boundsInRoot.center.y)
                assertFalse(controller.showingMatch)
                assertEquals(3, controller.month)
                assertEquals(88, controller.selectedDay)
                render("calendar-returned")
            } finally {
                scene.close()
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun longTeamNamesKeepLeaderNamesAlignedAndMatchupValuesReadable() {
        val longName = "開始年度の設定をする球団"
        val data =
            sample().copy(
                teams = mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D", 5 to longName, 6 to longName),
                games =
                    (0 until 33).map { index ->
                        val score =
                            when {
                                index < 9 -> 2 to 0
                                index < 25 -> 0 to 2
                                else -> 1 to 1
                            }
                        SeasonGame(1, 2, 1, score.first, score.second, null, emptyList())
                    },
            )

        fun hitter(
            id: Int,
            team: Int,
            name: String,
        ) = PlayerRosterPlayer(
            id,
            id,
            name,
            500,
            PlayerPosition.PITCHER,
            25,
            PlayerSquad.FIRST,
            PlayerRegistration.CONTROLLED,
            PlayerInjury.None,
            teamNumber = team,
            batting = mapOf(1 to mapOf("HONRUIDA" to PlayerCell("12", 12.0))),
        )
        val roster = PlayerRoster(listOf(hitter(10, 1, "選手A"), hitter(11, 5, "選手E")), emptyList(), season = data.season)

        SwingUtilities.invokeAndWait {
            listOf(820, 700, 500).forEach { width ->
                val controller = HomeController({ Result.success(data) }, direct, direct)
                controller.load(Path.of("long-names"))
                val scene =
                    ImageComposeScene(width, 680) {
                        MaterialTheme(colorScheme = rosterColorScheme, typography = appTypography) {
                            Surface { HomeScreen(controller, PlayerRosterViewState(roster = roster)) { } }
                        }
                    }
                try {
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }

                    fun node(text: String) =
                        nodes()
                            .filter { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true }
                            .maxBy { it.boundsInRoot.left }
                    repeat(3) { scene.render().close() }
                    scene.render().use { image ->
                        val directory = Path.of("build/home-previews")
                        Files.createDirectories(directory)
                        image.encodeToData()!!.use { Files.write(directory.resolve("long-names-$width.png"), it.bytes) }
                    }
                    val tab = node("対戦表").boundsInRoot.center
                    scene.sendPointerEvent(PointerEventType.Press, tab)
                    scene.sendPointerEvent(PointerEventType.Release, tab)
                    repeat(3) { scene.render().close() }
                    scene.render().use { image ->
                        val directory = Path.of("build/home-previews")
                        Files.createDirectories(directory)
                        image.encodeToData()!!.use { Files.write(directory.resolve("long-names-match-$width.png"), it.bytes) }
                    }
                    val stacked = width < 760
                    val requiredWidth =
                        when {
                            width < 600 -> 35f
                            stacked -> 45f
                            else -> 60f
                        }
                    assertTrue(node(if (stacked) "9−16\n8分" else "9−16−8").boundsInRoot.width >= requiredWidth)
                    val pagerVisible =
                        nodes().any { node ->
                            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "1–3" } == true
                        }
                    assertFalse(pagerVisible)
                    val expectedHeader =
                        when {
                            width < 600 -> "開始年"
                            stacked -> "開始年度"
                            else -> "開始年度の"
                        }
                    val longHeaders =
                        nodes().count { node ->
                            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == expectedHeader } == true
                        }
                    assertEquals(2, longHeaders)
                } finally {
                    scene.close()
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun homeLeagueTabsChangeStandingsWithoutChangingLeaders() {
        SwingUtilities.invokeAndWait {
            val controller = HomeController({ Result.success(sample()) }, direct, direct)
            controller.load(Path.of("sample"))
            val scene =
                ImageComposeScene(1440, 800) {
                    MaterialTheme(colorScheme = rosterColorScheme, typography = appTypography) {
                        Surface { HomeScreen(controller, PlayerRosterViewState()) { } }
                    }
                }
            try {
                fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

                fun nodes(text: String) =
                    scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }.filter { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
                    }

                fun click(node: SemanticsNode) {
                    scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
                    scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
                    repeat(3) { scene.render().close() }
                }
                repeat(3) { scene.render().close() }
                val standingsTop = nodes("球団成績を取得できません").single().boundsInRoot.top
                val secondLeague = nodes("2ndリーグ").sortedBy { it.boundsInRoot.top }
                assertEquals(2, secondLeague.size)
                click(secondLeague.first())
                val selected = nodes("2ndリーグ").sortedBy { it.boundsInRoot.top }
                assertEquals(true, selected.first().config.getOrNull(SemanticsProperties.Selected))
                assertEquals(false, selected.last().config.getOrNull(SemanticsProperties.Selected))
                click(nodes("交流戦").first())
                assertTrue(nodes("全12球団").isEmpty())
                assertEquals(1, nodes("2ndリーグ").size)
                val interleagueTop = nodes("交流戦はまだ始まっていません").single().boundsInRoot.top
                assertEquals(standingsTop, interleagueTop, 0.5f)
            } finally {
                scene.close()
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun homeRendersWithReferenceDataAtWideAndNarrowWidths() {
        CopiedDerbyTestDatabase.copyPrimaryReference().use { db ->
            val access = DerbySaveDatabaseAccess()
            val controller = HomeController(DerbyHomeLoader(access), direct, direct)
            controller.load(db.path)
            assertNotNull(controller.data)
            val roster = DerbyPlayerRosterLoader(access).load(db.path) as PlayerRosterLoadResult.Loaded
            assertEquals(roster.roster.teamColors, requireNotNull(controller.data).teamColors)
            val sixWeekMonth =
                requireNotNull(controller.data)
                    .days
                    .groupBy { it.month }
                    .values
                    .firstOrNull { it.first().weekday + it.size > 35 }
                    ?.first()
                    ?.month
            SwingUtilities.invokeAndWait {
                listOf(
                    Triple(1440, 800, sixWeekMonth),
                    Triple(1360, 650, sixWeekMonth),
                    Triple(1040, 620, sixWeekMonth),
                    Triple(1000, 800, sixWeekMonth),
                    Triple(940, 740, sixWeekMonth),
                    Triple(740, 640, sixWeekMonth),
                    Triple(1000, 800, 11),
                    Triple(940, 740, 11),
                    Triple(1360, 650, 11),
                ).forEach { (width, height, month) ->
                    month?.let(controller::changeMonth)
                    val scene =
                        ImageComposeScene(width, height) {
                            MaterialTheme(colorScheme = rosterColorScheme, typography = appTypography) {
                                Surface { HomeScreen(controller, PlayerRosterViewState(roster = roster.roster)) { } }
                            }
                        }
                    try {
                        repeat(3) { scene.render().close() }
                        val directory = Path.of("build/home-previews")
                        Files.createDirectories(directory)
                        scene.render().use { image ->
                            val name = "home-$width${if (month == 11) "-november" else ""}.png"
                            image.encodeToData()!!.use { Files.write(directory.resolve(name), it.bytes) }
                        }
                        if (width == 940 && month == 11) {
                            val day = requireNotNull(controller.data).days.first { controller.data?.gameOn(it) != null }
                            controller.changeMonth(day.month)
                            controller.openMatch(day)
                            assertTrue(controller.showingMatch)
                            repeat(3) { scene.render().close() }
                            scene.render().use { image ->
                                image.encodeToData()!!.use { Files.write(directory.resolve("home-stacked-match.png"), it.bytes) }
                            }
                            controller.backToCalendar()
                            assertEquals(day.month, controller.month)
                            assertEquals(day.day, controller.selectedDay)
                        }
                    } finally {
                        scene.close()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun matchPanelsRenderWithLongNamesAtNarrowWidthsAndShortHeights() {
        val data =
            sample().copy(
                teams = mapOf(1 to "福岡フェニックス球団", 2 to "東京スターズ球団"),
                players = mapOf(10 to "アレクサンダー太郎", 11 to "中村 翔太"),
                relief = (10..16).map { ReliefAppearance(88, it, 5, 7, 1, 3) },
                injuries = (10..16).map { MatchInjury(if (it % 2 == 0) 1 else 2, it, 88, 1, 7) },
            )
        SwingUtilities.invokeAndWait {
            listOf(320 to 260, 360 to 420, 440 to 530, 940 to 240).forEach { (width, height) ->
                listOf(false, true).forEach { relief ->
                    val scene =
                        ImageComposeScene(width, height) {
                            MaterialTheme(colorScheme = rosterColorScheme, typography = appTypography) {
                                Surface {
                                    if (relief) {
                                        ReliefSummary(data, data.days[0], Modifier.fillMaxSize())
                                    } else {
                                        MatchSummary(data, data.days[0], data.games[0]) { }
                                    }
                                }
                            }
                        }
                    try {
                        repeat(3) { scene.render().close() }
                        val directory = Path.of("build/home-previews")
                        Files.createDirectories(directory)
                        scene.render().use { image ->
                            image.encodeToData()!!.use {
                                val name = "match-${if (relief) "relief" else "summary"}-$width-$height.png"
                                Files.write(directory.resolve(name), it.bytes)
                            }
                        }
                    } finally {
                        scene.close()
                    }
                }
            }
        }
    }

    @Test
    fun teamAccentsKeepDarkSavedColorsAndMakeLightOrMissingColorsVisible() {
        val foreground = Color(0xFF303341)
        assertEquals(Color(0xFF1B5E20), readableTeamAccent(0x1B5E20, Color.White, foreground))
        listOf(0xFFFFFF, 0xFFFF00, 0x000000, null).forEach { rgb ->
            val accent = readableTeamAccent(rgb, Color.White, foreground)
            assertTrue(1.05f / (accent.luminance() + 0.05f) >= 3f, "RGB=$rgb の小さな印が白背景で見えること")
            assertEquals(1f, accent.alpha)
            listOf(Color.White, Color(0xFFF6F7FA), Color(0xFFF9EDFF)).forEach { background ->
                val textColor = readableTeamAccent(rgb, background, foreground, minimumContrast = 4.5f)
                val contrast = (background.luminance() + 0.05f) / (textColor.luminance() + 0.05f)
                assertTrue(contrast >= 4.5f, "RGB=$rgb の球団名が白・灰色・自チーム行の淡い背景で読めること")
            }
        }
    }

    @Test
    fun teamStatisticsUseSavedTotalsAndHandleZeroDenominators() {
        val empty = TeamSeasonStats(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val played =
            TeamSeasonStats(2, 10, 6, 3, 1, 40, 30, 100, 300, 12, 4, 20, 270, 2, 1, 5, 6)
                .copy(walks = 5, sacrificeBunts = 2, starterEarnedRuns = 12, starterOuts = 180, reliefEarnedRuns = 8, reliefOuts = 90)
        assertEquals(listOf(played, empty), teamStandings(listOf(empty, played), 0))
        assertEquals(listOf("0", "0−0−0", "—", "1.5", "0−0"), teamStatsValues(empty, played, 0))
        assertEquals("0.333", teamStatsValues(played, played, 1)[0])
        assertEquals(listOf("5", "2"), teamStatsValues(played, played, 1).takeLast(2))
        assertEquals(listOf("1.80", "2.40"), teamStatsValues(played, played, 2).take(2))
        assertEquals("—", teamStatsValues(empty, played, 2)[0])
        assertEquals("—", teamStatsValues(empty, played, 2)[1])
        assertEquals((0..6).toList(), teamStatsVisibleColumns(1, 0, false))
        assertEquals((0..3).toList(), teamStatsVisibleColumns(1, 0, true))
        assertEquals((4..6).toList(), teamStatsVisibleColumns(1, 1, true))
        assertEquals((3..5).toList(), teamStatsVisibleColumns(2, 1, true))
        val interleague =
            listOf(
                InterleagueTeamStats(2, 0, 0, 0, 0, 0, 0),
                InterleagueTeamStats(1, 3, 2, 1, 0, null, null),
            )
        assertEquals(listOf(1, 2), interleagueStandings(interleague).map { it.team })
        assertNull(interleague[0].winningRate)
    }

    @Test
    fun teamStatHighlightsRespectMetricDirectionTiesAndMissingRates() {
        val empty = TeamSeasonStats(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val high =
            empty.copy(
                team = 2,
                hits = 12,
                atBats = 30,
                runsAllowed = 20,
                starterEarnedRuns = 1,
                starterOuts = 27,
                holds = 1,
                saves = 8,
            )
        val middle =
            empty.copy(
                team = 3,
                hits = 9,
                atBats = 30,
                runsAllowed = 30,
                starterEarnedRuns = 2,
                starterOuts = 27,
                holds = 5,
                saves = 5,
            )
        val low =
            empty.copy(
                team = 4,
                hits = 6,
                atBats = 30,
                runsAllowed = 50,
                starterEarnedRuns = 3,
                starterOuts = 27,
                holds = 9,
                saves = 1,
            )
        val league = listOf(high, middle, low)
        assertEquals(TeamStatRank.BEST, teamStatRank(high, league, 1, 0))
        assertEquals(TeamStatRank.WORST, teamStatRank(low, league, 1, 0))
        assertEquals(TeamStatRank.OTHER, teamStatRank(middle, league, 1, 0))
        assertEquals(TeamStatRank.BEST, teamStatRank(high, league, 2, 0))
        assertEquals(TeamStatRank.WORST, teamStatRank(low, league, 2, 0))
        assertEquals(TeamStatRank.BEST, teamStatRank(high, league, 2, 2))
        assertEquals(TeamStatRank.WORST, teamStatRank(low, league, 2, 2))
        assertEquals(TeamStatRank.WORST, teamStatRank(high, league, 2, 5, 0))
        assertEquals(TeamStatRank.BEST, teamStatRank(high, league, 2, 5, 1))
        assertEquals(TeamStatRank.BEST, teamStatRank(low, league, 2, 5, 0))
        assertEquals(TeamStatRank.WORST, teamStatRank(low, league, 2, 5, 1))
        assertEquals(TeamStatRank.OTHER, teamStatRank(empty, league + empty, 2, 0))
        assertEquals(TeamStatRank.BEST, teamStatRank(high.copy(team = 5), league + high.copy(team = 5), 1, 1))
        assertEquals(TeamStatRank.OTHER, teamStatRank(empty, listOf(empty, empty.copy(team = 6)), 1, 2))
        assertEquals(TeamStatRank.OTHER, teamStatRank(high, league, 0, 0))
    }

    private fun sample(): HomeData =
        HomeData(
            team = 1,
            season = 5,
            year = 2028,
            currentDay = 89,
            money = 10000,
            color = 0xFFCC00,
            teamColors = mapOf(1 to 0xFFCC00, 2 to 0x1268B1),
            teams = mapOf(1 to "東京", 2 to "大阪"),
            players = mapOf(10 to "先発 太郎", 11 to "打者 次郎"),
            days = listOf(SeasonDay(88, 3, 29, 4, 1, 1), SeasonDay(89, 3, 30, 5, 0, null), SeasonDay(90, 3, 31, 6, 1, 2)),
            games = listOf(SeasonGame(1, 2, 1, 5, 2, 10, List(9) { 11 }), SeasonGame(2, 1, 1, 2, 5, 10, List(9) { 11 })),
            slots = mapOf((1 to 1) to 1, (1 to 2) to 2),
            relief = listOf(ReliefAppearance(88, 10, 5, 7, 0, 4)),
        )
}
