package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerSquad

sealed interface EditResult {
    data class Applied(
        val draft: LineupDraft,
        val notice: String? = null,
    ) : EditResult

    data class Rejected(
        val reason: String,
    ) : EditResult
}

data class RosterCounts(
    val total: Int,
    val pitchers: Int,
    val fielders: Int,
    val foreignPitchers: Int,
    val foreignFielders: Int,
) {
    val foreignTotal: Int get() = foreignPitchers + foreignFielders

    fun rosterErrors(): List<String> =
        buildList {
            if (total > LineupRules.FIRST_TEAM_MAX) add("1軍は${LineupRules.FIRST_TEAM_MAX}人までです")
            if (pitchers < LineupRules.MIN_PITCHERS) add("1軍の投手は${LineupRules.MIN_PITCHERS}人以上必要です")
            if (fielders < LineupRules.MIN_FIELDERS) add("1軍の野手は${LineupRules.MIN_FIELDERS}人以上必要です")
            if (foreignTotal > LineupRules.FOREIGN_TOTAL_MAX ||
                foreignPitchers > LineupRules.FOREIGN_PER_TYPE_MAX ||
                foreignFielders > LineupRules.FOREIGN_PER_TYPE_MAX
            ) {
                add("外国人は1軍に4人まで、投手・野手はそれぞれ3人までです")
            }
        }
}

