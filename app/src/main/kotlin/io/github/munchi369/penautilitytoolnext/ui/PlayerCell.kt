package io.github.munchi369.penautilitytoolnext.ui

import androidx.compose.ui.graphics.Color
import io.github.munchi369.penautilitytoolnext.game.AbilityRank

data class AbilityStyle(
    val rank: AbilityRank,
    val colorStart: Int,
) {
    init {
        require(colorStart >= 0) { "能力色の開始位置は0以上である必要があります" }
    }
}

data class PlayerCell(
    val text: String,
    val number: Double? = null,
    val abilityStyle: AbilityStyle? = null,
    val sortStatus: PlayerCellSortStatus = PlayerCellSortStatus.VALUE,
    val recordHigh: Boolean = false,
) {
    init {
        abilityStyle?.let { require(it.colorStart < text.length) { "能力色の開始位置が表示文字列の範囲外です" } }
    }
}

enum class PlayerCellSortStatus {
    VALUE,
    MISSING,
    UNKNOWN,
}

internal fun missingCell(text: String = "—"): PlayerCell = PlayerCell(text, sortStatus = PlayerCellSortStatus.MISSING)

internal fun unknownCell(): PlayerCell = PlayerCell("不明", sortStatus = PlayerCellSortStatus.UNKNOWN)

internal fun numberCell(value: Int): PlayerCell = PlayerCell(value.toString(), value.toDouble())

internal fun abilityCell(value: Int): PlayerCell {
    if (value == 0) return PlayerCell("", 0.0)
    val rank = AbilityRank.of(value)
    return PlayerCell("${rank.symbol}$value", value.toDouble(), AbilityStyle(rank, colorStart = 0))
}

internal fun pitchAbilityCell(
    name: String,
    value: Int?,
): PlayerCell {
    if (value == null) return missingCell("$name —")
    val ability = abilityCell(value)
    if (ability.text.isEmpty()) return PlayerCell(name, value.toDouble())
    val prefix = "$name "
    val style = requireNotNull(ability.abilityStyle)
    return ability.copy(text = prefix + ability.text, abilityStyle = style.copy(colorStart = prefix.length))
}

internal fun gameAbilityColor(rank: AbilityRank): Color =
    when (rank) {
        AbilityRank.S, AbilityRank.A -> Color(0xFFFF1493)
        AbilityRank.B -> Color(0xFFFF0000)
        AbilityRank.C -> Color(0xFFFF4500)
        AbilityRank.D -> Color(0xFFEBB200)
        AbilityRank.E -> Color(0xFF3BD70A)
        AbilityRank.F -> Color(0xFF007FEB)
        AbilityRank.G -> Color(0xFF858585)
    }
