package org.example.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.example.savedata.SaveDatabaseFailureKind
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

    private var requestVersion = 0L
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
        val version = ++requestVersion
        val resetOptions = optionsPath?.let { !samePath(it, normalizedPath) } ?: true
        if (resetOptions) {
            options = PlayerRosterOptions()
            optionsPath = null
        }
        state =
            PlayerRosterViewState(
                phase = PlayerRosterPhase.LOADING,
                message = "現役選手を読み込んでいます",
                targetPath = normalizedPath,
            )
        backgroundExecutor.execute {
            val nextState = rosterLoader.load(normalizedPath).toViewState(normalizedPath)
            val settingsRestore =
                if (resetOptions && nextState.roster != null) {
                    loadSettings(normalizedPath)
                } else {
                    null
                }
            uiExecutor.execute {
                if (version == requestVersion) {
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
        backgroundExecutor.execute {
            try {
                settingsStore.clear()
                uiExecutor.execute {
                    options = PlayerRosterOptions()
                    optionsPath = null
                    settingsNotice = null
                }
            } catch (error: PlayerRosterSettingsWriteException) {
                postSettingsNotice("一覧設定を初期化できませんでした", error)
            }
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
                if (samePath(result.settings.savePath, path)) {
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
        backgroundExecutor.execute {
            try {
                settingsStore.save(settings)
                uiExecutor.execute { settingsNotice = null }
            } catch (error: PlayerRosterSettingsWriteException) {
                postSettingsNotice("一覧設定を保存できませんでした", error)
            }
        }
    }

    private fun postSettingsNotice(
        message: String,
        error: PlayerRosterSettingsWriteException,
    ) {
        uiExecutor.execute {
            settingsNotice =
                PlayerRosterSettingsNotice(
                    message = message,
                    technicalDetails = error.toTechnicalDetails(),
                )
        }
    }

    private fun PlayerRosterSettingsWriteException.toTechnicalDetails(): String =
        buildString {
            appendLine("例外: ${this@toTechnicalDetails::class.qualifiedName}")
            this@toTechnicalDetails.message?.let { appendLine("メッセージ: $it") }
            cause?.message?.let { append("原因: $it") }
        }.trimEnd()

    private fun samePath(
        first: Path,
        second: Path,
    ): Boolean =
        first
            .toAbsolutePath()
            .normalize()
            .toString()
            .equals(second.toAbsolutePath().normalize().toString(), ignoreCase = true)

    private fun PlayerRosterLoadResult.toViewState(path: Path): PlayerRosterViewState =
        when (this) {
            is PlayerRosterLoadResult.Loaded ->
                PlayerRosterViewState(
                    phase = PlayerRosterPhase.LOADED,
                    message = "現役選手一覧",
                    targetPath = path,
                    roster = roster,
                )

            is PlayerRosterLoadResult.NoActiveTeam ->
                PlayerRosterViewState(
                    phase = PlayerRosterPhase.NO_ACTIVE_TEAM,
                    message = "自球団を確認できません",
                    targetPath = path,
                    technicalDetails = technicalDetails,
                )

            is PlayerRosterLoadResult.InvalidData ->
                PlayerRosterViewState(
                    phase = PlayerRosterPhase.INVALID_DATA,
                    message = "選手データを解釈できません",
                    targetPath = path,
                    technicalDetails = technicalDetails,
                )

            is PlayerRosterLoadResult.Failed ->
                failurePresentation(kind).let { presentation ->
                    PlayerRosterViewState(
                        phase = presentation.phase,
                        message = presentation.message,
                        targetPath = path,
                        technicalDetails = technicalDetails,
                    )
                }
        }

    private fun failurePresentation(kind: SaveDatabaseFailureKind): FailurePresentation =
        when (kind) {
            SaveDatabaseFailureKind.NOT_SAVE_DATABASE ->
                FailurePresentation(
                    phase = PlayerRosterPhase.NOT_SAVE_DATABASE,
                    message = "選択したフォルダーはセーブデータではありません",
                )

            SaveDatabaseFailureKind.IN_USE ->
                FailurePresentation(
                    phase = PlayerRosterPhase.IN_USE,
                    message = "ゲームまたは別ツールがセーブデータを使用中です",
                )

            SaveDatabaseFailureKind.UNAVAILABLE ->
                FailurePresentation(
                    phase = PlayerRosterPhase.UNAVAILABLE,
                    message = "選手一覧を読み込めません",
                )
        }

    private data class FailurePresentation(
        val phase: PlayerRosterPhase,
        val message: String,
    )

    private data class SettingsRestore(
        val settings: PlayerRosterSettings? = null,
        val notice: PlayerRosterSettingsNotice? = null,
    )
}
