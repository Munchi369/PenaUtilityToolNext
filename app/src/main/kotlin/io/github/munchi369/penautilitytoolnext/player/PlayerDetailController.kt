package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.savedata.LatestRequest
import java.nio.file.Path
import java.util.concurrent.Executor

data class PlayerDetailViewState(
    val player: PlayerRosterPlayer? = null,
    val loading: Boolean = false,
    val data: PlayerDetailData? = null,
    val error: PlayerDetailLoadResult.Failed? = null,
    val previous: Boolean = false,
    val next: Boolean = false,
    val requestedPlayerId: Int? = null,
    val requestedName: String? = null,
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
    private val rosterController: PlayerRosterController? = null,
) {
    var state by mutableStateOf(PlayerDetailViewState())
        private set
    var options by mutableStateOf(PlayerDetailOptions())
        private set
    var settings by mutableStateOf(PlayerDetailSettings())
        private set
    var settingsError by mutableStateOf<String?>(null)
        private set
    var teamColors by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set
    private var path: Path? = null
    private var players = emptyList<PlayerRosterPlayer>()
    private var standaloneRosterOptions by mutableStateOf(PlayerRosterOptions())

    val statsOptions: PlayerRosterOptions
        get() =
            (rosterController?.options ?: standaloneRosterOptions).copy(
                display = if (options.pitching) RosterDisplay.PITCHING_STATS else RosterDisplay.BATTING_STATS,
            )

    val rosterSettingsNotice: PlayerRosterSettingsNotice? get() = rosterController?.settingsNotice

    fun dismissRosterSettingsNotice() {
        rosterController?.dismissSettingsNotice()
    }

    fun updateStatsColumns(value: PlayerRosterOptions) {
        val display = statsOptions.display
        if (value.display != display) return
        val source = rosterController?.options ?: standaloneRosterOptions
        val layout =
            source.displays.getValue(display).copy(
                visibleColumns = value.current.visibleColumns,
                columnOrder = value.current.columnOrder,
            )
        val next = source.copy(displays = source.displays + (display to layout))
        if (rosterController != null) rosterController.updateOptions(next) else standaloneRosterOptions = next
    }

    private val loadRequest = LatestRequest(backgroundExecutor, uiExecutor)
    private val settingsRequest = LatestRequest(backgroundExecutor, uiExecutor)

    fun start() {
        settingsRequest.run(
            work = { Result.success(store?.load() ?: PlayerDetailSettings()) },
            onFailure = { Result.failure(it) },
        ) { result ->
            settings = result.getOrDefault(PlayerDetailSettings())
            settingsError = result.exceptionOrNull()?.let { "詳細の表示設定を読み込めません: ${it.message}" }
        }
    }

    fun open(
        path: Path,
        player: PlayerRosterPlayer,
        roster: PlayerRoster,
        rosterOptions: PlayerRosterOptions,
    ) {
        this.path = path
        standaloneRosterOptions = rosterOptions
        players = detailNavigationPlayers(roster, rosterOptions)
        teamColors = roster.teamColors
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

    fun openById(
        path: Path,
        playerId: Int,
        name: String,
        colors: Map<Int, Int> = emptyMap(),
    ) {
        require(playerId > 0)
        this.path = path
        players = emptyList()
        teamColors = colors
        options = PlayerDetailOptions()
        loadById(playerId, name)
    }

    fun close() {
        loadRequest.cancel()
        path = null
        players = emptyList()
        teamColors = emptyMap()
        state = PlayerDetailViewState()
    }

    fun move(delta: Int) {
        if (delta != -1 && delta != 1) return
        val index = players.indexOfFirst { it.playerId == state.player?.playerId }
        players.getOrNull(index + delta)?.let(::load)
    }

    fun reload() {
        if (state.loading) return
        state.player?.let(::load) ?: state.requestedPlayerId?.let { loadById(it, state.requestedName.orEmpty()) }
    }

    fun updateOptions(value: PlayerDetailOptions) {
        options = value
    }

    fun updateSettings(value: PlayerDetailSettings) {
        settings = value
        writeSettings("詳細の表示設定を保存できません") { store?.save(value) }
    }

    fun resetSettings() {
        settings = PlayerDetailSettings()
        writeSettings("詳細の表示設定を初期化できません") { store?.clear() }
    }

    private fun writeSettings(
        failure: String,
        write: () -> Unit,
    ) {
        settingsRequest.run(
            work = {
                write()
                null
            },
            onFailure = { "$failure: ${it.message}" },
            skipIfSuperseded = false,
        ) { settingsError = it }
    }

    private fun load(player: PlayerRosterPlayer) {
        val target = path ?: return
        val index = players.indexOfFirst { it.playerId == player.playerId }
        state =
            PlayerDetailViewState(
                player = player,
                loading = true,
                previous = index > 0,
                next = index >= 0 && index < players.lastIndex,
                requestedPlayerId = player.playerId,
                requestedName = player.name,
            )
        loadRequest.run(
            work = { loader.load(target, player.playerId) },
            onFailure = { PlayerDetailLoadResult.Failed(PlayerDetailLoadResult.UNAVAILABLE_MESSAGE, it.toString()) },
        ) { result ->
            state =
                when (result) {
                    is PlayerDetailLoadResult.Loaded -> state.copy(loading = false, data = result.data)
                    is PlayerDetailLoadResult.Failed -> state.copy(loading = false, error = result)
                }
        }
    }

    private fun loadById(
        playerId: Int,
        name: String,
    ) {
        val target = path ?: return
        state = PlayerDetailViewState(loading = true, requestedPlayerId = playerId, requestedName = name)
        loadRequest.run(
            work = { loader.load(target, playerId) },
            onFailure = { PlayerDetailLoadResult.Failed(PlayerDetailLoadResult.UNAVAILABLE_MESSAGE, it.toString()) },
        ) { result ->
            state =
                when (result) {
                    is PlayerDetailLoadResult.Loaded -> {
                        val position =
                            result.data.profile.value
                                ?.position
                        options = options.copy(pitching = position == PlayerPosition.PITCHER.displayName)
                        state.copy(loading = false, data = result.data)
                    }
                    is PlayerDetailLoadResult.Failed -> state.copy(loading = false, error = result)
                }
        }
    }
}
