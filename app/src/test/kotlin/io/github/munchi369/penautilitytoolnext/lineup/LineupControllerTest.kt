package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Executor

class LineupControllerTest {
    private val direct = Executor(Runnable::run)
    private val snapshot = snapshotOf(fullTeam())

    @Test
    fun successfulSaveWarningSurvivesAFailedReload() {
        var loads = 0
        var saved = 0
        val controller =
            LineupController(
                loader = {
                    if (++loads == 1) LineupLoadResult.Loaded(snapshot) else throw IOException("reload failed")
                },
                saver = { _, _ -> LineupSaveResult.SavedWithWarning("shutdown failed") },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
                onSaved = { saved++ },
            )
        controller.load(snapshot.path)

        controller.save()

        assertEquals(1, saved)
        assertEquals(SaveDataLoadPhase.UNAVAILABLE, controller.state.phase)
        assertFalse(controller.state.saving)
        assertTrue(requireNotNull(controller.state.notice).message.startsWith("保存しましたが"))
        assertEquals("shutdown failed", controller.state.notice?.technicalDetails)
    }

    @Test
    fun supersededPinFailureIsIgnoredButEveryWriteStillRuns() {
        val deliveries = ArrayDeque<Runnable>()
        var writes = 0
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = failingFirstPinStore { ++writes == 1 },
                backgroundExecutor = direct,
                uiExecutor = Executor { deliveries.addLast(it) },
            )
        controller.load(snapshot.path)
        deliveries.removeFirst().run()

        controller.togglePin(PinGroup.DH, 101)
        controller.togglePin(PinGroup.DH, 102)
        deliveries.removeFirst().run()
        assertEquals(null, controller.state.notice)
        deliveries.removeFirst().run()

