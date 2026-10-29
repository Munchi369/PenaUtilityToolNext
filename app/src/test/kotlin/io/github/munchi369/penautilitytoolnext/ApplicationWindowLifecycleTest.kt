package io.github.munchi369.penautilitytoolnext

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.awaitApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Frame
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class ApplicationWindowLifecycleTest {
    @Test
    fun asynchronousSettingsLoadCreatesWindowAndClosingSavesBeforeExit(
        @TempDir directory: Path,
    ) = runBlocking {
        val background = LinkedBlockingQueue<Runnable>()
        val store = WindowPreferencesStore(directory.resolve("window.properties"))
        val preferences = WindowPreferencesController(store, Executor(background::add), Executor(SwingUtilities::invokeLater))
        val created = CompletableDeferred<Frame>()
        val application =
            async(Dispatchers.Default) {
                awaitApplication {
                    DisposableEffect(Unit) {
                        preferences.load()
                        onDispose { }
                    }
                    ApplicationWindow(preferences, onClose = ::exitApplication, visible = false) {
                        LaunchedEffect(window) { created.complete(window) }
                    }
                }
            }
        try {
            withTimeout(15_000) {
                val load = withContext(Dispatchers.IO) { background.poll(5, TimeUnit.SECONDS) }
                assertNotNull(load)
                load!!.run()
                val window = created.await()
                SwingUtilities.invokeAndWait {
                    assertEquals(APPLICATION_TITLE, window.title)
                    window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
                }
                val save = withContext(Dispatchers.IO) { background.poll(5, TimeUnit.SECONDS) }
                assertNotNull(save)
                save!!.run()
                application.await()
                assertNotNull(store.load())
            }
        } finally {
            application.cancelAndJoin()
        }
    }
}
