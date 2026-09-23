package org.example.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path
import java.util.concurrent.Executor

data class PlayerDetailViewState(
    val player: PlayerRosterPlayer? = null,
    val loading: Boolean = false,
    val data: PlayerDetailData? = null,
    val error: PlayerDetailLoadResult.Failed? = null,
    val previous: Boolean = false,
    val next: Boolean = false,
)

data class PlayerDetailOptions(
    val tab: PlayerDetailTab = PlayerDetailTab.STATS,
    val pitching: Boolean = false,
    val division: Int = 1,
    val period: DetailStatsPeriod = DetailStatsPeriod.YEARS,
    val year: Int? = null,
)

class PlayerDetailController(
    private val loader: PlayerDetailLoader,
    private val backgroundExecutor: Executor,
    private val uiExecutor: Executor,
    private val store: PlayerDetailSettingsStore? = null,
) {
    var state by mutableStateOf(PlayerDetailViewState())
        private set
    var options by mutableStateOf(PlayerDetailOptions())
        private set
    var settings by mutableStateOf(PlayerDetailSettings())
        private set
    var settingsError by mutableStateOf<String?>(null)
        private set
    private var path: Path? = null
    private var players = emptyList<PlayerRosterPlayer>()
    private var request = 0L
    private var settingsRevision = 0L

    fun start() {
        val revision = settingsRevision
        backgroundExecutor.execute {
            val result = runCatching { store?.load() ?: PlayerDetailSettings() }
            uiExecutor.execute {
                if (revision == settingsRevision) {
                    settings = result.getOrDefault(PlayerDetailSettings())
                    settingsError = result.exceptionOrNull()?.let { "詳細の表示設定を読み込めません: ${it.message}" }
                }
            }
        }
    }

    fun open(
        path: Path,
        player: PlayerRosterPlayer,
        roster: PlayerRoster,
        rosterOptions: PlayerRosterOptions,
    ) {
        this.path = path
        players = detailNavigationPlayers(roster, rosterOptions)
        val pitching =
            when (rosterOptions.display) {
                RosterDisplay.PITCHING_ABILITY, RosterDisplay.PITCHING_STATS -> true
                RosterDisplay.BATTING_ABILITY, RosterDisplay.BATTING_STATS -> false
                RosterDisplay.BASIC -> player.position == PlayerPosition.PITCHER
            }
        options =
            PlayerDetailOptions(
                tab =
                    if (rosterOptions.display in listOf(RosterDisplay.BATTING_ABILITY, RosterDisplay.PITCHING_ABILITY)) {
                        PlayerDetailTab.ABILITY
                    } else {
                        PlayerDetailTab.STATS
                    },
                pitching = pitching,
                division = if (player.major) 3 else rosterOptions.statsSquad,
            )
        load(player)
    }

    fun close() {
        request++
        path = null
        players = emptyList()
        state = PlayerDetailViewState()
    }

    fun move(delta: Int) {
        if (delta != -1 && delta != 1) return
        val index = players.indexOfFirst { it.playerId == state.player?.playerId }
        players.getOrNull(index + delta)?.let(::load)
    }

    fun reload() {
        if (!state.loading) state.player?.let(::load)
    }

    fun updateOptions(value: PlayerDetailOptions) {
        options = value
    }

    fun updateSettings(value: PlayerDetailSettings) {
        settings = value
        val revision = ++settingsRevision
        backgroundExecutor.execute {
            val error = runCatching { store?.save(value) }.exceptionOrNull()
            uiExecutor.execute {
                if (revision == settingsRevision) settingsError = error?.let { "詳細の表示設定を保存できません: ${it.message}" }
            }
        }
    }

    fun resetSettings() {
        settings = PlayerDetailSettings()
        val revision = ++settingsRevision
        backgroundExecutor.execute {
            val error = runCatching { store?.clear() }.exceptionOrNull()
            uiExecutor.execute {
                if (revision == settingsRevision) settingsError = error?.let { "詳細の表示設定を初期化できません: ${it.message}" }
            }
        }
    }

    private fun load(player: PlayerRosterPlayer) {
        val target = path ?: return
        val version = ++request
        val index = players.indexOfFirst { it.playerId == player.playerId }
        state = PlayerDetailViewState(player, loading = true, previous = index > 0, next = index >= 0 && index < players.lastIndex)
        backgroundExecutor.execute {
            val result =
                try {
                    loader.load(target, player.playerId)
                } catch (error: Exception) {
                    PlayerDetailLoadResult.Failed("選手詳細を読み込めません", error.toString())
                }
            uiExecutor.execute {
                if (request == version) {
                    state =
                        when (result) {
                            is PlayerDetailLoadResult.Loaded -> state.copy(loading = false, data = result.data)
                            is PlayerDetailLoadResult.Failed -> state.copy(loading = false, error = result)
                        }
                }
            }
        }
    }
}