        assertEquals(2, writes)
        assertEquals(setOf(101, 102), controller.state.pins[PinGroup.DH])
        assertEquals(null, controller.state.notice)
    }

    @Test
    fun successfulPinRetryClearsOnlyThePinSaveError() {
        var writes = 0
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = failingFirstPinStore { ++writes == 1 },
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        controller.togglePin(PinGroup.DH, 101)
        assertTrue(requireNotNull(controller.state.notice).error)

        controller.togglePin(PinGroup.DH, 102)

        assertEquals(null, controller.state.notice)
    }

    @Test
    fun clearingTheSaveDiscardsOldPinNoticesWithoutSkippingTheWrite() {
        val work = ArrayDeque<Runnable>()
        var writes = 0
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = failingFirstPinStore { ++writes == 1 },
                backgroundExecutor = Executor { work.addLast(it) },
                uiExecutor = direct,
            )
        controller.load(snapshot.path)
        work.removeFirst().run()
        controller.togglePin(PinGroup.DH, 101)

        controller.clear()
        work.removeFirst().run()

        assertEquals(1, writes)
        assertEquals(LineupViewState(), controller.state)
    }

    private fun failingFirstPinStore(fail: () -> Boolean): LineupPinStore =
        object : LineupPinStore {
            override fun load(savePath: Path): Map<PinGroup, Set<Int>> = emptyMap()

            override fun save(
                savePath: Path,
                pins: Map<PinGroup, Set<Int>>,
            ) {
                if (fail()) throw IOException("pin write failed")
            }
        }

    @Test
    fun manualSwapsHighlightOnlyTheLatestChangeAndSelectionClearsIt() {
        LineupTab.entries.forEach { tab ->
            val controller = controller()
            controller.load(snapshot.path)
            controller.showTab(tab)
            val from = if (tab == LineupTab.FIELDERS) DragItem.LineupSlot(LineupMode.DH, 0) else DragItem.RoleSlot(PitchingRole.STARTER, 0)
            val to = if (tab == LineupTab.FIELDERS) DropSpot.LineupSlot(LineupMode.DH, 1) else DropSpot.RoleSlot(PitchingRole.STARTER, 1)
            controller.drop(from, to)
            assertTrue(to in controller.state.highlightedSpots)
            assertFalse(controller.state.autoCompleted)
            controller.select(if (tab == LineupTab.FIELDERS) 101 else 1)
            assertTrue(controller.state.highlightedSpots.isEmpty())
            controller.drop(from, to)
            assertTrue(controller.state.highlightedSpots.isNotEmpty())
            controller.reset(tab)
            assertTrue(controller.state.highlightedSpots.isEmpty())
        }
    }

    @Test
    fun newSwapsReplaceHighlightsAndRejectedDropsClearThem() {
        val controller = controller()
        controller.load(snapshot.path)
        controller.showTab(LineupTab.PITCHERS)
        controller.drop(DragItem.RoleSlot(PitchingRole.STARTER, 0), DropSpot.RoleSlot(PitchingRole.STARTER, 1))
        controller.drop(DragItem.RoleSlot(PitchingRole.STARTER, 2), DropSpot.RoleSlot(PitchingRole.STARTER, 3))
        assertFalse(DropSpot.RoleSlot(PitchingRole.STARTER, 0) in controller.state.highlightedSpots)
        assertTrue(DropSpot.RoleSlot(PitchingRole.STARTER, 3) in controller.state.highlightedSpots)
        controller.drop(DragItem.RoleSlot(PitchingRole.STARTER, 2), DropSpot.DevelopmentArea(true))
        assertTrue(controller.state.highlightedSpots.isEmpty())
        assertTrue(requireNotNull(controller.state.notice).error)
    }

    @Test
    fun automaticArrangementsAcknowledgeEverySuccessWithoutAddingNotices() {
        val team =
            snapshotOf(
                fullTeam(
                    listOf(
                        fielder(201, PlayerPosition.CATCHER, batting = 99, power = 99, squad = PlayerSquad.SECOND),
                        pitcher(202, overall = 999, starter = 99, reliever = 50, squad = PlayerSquad.SECOND),
                    ),
                ),
            )
        val controller = controller(team)
        assertFalse(controller.autoArrange(LineupTab.FIELDERS, LineupMode.DH))
        controller.load(snapshot.path)
        LineupTab.entries.forEach { tab ->
            assertTrue(controller.autoArrange(tab, LineupMode.DH))
            assertEquals(null, controller.state.notice)
            val arranged = controller.state.draft
            assertTrue(controller.autoArrange(tab, LineupMode.DH))
            assertEquals(arranged, controller.state.draft)
            assertEquals(null, controller.state.notice)
        }
        val draft = requireNotNull(controller.state.draft)
        assertEquals(PlayerSquad.FIRST, draft.squad(201))
        assertEquals(PlayerSquad.FIRST, draft.squad(202))
    }

    private fun controller(snapshot: LineupSnapshot = this.snapshot): LineupController =
        LineupController(
            loader = { LineupLoadResult.Loaded(snapshot) },
            saver = { _, _ -> LineupSaveResult.Saved },
            pinStore = NoOpLineupPinStore,
            backgroundExecutor = direct,
            uiExecutor = direct,
        )

    @Test
    fun unexpectedSaveExceptionsEndSavingWithAnError() {
        val controller =
            LineupController(
                loader = { LineupLoadResult.Loaded(snapshot) },
                saver = { _, _ -> throw java.io.UncheckedIOException(java.io.IOException("disk")) },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )
        controller.load(Path.of("save"))

        controller.save()

        assertFalse(controller.state.saving)
        assertTrue(controller.state.notice!!.error)
        assertTrue(
            controller.state.notice!!
                .technicalDetails!!
                .contains("disk"),
        )
    }

    @Test
    fun unexpectedLoadExceptionsShowTheUnavailableState() {
        val controller =
            LineupController(
                loader = { error("broken") },
                saver = { _, _ -> LineupSaveResult.Saved },
                pinStore = NoOpLineupPinStore,
                backgroundExecutor = direct,
                uiExecutor = direct,
            )

        controller.load(Path.of("save"))

        assertEquals(SaveDataLoadPhase.UNAVAILABLE, controller.state.phase)
        assertTrue(controller.state.canReload)
    }

    @Test
    fun reloadKeepsTabModeAndSelectionButDiscardsEdits() {
        val controller = controller()
        controller.load(Path.of("save"))
        controller.showTab(LineupTab.PITCHERS)
        controller.showMode(LineupMode.NORMAL)
        controller.select(3)
        controller.drop(DragItem.RoleSlot(PitchingRole.STARTER, 0), DropSpot.RoleSlot(PitchingRole.STARTER, 1))

        controller.reload()

        assertEquals(LineupView(LineupTab.PITCHERS, LineupMode.NORMAL, 3), controller.view)
        assertEquals(snapshot.initial, controller.state.draft)
    }

    @Test
    fun changingTheSaveDatabaseResetsTheView() {
        val controller = controller()
        controller.load(Path.of("save"))
        controller.showTab(LineupTab.PITCHERS)
        controller.considerSubPositions(true)
        controller.select(3)

        controller.clear()

        assertEquals(LineupView(), controller.view)
    }

    @Test
    fun subPositionOptionOnlyAffectsAutomaticFieldersAndSurvivesReload() {
        val snapshot =
            snapshotOf(
                fullTeam().map { player ->
                    when (player.id) {
                        104 -> player.copy(batting = 90, power = 90)
                        112 -> player.copy(batting = 70, power = 70, initialAptitudes = player.initialAptitudes + (FieldSlot.FIRST to 60))
                        else -> player
                    }
                },
            )
        val controller = controller(snapshot)
        controller.load(snapshot.path)
        controller.considerSubPositions(true)
        assertEquals(snapshot.initial, controller.state.draft)
        controller.autoArrange(LineupTab.FIELDERS, LineupMode.NORMAL)
        assertEquals(112, requireNotNull(controller.state.draft).normal.single { it.slot == FieldSlot.FIRST }.playerId)
        controller.showTab(LineupTab.PITCHERS)
        controller.autoArrange(LineupTab.PITCHERS, LineupMode.DH)
        assertEquals(snapshot.initial.starters, requireNotNull(controller.state.draft).starters)
        controller.reload()
        assertTrue(controller.view.considerSubPositions)
        assertEquals(snapshot.initial, controller.state.draft)
        controller.considerSubPositions(false)
        controller.autoArrange(LineupTab.FIELDERS, LineupMode.NORMAL)
        assertEquals(102, requireNotNull(controller.state.draft).normal.single { it.slot == FieldSlot.FIRST }.playerId)
    }

    private fun contractTeam(
        day: Int = 100,
        restDays: Int = 0,
    ): LineupSnapshot {
        val farm = fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND, restDays = restDays)
        val development =
            fielder(202, PlayerPosition.FIRST_BASEMAN, squad = PlayerSquad.SECOND, restDays = restDays)
                .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = 105)
        val farmPitcher = pitcher(20, 800, 80, 50, squad = PlayerSquad.SECOND, restDays = restDays)
        val developmentPitcher =
            pitcher(21, 800, 80, 50, squad = PlayerSquad.SECOND, restDays = restDays)
                .copy(initialRegistration = PlayerRegistration.DEVELOPMENT, uniformNumber = 108)
        return snapshotOf(fullTeam(listOf(farm, development, farmPitcher, developmentPitcher)), day = day)
    }

    @Test
    fun draggingBetweenFarmAndDevelopmentChangesContractsForBothPlayerTypes() {
        val snapshot = contractTeam()
        val controller = controller(snapshot)
        controller.load(snapshot.path)
        listOf(202 to false, 21 to true).forEach { (id, pitcher) ->
            controller.select(id)
            controller.drop(DragItem.ListedPlayer(id), DropSpot.FarmArea(pitcher))
            val registered = requireNotNull(controller.state.draft)
            assertEquals(PlayerRegistration.CONTROLLED, registered.contract(id).registration)
            assertEquals(PlayerSquad.SECOND, registered.squad(id))
            assertEquals(null, controller.state.notice)
            controller.drop(DragItem.ListedPlayer(id), DropSpot.DevelopmentArea(pitcher))
            assertEquals(PlayerRegistration.DEVELOPMENT, requireNotNull(controller.state.draft).contract(id).registration)
            assertEquals(id, controller.view.selectedId)
        }
        assertTrue(LineupEditing(snapshot).validate(requireNotNull(controller.state.draft)).isEmpty())
    }

    @Test
    fun droppingOnGroupMembersUsesTheirCurrentContractAndDroppingInSameGroupIsNoOp() {
        val snapshot = contractTeam()
        val controller = controller(snapshot)
        controller.load(snapshot.path)
        controller.drop(DragItem.ListedPlayer(201), DropSpot.ListedPlayer(202))
        val development = requireNotNull(controller.state.draft)
        assertEquals(PlayerRegistration.DEVELOPMENT, development.contract(201).registration)
        assertEquals(101, development.contract(201).uniformNumber)
        controller.drop(DragItem.ListedPlayer(201), DropSpot.DevelopmentArea(false))
        assertEquals(development, controller.state.draft)
        controller.drop(DragItem.ListedPlayer(201), DropSpot.ListedPlayer(20))
        assertEquals(development, controller.state.draft)
        controller.drop(DragItem.ListedPlayer(202), DropSpot.FarmArea(false))
        controller.drop(DragItem.ListedPlayer(201), DropSpot.ListedPlayer(202))
        assertEquals(PlayerRegistration.CONTROLLED, requireNotNull(controller.state.draft).contract(201).registration)
    }

    @Test
    fun contractDragPreservesWaitAndCannotPromoteDevelopmentDirectly() {
        val snapshot = contractTeam(restDays = 5)
        val controller = controller(snapshot)
        controller.load(snapshot.path)
        controller.drop(DragItem.ListedPlayer(202), DropSpot.FirstTeamArea(false))
        assertEquals(snapshot.initial, controller.state.draft)
        controller.drop(DragItem.ListedPlayer(202), DropSpot.FarmArea(false))
        val registered = requireNotNull(controller.state.draft)
        assertEquals(PlayerRegistration.CONTROLLED, registered.contract(202).registration)
        controller.drop(DragItem.ListedPlayer(202), DropSpot.FirstTeamArea(false))
        assertEquals(registered, controller.state.draft)
        assertTrue(requireNotNull(controller.state.notice).message.contains("休止"))
        controller.drop(DragItem.ListedPlayer(201), DropSpot.DevelopmentArea(false))
        assertEquals(PlayerRegistration.DEVELOPMENT, requireNotNull(controller.state.draft).contract(201).registration)
    }

    @Test
    fun contractDragRejectsPeriodCapacityFirstTeamAndPlayersInUseWithoutPartialChanges() {
        val restricted = contractTeam(day = 213)
        val controller = controller(restricted)
        controller.load(restricted.path)
        controller.drop(DragItem.ListedPlayer(202), DropSpot.FarmArea(false))
        assertEquals(restricted.initial, controller.state.draft)
        assertTrue(requireNotNull(controller.state.notice).message.contains("期間外"))
        val open = contractTeam()
        controller.load(open.path)
        controller.drop(DragItem.ListedPlayer(116), DropSpot.DevelopmentArea(false))
        assertEquals(open.initial, controller.state.draft)
        controller.drop(DragItem.LineupSlot(LineupMode.DH, 0), DropSpot.DevelopmentArea(false))
        assertEquals(open.initial, controller.state.draft)
        controller.drop(DragItem.LineupSlot(LineupMode.DH, 0), DropSpot.ListedPlayer(202))
        assertEquals(open.initial, controller.state.draft)
        controller.drop(DragItem.RoleSlot(PitchingRole.STARTER, 0), DropSpot.DevelopmentArea(true))
        assertEquals(open.initial, controller.state.draft)
        controller.drop(DragItem.RoleSlot(PitchingRole.STARTER, 0), DropSpot.ListedPlayer(21))
        assertEquals(open.initial, controller.state.draft)
        controller.drop(DragItem.ListedPlayer(202), DropSpot.FarmArea(true))
        assertEquals(open.initial, controller.state.draft)
        val farm = (301..341).map { fielder(it, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND) }
        val full = snapshotOf(fullTeam(farm) + open.player(202))
        val fullController = this.controller(full)
        fullController.load(full.path)
        fullController.drop(DragItem.ListedPlayer(202), DropSpot.FarmArea(false))
        assertEquals(full.initial, fullController.state.draft)
        assertTrue(requireNotNull(fullController.state.notice).message.contains("満員"))
    }
}