class LineupEditing(
    private val snapshot: LineupSnapshot,
) {
    fun counts(draft: LineupDraft): RosterCounts {
        val first = snapshot.players.filter { draft.squad(it.id) == PlayerSquad.FIRST }
        return RosterCounts(
            total = first.size,
            pitchers = first.count { it.isPitcher },
            fielders = first.count { !it.isPitcher },
            foreignPitchers = first.count { it.isPitcher && it.foreign },
            foreignFielders = first.count { !it.isPitcher && it.foreign },
        )
    }

    fun foreignRoom(
        draft: LineupDraft,
        pitcher: Boolean,
    ): Int {
        val counts = counts(draft)
        val sameType = if (pitcher) counts.foreignPitchers else counts.foreignFielders
        return minOf(LineupRules.FOREIGN_PER_TYPE_MAX - sameType, LineupRules.FOREIGN_TOTAL_MAX - counts.foreignTotal)
    }

    fun swapLineup(
        draft: LineupDraft,
        mode: LineupMode,
        from: Int,
        to: Int,
    ): LineupDraft {
        if (from == to) return draft
        val entries = draft.lineup(mode).toMutableList()
        entries[from] = entries[to].also { entries[to] = entries[from] }
        return draft.withLineup(mode, entries)
    }

    fun placeInLineup(
        draft: LineupDraft,
        mode: LineupMode,
        index: Int,
        playerId: Int,
    ): EditResult {
        val player = snapshot.player(playerId)
        if (player.isPitcher) return EditResult.Rejected("投手は野手の打順に入れられません")
        val entries = draft.lineup(mode)
        if (entries[index].slot == FieldSlot.PITCHER) return EditResult.Rejected("投手の打順枠には野手を置けません")
        val current = entries.indexOfFirst { it.playerId == playerId }
        if (current >= 0) return EditResult.Applied(swapLineup(draft, mode, current, index))
        val promoted =
            when (val result = promote(draft, playerId)) {
                is EditResult.Rejected -> return result
                is EditResult.Applied -> result
            }
        val updated = draft.lineup(mode).toMutableList().also { it[index] = it[index].copy(playerId = playerId) }
        return EditResult.Applied(promoted.draft.withLineup(mode, updated), promoted.notice)
    }

    fun changePosition(
        draft: LineupDraft,
        mode: LineupMode,
        index: Int,
        slot: FieldSlot,
    ): LineupDraft {
        val entries = draft.lineup(mode).toMutableList()
        val current = entries[index]
        if (current.slot == slot || current.slot == FieldSlot.PITCHER || slot == FieldSlot.PITCHER) return draft
        val other = entries.indexOfFirst { it.slot == slot }
        if (other >= 0) entries[other] = entries[other].copy(slot = current.slot)
        entries[index] = current.copy(slot = slot)
        return draft.withLineup(mode, entries)
    }

    fun placeInRole(
        draft: LineupDraft,
        role: PitchingRole,
        index: Int,
        playerId: Int,
    ): EditResult {
        val player = snapshot.player(playerId)
        if (!player.isPitcher) return EditResult.Rejected("野手は投手の役割に入れられません")
        PitchingRole.entries.forEach { currentRole ->
            val currentIndex = draft.roles(currentRole).indexOf(playerId)
            if (currentIndex >= 0) return EditResult.Applied(swapRoles(draft, currentRole, currentIndex, role, index))
        }
        val promoted =
            when (val result = promote(draft, playerId)) {
                is EditResult.Rejected -> return result
                is EditResult.Applied -> result
            }
        val ids =
            promoted.draft
                .roles(role)
                .toMutableList()
                .also { it[index] = playerId }
        return EditResult.Applied(promoted.draft.withRoles(role, ids), promoted.notice)
    }

    fun swapRoles(
        draft: LineupDraft,
        fromRole: PitchingRole,
        fromIndex: Int,
        toRole: PitchingRole,
        toIndex: Int,
    ): LineupDraft {
        val moving = draft.roles(fromRole)[fromIndex]
        val displaced = draft.roles(toRole)[toIndex]
        if (fromRole == toRole) {
            val ids = draft.roles(fromRole).toMutableList()
            ids[fromIndex] = displaced
            ids[toIndex] = moving
            return draft.withRoles(fromRole, ids)
        }
        val source = draft.roles(fromRole).toMutableList().also { it[fromIndex] = displaced }
        val target = draft.roles(toRole).toMutableList().also { it[toIndex] = moving }
        return draft.withRoles(fromRole, source).withRoles(toRole, target)
    }

    fun removeFromRole(
        draft: LineupDraft,
        playerId: Int,
    ): LineupDraft =
        PitchingRole.entries.fold(draft) { current, role ->
            current.withRoles(role, current.roles(role).map { if (it == playerId) null else it })
        }

    /**
     * Promotes a farm player. When the first team would exceed its limit, the weakest same-type player who
     * was first-team at load and holds no lineup slot or role is demoted instead.
     */
    fun promote(
        draft: LineupDraft,
        playerId: Int,
    ): EditResult {
        if (!draft.isControlled(playerId)) return EditResult.Rejected("育成選手は先に2軍へ移して支配下登録してください")
        if (draft.squad(playerId) == PlayerSquad.FIRST) return EditResult.Applied(draft)
        val player = snapshot.player(playerId)
        if (player.initialSquad == PlayerSquad.SECOND && player.restDays > 0) {
            return EditResult.Rejected("${player.name}は休止期間中（残り${player.restDays}日）のため昇格できません")
        }
        if (player.foreign && foreignRoom(draft, player.isPitcher) <= 0) {
            return EditResult.Rejected("外国人枠（1軍4人まで、投手・野手はそれぞれ3人まで）を超えるため昇格できません")
        }
        val promoted = draft.withState(playerId) { it.copy(squad = PlayerSquad.FIRST) }
        if (counts(promoted).total <= LineupRules.FIRST_TEAM_MAX) {
            return EditResult.Applied(promoted, "${player.name}を1軍に昇格します")
        }
        val candidate =
            snapshot.players
                .filter { other ->
                    other.id != playerId &&
                        other.isPitcher == player.isPitcher &&
                        other.initialSquad == PlayerSquad.FIRST &&
                        promoted.squad(other.id) == PlayerSquad.FIRST &&
                        !promoted.inAnyLineup(other.id) &&
                        !promoted.inAnyRole(other.id)
                }.minWithOrNull(compareBy<LineupPlayer> { if (it.isPitcher) it.overall else it.hitting }.thenBy { it.id })
                ?: return EditResult.Rejected(
                    "1軍が${LineupRules.FIRST_TEAM_MAX}人を超えるため昇格できません。先に降格させる${if (player.isPitcher) "投手" else "野手"}を2軍へ移してください",
                )
        return EditResult.Applied(
            promoted.withState(candidate.id) { it.copy(squad = PlayerSquad.SECOND) },
            "${player.name}を1軍に昇格し、${candidate.name}を2軍に降格します",
        )
    }

    fun demote(
        draft: LineupDraft,
        playerId: Int,
    ): EditResult {
        val player = snapshot.player(playerId)
        if (draft.squad(playerId) == PlayerSquad.SECOND) return EditResult.Applied(draft)
        if (draft.inAnyLineup(playerId)) return EditResult.Rejected("スタメンの選手は降格できません")
        if (draft.inAnyRole(playerId)) return EditResult.Rejected("役割に入っている投手は降格できません")
        val counts = counts(draft)
        if (player.isPitcher && counts.pitchers <= LineupRules.MIN_PITCHERS) {
            return EditResult.Rejected("1軍の投手は${LineupRules.MIN_PITCHERS}人以上必要です")
        }
        if (!player.isPitcher && counts.fielders <= LineupRules.MIN_FIELDERS) {
            return EditResult.Rejected("1軍の野手は${LineupRules.MIN_FIELDERS}人以上必要です")
        }
        return EditResult.Applied(draft.withState(playerId) { it.copy(squad = PlayerSquad.SECOND) })
    }

    /** Restores one tab's edits; rejected when the other tab's registration changes would leave the roster invalid. */
    fun reset(
        draft: LineupDraft,
        tab: LineupTab,
    ): EditResult {
        val initial = snapshot.initial
        val pitchers = tab == LineupTab.PITCHERS
        val tabPlayers = snapshot.players.filter { it.isPitcher == pitchers }.mapTo(mutableSetOf()) { it.id }
        val players =
            draft.players.mapValues { (id, state) ->
                val before = initial.state(id)
                val registration = if (id in tabPlayers) state.copy(squad = before.squad, contract = before.contract) else state
                if (pitchers) {
                    registration.copy(pitchingAptitude = before.pitchingAptitude)
                } else {
                    registration.copy(aptitudes = before.aptitudes, position = before.position)
                }
            }
        val restored =
            if (pitchers) {
                draft.copy(starters = initial.starters, relievers = initial.relievers, players = players)
            } else {
                draft.copy(normal = initial.normal, dh = initial.dh, players = players)
            }
        val errors = counts(restored).rosterErrors() + LineupContracts(snapshot).validate(restored)
        if (errors.isNotEmpty()) {
            val other = if (pitchers) LineupTab.FIELDERS else LineupTab.PITCHERS
            return EditResult.Rejected("${errors.first()}。先に${other.label}タブで元に戻してください")
        }
        return EditResult.Applied(restored)
    }

    fun setAptitude(
        draft: LineupDraft,
        playerId: Int,
        slot: FieldSlot,
        value: Int,
    ): LineupDraft = draft.withState(playerId) { it.copy(aptitudes = it.aptitudes + (slot to value.coerceIn(0, 100))) }

    fun setMainPosition(
        draft: LineupDraft,
        playerId: Int,
        slot: FieldSlot,
    ): LineupDraft {
        val position = slot.mainPosition ?: return draft
        if (snapshot.player(playerId).isPitcher || slot !in FieldSlot.defense) return draft
        return draft.withState(playerId) { it.copy(position = position) }
    }

    fun setPitchingAptitude(
        draft: LineupDraft,
        playerId: Int,
        role: PitchingRole,
        value: Int,
    ): LineupDraft {
        if (!snapshot.player(playerId).isPitcher) return draft
        return draft.withState(playerId) { it.copy(pitchingAptitude = it.pitchingAptitude.with(role, value.coerceIn(0, 100))) }
    }

    /** Copies one mode's order and defense into the other; DH and the pitcher's batting slot swap places. */
    fun copyLineup(
        draft: LineupDraft,
        from: LineupMode,
    ): LineupDraft {
        val source = draft.lineup(from)
        if (from == LineupMode.DH) {
            return draft.withLineup(LineupMode.NORMAL, source.filter { it.slot != FieldSlot.DH } + LineupEntry(null, FieldSlot.PITCHER))
        }
        val fielders = source.mapNotNullTo(mutableSetOf()) { it.playerId }
        val designated =
            draft.dh
                .firstOrNull { it.slot == FieldSlot.DH }
                ?.playerId
                ?.takeIf { it !in fielders && draft.squad(it) == PlayerSquad.FIRST && draft.isControlled(it) }
                ?: snapshot.players
                    .filter { !it.isPitcher && draft.isControlled(it.id) && draft.squad(it.id) == PlayerSquad.FIRST && it.id !in fielders }
                    .maxWithOrNull(strongestFirst { it.hitting })
                    ?.id
        return draft.withLineup(
            LineupMode.DH,
            source.map { if (it.slot == FieldSlot.PITCHER) LineupEntry(designated, FieldSlot.DH) else it },
        )
    }

    /** Players registered on the first team but outside the given lineup, strongest hitters first. */
    fun bench(
        draft: LineupDraft,
        mode: LineupMode,
    ): List<LineupPlayer> {
        val starters = draft.lineup(mode).mapNotNullTo(mutableSetOf()) { it.playerId }
        return snapshot.players
            .filter { !it.isPitcher && draft.isControlled(it.id) && draft.squad(it.id) == PlayerSquad.FIRST && it.id !in starters }
            .sortedWith(compareByDescending<LineupPlayer> { it.hitting }.thenBy { it.uniformNumber })
    }

    fun validate(draft: LineupDraft): List<String> {
        val errors = LineupContracts(snapshot).validate(draft).toMutableList()
        LineupMode.entries.forEach { mode ->
            val entries = draft.lineup(mode)
            val expected = FieldSlot.defense + if (mode == LineupMode.DH) FieldSlot.DH else FieldSlot.PITCHER
            if (entries.map { it.slot }.sorted() != expected.sorted()) errors += "${mode.label}: 守備位置が揃っていません"
            val ids = entries.mapNotNull { it.playerId }
            if (entries.any { it.slot != FieldSlot.PITCHER && it.playerId == null }) errors += "${mode.label}: 空いている打順があります"
            if (ids.size != ids.toSet().size) errors += "${mode.label}: 同じ選手が複数の打順に入っています"
            ids.map(snapshot::player).forEach { player ->
                if (player.isPitcher) errors += "${mode.label}: 投手の${player.name}が野手の打順に入っています"
                if (draft.squad(player.id) != PlayerSquad.FIRST) errors += "${mode.label}: ${player.name}が1軍に登録されていません"
            }
        }
        val roleIds = (draft.starters + draft.relievers).filterNotNull()
        if (draft.starters.none { it != null }) errors += "先発が1人もいません"
        if (roleIds.size != roleIds.toSet().size) errors += "同じ投手が複数の役割に入っています"
        roleIds.map(snapshot::player).forEach { player ->
            if (!player.isPitcher) errors += "野手の${player.name}が投手の役割に入っています"
            if (draft.squad(player.id) != PlayerSquad.FIRST) errors += "${player.name}が1軍に登録されていません"
        }
        errors += counts(draft).rosterErrors()
        snapshot.players
            .filter { it.initialSquad == PlayerSquad.SECOND && it.restDays > 0 && draft.squad(it.id) == PlayerSquad.FIRST }
            .forEach { errors += "${it.name}は休止期間中（残り${it.restDays}日）のため昇格できません" }
        return errors.distinct()
    }

    fun changeCount(draft: LineupDraft): Int {
        val initial = snapshot.initial
        var count = 0
        LineupMode.entries.forEach { mode ->
            count += draft.lineup(mode).zip(initial.lineup(mode)).count { (now, before) -> now != before }
        }
        PitchingRole.entries.forEach { role ->
            count += draft.roles(role).zip(initial.roles(role)).count { (now, before) -> now != before }
        }
        draft.players.forEach { (id, state) ->
            val before = initial.players[id]
            if (state.contract != before?.contract) count++
            if (state.squad != before?.squad) count++
            count += state.aptitudes.count { (slot, value) -> initial.aptitude(id, slot) != value }
            if (state.position != before?.position) count++
            count += PitchingRole.entries.count { state.pitchingAptitude.of(it) != before?.pitchingAptitude?.of(it) }
        }
        return count
    }
}

/** Orders by [score] and, on ties, ranks the lower player ID higher, so `maxWith` is deterministic. */
internal fun strongestFirst(score: (LineupPlayer) -> Int): Comparator<LineupPlayer> =
    compareBy<LineupPlayer>(score).thenByDescending { it.id }
