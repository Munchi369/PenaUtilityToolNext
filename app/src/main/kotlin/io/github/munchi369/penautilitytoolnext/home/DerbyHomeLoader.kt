package io.github.munchi369.penautilitytoolnext.home

import io.github.munchi369.penautilitytoolnext.game.GameTeams
import io.github.munchi369.penautilitytoolnext.game.SaveRows
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import io.github.munchi369.penautilitytoolnext.savedata.nullableInt
import io.github.munchi369.penautilitytoolnext.savedata.requiredInt
import io.github.munchi369.penautilitytoolnext.savedata.rows
import io.github.munchi369.penautilitytoolnext.savedata.runLogged
import io.github.munchi369.penautilitytoolnext.team.teamColor
import java.nio.file.Path
import java.sql.Connection

internal class DerbyHomeLoader(
    private val access: DerbySaveDatabaseAccess,
) : HomeLoader {
    override fun load(path: Path): Result<HomeData> =
        when (val result = access.read(path, "ホームの読み込み", ::query)) {
            is SaveDatabaseResult.Success -> Result.success(result.value)
            is SaveDatabaseResult.Failed -> Result.failure(IllegalStateException(result.technicalDetails))
        }

    private fun query(connection: Connection): HomeData {
        val context =
            connection
                .rows("SELECT MYTEAM, SYSNENME, SYSDAY, MONEY FROM SAVE WHERE ${SaveRows.CURRENT_CONDITION}") {
                    listOf(requiredInt("MYTEAM"), requiredInt("SYSNENME"), requiredInt("SYSDAY"), nullableInt("MONEY"))
                }.single()
        val team = requireNotNull(context[0]).also { require(it in GameTeams.domestic) }
        val season = requireNotNull(context[1])
        val notices = mutableListOf<String>()

        fun <T> section(
            label: String,
            work: () -> T,
            fallback: T,
        ): T =
            runLogged(work) {
                notices += "$label を読み込めません"
                fallback
            }
        val year = section("西暦", { connection.rows("SELECT NENDO FROM SETTEI") { requiredInt("NENDO") }.single() + season }, null)
        val colors =
            section("チームカラー", {
                GameTeams.domestic.mapNotNull { number -> connection.teamColor(number)?.let { number to it } }.toMap()
            }, emptyMap())
        val names = connection.rows("SELECT SENSYU_ID, NAME FROM SENSYU_DATA") { requiredInt("SENSYU_ID") to getString("NAME") }.toMap()
        val days =
            seasonGameNumbers(
                connection.rows("SELECT DAY, TUKI, HI, YOBI, EVENT FROM NITTEI ORDER BY DAY") {
                    SeasonDay(requiredInt("DAY"), requiredInt("TUKI"), requiredInt("HI"), requiredInt("YOBI"), requiredInt("EVENT"), null)
                        .also { require(it.month in 1..12 && it.date in 1..31 && it.weekday in 0..6) }
                },
            )
        val games =
            connection.rows("SELECT * FROM TEAM_SIAI_KEKKA WHERE NENME = ? AND ITINIGUN = 1", season) {
                SeasonGame(
                    team = requiredInt("TEAM"),
                    opponent = requiredInt("AITE"),
                    game = requiredInt("JUN"),
                    runs = requiredInt("TOKUTEN"),
                    allowed = requiredInt("SITTEN"),
                    starter = nullableInt("SENPATU"),
                    battingOrder = (1..9).map { nullableInt("DAJUN$it") },
                )
            }
        require(games.map { it.team to it.game }.distinct().size == games.size) { "試合結果が重複しています" }
        val teamStats =
            section("球団成績", {
                val stats =
                    connection
                        .rows("SELECT * FROM TEAM_SEISEKI WHERE NENDO = ? AND ITINIGUN = 1 AND TEAMNO BETWEEN 1 AND 12", season) {
                            TeamSeasonStats(
                                team = requiredInt("TEAMNO"),
                                games = requiredInt("SIAI"),
                                wins = requiredInt("KATI"),
                                losses = requiredInt("MAKE"),
                                draws = requiredInt("HIKIWAKE"),
                                runs = requiredInt("TOKUTEN"),
                                runsAllowed = requiredInt("SITTEN"),
                                hits = requiredInt("ANDA"),
                                atBats = requiredInt("DASU"),
                                homeRuns = requiredInt("HONRUI"),
                                steals = requiredInt("TOURUI"),
                                earnedRuns = requiredInt("JISEKI"),
                                pitchingOuts = requiredInt("KAISUU"),
                                completeGames = requiredInt("KANTOU"),
                                shutouts = requiredInt("KANPUU"),
                                holds = requiredInt("H"),
                                saves = requiredInt("S"),
                                walks = requiredInt("SIKYU"),
                                sacrificeBunts = requiredInt("GIDA"),
                                starterEarnedRuns = requiredInt("JISEKIS"),
                                starterOuts = requiredInt("KAISUUS"),
                                reliefEarnedRuns = requiredInt("JISEKIN"),
                                reliefOuts = requiredInt("KAISUUN"),
                            )
                        }
                require(stats.map(TeamSeasonStats::team).distinct().size == stats.size) { "球団成績が重複しています" }
                stats
            }, emptyList())
        val currentDay = requireNotNull(context[2])
        var interleagueStatsUnavailable = false
        val interleagueStats =
            if (currentDay < 155) {
                emptyList()
            } else {
                runLogged({
                    val interleagueGames = days.filter { it.day in 155..174 && it.event == 1 }.mapNotNull(SeasonDay::game).toSet()
                    val resultsByTeam = games.filter { it.game in interleagueGames }.groupBy(SeasonGame::team)
                    val stats =
                        connection.rows(
                            "SELECT TEAMNO, SIAI, KATI, MAKE, HIKIWAKE FROM TEAM_SEISEKI " +
                                "WHERE NENDO = ? AND ITINIGUN = 1 AND TEAMNO BETWEEN 13 AND 24",
                            season,
                        ) {
                            val teamNumber = requiredInt("TEAMNO") - 12
                            val played = requiredInt("SIAI")
                            val results = resultsByTeam[teamNumber].orEmpty()
                            InterleagueTeamStats(
                                team = teamNumber,
                                games = played,
                                wins = requiredInt("KATI"),
                                losses = requiredInt("MAKE"),
                                draws = requiredInt("HIKIWAKE"),
                                runs = results.takeIf { it.size == played }?.sumOf(SeasonGame::runs),
                                runsAllowed = results.takeIf { it.size == played }?.sumOf(SeasonGame::allowed),
                            )
                        }
                    require(
                        (stats.isEmpty() && resultsByTeam.isEmpty()) ||
                            (stats.size == 12 && stats.map(InterleagueTeamStats::team).toSet() == GameTeams.domestic.toSet()),
                    ) {
                        "交流戦の12球団の成績が揃っていません"
                    }
                    stats
                }) {
                    interleagueStatsUnavailable = true
                    emptyList()
                }
            }
        val postseason =
            if (currentDay < 284) {
                null
            } else {
                section("ポストシーズン結果", {
                    connection
                        .rows("SELECT * FROM CS WHERE NENME = ?", season) {
                            PostseasonResults(
                                first1 = requiredInt("FIRST1"),
                                first2 = requiredInt("FIRST2"),
                                first3 = requiredInt("FIRST3"),
                                second1 = requiredInt("SECOND1"),
                                second2 = requiredInt("SECOND2"),
                                second3 = requiredInt("SECOND3"),
                                firstCs1 = requiredInt("FIRSTCS1"),
                                secondCs1 = requiredInt("SECONDCS1"),
                                firstCs2 = requiredInt("FIRSTCS2"),
                                secondCs2 = requiredInt("SECONDCS2"),
                                japanWinner = requiredInt("NIHON1"),
                            )
                        }.singleOrNull()
                }, null)
            }
        val relief =
            section("救援登板履歴", {
                connection.rows("SELECT * FROM NAKATSUGI_RIREKI WHERE NENME = ?", season) {
                    ReliefAppearance(
                        day = requiredInt("DATE"),
                        player = requiredInt("ID"),
                        outs = requiredInt("KAI"),
                        batters = requiredInt("DASHA"),
                        runs = requiredInt("SITTEN"),
                        decision = requiredInt("KMHS"),
                    )
                }
            }, emptyList())
        val injuries =
            section("故障・違和感履歴", {
                connection.rows(
                    "SELECT HAN1, HAN2, HAN4, HAN5, HAN6 FROM RIREKI WHERE KUBUN = 6 AND NENME = ?",
                    season,
                ) {
                    MatchInjury(
                        team = requiredInt("HAN1"),
                        player = requiredInt("HAN2"),
                        day = requiredInt("HAN4"),
                        kind = requiredInt("HAN5"),
                        duration = requiredInt("HAN6"),
                    )
                }
            }, emptyList())
        val slots =
            connection
                .rows("SELECT JUN, TEAM, TEAMNUM FROM SIAI_NITTEI") {
                    (requiredInt("JUN") to requiredInt("TEAM")) to requiredInt("TEAMNUM")
                }.toMap()
        return HomeData(
            team = team,
            season = season,
            year = year,
            currentDay = currentDay,
            money = context[3],
            color = colors[team],
            teams = connection.rows("SELECT TEAMNO, TEAMNAME FROM TEAM_INFO") { requiredInt("TEAMNO") to getString("TEAMNAME") }.toMap(),
            players = names,
            days = days,
            games = games,
            slots = slots,
            relief = relief,
            notices = notices,
            injuries = injuries,
            teamColors = colors,
            teamStats = teamStats,
            interleagueStats = interleagueStats,
            interleagueStatsUnavailable = interleagueStatsUnavailable,
            postseason = postseason,
        )
    }
}
