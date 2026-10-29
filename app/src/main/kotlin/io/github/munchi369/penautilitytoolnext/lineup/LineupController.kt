package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.savedata.LatestRequest
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseFailureKind
import io.github.munchi369.penautilitytoolnext.savedata.toTechnicalDetails
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Executor

enum class LineupTab(
    val label: String,
) {
    FIELDERS("野手"),
    PITCHERS("投手"),
}

data class LineupNotice(
    val message: String,
    val error: Boolean = false,
    val technicalDetails: String? = null,
)

data class LineupViewState(
    val phase: SaveDataLoadPhase = SaveDataLoadPhase.IDLE,
    val message: String = "起用を読み込んでいません",
    val targetPath: Path? = null,
    val snapshot: LineupSnapshot? = null,
    val draft: LineupDraft? = null,
    val pins: Map<PinGroup, Set<Int>> = emptyMap(),
    val notice: LineupNotice? = null,
    val technicalDetails: String? = null,
    val saving: Boolean = false,
    val highlightedSpots: Set<DropSpot> = emptySet(),
    val autoCompleted: Boolean = false,
) {
    val isLoading: Boolean get() = phase == SaveDataLoadPhase.LOADING

    val canReload: Boolean get() = targetPath != null && !isLoading && !saving
}

data class LineupView(
    val tab: LineupTab = LineupTab.FIELDERS,
    val mode: LineupMode = LineupMode.DH,
    val selectedId: Int? = null,
    val considerSubPositions: Boolean = false,
)

sealed interface DragItem {
    data class LineupSlot(
        val mode: LineupMode,
        val index: Int,
    ) : DragItem

    data class RoleSlot(
        val role: PitchingRole,
        val index: Int,
    ) : DragItem

    data class ListedPlayer(
        val playerId: Int,
    ) : DragItem
}

sealed interface DropSpot {
    data class LineupSlot(
        val mode: LineupMode,
        val index: Int,
    ) : DropSpot

    data class RoleSlot(
        val role: PitchingRole,
        val index: Int,
    ) : DropSpot

    data class ListedPlayer(
        val playerId: Int,
    ) : DropSpot

    data class FirstTeamArea(
        val pitchers: Boolean,
    ) : DropSpot

    data class FarmArea(
        val pitchers: Boolean,
    ) : DropSpot

    data class DevelopmentArea(
        val pitchers: Boolean,
    ) : DropSpot
}

