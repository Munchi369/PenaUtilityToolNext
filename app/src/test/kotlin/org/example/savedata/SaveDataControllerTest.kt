package org.example.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor

class SaveDataControllerTest {
    private val directExecutor = Executor(Runnable::run)
    private val fixedInstant = Instant.parse("2026-08-30T12:34:56Z")

    @Test
    fun startupResolvesAndChecksDevelopmentDatabase(
        @TempDir root: Path,
    ) {
        val store = FakeSettingsStore(root.resolve("config/settings.properties"))
        val developmentDatabase = root.resolve("test-data/dev/penanto3")
        val controller = controller(root, store, probe = { SaveDatabaseProbeResult.Available })

        controller.start()

        assertEquals(SaveDataPhase.AVAILABLE, controller.state.phase)
        assertEquals(developmentDatabase, controller.state.target?.path)
        assertEquals(SaveDatabaseOrigin.DEVELOPMENT, controller.state.origin)
        assertEquals(fixedInstant, controller.state.checkedAt)
        assertFalse(controller.state.hasSavedSelection)
    }

    @Test
    fun manualSelectionIsSavedBeforeProbe(
        @TempDir root: Path,
    ) {
        val store = FakeSettingsStore(root.resolve("config/settings.properties"))
        val selected = root.resolve("selected")
        var pathSeenByProbe: Path? = null
        val controller =
            controller(
                root,
                store,
                probe = { path ->
                    pathSeenByProbe = store.settings.selectedPath
                    SaveDatabaseProbeResult.Failed(
                        SaveDatabaseFailureKind.NOT_SAVE_DATABASE,
                        "missing marker",
                    )
                },
            )
        controller.start()

        controller.choose(selected)

        assertEquals(selected, pathSeenByProbe)
        assertEquals(selected, store.settings.selectedPath)
        assertEquals(SaveDataPhase.NOT_SAVE_DATABASE, controller.state.phase)
        assertTrue(controller.state.hasSavedSelection)
        assertTrue(controller.state.canReturnToAutomatic)
    }

    @Test
    fun invalidSettingsCanBeResetAndAutomaticallyResolved(
        @TempDir root: Path,
    ) {
        var rosterSettingsReset = false
        val store =
            FakeSettingsStore(root.resolve("config/settings.properties")).apply {
                loadResult = SaveDatabaseSettingsLoadResult.Invalid("broken settings")
            }
        val controller =
            controller(
                root,
                store,
                probe = { SaveDatabaseProbeResult.Available },
                onSettingsReset = { rosterSettingsReset = true },
            )
        controller.start()

        assertEquals(SaveDataPhase.SETTINGS_ERROR, controller.state.phase)
        assertTrue(controller.state.canResetSettings)

        store.loadResult = SaveDatabaseSettingsLoadResult.Loaded(store.settings)
        controller.resetSettings()

        assertEquals(SaveDataPhase.AVAILABLE, controller.state.phase)
        assertEquals(null, store.settings.selectedPath)
        assertTrue(rosterSettingsReset)
    }

    @Test
    fun inUseFailureCanBeRetried(
        @TempDir root: Path,
    ) {
        val selected = root.resolve("selected")
        val store =
            FakeSettingsStore(root.resolve("config/settings.properties")).apply {
                settings = SaveDatabaseSettings(selected)
                loadResult = SaveDatabaseSettingsLoadResult.Loaded(settings)
            }
        var available = false
        val controller =
            controller(
                root,
                store,
                probe = {
                    if (available) {
                        SaveDatabaseProbeResult.Available
                    } else {
                        SaveDatabaseProbeResult.Failed(
                            SaveDatabaseFailureKind.IN_USE,
                            "SQLState: XSDB6",
                        )
                    }
                },
            )
        controller.start()

        assertEquals(SaveDataPhase.IN_USE, controller.state.phase)
        assertTrue(controller.state.canRetry)

        available = true
        controller.retry()

        assertEquals(SaveDataPhase.AVAILABLE, controller.state.phase)
    }

    @Test
    fun startupOverrideLocksSelection(
        @TempDir root: Path,
    ) {
        val target = root.resolve("override")
        val store = FakeSettingsStore(root.resolve("config/settings.properties"))
        val resolver =
            SaveDatabasePathResolver(
                applicationDirectory = root,
                developmentMode = true,
                systemProperty = { target.toString() },
                environmentVariable = { null },
                markerExists = { true },
            )
        val controller =
            SaveDataController(
                settingsStore = store,
                pathResolver = resolver,
                databaseProbe = SaveDatabaseProbe { SaveDatabaseProbeResult.Available },
                backgroundExecutor = directExecutor,
                uiExecutor = directExecutor,
                clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            )

        controller.start()

        assertTrue(controller.state.selectionLocked)
        assertFalse(controller.state.canChoose)
    }

    private fun controller(
        root: Path,
        store: FakeSettingsStore,
        probe: (Path) -> SaveDatabaseProbeResult,
        onSettingsReset: () -> Unit = {},
    ): SaveDataController =
        SaveDataController(
            settingsStore = store,
            pathResolver =
                SaveDatabasePathResolver(
                    applicationDirectory = root,
                    developmentMode = true,
                    systemProperty = { null },
                    environmentVariable = { null },
                    markerExists = { true },
                ),
            databaseProbe = SaveDatabaseProbe(probe),
            backgroundExecutor = directExecutor,
            uiExecutor = directExecutor,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            onSettingsReset = onSettingsReset,
        )
}

private class FakeSettingsStore(
    override val settingsFile: Path,
) : SaveDatabaseSettingsStore {
    var settings = SaveDatabaseSettings(selectedPath = null)
    var loadResult: SaveDatabaseSettingsLoadResult = SaveDatabaseSettingsLoadResult.Loaded(settings)

    override fun load(): SaveDatabaseSettingsLoadResult = loadResult

    override fun save(settings: SaveDatabaseSettings) {
        this.settings = settings
        loadResult = SaveDatabaseSettingsLoadResult.Loaded(settings)
    }
}
