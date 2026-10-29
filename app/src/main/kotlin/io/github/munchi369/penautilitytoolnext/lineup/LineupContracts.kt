package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.GameCalendar
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad

data class ContractCounts(
    val controlled: Int,
    val development: Int,
)

data class ContractChange(
    val target: PlayerRegistration,
    val contract: PlayerContract?,
    val reason: String?,
)

class LineupContracts(
    private val snapshot: LineupSnapshot,
) {
    fun counts(draft: LineupDraft): ContractCounts =
        ContractCounts(
            controlled = draft.players.values.count { it.contract.registration == PlayerRegistration.CONTROLLED },
            development = draft.players.values.count { it.contract.registration == PlayerRegistration.DEVELOPMENT },
        )

    fun preview(
        draft: LineupDraft,
        playerId: Int,
    ): ContractChange {
        val target = if (draft.isControlled(playerId)) PlayerRegistration.DEVELOPMENT else PlayerRegistration.CONTROLLED
        val counts = counts(draft)
        val used =
            draft.players.values
                .map { it.contract }
                .filter { it.registration == target }
                .mapTo(mutableSetOf()) { it.uniformNumber }
        val range = if (target == PlayerRegistration.CONTROLLED) 0..99 else 101..199
        val number = range.firstOrNull { it !in used }
        val reason =
            when {
                !GameCalendar.allowsControlledRegistration(snapshot.day) -> "契約変更の期間外です（213〜308日目）"
                draft.squad(playerId) != PlayerSquad.SECOND -> "先に2軍へ下げてください"
                draft.inAnyLineup(playerId) || draft.inAnyRole(playerId) -> "先にスタメン・投手の役割から外してください"
                target == PlayerRegistration.CONTROLLED && counts.controlled >= CONTROLLED_MAX -> "支配下枠が満員です（70人）"
                target == PlayerRegistration.DEVELOPMENT && counts.development >= DEVELOPMENT_MAX -> "育成枠が満員です（99人）"
                number == null -> "変更先の背番号に空きがありません"
                else -> null
            }
        return ContractChange(target, number?.let { PlayerContract(target, it) }, reason)
    }

    fun change(
        draft: LineupDraft,
        playerId: Int,
    ): EditResult {
        val preview = preview(draft, playerId)
        preview.reason?.let { return EditResult.Rejected(it) }
        val contract = requireNotNull(preview.contract)
        return EditResult.Applied(draft.withState(playerId) { it.copy(contract = contract) })
    }

    fun validate(draft: LineupDraft): List<String> =
        buildList {
            if (draft.players.keys != snapshot.initial.players.keys) {
                add("契約変更の対象選手が一致しません")
                return@buildList
            }
            val counts = counts(draft)
            if (counts.controlled > CONTROLLED_MAX) add("支配下は70人までです")
            if (counts.development > DEVELOPMENT_MAX) add("育成は99人までです")
            val contracts = draft.players.values.map { it.contract }
            if (contracts.size != contracts.distinct().size) add("背番号が重複しています")
            snapshot.players.forEach { player ->
                val contract = draft.contract(player.id)
                val changed = contract != player.initialContract
                val range = if (contract.registration == PlayerRegistration.CONTROLLED) 0..99 else 100..199
                if (contract.uniformNumber !in range ||
                    (changed && contract.registration == PlayerRegistration.DEVELOPMENT && contract.uniformNumber == 100)
                ) {
                    add("${player.name}の背番号が契約区分に合っていません")
                }
                if (changed && !GameCalendar.allowsControlledRegistration(snapshot.day)) add("契約変更の期間外です（213〜308日目）")
                if (contract.registration == PlayerRegistration.DEVELOPMENT &&
                    (draft.squad(player.id) != PlayerSquad.SECOND || draft.inAnyLineup(player.id) || draft.inAnyRole(player.id))
                ) {
                    add("${player.name}は育成契約のため1軍で起用できません")
                }
            }
        }.distinct()

    companion object {
        const val CONTROLLED_MAX = 70
        const val DEVELOPMENT_MAX = 99
    }
}