class LineupController(
    private val loader: LineupLoader,
    private val saver: LineupSaver,
    private val pinStore: LineupPinStore,
    private val backgroundExecutor: Executor,
    private val uiExecutor: Executor,
    private val onSaved: () -> Unit = {},
) {
    var state by mutableStateOf(LineupViewState())
        private set

    /** Survives reloads and leaving the screen; resets when the save database changes. */
    var view by mutableStateOf(LineupView())
        private set
    private val request = LatestRequest(backgroundExecutor, uiExecutor)
    private val pinRequest = LatestRequest(backgroundExecutor, uiExecutor)
    private var pinSaveNotice: LineupNotice? = null

    fun clear() {
        request.cancel()
        pinRequest.cancel()
        pinSaveNotice = null
        state = LineupViewState()
        view = LineupView()
    }

    fun showTab(tab: LineupTab) {
        clearHighlights()
        view = view.copy(tab = tab)
    }

    fun showMode(mode: LineupMode) {
        clearHighlights()
        view = view.copy(mode = mode)
    }

    fun considerSubPositions(enabled: Boolean) {
        clearHighlights()
        if (!state.saving) view = view.copy(considerSubPositions = enabled)
    }

    fun select(playerId: Int) {
        clearHighlights()
        view = view.copy(selectedId = playerId)
    }

    fun load(
        path: Path,
        notice: LineupNotice? = null,
    ) {
        val normalized = path.toAbsolutePath().normalize()
        pinRequest.cancel()
        pinSaveNotice = null
        state = LineupViewState(phase = SaveDataLoadPhase.LOADING, message = "起用を読み込んでいます", targetPath = normalized, notice = notice)
        request.run(
            work = {
                val result = loader.load(normalized)
                var pinNotice: LineupNotice? = null
                val pins =
                    try {
                        pinStore.load(normalized)
                    } catch (error: IOException) {
                        pinNotice = LineupNotice("固定の設定を読み込めませんでした", error = true, technicalDetails = error.message)
                        emptyMap()
                    }
                result.toViewState(normalized, pins, notice ?: pinNotice)
            },
            onFailure = { error ->
                LineupViewState(
                    SaveDataLoadPhase.UNAVAILABLE,
                    UNAVAILABLE_MESSAGE,
                    normalized,
                    notice = notice,
                    technicalDetails = error.toTechnicalDetails("起用の読み込み: $normalized"),
                )
            },
        ) { state = it }
    }

    fun reload() {
        if (state.canReload) state.targetPath?.let { load(it) }
    }

    fun dismissNotice() {
        clearHighlights()
        state = state.copy(notice = null)
    }

    fun drop(
        item: DragItem,
        spot: DropSpot,
    ) {
        edit(feedbackTab = view.tab) { snapshot, draft, editing ->
            when (spot) {
                is DropSpot.LineupSlot ->
                    when (item) {
                        is DragItem.LineupSlot ->
                            if (item.mode ==
                                spot.mode
                            ) {
                                EditResult.Applied(editing.swapLineup(draft, spot.mode, item.index, spot.index))
                            } else {
                                null
                            }
                        is DragItem.ListedPlayer -> editing.placeInLineup(draft, spot.mode, spot.index, item.playerId)
                        is DragItem.RoleSlot -> null
                    }

                is DropSpot.RoleSlot ->
                    when (item) {
                        is DragItem.RoleSlot -> EditResult.Applied(editing.swapRoles(draft, item.role, item.index, spot.role, spot.index))
                        is DragItem.ListedPlayer -> editing.placeInRole(draft, spot.role, spot.index, item.playerId)
                        is DragItem.LineupSlot -> null
                    }

                is DropSpot.ListedPlayer -> {
                    val target = snapshot.player(spot.playerId)
                    when (item) {
                        is DragItem.LineupSlot ->
                            when {
                                target.isPitcher -> null
                                !draft.isControlled(target.id) -> EditResult.Rejected("先にスタメンから外して2軍へ下げてください")
                                else -> editing.placeInLineup(draft, item.mode, item.index, target.id)
                            }
                        is DragItem.RoleSlot ->
                            when {
                                !target.isPitcher -> null
                                !draft.isControlled(target.id) -> EditResult.Rejected("先に投手の役割から外して2軍へ下げてください")
                                else -> editing.placeInRole(draft, item.role, item.index, target.id)
                            }
                        is DragItem.ListedPlayer ->
                            if (snapshot.player(item.playerId).isPitcher != target.isPitcher) {
                                null
                            } else {
                                moveToGroup(
                                    snapshot,
                                    draft,
                                    editing,
                                    item.playerId,
                                    draft.contract(target.id).registration,
                                    draft.squad(target.id),
                                )
                            }
                    }
                }

                is DropSpot.FirstTeamArea ->
                    when (item) {
                        is DragItem.ListedPlayer ->
                            if (snapshot.player(item.playerId).isPitcher == spot.pitchers) editing.promote(draft, item.playerId) else null
                        is DragItem.RoleSlot ->
                            draft.roles(item.role)[item.index]?.let { EditResult.Applied(editing.removeFromRole(draft, it)) }
                        is DragItem.LineupSlot -> null
                    }

                is DropSpot.FarmArea ->
                    when (item) {
                        is DragItem.ListedPlayer ->
                            if (snapshot.player(item.playerId).isPitcher == spot.pitchers) {
                                moveToGroup(snapshot, draft, editing, item.playerId, PlayerRegistration.CONTROLLED, PlayerSquad.SECOND)
                            } else {
                                null
                            }
                        is DragItem.LineupSlot -> EditResult.Rejected("スタメンの選手は降格できません")
                        is DragItem.RoleSlot -> EditResult.Rejected("役割に入っている投手は降格できません")
                    }

                is DropSpot.DevelopmentArea ->
                    when (item) {
                        is DragItem.ListedPlayer ->
                            if (snapshot.player(item.playerId).isPitcher == spot.pitchers) {
                                moveToGroup(snapshot, draft, editing, item.playerId, PlayerRegistration.DEVELOPMENT, PlayerSquad.SECOND)
                            } else {
                                null
                            }
                        is DragItem.LineupSlot -> EditResult.Rejected("先にスタメンから外して2軍へ下げてください")
                        is DragItem.RoleSlot -> EditResult.Rejected("先に投手の役割から外して2軍へ下げてください")
                    }
            }
        }
    }

    private fun moveToGroup(
        snapshot: LineupSnapshot,
        draft: LineupDraft,
        editing: LineupEditing,
        playerId: Int,
        registration: PlayerRegistration,
        squad: PlayerSquad,
    ): EditResult {
        if (draft.contract(playerId).registration != registration && squad == PlayerSquad.SECOND) {
            return LineupContracts(snapshot).change(draft, playerId)
        }
        if (registration == PlayerRegistration.DEVELOPMENT) return EditResult.Applied(draft)
        return if (squad == PlayerSquad.FIRST) editing.promote(draft, playerId) else editing.demote(draft, playerId)
    }

    fun changePosition(
        mode: LineupMode,
        index: Int,
        slot: FieldSlot,
    ) {
        edit(feedbackTab = LineupTab.FIELDERS, feedbackMode = mode) { _, draft, editing ->
            EditResult.Applied(editing.changePosition(draft, mode, index, slot))
        }
    }

    fun setAptitude(
        playerId: Int,
        slot: FieldSlot,
        value: Int,
    ) {
        edit { _, draft, editing -> EditResult.Applied(editing.setAptitude(draft, playerId, slot, value)) }
    }

    fun copyLineup(from: LineupMode) {
        edit { _, draft, editing -> EditResult.Applied(editing.copyLineup(draft, from)) }
    }

    fun setMainPosition(
        playerId: Int,
        slot: FieldSlot,
    ) {
        edit { _, draft, editing -> EditResult.Applied(editing.setMainPosition(draft, playerId, slot)) }
    }

    fun setPitchingAptitude(
        playerId: Int,
        role: PitchingRole,
        value: Int,
    ) {
        edit { _, draft, editing -> EditResult.Applied(editing.setPitchingAptitude(draft, playerId, role, value)) }
    }

    fun restoreAptitudeEdit(
        playerId: Int,
        beforeEdit: LineupPlayerState,
    ) {
        edit { _, draft, _ ->
            EditResult.Applied(
                draft.withState(playerId) {
                    it.copy(
                        position = beforeEdit.position,
                        aptitudes = beforeEdit.aptitudes,
                        pitchingAptitude = beforeEdit.pitchingAptitude,
                    )
                },
            )
        }
    }

    fun autoArrange(
        tab: LineupTab,
        mode: LineupMode,
    ): Boolean {
        var applied = false
        edit(feedbackTab = tab, feedbackMode = mode, autoCompleted = true) { snapshot, draft, editing ->
            val arranger = LineupAutoArranger(snapshot, editing)
            val result =
                when (tab) {
                    LineupTab.FIELDERS -> arranger.arrangeFielders(draft, mode, state.pins, view.considerSubPositions)
                    LineupTab.PITCHERS -> arranger.arrangePitchers(draft, state.pins)
                }
            if (result is EditResult.Applied) {
                applied = true
                result.copy(notice = null)
            } else {
                result
            }
        }
        return applied
    }

    fun reset(tab: LineupTab) {
        edit { _, draft, editing -> editing.reset(draft, tab) }
    }

    fun togglePin(
        group: PinGroup,
        playerId: Int,
    ) {
        clearHighlights()
        val path = state.targetPath ?: return
        val current = state.pins[group].orEmpty()
        val pins = state.pins + (group to if (playerId in current) current - playerId else current + playerId)
        state = state.copy(pins = pins)
        pinRequest.run(
            work = {
                pinStore.save(path, pins)
                null
            },
            onFailure = { error ->
                LineupNotice("固定の設定を保存できませんでした", error = true, technicalDetails = error.toTechnicalDetails("固定の設定: $path"))
            },
            skipIfSuperseded = false,
        ) { notice ->
            if (notice != null || state.notice == pinSaveNotice) {
                state = state.copy(notice = notice)
            }
            pinSaveNotice = notice
        }
    }

    fun save() {
        clearHighlights()
        val snapshot = state.snapshot ?: return
        val draft = state.draft ?: return
        if (state.saving) return
        val errors = LineupEditing(snapshot).validate(draft)
        if (errors.isNotEmpty()) {
            state = state.copy(notice = LineupNotice("保存できません\n" + errors.joinToString("\n"), error = true))
            return
        }
        state = state.copy(saving = true, notice = null)
        request.run(
            work = { saver.save(snapshot, draft) },
            onFailure = { LineupSaveResult.Failed(SaveDatabaseFailureKind.UNAVAILABLE, it.toTechnicalDetails("起用の保存: ${snapshot.path}")) },
            skipIfSuperseded = false,
        ) { result ->
            when (result) {
                LineupSaveResult.Saved, is LineupSaveResult.SavedWithWarning -> {
                    onSaved()
                    val notice =
                        if (result is LineupSaveResult.SavedWithWarning) {
                            LineupNotice(
                                "保存しましたが、接続の終了処理に失敗しました。再読み込みして状態を確認してください",
                                error = true,
                                technicalDetails = result.technicalDetails,
                            )
                        } else {
                            LineupNotice("保存しました")
                        }
                    load(snapshot.path, notice)
                }

                LineupSaveResult.Conflict ->
                    state =
                        state.copy(
                            saving = false,
                            notice = LineupNotice("読み込んだ後にセーブデータが変更されたため、保存しませんでした。再読み込みしてください", error = true),
                        )

                is LineupSaveResult.InvalidChanges ->
                    state = state.copy(saving = false, notice = LineupNotice(result.reason, error = true))

                is LineupSaveResult.BackupFailed ->
                    state =
                        state.copy(
                            saving = false,
                            notice = LineupNotice("バックアップを作成できなかったため、保存しませんでした", true, result.technicalDetails),
                        )

                is LineupSaveResult.Failed ->
                    state =
                        state.copy(
                            saving = false,
                            notice =
                                LineupNotice(
                                    if (result.kind == SaveDatabaseFailureKind.IN_USE) {
                                        "ゲーム本体がセーブデータを使用中です。本体を終了してから保存してください"
                                    } else {
                                        "保存の完了を確認できませんでした。再読み込みして内容を確認してください"
                                    },
                                    error = true,
                                    technicalDetails = result.technicalDetails,
                                ),
                        )
            }
        }
    }

    fun clearHighlights() {
        if (state.highlightedSpots.isNotEmpty() || state.autoCompleted) {
            state = state.copy(highlightedSpots = emptySet(), autoCompleted = false)
        }
    }

    private fun edit(
        feedbackTab: LineupTab? = null,
        feedbackMode: LineupMode = view.mode,
        autoCompleted: Boolean = false,
        operation: (LineupSnapshot, LineupDraft, LineupEditing) -> EditResult?,
    ) {
        clearHighlights()
        val snapshot = state.snapshot ?: return
        val draft = state.draft ?: return
        if (state.saving) return
        when (val result = operation(snapshot, draft, LineupEditing(snapshot))) {
            null -> Unit
            is EditResult.Applied ->
                state =
                    state.copy(
                        draft = result.draft,
                        notice = result.notice?.let(::LineupNotice),
                        highlightedSpots = feedbackTab?.let { arrangementChangedSpots(draft, result.draft, it, feedbackMode) }.orEmpty(),
                        autoCompleted = autoCompleted,
                    )
            is EditResult.Rejected -> state = state.copy(notice = LineupNotice(result.reason, error = true))
        }
    }

    private fun LineupLoadResult.toViewState(
        path: Path,
        pins: Map<PinGroup, Set<Int>>,
        notice: LineupNotice?,
    ): LineupViewState =
        when (this) {
            is LineupLoadResult.Loaded ->
                LineupViewState(
                    phase = SaveDataLoadPhase.LOADED,
                    message = "起用",
                    targetPath = path,
                    snapshot = snapshot,
                    draft = snapshot.initial,
                    pins = pins,
                    notice = notice,
                )

            is LineupLoadResult.NoActiveTeam ->
                LineupViewState(
                    SaveDataLoadPhase.NO_ACTIVE_TEAM,
                    "自チームを確認できません",
                    path,
                    notice = notice,
                    technicalDetails = technicalDetails,
                )

            is LineupLoadResult.InvalidData ->
                LineupViewState(
                    SaveDataLoadPhase.INVALID_DATA,
                    "起用データを解釈できません",
                    path,
                    notice = notice,
                    technicalDetails = technicalDetails,
                )

            is LineupLoadResult.Failed ->
                LineupViewState(
                    SaveDataLoadPhase.failed(kind),
                    kind.message(UNAVAILABLE_MESSAGE),
                    path,
                    notice = notice,
                    technicalDetails = technicalDetails,
                )
        }

    private companion object {
        const val UNAVAILABLE_MESSAGE = "起用を読み込めません"
    }
}
