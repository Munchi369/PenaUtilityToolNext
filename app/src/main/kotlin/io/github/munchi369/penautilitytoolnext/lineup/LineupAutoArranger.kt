package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerSquad

class LineupAutoArranger(
    private val snapshot: LineupSnapshot,
    private val editing: LineupEditing = LineupEditing(snapshot),
) {
    fun arrangeFielders(
        draft: LineupDraft,
        mode: LineupMode,
        pins: Map<PinGroup, Set<Int>>,
        considerSubPositions: Boolean = false,
    ): EditResult {
        val pinned = pins[mode.pinGroup].orEmpty()
        val current = draft.lineup(mode)
        val fixed = current.withIndex().filter { (_, entry) -> entry.playerId != null && entry.playerId in pinned }
        val fixedIds = fixed.mapNotNull { it.value.playerId }.toSet()
        val pool = Pool(draft, pitcher = false, excluded = fixedIds)

        val openDefense = FieldSlot.defense.filter { slot -> fixed.none { it.value.slot == slot } }
        val chosen = linkedMapOf<FieldSlot, LineupPlayer>()
        val openDh = mode == LineupMode.DH && fixed.none { it.value.slot == FieldSlot.DH }
        if (considerSubPositions) {
            val slots = openDefense + if (openDh) listOf(FieldSlot.DH) else emptyList()
            chosen.putAll(pool.assignFielders(slots))
        } else {
            openDefense.forEach { slot ->
                pool.take({ draft.isMain(it.id, slot) }, compareBy { it.hitting })?.let { chosen[slot] = it }
            }
            openDefense.filterNot(chosen::containsKey).forEach { slot ->
                pool
                    .take(
                        { draft.aptitude(it.id, slot) > 0 },
                        compareBy<LineupPlayer> { draft.aptitude(it.id, slot) }.thenBy { it.hitting },
                    )?.let { chosen[slot] = it }
            }
            openDefense.filterNot(chosen::containsKey).forEach { slot ->
                pool.take({ true }, compareBy { it.hitting })?.let { chosen[slot] = it }
            }
            if (openDh) {
                pool.take({ true }, compareBy { it.hitting })?.let { chosen[FieldSlot.DH] = it }
            }
        }
        val expectedOpen = openDefense.size + if (openDh) 1 else 0
        if (chosen.size < expectedOpen) return EditResult.Rejected("スタメンを埋められる野手が足りません")

        val order = arrayOfNulls<LineupEntry>(current.size)
        fixed.forEach { (index, entry) -> order[index] = entry }
        val pitcherIndex =
            if (mode == LineupMode.NORMAL) {
                if (order[PITCHER_BATTING_INDEX] == null) PITCHER_BATTING_INDEX else order.indices.last { order[it] == null }
            } else {
                null
            }
        pitcherIndex?.let { order[it] = LineupEntry(null, FieldSlot.PITCHER) }
        val remaining = chosen.entries.map { (slot, player) -> LineupEntry(player.id, slot) to player }.toMutableList()
        BATTING_ORDER_PRIORITY.forEach { index ->
            if (order[index] != null) return@forEach
            val best =
                remaining.maxWithOrNull(
                    compareBy<Pair<LineupEntry, LineupPlayer>> { orderScore(index, it.second) }.thenBy { -it.second.id },
                )
            if (best != null) {
                order[index] = best.first
                remaining.remove(best)
            }
        }
        var updated = draft.withLineup(mode, order.map { requireNotNull(it) })
        val notices = mutableListOf<String>()
        chosen.values.filter { draft.squad(it.id) == PlayerSquad.SECOND }.forEach { player ->
            when (val result = editing.promote(updated, player.id)) {
                is EditResult.Rejected -> return result
                is EditResult.Applied -> {
                    updated = result.draft
                    result.notice?.let(notices::add)
                }
            }
        }
        return EditResult.Applied(updated, notices.joinToString("\n").ifEmpty { null })
    }

    fun arrangePitchers(
        draft: LineupDraft,
        pins: Map<PinGroup, Set<Int>>,
    ): EditResult {
        val pinned = pins[PinGroup.PITCHING].orEmpty()
        val roles =
            PitchingRole.entries.associateWith { role ->
                draft.roles(role).map { id -> id?.takeIf { it in pinned } }.toMutableList()
            }
        val fixedIds =
            roles.values
                .flatten()
                .filterNotNull()
                .toSet()
        val pool = Pool(draft, pitcher = true, excluded = fixedIds)
        val starterType: (LineupPlayer) -> Boolean = { draft.pitchingAptitude(it.id).starterType }

        fun fill(
            role: PitchingRole,
            preferred: (LineupPlayer) -> Boolean,
            fallback: Comparator<LineupPlayer>,
        ) {
            val ids = roles.getValue(role)
            (0 until LineupRules.AUTO_ROLE_SLOTS).filter { ids[it] == null }.forEach { index ->
                val player = pool.take(preferred, compareBy { it.overall }) ?: pool.take({ true }, fallback)
                ids[index] = player?.id
            }
        }
        fill(PitchingRole.STARTER, starterType, compareBy { draft.pitchingAptitude(it.id).starter })
        fill(PitchingRole.RELIEVER, { !starterType(it) }, compareBy { draft.pitchingAptitude(it.id).reliever })

        var updated = PitchingRole.entries.fold(draft) { current, role -> current.withRoles(role, roles.getValue(role).toList()) }
        val notices = mutableListOf<String>()
        roles.values
            .flatten()
            .filterNotNull()
            .filter { draft.squad(it) == PlayerSquad.SECOND }
            .forEach { id ->
                when (val result = editing.promote(updated, id)) {
                    is EditResult.Rejected -> return result
                    is EditResult.Applied -> {
                        updated = result.draft
                        result.notice?.let(notices::add)
                    }
                }
            }
        return EditResult.Applied(updated, notices.joinToString("\n").ifEmpty { null })
    }

    private fun orderScore(
        index: Int,
        player: LineupPlayer,
    ): Double =
        when (index) {
            3 -> player.batting + player.power * 1.5
            2 -> player.batting * 1.5 + player.power * 1.2
            0, 1 -> player.batting.toDouble()
            else -> player.hitting.toDouble()
        }

    private inner class Pool(
        private val draft: LineupDraft,
        pitcher: Boolean,
        excluded: Set<Int>,
    ) {
        private val candidates =
            snapshot.players
                .filter { player ->
                    player.isPitcher == pitcher &&
                        draft.isControlled(player.id) &&
                        player.id !in excluded &&
                        !player.injured &&
                        (draft.squad(player.id) == PlayerSquad.FIRST || player.initialSquad == PlayerSquad.FIRST || player.restDays == 0)
                }.toMutableList()
        private var foreignRoom = editing.foreignRoom(draft, pitcher)

        fun assignFielders(slots: List<FieldSlot>): Map<FieldSlot, LineupPlayer> {
            // Descending masks ensure that each player is assigned at most once.
            val foreignStates = foreignRoom.coerceAtLeast(0) + 1
            val masks = 1 shl slots.size
            val assignments = arrayOfNulls<FieldAssignment>(masks * foreignStates)
            assignments[0] = FieldAssignment(List(slots.size) { null })
            candidates.sortedBy { it.id }.forEach { player ->
                val foreignCost = if (player.foreign && draft.squad(player.id) == PlayerSquad.SECOND) 1 else 0
                for (mask in masks - 1 downTo 0) {
                    for (foreign in 0 until foreignStates - foreignCost) {
                        val previous = assignments[mask * foreignStates + foreign] ?: continue
                        slots.forEachIndexed { index, slot ->
                            val bit = 1 shl index
                            if (mask and bit == 0) {
                                val next = previous.withPlayer(index, slot, player, draft)
                                val key = (mask or bit) * foreignStates + foreign + foreignCost
                                if (next.betterThan(assignments[key])) assignments[key] = next
                            }
                        }
                    }
                }
            }
            val best =
                (0 until foreignStates)
                    .mapNotNull { assignments[(masks - 1) * foreignStates + it] }
                    .reduceOrNull { best, next -> if (next.betterThan(best)) next else best }
                    ?: return emptyMap()
            return slots.mapIndexed { index, slot -> slot to requireNotNull(best.players[index]) }.toMap()
        }

        fun take(
            filter: (LineupPlayer) -> Boolean,
            score: Comparator<LineupPlayer>,
        ): LineupPlayer? {
            val best =
                candidates
                    .filter { filter(it) && usable(it) }
                    .maxWithOrNull(score.thenByDescending { it.id })
                    ?: return null
            candidates.remove(best)
            if (best.foreign && draft.squad(best.id) == PlayerSquad.SECOND) foreignRoom--
            return best
        }

        private fun usable(player: LineupPlayer): Boolean =
            !(
                player.foreign &&
                    draft.squad(player.id) == PlayerSquad.SECOND &&
                    foreignRoom <= 0
            )
    }

    private data class FieldAssignment(
        val players: List<LineupPlayer?>,
        val supported: Int = 0,
        val hitting: Int = 0,
        val aptitude: Int = 0,
        val mainPositions: Int = 0,
    ) {
        fun withPlayer(
            index: Int,
            slot: FieldSlot,
            player: LineupPlayer,
            draft: LineupDraft,
        ): FieldAssignment {
            val main = draft.isMain(player.id, slot)
            val defense = draft.aptitude(player.id, slot)
            return FieldAssignment(
                players = players.toMutableList().also { it[index] = player },
                supported = supported + if (slot == FieldSlot.DH || main || defense > 0) 1 else 0,
                hitting = hitting + player.hitting,
                aptitude = aptitude + defense,
                mainPositions = mainPositions + if (main) 1 else 0,
            )
        }

        fun betterThan(other: FieldAssignment?): Boolean {
            if (other == null) return true
            val score = compareValuesBy(this, other, { it.supported }, { it.hitting }, { it.aptitude }, { it.mainPositions })
            if (score != 0) return score > 0
            players.indices.forEach { index ->
                val id = players[index]?.id ?: Int.MAX_VALUE
                val otherId = other.players[index]?.id ?: Int.MAX_VALUE
                if (id != otherId) return id < otherId
            }
            return false
        }
    }

    private companion object {
        const val PITCHER_BATTING_INDEX = 8
        val BATTING_ORDER_PRIORITY = listOf(3, 2, 4, 0, 5, 1, 6, 7, 8)
    }
}
