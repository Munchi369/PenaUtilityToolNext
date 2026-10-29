package io.github.munchi369.penautilitytoolnext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import java.awt.Frame
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowStateListener

@Composable
internal fun ApplicationWindow(
    preferences: WindowPreferencesController,
    onClose: () -> Unit,
    visible: Boolean = true,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    LaunchedEffect(preferences) {
        snapshotFlow { preferences.loaded }.first { it }
        // Keep the application alive while the externally loaded state creates its first window.
        awaitCancellation()
    }
    if (!preferences.loaded) return
    val initial = remember { restoreWindowPreferences(preferences.saved, desktopWorkAreas()) }
    val tracker = remember { WindowBoundsTracker(initial) }
    val state =
        rememberWindowState(
            width = initial.bounds.width.dp,
            height = initial.bounds.height.dp,
            position = WindowPosition(initial.bounds.x.dp, initial.bounds.y.dp),
            placement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
    Window(
        onCloseRequest = { preferences.saveAndClose(tracker.preferences, onClose) },
        state = state,
        visible = visible,
        title = APPLICATION_TITLE,
    ) {
        DisposableEffect(window) {
            fun captureBounds() {
                if (window.width <= 0 || window.height <= 0) return
                tracker.update(
                    WindowBounds(window.x, window.y, window.width, window.height),
                    maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
                    minimized = window.extendedState and Frame.ICONIFIED != 0,
                    workArea = workArea(window.graphicsConfiguration),
                )
            }

            val componentListener =
                object : ComponentAdapter() {
                    override fun componentMoved(e: ComponentEvent) = captureBounds()

                    override fun componentResized(e: ComponentEvent) = captureBounds()
                }
            val stateListener = WindowStateListener { captureBounds() }
            window.addComponentListener(componentListener)
            window.addWindowStateListener(stateListener)
            onDispose {
                window.removeComponentListener(componentListener)
                window.removeWindowStateListener(stateListener)
            }
        }
        content()
    }
}

private fun desktopWorkAreas(): List<WindowBounds> {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val devices = listOf(environment.defaultScreenDevice) + environment.screenDevices.filter { it != environment.defaultScreenDevice }
    return devices.map { workArea(it.defaultConfiguration) }
}

private fun workArea(configuration: GraphicsConfiguration): WindowBounds {
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return WindowBounds(
        bounds.x + insets.left,
        bounds.y + insets.top,
        (bounds.width - insets.left - insets.right).coerceAtLeast(1),
        (bounds.height - insets.top - insets.bottom).coerceAtLeast(1),
    )
}
