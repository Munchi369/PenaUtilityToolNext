package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseFailureKind
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import java.nio.file.Path

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
    val teamColors: Map<Int, Int> = emptyMap(),
    val completedGames: Map<Int, Int> = emptyMap(),
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

data class PlayerRosterViewState(
    val phase: SaveDataLoadPhase = SaveDataLoadPhase.IDLE,
    val message: String = "選手一覧を読み込んでいません",
    val targetPath: Path? = null,
    val roster: PlayerRoster? = null,
    val technicalDetails: String? = null,
) {
    val isLoading: Boolean
        get() = phase == SaveDataLoadPhase.LOADING

    val canReload: Boolean
        get() = targetPath != null && !isLoading
}

data class PlayerRosterSettingsNotice(
    val message: String,
    val technicalDetails: String,
)
