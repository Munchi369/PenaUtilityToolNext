package io.github.munchi369.penautilitytoolnext.savedata

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

internal val applicationLogger: Logger = Logger.getLogger("PenaUtilityToolNext")

/** Keeps a few small rotating log files; the packaged app has no console to show stderr. */
fun installFileLogging(directory: Path) {
    runCatching {
        Files.createDirectories(directory)
        val handler =
            FileHandler(directory.resolve("app-%g.log").toString(), 1_000_000, 3, true).apply {
                encoding = "UTF-8"
                formatter = SimpleFormatter()
            }
        applicationLogger.addHandler(handler)
    }.onFailure { applicationLogger.log(Level.WARNING, "ログファイルを開けません: $directory", it) }
}

/**
 * Runs background work whose result only matters while it is the newest request.
 * Start and cancel on the UI executor; a superseded request is skipped if it has not started yet
 * and its result is dropped if it has.
 */
class LatestRequest(
    private val backgroundExecutor: Executor,
    private val uiExecutor: Executor,
) {
    @Volatile
    private var version = 0L

    fun cancel() {
        version++
    }

    /**
     * Unexpected exceptions are logged and turned into a result by [onFailure] so callers never stay in a loading state.
     * Writes pass `skipIfSuperseded = false` so they still run when a newer request arrives before they start.
     */
    fun <T> run(
        work: () -> T,
        onFailure: (Exception) -> T,
        skipIfSuperseded: Boolean = true,
        deliver: (T) -> Unit,
    ) {
        val request = ++version
        backgroundExecutor.execute {
            if (skipIfSuperseded && request != version) return@execute
            val result = runLogged(work, onFailure)
            uiExecutor.execute { if (request == version) deliver(result) }
        }
    }
}

internal fun <T> runLogged(
    work: () -> T,
    onFailure: (Exception) -> T,
): T =
    try {
        work()
    } catch (error: Exception) {
        applicationLogger.log(Level.SEVERE, "バックグラウンド処理で予期しない例外が発生しました", error)
        onFailure(error)
    }
