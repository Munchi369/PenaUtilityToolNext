package io.github.munchi369.penautilitytoolnext

import io.github.munchi369.penautilitytoolnext.home.DerbyHomeLoader
import io.github.munchi369.penautilitytoolnext.home.HomeController
import io.github.munchi369.penautilitytoolnext.lineup.DerbyLineupLoader
import io.github.munchi369.penautilitytoolnext.lineup.DerbyLineupWriter
import io.github.munchi369.penautilitytoolnext.lineup.JsonLineupPinStore
import io.github.munchi369.penautilitytoolnext.lineup.LineupController
import io.github.munchi369.penautilitytoolnext.player.DerbyPlayerDetailLoader
import io.github.munchi369.penautilitytoolnext.player.DerbyPlayerRosterLoader
import io.github.munchi369.penautilitytoolnext.player.JsonPlayerRosterSettingsStore
import io.github.munchi369.penautilitytoolnext.player.PlayerDetailController
import io.github.munchi369.penautilitytoolnext.player.PlayerDetailSettingsStore
import io.github.munchi369.penautilitytoolnext.player.PlayerRosterController
import io.github.munchi369.penautilitytoolnext.savedata.ApplicationEnvironmentLocator
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseProbe
import io.github.munchi369.penautilitytoolnext.savedata.LatestSaveDatabaseBackup
import io.github.munchi369.penautilitytoolnext.savedata.PropertiesSaveDatabaseSettingsStore
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataController
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabasePathResolver
import io.github.munchi369.penautilitytoolnext.savedata.applicationLogger
import io.github.munchi369.penautilitytoolnext.savedata.installFileLogging
import io.github.munchi369.penautilitytoolnext.team.DerbyTeamIdentityLoader
import io.github.munchi369.penautilitytoolnext.team.TeamIdentityController
import java.awt.EventQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class ApplicationControllers private constructor(
    val saveData: SaveDataController,
    val playerRoster: PlayerRosterController,
    val teamIdentity: TeamIdentityController,
    val playerDetail: PlayerDetailController,
    val lineup: LineupController,
    internal val home: HomeController,
    internal val windowPreferences: WindowPreferencesController,
    private val worker: ExecutorService,
) : AutoCloseable {
    fun start() {
        windowPreferences.load()
        playerDetail.start()
        saveData.start()
    }

    override fun close() {
        worker.shutdown()
        if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
            worker.shutdownNow()
        }
    }

    companion object {
        fun createDefault(): ApplicationControllers {
            val environment = ApplicationEnvironmentLocator.locate()
            installFileLogging(environment.logDirectory)
            val worker =
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "save-database-worker").apply {
                        isDaemon = true
                        setUncaughtExceptionHandler { _, error -> applicationLogger.log(Level.SEVERE, "ワーカーで未処理の例外が発生しました", error) }
                    }
                }
            val uiExecutor = Executor(EventQueue::invokeLater)
            val databaseAccess = DerbySaveDatabaseAccess()
            val playerRosterController =
                PlayerRosterController(
                    rosterLoader = DerbyPlayerRosterLoader(databaseAccess),
                    backgroundExecutor = worker,
                    uiExecutor = uiExecutor,
                    settingsStore =
                        JsonPlayerRosterSettingsStore(
                            settingsFile = environment.playerRosterSettingsFile,
                            legacySettingsFile = environment.legacyPlayerRosterSettingsFile,
                        ),
                )
            val playerDetailController =
                PlayerDetailController(
                    DerbyPlayerDetailLoader(databaseAccess),
                    worker,
                    uiExecutor,
                    PlayerDetailSettingsStore(environment.playerDetailSettingsFile),
                    rosterController = playerRosterController,
                )
            val saveDataController =
                SaveDataController(
                    settingsStore = PropertiesSaveDatabaseSettingsStore(environment.settingsFile),
                    pathResolver =
                        SaveDatabasePathResolver(
                            applicationDirectory = environment.applicationDirectory,
                            developmentMode = environment.developmentMode,
                        ),
                    databaseProbe = DerbySaveDatabaseProbe(databaseAccess),
                    backgroundExecutor = worker,
                    uiExecutor = uiExecutor,
                    onSettingsReset = {
                        playerRosterController.resetPersistedSettings()
                        playerDetailController.resetSettings()
                    },
                )
            val teamIdentityController = TeamIdentityController(DerbyTeamIdentityLoader(databaseAccess), worker, uiExecutor)
            val lineupController =
                LineupController(
                    loader = DerbyLineupLoader(databaseAccess),
                    saver = DerbyLineupWriter(databaseAccess, LatestSaveDatabaseBackup(environment.latestBackupDirectory)),
                    pinStore = JsonLineupPinStore(environment.lineupPinsFile),
                    backgroundExecutor = worker,
                    uiExecutor = uiExecutor,
                    onSaved = {
                        playerRosterController.reload()
                    },
                )
            return ApplicationControllers(
                saveDataController,
                playerRosterController,
                teamIdentityController,
                playerDetailController,
                lineupController,
                HomeController(
                    DerbyHomeLoader(databaseAccess),
                    worker,
                    uiExecutor,
                ),
                WindowPreferencesController(WindowPreferencesStore(environment.windowSettingsFile), worker, uiExecutor),
                worker,
            )
        }
    }
}
