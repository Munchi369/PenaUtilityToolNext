package io.github.munchi369.penautilitytoolnext.game

enum class AbilityRank(
    val symbol: String,
) {
    S("S"),
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    E("E"),
    F("F"),
    G("G"),
    ;

    companion object {
        /** The game's rank thresholds; values below 30 are G. */
        fun of(value: Int): AbilityRank =
            when {
                value >= 90 -> S
                value >= 80 -> A
                value >= 70 -> B
                value >= 60 -> C
                value >= 50 -> D
                value >= 40 -> E
                value >= 30 -> F
                else -> G
            }
    }
}
