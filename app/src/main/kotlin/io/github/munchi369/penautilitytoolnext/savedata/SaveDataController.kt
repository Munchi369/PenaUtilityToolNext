package io.github.munchi369.penautilitytoolnext.savedata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.Executor

class SaveDataController(
    private val settingsStore: SaveDatabaseSettingsStore,
    private val pathResolver: SaveDatabasePathResolver,
    private val databaseProbe: SaveDatabaseProbe,
    private val backgroundExecutor: Executor,
    private val uiExecutor: Executor,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onSettingsReset: () -> Unit = {},
) {
    private var settings = SaveDatabaseSettings(selectedPath = null)

    var backupEnabled by mutableStateOf(false)
        private set

    var backupSettingsSaving by mutableStateOf(false)
        private set

    var backupSettingsError by mutableStateOf<String?>(null)
        private set

    fun updateBackupEnabled(enabled: Boolean) {
        if (state.isBusy || state.phase == SaveDataPhase.SETTINGS_ERROR || backupSettingsSaving) return
        backupSettingsSaving = true
        backupSettingsError = null
        inBackground(state.target, state.hasSavedSelection) {
            try {
                val updated = settings.copy(backupEnabled = enabled)
                settingsStore.save(updated)
                settings = updated
                uiExecutor.execute { backupEnabled = enabled }
            } catch (error: SaveDatabaseSettingsWriteException) {
                uiExecutor.execute { backupSettingsError = error.toTechnicalDetails("バックアップ設定を保存できません") }
            } finally {
                uiExecutor.execute { backupSettingsSaving = false }
            }
        }
    }

    var state by mutableStateOf(
        SaveDataViewState(
            phase = SaveDataPhase.LOADING,
            message = "設定を読み込んでいます",
        ),
    )
        private set

    fun start() {
        inBackground(target = null, hasSavedSelection = false) {
            when (val result = settingsStore.load()) {
                is SaveDatabaseSettingsLoadResult.Invalid -> {
                    postState(
                        SaveDataViewState(
                            phase = SaveDataPhase.SETTINGS_ERROR,
                            message = "設定ファイルを読み込めません",
                            technicalDetails = result.technicalDetails,
                        ),
                    )
                }

                is SaveDatabaseSettingsLoadResult.Loaded -> resolveAndProbe(result.settings)
            }
        }
    }

    fun choose(path: Path) {
        if (!state.canChoose) return

        val target =
            SaveDatabaseTarget(
                path = path.toAbsolutePath().normalize(),
                origin = SaveDatabaseOrigin.SAVED_SETTING,
            )
        val previousHasSavedSelection = state.hasSavedSelection
        state = checkingState(target, hasSavedSelection = true)

        inBackground(target, hasSavedSelection = true) {
            try {
                val updated = settings.copy(selectedPath = target.path)
                settingsStore.save(updated)
                settings = updated
                probe(target, hasSavedSelection = true)
            } catch (error: SaveDatabaseSettingsWriteException) {
                postSettingsSaveError(
                    target = target,
                    hasSavedSelection = previousHasSavedSelection,
                    error = error,
                )
            }
        }
    }

    fun retry() {
        if (!state.canRetry) return

        val target = state.target ?: return
        val hasSavedSelection = state.hasSavedSelection
        state = checkingState(target, hasSavedSelection)
        inBackground(target, hasSavedSelection) { probe(target, hasSavedSelection) }
    }

    fun returnToAutomaticSelection() {
        if (!state.canReturnToAutomatic) return

        val previousState = state
        state =
            SaveDataViewState(
                phase = SaveDataPhase.LOADING,
                message = "自動選択へ戻しています",
            )
        inBackground(previousState.target, previousState.hasSavedSelection) {
            try {
                val updated = settings.copy(selectedPath = null)
                settingsStore.save(updated)
                resolveAndProbe(updated)
            } catch (error: SaveDatabaseSettingsWriteException) {
                postSettingsSaveError(
                    target = previousState.target,
                    hasSavedSelection = previousState.hasSavedSelection,
                    error = error,
                )
            }
        }
    }

    fun resetSettings() {
        if (!state.canResetSettings) return

        state =
            SaveDataViewState(
                phase = SaveDataPhase.LOADING,
                message = "設定を初期化しています",
            )
        inBackground(target = null, hasSavedSelection = false) {
            try {
                val settings = SaveDatabaseSettings(selectedPath = null)
                settingsStore.save(settings)
                uiExecutor.execute(onSettingsReset)
                resolveAndProbe(settings)
            } catch (error: SaveDatabaseSettingsWriteException) {
                postSettingsSaveError(
                    target = null,
                    hasSavedSelection = false,
                    error = error,
                )
            }
        }
    }

    private fun resolveAndProbe(settings: SaveDatabaseSettings) {
        this.settings = settings
        uiExecutor.execute { backupEnabled = settings.backupEnabled }
        when (val resolution = pathResolver.resolve(settings.selectedPath)) {
            is SaveDatabasePathResolution.Found -> {
                val hasSavedSelection = settings.selectedPath != null
                postState(checkingState(resolution.target, hasSavedSelection))
                probe(resolution.target, hasSavedSelection)
            }

            is SaveDatabasePathResolution.InvalidOverride -> {
                postState(
                    SaveDataViewState(
                        phase = SaveDataPhase.UNAVAILABLE,
                        message = "セーブデータを利用できません",
                        rawPath = resolution.rawPath,
                        origin = resolution.origin,
                        technicalDetails = resolution.technicalDetails,
                        hasSavedSelection = settings.selectedPath != null,
                        selectionLocked = true,
                    ),
                )
            }

            SaveDatabasePathResolution.NotFound -> {
                postState(
                    SaveDataViewState(
                        phase = SaveDataPhase.NO_SELECTION,
                        message = "セーブデータが選択されていません",
                    ),
                )
            }
        }
    }

    private fun probe(
        target: SaveDatabaseTarget,
        hasSavedSelection: Boolean,
    ) {
        val nextState =
            when (val result = databaseProbe.probe(target.path)) {
                SaveDatabaseProbeResult.Available ->
                    SaveDataViewState(
                        phase = SaveDataPhase.AVAILABLE,
                        message = "セーブデータを利用できます",
                        target = target,
                        checkedAt = clock.instant(),
                        hasSavedSelection = hasSavedSelection,
                    )

                is SaveDatabaseProbeResult.Failed ->
                    failureState(target, hasSavedSelection, result)
            }
        postState(nextState)
    }

    private fun failureState(
        target: SaveDatabaseTarget,
        hasSavedSelection: Boolean,
        failure: SaveDatabaseProbeResult.Failed,
    ): SaveDataViewState {
        val phase =
            when (failure.kind) {
                SaveDatabaseFailureKind.NOT_SAVE_DATABASE -> SaveDataPhase.NOT_SAVE_DATABASE
                SaveDatabaseFailureKind.IN_USE -> SaveDataPhase.IN_USE
                SaveDatabaseFailureKind.UNAVAILABLE -> SaveDataPhase.UNAVAILABLE
            }

        return SaveDataViewState(
            phase = phase,
            message = failure.kind.message("セーブデータを利用できません"),
            target = target,
            technicalDetails = failure.technicalDetails,
            hasSavedSelection = hasSavedSelection,
        )
    }

    private fun checkingState(
        target: SaveDatabaseTarget,
        hasSavedSelection: Boolean,
    ): SaveDataViewState =
        SaveDataViewState(
            phase = SaveDataPhase.CHECKING,
            message = "セーブデータを確認しています",
            target = target,
            hasSavedSelection = hasSavedSelection,
        )

    private fun postSettingsSaveError(
        target: SaveDatabaseTarget?,
        hasSavedSelection: Boolean,
        error: SaveDatabaseSettingsWriteException,
    ) {
        postState(
            SaveDataViewState(
                phase = SaveDataPhase.SETTINGS_SAVE_ERROR,
                message = "設定を保存できません",
                target = target,
                technicalDetails = error.toTechnicalDetails("設定ファイル: ${settingsStore.settingsFile}"),
                hasSavedSelection = hasSavedSelection,
            ),
        )
    }

    private fun inBackground(
        target: SaveDatabaseTarget?,
        hasSavedSelection: Boolean,
        work: () -> Unit,
    ) {
        backgroundExecutor.execute {
            runLogged(work) { error ->
                postState(
                    SaveDataViewState(
                        phase = SaveDataPhase.UNAVAILABLE,
                        message = "セーブデータを利用できません",
                        target = target,
                        technicalDetails = error.toTechnicalDetails("セーブデータの確認"),
                        hasSavedSelection = hasSavedSelection,
                    ),
                )
            }
        }
    }

    private fun postState(nextState: SaveDataViewState) {
        uiExecutor.execute { state = nextState }
    }
}
