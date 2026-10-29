package io.github.munchi369.penautilitytoolnext.game

/** Domestic team numbers shared by `SAVE.MYTEAM`, `TEAM_INFO.TEAMNO` and `SENSYU_NUM`. */
object GameTeams {
    const val DOMESTIC_COUNT = 12
    val domestic: IntRange = 1..DOMESTIC_COUNT
    val firstLeague: IntRange = 1..DOMESTIC_COUNT / 2
    val secondLeague: IntRange = DOMESTIC_COUNT / 2 + 1..DOMESTIC_COUNT

    /** Tool-only team number for players in the major league; the game has no team row for them. */
    const val MAJOR = 13
}

/** `SENSYU_DATA.GENINFLG` values of players the tool treats as active. */
object PlayerStatusCode {
    const val ACTIVE = 1
    const val MAJOR = 3
    const val ACTIVE_OR_MAJOR_SQL = "($ACTIVE, $MAJOR)"
}

/**
 * `SENSYU_NUM` = (team - 1) * 100 + number % 100, plus 10000 for development contracts.
 * Development numbers are shown as 100 + number % 100.
 */
object PlayerNumberCode {
    private const val TEAM_BLOCK = 100
    private const val DEVELOPMENT_OFFSET = 10000

    fun team(raw: Int): Int = raw % DEVELOPMENT_OFFSET / TEAM_BLOCK + 1

    fun uniformNumber(raw: Int): Int = raw % TEAM_BLOCK + raw / DEVELOPMENT_OFFSET * TEAM_BLOCK

    fun registration(raw: Int): PlayerRegistration =
        if (raw >= DEVELOPMENT_OFFSET) PlayerRegistration.DEVELOPMENT else PlayerRegistration.CONTROLLED

    fun isDomestic(raw: Int): Boolean =
        raw in 0 until GameTeams.DOMESTIC_COUNT * TEAM_BLOCK ||
            raw in DEVELOPMENT_OFFSET until DEVELOPMENT_OFFSET + GameTeams.DOMESTIC_COUNT * TEAM_BLOCK

    fun encode(
        team: Int,
        registration: PlayerRegistration,
        uniformNumber: Int,
    ): Int =
        (team - 1) * TEAM_BLOCK + uniformNumber % TEAM_BLOCK +
            if (registration == PlayerRegistration.DEVELOPMENT) DEVELOPMENT_OFFSET else 0

    /** SQL condition for the `SENSYU_NUM` blocks of one domestic team, controlled and development. */
    fun teamCondition(team: Int): String {
        val base = (team - 1) * TEAM_BLOCK
        return "(SENSYU_NUM BETWEEN $base AND ${base + TEAM_BLOCK - 1} " +
            "OR SENSYU_NUM BETWEEN ${base + DEVELOPMENT_OFFSET} AND ${base + DEVELOPMENT_OFFSET + TEAM_BLOCK - 1})"
    }
}

/** Rows of the `SAVE` table. */
object SaveRows {
    /** The row of the save in progress; rows with a negative `MYTEAM` hold per-team settings such as colors. */
    const val CURRENT_CONDITION = "MYTEAM >= 0 OR MYTEAM IS NULL"

    /** Team colors are stored as RGB in the `SYSNENME`, `SYSDAY` and `MONEY` columns of this row. */
    fun teamColorMyTeam(team: Int): Int = -(team + 10)

    val teamColorColumns = listOf("SYSNENME", "SYSDAY", "MONEY")
}

/** Rows of the `KIYO` table. */
object KiyoRows {
    /** The regular lineup row. `HIKAEDH12` holds the row kind: 1 is for skipped games and 3 is history. */
    const val REGULAR_CONDITION = "HIKAEDH12 = 0"

    /** History rows; all-star members are stored here with the year in `HIKAE13`. */
    const val HISTORY_CONDITION = "HIKAEDH12 = 3"
}

/** Day-of-season (`SAVE.SYSDAY`) rules taken from the game. */
object GameCalendar {
    const val DEMOTION_WAIT_DAYS = 10

    /** 一軍入替: demotions start the wait except on days 86, 87 and from day 279 onward. */
    fun startsDemotionWait(day: Int): Boolean = day != 86 && day != 87 && day < 279

    /** 支配下登録 is possible until day 212 and from day 309. */
    fun allowsControlledRegistration(day: Int): Boolean = day <= 212 || day >= 309

    /** From this day the ready stamina of starters is 10 lower. */
    const val LATE_SEASON_STAMINA_DAY = 316
}
