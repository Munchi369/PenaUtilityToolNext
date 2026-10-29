package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.GameCalendar
import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerNumberCode
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseFailureKind
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import java.nio.file.Path

enum class FieldSlot(
    val label: String,
    val shortLabel: String,
    val aptitudeColumn: String?,
    val mainPosition: PlayerPosition?,
) {
    CATCHER("捕手", "捕", "POJI_HOSYU", PlayerPosition.CATCHER),
    FIRST("一塁", "一", "POJI_ITIRUI", PlayerPosition.FIRST_BASEMAN),
    SECOND("二塁", "二", "POJI_NIRUI", PlayerPosition.SECOND_BASEMAN),
    THIRD("三塁", "三", "POJI_SANRUI", PlayerPosition.THIRD_BASEMAN),
    SHORT("遊撃", "遊", "POJI_YUUGEKI", PlayerPosition.SHORTSTOP),
    LEFT("左翼", "左", "POJI_LEFT", PlayerPosition.LEFT_FIELDER),
    CENTER("中堅", "中", "POJI_CENTER", PlayerPosition.CENTER_FIELDER),
    RIGHT("右翼", "右", "POJI_RIGHT", PlayerPosition.RIGHT_FIELDER),
    DH("DH", "DH", null, null),
    PITCHER("投手", "投", null, PlayerPosition.PITCHER),
    ;

    companion object {
        val defense = listOf(CATCHER, FIRST, SECOND, THIRD, SHORT, LEFT, CENTER, RIGHT)
    }
}

enum class LineupMode(
    val label: String,
    val pinGroup: PinGroup,
) {
    NORMAL("DHなし", PinGroup.NORMAL),
    DH("DHあり", PinGroup.DH),
}

enum class PitchingRole(
    val label: String,
) {
    STARTER("先発"),
    RELIEVER("中継ぎ"),
    ;

    fun roleName(index: Int): String =
        when (this) {
            STARTER -> if (index == 0) "エース" else "先発${index + 1}番手"
            RELIEVER -> relieverNames.getOrElse(index) { "中継${index + 1}番手" }
        }

    private companion object {
        val relieverNames = listOf("クローザー", "セットアッパー", "準セットアッパー", "接戦ビハインド", "先発早々降板時", "敗戦処理")
    }
}

enum class PinGroup {
    NORMAL,
    DH,
    PITCHING,
}

data class LineupEntry(
    val playerId: Int?,
    val slot: FieldSlot,
)

data class LineupPlayer(
    val id: Int,
    val name: String,
    val uniformNumber: Int,
    val position: PlayerPosition,
    val initialSquad: PlayerSquad,
    val restDays: Int,
    val foreign: Boolean,
    val injury: PlayerInjury,
    val age: Int,
    val overall: Int,
    val batting: Int,
    val power: Int,
    val starterAptitude: Int,
    val relieverAptitude: Int,
    val initialAptitudes: Map<FieldSlot, Int>,
    val cells: Map<String, PlayerCell> = emptyMap(),
    val initialRegistration: PlayerRegistration = PlayerRegistration.CONTROLLED,
    val year: Int = 1,
) {
    val initialContract: PlayerContract get() = PlayerContract(initialRegistration, uniformNumber)
    val isPitcher: Boolean get() = position == PlayerPosition.PITCHER
    val hitting: Int get() = batting + power
    val initialPitchingAptitude: PitchingAptitude get() = PitchingAptitude(starterAptitude, relieverAptitude)
    val initialState: LineupPlayerState
        get() = LineupPlayerState(initialSquad, initialContract, position, initialAptitudes, initialPitchingAptitude)
    val injured: Boolean get() = injury is PlayerInjury.Injured
}

/** The editable values of one player in a [LineupDraft]. */
data class LineupPlayerState(
    val squad: PlayerSquad,
    val contract: PlayerContract,
    val position: PlayerPosition,
    val aptitudes: Map<FieldSlot, Int>,
    val pitchingAptitude: PitchingAptitude,
)

