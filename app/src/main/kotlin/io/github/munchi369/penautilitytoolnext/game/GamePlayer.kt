package io.github.munchi369.penautilitytoolnext.game

enum class PlayerPosition(
    val databaseValue: Int,
    val displayName: String,
) {
    PITCHER(1, "投手"),
    CATCHER(2, "捕手"),
    FIRST_BASEMAN(3, "一塁手"),
    SECOND_BASEMAN(4, "二塁手"),
    THIRD_BASEMAN(5, "三塁手"),
    SHORTSTOP(6, "遊撃手"),
    LEFT_FIELDER(7, "左翼手"),
    CENTER_FIELDER(8, "中堅手"),
    RIGHT_FIELDER(9, "右翼手"),
    ;

    companion object {
        fun fromDatabaseValue(value: Int): PlayerPosition? = entries.firstOrNull { it.databaseValue == value }
    }
}

/** `ITINIGUN` of `SENSYU_DATA` and of season record tables. */
enum class PlayerSquad(
    val databaseValue: Int,
) {
    FIRST(1),
    SECOND(2),
    ;

    companion object {
        fun fromDatabaseValue(value: Int): PlayerSquad? = entries.firstOrNull { it.databaseValue == value }
    }
}

enum class PlayerRegistration(
    val displayName: String,
) {
    CONTROLLED("支配下"),
    DEVELOPMENT("育成"),
}

/** `KEGA`: 0 is healthy, 1000s are discomfort and 2000s are injuries; the last three digits are remaining days. */
sealed interface PlayerInjury {
    val displayName: String

    data object None : PlayerInjury {
        override val displayName: String = "なし"
    }

    data class Discomfort(
        val remainingDays: Int,
    ) : PlayerInjury {
        override val displayName: String = "違和感・残り${remainingDays}日"
    }

    data class Injured(
        val remainingDays: Int,
    ) : PlayerInjury {
        override val displayName: String = "故障・残り${remainingDays}日"
    }

    companion object {
        fun fromDatabaseValue(value: Int): PlayerInjury? =
            when (value) {
                0 -> None
                in 1000..1999 -> Discomfort(value % 1000)
                in 2000..2999 -> Injured(value % 1000)
                else -> null
            }
    }
}
