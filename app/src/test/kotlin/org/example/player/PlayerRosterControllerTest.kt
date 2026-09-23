package org.example.player

import org.example.savedata.SaveDatabaseFailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executor

class PlayerRosterControllerTest {
    private val directExecutor = Executor(Runnable::run)

    @Test
    fun loadPublishesRosterAndEnablesReload(
        @TempDir root: Path,
    ) {
        val roster =
            PlayerRoster(
                firstTeam = listOf(player(PlayerSquad.FIRST)),
                secondTeam = listOf(player(PlayerSquad.SECOND)),
            )
        var loadCount = 0
        val controller =
            controller {
                loadCount += 1
                PlayerRosterLoadResult.Loaded(roster)
            }

        controller.load(root.resolve("penanto3"))
        controller.reload()

        assertEquals(2, loadCount)
        assertEquals(PlayerRosterPhase.LOADED, controller.state.phase)
        assertEquals(roster, controller.state.roster)
        assertTrue(controller.state.canReload)
    }

    @Test
    fun missingCurrentTeamDefaultsToAllPlayers(
        @TempDir root: Path,
    ) {
        val controller =
            controller {
                PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = null))
            }

        controller.load(root.resolve("penanto3"))

        assertEquals(RosterTeamScope.All, controller.options.teamScope)
    }

    @Test
    fun noActiveTeamIsShownWithoutPartialRoster(
        @TempDir root: Path,
    ) {
        val controller =
            controller {
                PlayerRosterLoadResult.NoActiveTeam("SAVE.MYTEAM=0")
            }

        controller.load(root.resolve("penanto3"))

        assertEquals(PlayerRosterPhase.NO_ACTIVE_TEAM, controller.state.phase)
        assertEquals("自球団を確認できません", controller.state.message)
        assertEquals(null, controller.state.roster)
        assertEquals("SAVE.MYTEAM=0", controller.state.technicalDetails)
    }

    @Test
    fun databaseInUseFailureCanBeRetried(
        @TempDir root: Path,
    ) {
        var available = false
        val controller =
            controller {
                if (available) {
                    PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList()))
                } else {
                    PlayerRosterLoadResult.Failed(
                        SaveDatabaseFailureKind.IN_USE,
                        "SQLState: XSDB6",
                    )
                }
            }

        controller.load(root.resolve("penanto3"))

        assertEquals(PlayerRosterPhase.IN_USE, controller.state.phase)
        assertTrue(controller.state.canReload)

        available = true
        controller.reload()

        assertEquals(PlayerRosterPhase.LOADED, controller.state.phase)
        assertFalse(controller.state.isLoading)
    }

    @Test
    fun returningToOriginalSaveAfterFailureSelectsItsCurrentTeam(
        @TempDir root: Path,
    ) {
        val original = root.resolve("original")
        val controller =
            controller { path ->
                if (path == original) {
                    PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6))
                } else {
                    PlayerRosterLoadResult.InvalidData("別のセーブの読み込み失敗")
                }
            }

        controller.load(original)
        controller.updateOptions(controller.options.copy(search = "選手", teamScope = RosterTeamScope.Major))
        controller.load(root.resolve("other"))
        assertEquals(PlayerRosterPhase.INVALID_DATA, controller.state.phase)
        controller.load(original)

        assertEquals(PlayerRosterPhase.LOADED, controller.state.phase)
        assertEquals(RosterTeamScope.Team(6), controller.options.teamScope)
        assertEquals("", controller.options.search)
        assertEquals(RosterDisplay.BASIC, controller.options.display)
    }

    @Test
    fun retryingFailedNewSaveInitializesItsTeamAndLaterReloadKeepsOptions(
        @TempDir root: Path,
    ) {
        var fail = false
        val controller =
            controller { path ->
                if (fail) {
                    PlayerRosterLoadResult.InvalidData("一時的な読み込み失敗")
                } else {
                    val team = if (path == root.resolve("original")) 6 else 3
                    PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = team))
                }
            }

        controller.load(root.resolve("original"))
        fail = true
        controller.load(root.resolve("other"))
        fail = false
        controller.reload()
        assertEquals(RosterTeamScope.Team(3), controller.options.teamScope)

        controller.updateOptions(controller.options.copy(search = "選手", teamScope = RosterTeamScope.Major))
        fail = true
        controller.reload()
        fail = false
        controller.reload()
        assertEquals(RosterTeamScope.Major, controller.options.teamScope)
        assertEquals("選手", controller.options.search)
    }

    @Test
    fun restartRestoresPersistentOptionsButNotTransientWorkspaceState(
        @TempDir root: Path,
    ) {
        val path = root.resolve("penanto3")
        val store = JsonPlayerRosterSettingsStore(root.resolve("config/player-roster.json"))
        val first =
            controller(
                loader = { PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6)) },
                settingsStore = store,
            )
        first.load(path)
        first.updateOptions(
            first.options.copy(
                teamScope = RosterTeamScope.Major,
                display = RosterDisplay.PITCHING_STATS,
                search = "検索中",
                squad = PlayerSquad.SECOND,
                registration = PlayerRegistration.DEVELOPMENT,
                grouping = RosterGrouping.POSITION,
                automaticDisplaySwitch = true,
                statsSquad = 2,
            ),
        )

        val restarted =
            controller(
                loader = { PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 3)) },
                settingsStore = store,
            )
        restarted.load(path)

        assertEquals(RosterTeamScope.Major, restarted.options.teamScope)
        assertEquals(RosterDisplay.BASIC, restarted.options.display)
        assertEquals("", restarted.options.search)
        assertEquals(PlayerSquad.SECOND, restarted.options.squad)
        assertEquals(PlayerRegistration.DEVELOPMENT, restarted.options.registration)
        assertEquals(RosterGrouping.POSITION, restarted.options.grouping)
        assertTrue(restarted.options.automaticDisplaySwitch)
        assertEquals(2, restarted.options.statsSquad)
    }

    @Test
    fun failedDifferentSaveKeepsPreviousSettingsUntilAnotherSaveLoads(
        @TempDir root: Path,
    ) {
        val original = root.resolve("original")
        val other = root.resolve("other")
        val store = JsonPlayerRosterSettingsStore(root.resolve("config/player-roster.json"))
        var otherSucceeds = false
        val controller =
            controller(
                loader = { path ->
                    if (path == other && !otherSucceeds) {
                        PlayerRosterLoadResult.InvalidData("一時的な読み込み失敗")
                    } else {
                        PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6))
                    }
                },
                settingsStore = store,
            )
        controller.load(original)
        controller.updateOptions(controller.options.copy(registration = PlayerRegistration.DEVELOPMENT))

        controller.load(other)
        assertEquals(PlayerRosterPhase.INVALID_DATA, controller.state.phase)
        controller.load(original)
        assertEquals(PlayerRegistration.DEVELOPMENT, controller.options.registration)

        otherSucceeds = true
        controller.load(other)
        assertEquals(null, controller.options.registration)
        assertEquals(PlayerRosterSettingsLoadResult.Missing, store.load())
    }

    @Test
    fun settingsWriteFailureDoesNotBlockTheOptionChange(
        @TempDir root: Path,
    ) {
        val store =
            object : PlayerRosterSettingsStore {
                override fun load(): PlayerRosterSettingsLoadResult = PlayerRosterSettingsLoadResult.Missing

                override fun save(settings: PlayerRosterSettings): Unit =
                    throw PlayerRosterSettingsWriteException("書き込み失敗", IllegalStateException("read only"))

                override fun clear() = Unit
            }
        val controller =
            controller(
                loader = { PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6)) },
                settingsStore = store,
            )
        controller.load(root.resolve("penanto3"))

        controller.updateOptions(controller.options.copy(registration = PlayerRegistration.DEVELOPMENT))

        assertEquals(PlayerRegistration.DEVELOPMENT, controller.options.registration)
        assertEquals("一覧設定を保存できませんでした", controller.settingsNotice?.message)
    }

    @Test
    fun transientWorkspaceChangesAreNotWritten(
        @TempDir root: Path,
    ) {
        var saveCount = 0
        val store =
            object : PlayerRosterSettingsStore {
                override fun load(): PlayerRosterSettingsLoadResult = PlayerRosterSettingsLoadResult.Missing

                override fun save(settings: PlayerRosterSettings) {
                    saveCount += 1
                }

                override fun clear() = Unit
            }
        val controller =
            controller(
                loader = { PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6)) },
                settingsStore = store,
            )
        controller.load(root.resolve("penanto3"))

        controller.updateOptions(
            controller.options.copy(
                teamScope = RosterTeamScope.Team(3),
                display = RosterDisplay.PITCHING_STATS,
                search = "検索中",
            ),
        )
        assertEquals(1, saveCount)

        controller.updateOptions(controller.options.copy(registration = PlayerRegistration.DEVELOPMENT))
        assertEquals(2, saveCount)
    }

    @Test
    fun invalidSettingsUseDefaultsAndShowANotice(
        @TempDir root: Path,
    ) {
        val store =
            object : PlayerRosterSettingsStore {
                override fun load(): PlayerRosterSettingsLoadResult = PlayerRosterSettingsLoadResult.Invalid("schemaVersion: 99")

                override fun save(settings: PlayerRosterSettings) = Unit

                override fun clear() = Unit
            }
        val controller =
            controller(
                loader = { PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6)) },
                settingsStore = store,
            )

        controller.load(root.resolve("penanto3"))

        assertEquals(PlayerRosterOptions.forTeam(6), controller.options)
        assertEquals("一覧設定を読み込めませんでした", controller.settingsNotice?.message)
    }

    @Test
    fun explicitApplicationResetDeletesRosterSettings(
        @TempDir root: Path,
    ) {
        val path = root.resolve("penanto3")
        val store = JsonPlayerRosterSettingsStore(root.resolve("config/player-roster.json"))
        val controller =
            controller(
                loader = { PlayerRosterLoadResult.Loaded(PlayerRoster(emptyList(), emptyList(), currentTeam = 6)) },
                settingsStore = store,
            )
        controller.load(path)
        controller.updateOptions(controller.options.copy(registration = PlayerRegistration.DEVELOPMENT))

        controller.resetPersistedSettings()

        assertEquals(PlayerRosterSettingsLoadResult.Missing, store.load())
        assertEquals(PlayerRosterOptions(), controller.options)
    }

    private fun controller(
        settingsStore: PlayerRosterSettingsStore = NoOpPlayerRosterSettingsStore,
        loader: (Path) -> PlayerRosterLoadResult,
    ): PlayerRosterController =
        PlayerRosterController(
            rosterLoader = PlayerRosterLoader(loader),
            backgroundExecutor = directExecutor,
            uiExecutor = directExecutor,
            settingsStore = settingsStore,
        )

    private fun player(squad: PlayerSquad): PlayerRosterPlayer =
        PlayerRosterPlayer(
            playerId = 1,
            uniformNumber = 10,
            name = "テスト選手",
            overall = 500,
            position = PlayerPosition.PITCHER,
            age = 24,
            squad = squad,
            registration = PlayerRegistration.CONTROLLED,
            injury = PlayerInjury.None,
        )
}
