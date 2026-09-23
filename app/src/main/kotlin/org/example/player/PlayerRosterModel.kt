package org.example.player

import org.example.savedata.SaveDatabaseFailureKind
import java.nio.file.Path

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

enum class PlayerSquad {
    FIRST,
    SECOND,
}

enum class PlayerRegistration(
    val displayName: String,
) {
    CONTROLLED("支配下"),
    DEVELOPMENT("育成"),
}

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
}

data class PlayerRosterPlayer(
    val playerId: Int,
    val uniformNumber: Int,
    val name: String,
    val overall: Int,
    val position: PlayerPosition,
    val age: Int,
    val squad: PlayerSquad,
    val registration: PlayerRegistration,
    val injury: PlayerInjury,
    val teamNumber: Int = 0,
    val teamName: String = "",
    val major: Boolean = false,
    val information: Map<String, PlayerCell> = emptyMap(),
    val batting: Map<Int, Map<String, PlayerCell>> = emptyMap(),
    val pitching: Map<Int, Map<String, PlayerCell>> = emptyMap(),
)

data class PlayerRoster(
    val firstTeam: List<PlayerRosterPlayer>,
    val secondTeam: List<PlayerRosterPlayer>,
    val players: List<PlayerRosterPlayer> = firstTeam + secondTeam,
    val teams: Map<Int, String> = emptyMap(),
    val currentTeam: Int? = null,
    val season: Int? = null,
)

sealed interface PlayerRosterLoadResult {
    data class Loaded(
        val roster: PlayerRoster,
    ) : PlayerRosterLoadResult

    data class NoActiveTeam(
        val technicalDetails: String,
    ) : PlayerRosterLoadResult

    data class InvalidData(
        val technicalDetails: String,
    ) : PlayerRosterLoadResult

    data class Failed(
        val kind: SaveDatabaseFailureKind,
        val technicalDetails: String,
    ) : PlayerRosterLoadResult
}

fun interface PlayerRosterLoader {
    fun load(path: Path): PlayerRosterLoadResult
}

enum class PlayerRosterPhase {
    IDLE,
    LOADING,
    LOADED,
    NO_ACTIVE_TEAM,
    INVALID_DATA,
    NOT_SAVE_DATABASE,
    IN_USE,
    UNAVAILABLE,
}

data class PlayerRosterViewState(
    val phase: PlayerRosterPhase = PlayerRosterPhase.IDLE,
    val message: String = "選手一覧を読み込んでいません",
    val targetPath: Path? = null,
    val roster: PlayerRoster? = null,
    val technicalDetails: String? = null,
) {
    val isLoading: Boolean
        get() = phase == PlayerRosterPhase.LOADING

    val canReload: Boolean
        get() = targetPath != null && !isLoading
}

data class PlayerRosterSettingsNotice(
    val message: String,
    val technicalDetails: String,
)
