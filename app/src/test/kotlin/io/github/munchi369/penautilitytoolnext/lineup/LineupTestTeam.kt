package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerInjury
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import java.nio.file.Path

internal fun fielder(
    id: Int,
    position: PlayerPosition,
    batting: Int = 50,
    power: Int = 50,
    squad: PlayerSquad = PlayerSquad.FIRST,
    restDays: Int = 0,
    foreign: Boolean = false,
    injury: PlayerInjury = PlayerInjury.None,
    aptitudes: Map<FieldSlot, Int> = emptyMap(),
): LineupPlayer =
    LineupPlayer(
        id = id,
        name = "野手$id",
        uniformNumber = id % 100,
        position = position,
        initialSquad = squad,
        restDays = restDays,
        foreign = foreign,
        injury = injury,
        age = 25,
        overall = 600,
        batting = batting,
        power = power,
        starterAptitude = 0,
        relieverAptitude = 0,
        initialAptitudes = FieldSlot.defense.associateWith { slot -> aptitudes[slot] ?: if (slot.mainPosition == position) 80 else 0 },
    )

internal fun pitcher(
    id: Int,
    overall: Int,
    starter: Int,
    reliever: Int,
    squad: PlayerSquad = PlayerSquad.FIRST,
    restDays: Int = 0,
    foreign: Boolean = false,
    injury: PlayerInjury = PlayerInjury.None,
): LineupPlayer =
    LineupPlayer(
        id = id,
        name = "投手$id",
        uniformNumber = id % 100,
        position = PlayerPosition.PITCHER,
        initialSquad = squad,
        restDays = restDays,
        foreign = foreign,
        injury = injury,
        age = 25,
        overall = overall,
        batting = 20,
        power = 20,
        starterAptitude = starter,
        relieverAptitude = reliever,
        initialAptitudes = FieldSlot.defense.associateWith { 0 },
    )

private val defensePositions =
    listOf(
        PlayerPosition.CATCHER,
        PlayerPosition.FIRST_BASEMAN,
        PlayerPosition.SECOND_BASEMAN,
        PlayerPosition.THIRD_BASEMAN,
        PlayerPosition.SHORTSTOP,
        PlayerPosition.LEFT_FIELDER,
        PlayerPosition.CENTER_FIELDER,
        PlayerPosition.RIGHT_FIELDER,
    )

/** 13 first-team pitchers (1..13) and 16 first-team fielders (101..116): the first team is full at 29. */
internal fun fullTeam(extra: List<LineupPlayer> = emptyList()): List<LineupPlayer> =
    (
        (1..13).map { pitcher(it, overall = 900 - it * 10, starter = if (it <= 7) 80 else 50, reliever = if (it <= 7) 50 else 80) } +
            (0 until 16).map { index -> fielder(101 + index, defensePositions[index % 8], batting = 60 - index, power = 60 - index) } +
            extra
    ).mapIndexed { index, player ->
        if (player.initialRegistration == PlayerRegistration.CONTROLLED) player.copy(uniformNumber = index) else player
    }

internal fun snapshotOf(
    players: List<LineupPlayer>,
    day: Int = 100,
    normal: List<LineupEntry>? = null,
    dh: List<LineupEntry>? = null,
    starters: List<Int?>? = null,
    relievers: List<Int?>? = null,
): LineupSnapshot {
    val defaultNormal =
        FieldSlot.defense.mapIndexed { index, slot -> LineupEntry(101 + index, slot) } + LineupEntry(null, FieldSlot.PITCHER)
    val defaultDh = FieldSlot.defense.mapIndexed { index, slot -> LineupEntry(101 + index, slot) } + LineupEntry(109, FieldSlot.DH)
    val pitcherIds = players.filter { it.isPitcher && it.initialSquad == PlayerSquad.FIRST }.map { it.id }
    val initial =
        LineupDraft(
            normal = normal ?: defaultNormal,
            dh = dh ?: defaultDh,
            starters = starters ?: (pitcherIds.take(6) + List(3) { null }),
            relievers = relievers ?: (pitcherIds.drop(6).take(6) + List(3) { null }),
            players = players.associate { it.id to it.initialState },
        )
    return LineupSnapshot(
        path = Path.of("test"),
        teamNumber = 1,
        day = day,
        players = players,
        initial = initial,
        fingerprint = LineupFingerprint(emptyMap(), emptyMap()),
    )
}
