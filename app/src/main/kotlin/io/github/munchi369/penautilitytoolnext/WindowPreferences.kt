package io.github.munchi369.penautilitytoolnext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.munchi369.penautilitytoolnext.savedata.LatestRequest
import io.github.munchi369.penautilitytoolnext.savedata.toSettingsText
import io.github.munchi369.penautilitytoolnext.savedata.writeSettingsFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.min

internal data class WindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0)
    }

    fun overlap(other: WindowBounds): Long {
        val width = min(x.toLong() + width, other.x.toLong() + other.width) - max(x.toLong(), other.x.toLong())
        val height = min(y.toLong() + height, other.y.toLong() + other.height) - max(y.toLong(), other.y.toLong())
        return width.coerceAtLeast(0) * height.coerceAtLeast(0)
    }
}

internal data class WindowPreferences(
    val bounds: WindowBounds,
    val maximized: Boolean = false,
)

/** Work areas and window bounds use AWT logical coordinates, including negative monitor origins. */
internal fun restoreWindowPreferences(
    saved: WindowPreferences?,
    workAreas: List<WindowBounds>,
): WindowPreferences {
    require(workAreas.isNotEmpty())
    val primary = workAreas.first()
    if (saved == null) {
        val width = min(1440, (primary.width * 0.9).toInt().coerceAtLeast(1))
        val height = min(900, (primary.height * 0.9).toInt().coerceAtLeast(1))
        return WindowPreferences(
            WindowBounds(
                primary.x + (primary.width - width) / 2,
                primary.y + (primary.height - height) / 2,
                width,
                height,
            ),
        )
    }
    val screen = workAreas.maxBy { it.overlap(saved.bounds) }.takeIf { it.overlap(saved.bounds) > 0 } ?: primary
    val width = saved.bounds.width.coerceAtMost(screen.width)
    val height = saved.bounds.height.coerceAtMost(screen.height)
    val disconnected = screen.overlap(saved.bounds) == 0L
    val x =
        if (disconnected) {
            screen.x + (screen.width - width) / 2
        } else {
            saved.bounds.x.coerceIn(screen.x, screen.x + screen.width - width)
        }
    val y =
        if (disconnected) {
            screen.y + (screen.height - height) / 2
        } else {
            saved.bounds.y.coerceIn(screen.y, screen.y + screen.height - height)
        }
    return saved.copy(bounds = WindowBounds(x, y, width, height))
}

/** Retains the normal bounds when maximize/minimize events report different native bounds. */
internal class WindowBoundsTracker(
    initial: WindowPreferences,
) {
    var preferences: WindowPreferences = initial
        private set

    fun update(
        bounds: WindowBounds,
        maximized: Boolean,
        minimized: Boolean,
        workArea: WindowBounds? = null,
    ) {
        if (minimized) return
        preferences = preferences.copy(bounds = if (maximized) preferences.bounds else bounds, maximized = maximized)
        if (maximized && workArea != null) {
            preferences = restoreWindowPreferences(preferences, listOf(workArea))
        }
    }
}

internal class WindowPreferencesStore(
    private val file: Path,
) {
    fun load(): WindowPreferences? {
        if (!Files.exists(file)) return null
        val properties = Properties()
        Files.newBufferedReader(file).use(properties::load)
        require(properties.getProperty("schemaVersion") == "1") { "ウィンドウ設定の形式を読み取れません: $file" }
        return WindowPreferences(
            WindowBounds(
                properties.getProperty("x").toInt(),
                properties.getProperty("y").toInt(),
                properties.getProperty("width").toInt(),
                properties.getProperty("height").toInt(),
            ),
            properties.getProperty("maximized").toBooleanStrict(),
        )
    }

    fun save(preferences: WindowPreferences) {
        val properties =
            Properties().apply {
                setProperty("schemaVersion", "1")
                setProperty("x", preferences.bounds.x.toString())
                setProperty("y", preferences.bounds.y.toString())
                setProperty("width", preferences.bounds.width.toString())
                setProperty("height", preferences.bounds.height.toString())
                setProperty("maximized", preferences.maximized.toString())
            }
        writeSettingsFile(file, properties.toSettingsText())
    }
}

internal class WindowPreferencesController(
    private val store: WindowPreferencesStore,
    backgroundExecutor: Executor,
    uiExecutor: Executor,
) {
    private val requests = LatestRequest(backgroundExecutor, uiExecutor)
    var loaded by mutableStateOf(false)
        private set
    var saved: WindowPreferences? by mutableStateOf(null)
        private set
    private var closing = false

    fun load() {
        requests.run(store::load, onFailure = { null }) {
            saved = it
            loaded = true
        }
    }

    fun saveAndClose(
        preferences: WindowPreferences,
        onClose: () -> Unit,
    ) {
        if (closing) return
        closing = true
        requests.run(
            work = { store.save(preferences) },
            onFailure = { },
            skipIfSuperseded = false,
        ) { onClose() }
    }
}
