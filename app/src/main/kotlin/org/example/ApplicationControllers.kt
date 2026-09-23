package org.example

import org.example.player.DerbyPlayerDetailLoader
import org.example.player.DerbyPlayerRosterLoader
import org.example.player.JsonPlayerRosterSettingsStore
import org.example.player.PlayerDetailController
import org.example.player.PlayerDetailSettingsStore
import org.example.player.PlayerRosterController
import org.example.savedata.ApplicationEnvironmentLocator
import org.example.savedata.DerbySaveDatabaseAccess
import org.example.savedata.DerbySaveDatabaseProbe
import org.example.savedata.PropertiesSaveDatabaseSettingsStore
import org.example.savedata.SaveDataController
import org.example.savedata.SaveDatabasePathResolver
import org.example.team.DerbyTeamIdentityLoader
import org.example.team.TeamIdentityController
import java.awt.EventQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ApplicationControllers private constructor(
    val saveData: SaveDataController,
    val playerRoster: PlayerRosterController,
    val teamIdentity: TeamIdentityController,
    val playerDetail: PlayerDetailController,
    private val worker: ExecutorService,
) : AutoCloseable {
    fun start() {
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
            val worker =
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "save-database-worker").apply { isDaemon = true }
                }
            val uiExecutor = Executor(EventQueue::invokeLater)
            val databaseAccess = DerbySaveDatabaseAccess()
            val playerDetailController =
                PlayerDetailController(
                    DerbyPlayerDetailLoader(databaseAccess),
                    worker,
                    uiExecutor,
                    PlayerDetailSettingsStore(environment.playerRosterSettingsFile.resolveSibling("player-detail.json")),
                )
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
            return ApplicationControllers(
                saveDataController,
                playerRosterController,
                teamIdentityController,
                playerDetailController,
                worker,
            )
        }
    }
}
