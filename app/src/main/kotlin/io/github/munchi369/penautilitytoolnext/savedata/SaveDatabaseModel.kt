package io.github.munchi369.penautilitytoolnext.savedata

import java.nio.file.Path
import java.time.Instant

enum class SaveDatabaseOrigin(
    val displayName: String,
    val locksSelection: Boolean,
) {
    JVM_PROPERTY("起動指定（JVMプロパティ）", true),
    ENVIRONMENT_VARIABLE("起動指定（環境変数）", true),
    SAVED_SETTING("保存済み", false),
    DEVELOPMENT("開発用", false),
    STANDARD_LAYOUT("標準配置", false),
}

data class SaveDatabaseTarget(
    val path: Path,
    val origin: SaveDatabaseOrigin,
)

sealed interface SaveDatabasePathResolution {
    data class Found(
        val target: SaveDatabaseTarget,
    ) : SaveDatabasePathResolution

    data class InvalidOverride(
        val origin: SaveDatabaseOrigin,
        val rawPath: String,
        val technicalDetails: String,
    ) : SaveDatabasePathResolution

    data object NotFound : SaveDatabasePathResolution
}

data class SaveDatabaseSettings(
    val selectedPath: Path?,
    val backupEnabled: Boolean = false,
)

sealed interface SaveDatabaseSettingsLoadResult {
    data class Loaded(
        val settings: SaveDatabaseSettings,
    ) : SaveDatabaseSettingsLoadResult

    data class Invalid(
        val technicalDetails: String,
    ) : SaveDatabaseSettingsLoadResult
}

enum class SaveDatabaseFailureKind {
    NOT_SAVE_DATABASE,
    IN_USE,
    UNAVAILABLE,
    ;

    /** [unavailable] names what could not be done, because only that case differs between screens. */
    fun message(unavailable: String): String =
        when (this) {
            NOT_SAVE_DATABASE -> "選択したフォルダーはセーブデータではありません"
            IN_USE -> "ゲームまたは別ツールがセーブデータを使用中です"
            UNAVAILABLE -> unavailable
        }
}

/** Load state shared by screens that read the selected save database. */
enum class SaveDataLoadPhase {
    IDLE,
    LOADING,
    LOADED,
    NO_ACTIVE_TEAM,
    INVALID_DATA,
    NOT_SAVE_DATABASE,
    IN_USE,
    UNAVAILABLE,
    ;

    companion object {
        fun failed(kind: SaveDatabaseFailureKind): SaveDataLoadPhase =
            when (kind) {
                SaveDatabaseFailureKind.NOT_SAVE_DATABASE -> NOT_SAVE_DATABASE
                SaveDatabaseFailureKind.IN_USE -> IN_USE
                SaveDatabaseFailureKind.UNAVAILABLE -> UNAVAILABLE
            }
    }
}

sealed interface SaveDatabaseProbeResult {
    data object Available : SaveDatabaseProbeResult

    data class Failed(
        val kind: SaveDatabaseFailureKind,
        val technicalDetails: String,
    ) : SaveDatabaseProbeResult
}

enum class SaveDataPhase {
    LOADING,
    NO_SELECTION,
    CHECKING,
    AVAILABLE,
    NOT_SAVE_DATABASE,
    IN_USE,
    UNAVAILABLE,
    SETTINGS_ERROR,
    SETTINGS_SAVE_ERROR,
}

data class SaveDataViewState(
    val phase: SaveDataPhase,
    val message: String,
    val target: SaveDatabaseTarget? = null,
    val rawPath: String? = null,
    val origin: SaveDatabaseOrigin? = target?.origin,
    val checkedAt: Instant? = null,
    val technicalDetails: String? = null,
    val hasSavedSelection: Boolean = false,
    val selectionLocked: Boolean = origin?.locksSelection == true,
) {
    val isBusy: Boolean
        get() = phase == SaveDataPhase.LOADING || phase == SaveDataPhase.CHECKING

    val displayPath: String?
        get() = target?.path?.toString() ?: rawPath

    val canChoose: Boolean
        get() = !isBusy && !selectionLocked && phase != SaveDataPhase.SETTINGS_ERROR

    val canRetry: Boolean
        get() = !isBusy && target != null && phase != SaveDataPhase.SETTINGS_SAVE_ERROR

    val canReturnToAutomatic: Boolean
        get() = !isBusy && !selectionLocked && hasSavedSelection && phase != SaveDataPhase.SETTINGS_ERROR

    val canResetSettings: Boolean
        get() = !isBusy && phase == SaveDataPhase.SETTINGS_ERROR
}
