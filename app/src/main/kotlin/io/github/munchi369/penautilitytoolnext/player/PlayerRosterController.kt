package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.munchi369.penautilitytoolnext.savedata.LatestRequest
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import io.github.munchi369.penautilitytoolnext.savedata.sameSaveDatabase
import io.github.munchi369.penautilitytoolnext.savedata.toTechnicalDetails
import java.nio.file.Path
import java.util.concurrent.Executor

class PlayerRosterController(
    private val rosterLoader: PlayerRosterLoader,
    private val backgroundExecutor: Executor,
    private val uiExecutor: Executor,
    private val settingsStore: PlayerRosterSettingsStore = NoOpPlayerRosterSettingsStore,
) {
    var state by mutableStateOf(PlayerRosterViewState())
        private set

    private val loadRequest = LatestRequest(backgroundExecutor, uiExecutor)
    private val settingsRequest = LatestRequest(backgroundExecutor, uiExecutor)
    var options by mutableStateOf(PlayerRosterOptions())
        private set
    private var optionsPath: Path? = null
    var settingsNotice by mutableStateOf<PlayerRosterSettingsNotice?>(null)
        private set

    fun updateOptions(value: PlayerRosterOptions) {
        val nextOptions = value.coercedGrouping()
        val path = optionsPath
        val previousSettings = path?.let { PlayerRosterSettings.from(it, options) }
        val nextSettings = path?.let { PlayerRosterSettings.from(it, nextOptions) }
        options = nextOptions
        if (nextSettings != null && nextSettings != previousSettings) {
            persist(nextSettings)
        }
    }

    fun load(path: Path) {
        val normalizedPath = path.toAbsolutePath().normalize()
        val resetOptions = optionsPath?.let { !sameSaveDatabase(it, normalizedPath) } ?: true
        if (resetOptions) {
            settingsRequest.cancel()
            settingsNotice = null
            options = PlayerRosterOptions()
            optionsPath = null
        }
        state =
            PlayerRosterViewState(
                phase = SaveDataLoadPhase.LOADING,
                message = "現役選手を読み込んでいます",
                targetPath = normalizedPath,
            )
        loadRequest.run(
            work = {
                val nextState = rosterLoader.load(normalizedPath).toViewState(normalizedPath)
                nextState to if (resetOptions && nextState.roster != null) loadSettings(normalizedPath) else null
            },
            onFailure = { error ->
                PlayerRosterViewState(
                    phase = SaveDataLoadPhase.UNAVAILABLE,
                    message = UNAVAILABLE_MESSAGE,
                    targetPath = normalizedPath,
                    technicalDetails = error.toTechnicalDetails("選手一覧の読み込み: $normalizedPath"),
                ) to null
            },
        ) { (nextState, settingsRestore) ->
            if (resetOptions && nextState.roster != null) {
                val defaults =
                    nextState.roster.currentTeam?.let(PlayerRosterOptions::forTeam)
                        ?: PlayerRosterOptions(teamScope = RosterTeamScope.All)
                options = settingsRestore?.settings?.restore(defaults) ?: defaults
                optionsPath = normalizedPath
                settingsNotice = settingsRestore?.notice
            }
            state = nextState
        }
    }

    fun reload() {
        if (!state.canReload) return
        state.targetPath?.let(::load)
    }

    fun dismissSettingsNotice() {
        settingsNotice = null
    }

    fun resetPersistedSettings() {
        writeSettings("一覧設定を初期化できませんでした", settingsStore::clear) {
            options = PlayerRosterOptions()
            optionsPath = null
        }
    }

    private fun loadSettings(path: Path): SettingsRestore =
        when (val result = settingsStore.load()) {
            PlayerRosterSettingsLoadResult.Missing -> SettingsRestore()
            is PlayerRosterSettingsLoadResult.Invalid ->
                SettingsRestore(
                    notice =
                        PlayerRosterSettingsNotice(
                            message = "一覧設定を読み込めませんでした",
                            technicalDetails = result.technicalDetails,
                        ),
                )
            is PlayerRosterSettingsLoadResult.Loaded ->
                if (sameSaveDatabase(result.settings.savePath, path)) {
                    SettingsRestore(settings = result.settings)
                } else {
                    try {
                        settingsStore.clear()
                        SettingsRestore()
                    } catch (error: PlayerRosterSettingsWriteException) {
                        SettingsRestore(
                            notice =
                                PlayerRosterSettingsNotice(
                                    message = "以前の一覧設定を初期化できませんでした",
                                    technicalDetails = error.toTechnicalDetails(),
                                ),
                        )
                    }
                }
        }

    private fun persist(settings: PlayerRosterSettings) {
        writeSettings("一覧設定を保存できませんでした", { settingsStore.save(settings) })
    }

    private fun writeSettings(
        message: String,
        write: () -> Unit,
        onSuccess: () -> Unit = {},
    ) {
        settingsRequest.run(
            work = {
                write()
                null
            },
            onFailure = { error ->
                PlayerRosterSettingsNotice(
                    message = message,
                    technicalDetails =
                        if (error is PlayerRosterSettingsWriteException) error.toTechnicalDetails() else error.toTechnicalDetails("一覧設定"),
                )
            },
            skipIfSuperseded = false,
        ) { notice ->
            settingsNotice = notice
            if (notice == null) onSuccess()
        }
    }

    private fun PlayerRosterSettingsWriteException.toTechnicalDetails(): String =
        buildString {
            appendLine("例外: ${this@toTechnicalDetails::class.qualifiedName}")
            this@toTechnicalDetails.message?.let { appendLine("メッセージ: $it") }
            cause?.message?.let { append("原因: $it") }
        }.trimEnd()

    private fun PlayerRosterLoadResult.toViewState(path: Path): PlayerRosterViewState =
        when (this) {
            is PlayerRosterLoadResult.Loaded ->
                PlayerRosterViewState(
                    phase = SaveDataLoadPhase.LOADED,
                    message = "現役選手一覧",
                    targetPath = path,
                    roster = roster,
                )

            is PlayerRosterLoadResult.NoActiveTeam ->
                PlayerRosterViewState(
                    phase = SaveDataLoadPhase.NO_ACTIVE_TEAM,
                    message = "自球団を確認できません",
                    targetPath = path,
                    technicalDetails = technicalDetails,
                )

            is PlayerRosterLoadResult.InvalidData ->
                PlayerRosterViewState(
                    phase = SaveDataLoadPhase.INVALID_DATA,
                    message = "選手データを解釈できません",
                    targetPath = path,
                    technicalDetails = technicalDetails,
                )

            is PlayerRosterLoadResult.Failed ->
                PlayerRosterViewState(
                    phase = SaveDataLoadPhase.failed(kind),
                    message = kind.message(UNAVAILABLE_MESSAGE),
                    targetPath = path,
                    technicalDetails = technicalDetails,
                )
        }

    private data class SettingsRestore(
        val settings: PlayerRosterSettings? = null,
        val notice: PlayerRosterSettingsNotice? = null,
    )

    private companion object {
        const val UNAVAILABLE_MESSAGE = "選手一覧を読み込めません"
    }
}
