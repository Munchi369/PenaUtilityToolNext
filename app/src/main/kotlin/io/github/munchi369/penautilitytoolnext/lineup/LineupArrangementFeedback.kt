package io.github.munchi369.penautilitytoolnext.lineup

internal fun arrangementChangedSpots(
    before: LineupDraft,
    after: LineupDraft,
    tab: LineupTab,
    mode: LineupMode,
): Set<DropSpot> =
    buildSet {
        when (tab) {
            LineupTab.FIELDERS ->
                after.lineup(mode).forEachIndexed { index, entry ->
                    val previous = before.lineup(mode)[index]
                    if (entry != previous) {
                        add(DropSpot.LineupSlot(mode, index))
                        entry.playerId?.let { add(DropSpot.ListedPlayer(it)) }
                        previous.playerId?.let { add(DropSpot.ListedPlayer(it)) }
                    }
                }
            LineupTab.PITCHERS ->
                PitchingRole.entries.forEach { role ->
                    after.roles(role).forEachIndexed { index, id ->
                        val previous = before.roles(role)[index]
                        if (id != previous) {
                            add(DropSpot.RoleSlot(role, index))
                            id?.let { add(DropSpot.ListedPlayer(it)) }
                            previous?.let { add(DropSpot.ListedPlayer(it)) }
                        }
                    }
                }
        }
        after.players.forEach { (id, state) ->
            if (state.squad != before.squad(id) || state.contract != before.contract(id)) add(DropSpot.ListedPlayer(id))
        }
    }