data class LineupDraft(
    val normal: List<LineupEntry>,
    val dh: List<LineupEntry>,
    val starters: List<Int?>,
    val relievers: List<Int?>,
    val players: Map<Int, LineupPlayerState>,
) {
    fun state(id: Int): LineupPlayerState = players.getValue(id)

    fun withState(
        id: Int,
        update: (LineupPlayerState) -> LineupPlayerState,
    ): LineupDraft = copy(players = players + (id to update(state(id))))

    fun contract(id: Int): PlayerContract = state(id).contract

    fun isControlled(id: Int): Boolean = contract(id).registration == PlayerRegistration.CONTROLLED

    fun lineup(mode: LineupMode): List<LineupEntry> = if (mode == LineupMode.NORMAL) normal else dh

    fun withLineup(
        mode: LineupMode,
        entries: List<LineupEntry>,
    ): LineupDraft = if (mode == LineupMode.NORMAL) copy(normal = entries) else copy(dh = entries)

    fun roles(role: PitchingRole): List<Int?> = if (role == PitchingRole.STARTER) starters else relievers

    fun withRoles(
        role: PitchingRole,
        ids: List<Int?>,
    ): LineupDraft = if (role == PitchingRole.STARTER) copy(starters = ids) else copy(relievers = ids)

    fun squad(id: Int): PlayerSquad = state(id).squad

    fun inAnyLineup(id: Int): Boolean = normal.any { it.playerId == id } || dh.any { it.playerId == id }

    fun inAnyRole(id: Int): Boolean = id in starters || id in relievers

    fun aptitude(
        id: Int,
        slot: FieldSlot,
    ): Int = players[id]?.aptitudes?.get(slot) ?: 0

    fun mainPosition(id: Int): PlayerPosition = state(id).position

    fun isMain(
        id: Int,
        slot: FieldSlot,
    ): Boolean = slot.mainPosition != null && players[id]?.position == slot.mainPosition

    fun pitchingAptitude(id: Int): PitchingAptitude = state(id).pitchingAptitude
}

data class PitchingAptitude(
    val starter: Int,
    val reliever: Int,
) {
    val starterType: Boolean get() = starter >= reliever

    fun of(role: PitchingRole): Int = if (role == PitchingRole.STARTER) starter else reliever

    fun with(
        role: PitchingRole,
        value: Int,
    ): PitchingAptitude = if (role == PitchingRole.STARTER) copy(starter = value) else copy(reliever = value)
}

data class LineupFingerprint(
    val kiyo: Map<String, Int>,
    val players: Map<Int, List<Int>>,
    val context: Map<String, Int?> = emptyMap(),
    val seasonNumbers: Map<SeasonNumberKey, Int?> = emptyMap(),
)

data class PlayerContract(
    val registration: PlayerRegistration,
    val uniformNumber: Int,
) {
    fun encodedNumber(team: Int): Int = PlayerNumberCode.encode(team, registration, uniformNumber)
}

data class SeasonNumberKey(
    val table: String,
    val playerId: Int,
    val year: Int,
    val squad: Int,
)

data class LineupSnapshot(
    val path: Path,
    val teamNumber: Int,
    val day: Int,
    val players: List<LineupPlayer>,
    val initial: LineupDraft,
    val fingerprint: LineupFingerprint,
    val notices: List<String> = emptyList(),
) {
    private val byId = players.associateBy { it.id }

    fun player(id: Int): LineupPlayer = byId.getValue(id)

    fun playerOrNull(id: Int?): LineupPlayer? = id?.let(byId::get)
}

sealed interface LineupLoadResult {
    data class Loaded(
        val snapshot: LineupSnapshot,
    ) : LineupLoadResult

    data class NoActiveTeam(
        val technicalDetails: String,
    ) : LineupLoadResult

    data class InvalidData(
        val technicalDetails: String,
    ) : LineupLoadResult

    data class Failed(
        val kind: SaveDatabaseFailureKind,
        val technicalDetails: String,
    ) : LineupLoadResult
}

fun interface LineupLoader {
    fun load(path: Path): LineupLoadResult
}

sealed interface LineupSaveResult {
    data object Saved : LineupSaveResult

    data class SavedWithWarning(
        val technicalDetails: String,
    ) : LineupSaveResult

    data object Conflict : LineupSaveResult

    data class InvalidChanges(
        val reason: String,
    ) : LineupSaveResult

    data class BackupFailed(
        val technicalDetails: String,
    ) : LineupSaveResult

    data class Failed(
        val kind: SaveDatabaseFailureKind,
        val technicalDetails: String,
    ) : LineupSaveResult
}

fun interface LineupSaver {
    fun save(
        snapshot: LineupSnapshot,
        draft: LineupDraft,
    ): LineupSaveResult
}

object LineupRules {
    const val FIRST_TEAM_MAX = 29
    const val MIN_PITCHERS = 8
    const val MIN_FIELDERS = 12
    const val FOREIGN_TOTAL_MAX = 4
    const val FOREIGN_PER_TYPE_MAX = 3
    const val ROLE_SLOTS = 9
    const val AUTO_ROLE_SLOTS = 6
    const val NORMAL_BENCH_SLOTS = 13
    const val DH_BENCH_SLOTS = 11

    fun restDaysAfterDemotion(day: Int): Int? = GameCalendar.DEMOTION_WAIT_DAYS.takeIf { GameCalendar.startsDemotionWait(day) }
}
