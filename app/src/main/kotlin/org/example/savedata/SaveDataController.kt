package org.example.savedata

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
    private val closeBackgroundExecutor: () -> Unit = {},
    private val onSettingsReset: () -> Unit = {},
) : AutoCloseable {
    var state by mutableStateOf(
        SaveDataViewState(
            phase = SaveDataPhase.LOADING,
            message = "設定を読み込んでいます",
        ),
    )
        private set

    fun start() {
        backgroundExecutor.execute {
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

        backgroundExecutor.execute {
            try {
                settingsStore.save(SaveDatabaseSettings(selectedPath = target.path))
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
        backgroundExecutor.execute { probe(target, hasSavedSelection) }
    }

    fun returnToAutomaticSelection() {
        if (!state.canReturnToAutomatic) return

        val previousState = state
        state =
            SaveDataViewState(
                phase = SaveDataPhase.LOADING,
                message = "自動選択へ戻しています",
            )
        backgroundExecutor.execute {
            try {
                val settings = SaveDatabaseSettings(selectedPath = null)
                settingsStore.save(settings)
                resolveAndProbe(settings)
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
        backgroundExecutor.execute {
            try {
                val settings = SaveDatabaseSettings(selectedPath = null)
                settingsStore.save(settings)
                onSettingsReset()
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

    override fun close() {
        closeBackgroundExecutor()
    }

    private fun resolveAndProbe(settings: SaveDatabaseSettings) {
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
        val phaseAndMessage =
            when (failure.kind) {
                SaveDatabaseFailureKind.NOT_SAVE_DATABASE ->
                    SaveDataPhase.NOT_SAVE_DATABASE to "選択したフォルダーはセーブデータではありません"

                SaveDatabaseFailureKind.IN_USE ->
                    SaveDataPhase.IN_USE to "ゲームまたは別ツールがセーブデータを使用中です"

                SaveDatabaseFailureKind.UNAVAILABLE ->
                    SaveDataPhase.UNAVAILABLE to "セーブデータを利用できません"
            }

        return SaveDataViewState(
            phase = phaseAndMessage.first,
            message = phaseAndMessage.second,
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

    private fun postState(nextState: SaveDataViewState) {
        uiExecutor.execute { state = nextState }
    }
}
