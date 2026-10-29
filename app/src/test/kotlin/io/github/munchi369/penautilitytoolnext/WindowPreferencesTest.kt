package io.github.munchi369.penautilitytoolnext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor

class WindowPreferencesTest {
    @Test
    fun firstLaunchUsesNinetyPercentOfWorkAreaAndCapsAt1440By900() {
        assertEquals(
            WindowPreferences(WindowBounds(192, 94, 1440, 900)),
            restoreWindowPreferences(null, listOf(WindowBounds(0, 24, 1824, 1040))),
        )
        assertEquals(
            WindowPreferences(WindowBounds(64, 38, 1152, 648)),
            restoreWindowPreferences(null, listOf(WindowBounds(0, 2, 1280, 720))),
        )
    }

    @Test
    fun existingWindowKeepsNegativeMonitorCoordinatesAndMaximizedState() {
        val saved = WindowPreferences(WindowBounds(-1700, 120, 1280, 720), maximized = true)
        assertEquals(
            saved,
            restoreWindowPreferences(saved, listOf(WindowBounds(0, 0, 1920, 1040), WindowBounds(-1920, 0, 1920, 1040))),
        )
    }

    @Test
    fun disconnectedMonitorReturnsWindowToCenterOfPrimaryWorkArea() {
        assertEquals(
            WindowPreferences(WindowBounds(160, 80, 1280, 800), maximized = true),
            restoreWindowPreferences(
                WindowPreferences(WindowBounds(-1900, 100, 1280, 800), maximized = true),
                listOf(WindowBounds(0, 0, 1600, 960)),
            ),
        )
    }

    @Test
    fun reducedWorkAreaClampsSizeAndKeepsAllEdgesOnScreen() {
        assertEquals(
            WindowPreferences(WindowBounds(0, 32, 1024, 700)),
            restoreWindowPreferences(
                WindowPreferences(WindowBounds(900, -20, 1440, 900)),
                listOf(WindowBounds(0, 32, 1024, 700)),
            ),
        )
        assertEquals(
            WindowPreferences(WindowBounds(200, 100, 800, 600)),
            restoreWindowPreferences(WindowPreferences(WindowBounds(900, 650, 800, 600)), listOf(WindowBounds(0, 0, 1000, 700))),
        )
    }

    @Test
    fun maximizeAndMinimizeNeverReplaceNormalBounds() {
        val normal = WindowBounds(100, 120, 1200, 800)
        val tracker = WindowBoundsTracker(WindowPreferences(normal))
        tracker.update(WindowBounds(-8, -8, 1936, 1056), maximized = true, minimized = false)
        assertEquals(WindowPreferences(normal, maximized = true), tracker.preferences)
        tracker.update(WindowBounds(-32000, -32000, 160, 28), maximized = false, minimized = true)
        assertEquals(WindowPreferences(normal, maximized = true), tracker.preferences)
        val resized = WindowBounds(40, 60, 1100, 700)
        tracker.update(resized, maximized = false, minimized = false)
        assertEquals(WindowPreferences(resized), tracker.preferences)
    }

    @Test
    fun maximizedWindowMovedToAnotherMonitorKeepsNormalSizeOnThatMonitor() {
        val tracker = WindowBoundsTracker(WindowPreferences(WindowBounds(100, 120, 1200, 800)))
        tracker.update(
            WindowBounds(-1928, -8, 1936, 1056),
            maximized = true,
            minimized = false,
            workArea = WindowBounds(-1920, 0, 1920, 1040),
        )
        assertEquals(WindowPreferences(WindowBounds(-1560, 120, 1200, 800), maximized = true), tracker.preferences)
    }

    @Test
    fun independentSettingsRoundTripAndMissingFileUsesDefaults(
        @TempDir directory: Path,
    ) {
        val store = WindowPreferencesStore(directory.resolve("config/window.properties"))
        assertNull(store.load())
        val saved = WindowPreferences(WindowBounds(-1200, 40, 1100, 700), maximized = true)
        store.save(saved)
        assertEquals(saved, store.load())
        assertFalse(Files.exists(directory.resolve("config/settings.properties")))
    }

    @Test
    fun legacyWindowSettingsKeepGeometry(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("window.properties")
        Files.writeString(file, "schemaVersion=1\nx=100\ny=80\nwidth=1200\nheight=800\nmaximized=true\n")
        assertEquals(WindowPreferences(WindowBounds(100, 80, 1200, 800), maximized = true), WindowPreferencesStore(file).load())
    }

    @Test
    fun obsoleteNavigationSettingIsIgnoredAndRemovedOnSave(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("window.properties")
        val geometry = WindowPreferences(WindowBounds(100, 80, 1200, 800), maximized = true)
        listOf("true", "false", "broken").forEach { value ->
            Files.writeString(file, "schemaVersion=1\nx=100\ny=80\nwidth=1200\nheight=800\nmaximized=true\nnavigationExpanded=$value\n")
            val store = WindowPreferencesStore(file)
            assertEquals(geometry, store.load())
            store.save(geometry)
            assertFalse(Files.readString(file).contains("navigationExpanded"))
        }
    }

    @Test
    fun damagedSettingsFallBackAndFailedSaveStillCompletesClose(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("window.properties")
        Files.writeString(file, "schemaVersion=1\nwidth=broken")
        val background = ArrayDeque<Runnable>()
        val ui = ArrayDeque<Runnable>()
        val controller = WindowPreferencesController(WindowPreferencesStore(file), Executor(background::add), Executor(ui::add))
        controller.load()
        assertFalse(controller.loaded)
        background.removeFirst().run()
        ui.removeFirst().run()
        assertTrue(controller.loaded)
        assertNull(controller.saved)
        Files.delete(file)
        Files.createDirectory(file)
        var closed = 0
        controller.saveAndClose(WindowPreferences(WindowBounds(0, 0, 1200, 800))) { closed++ }
        controller.saveAndClose(WindowPreferences(WindowBounds(0, 0, 1200, 800))) { closed++ }
        assertEquals(0, closed)
        background.removeFirst().run()
        assertEquals(0, closed)
        ui.removeFirst().run()
        assertEquals(1, closed)
    }
}
