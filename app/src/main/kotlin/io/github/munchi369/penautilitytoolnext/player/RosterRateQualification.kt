package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.ui.PlayerCellSortStatus

internal enum class RosterQualificationStatus { QUALIFIED, BELOW, UNKNOWN }

internal enum class RosterQualificationKind(
    val label: String,
    val workloadKey: String,
) {
    BATTING("規定打席", "DASEKI"),
    PITCHING("規定投球回", "KAISUU"),
}

internal data class RosterRateQualification(
    val kind: RosterQualificationKind,
    val display: RosterDisplay,
    val sortKey: String,
    val statsSquad: Int,
    val completedGames: Int,
) {
    fun status(player: PlayerRosterPlayer): RosterQualificationStatus {
        if (player.major) return RosterQualificationStatus.UNKNOWN
        val rate = player.cell(display, sortKey, statsSquad)
        val workload = player.cell(display, kind.workloadKey, statsSquad)
        if (rate.sortStatus != PlayerCellSortStatus.VALUE || rate.number == null || workload.number == null) {
            return RosterQualificationStatus.UNKNOWN
        }
        val threshold =
            when (kind) {
                RosterQualificationKind.BATTING -> (completedGames * 3.1).toInt()
                RosterQualificationKind.PITCHING -> completedGames * 3
            }
        return if (workload.number >= threshold) RosterQualificationStatus.QUALIFIED else RosterQualificationStatus.BELOW
    }

    fun separatorLabel(status: RosterQualificationStatus): String =
        when (status) {
            RosterQualificationStatus.QUALIFIED -> ""
            RosterQualificationStatus.BELOW -> "${kind.label}未到達"
            RosterQualificationStatus.UNKNOWN -> "規定判定なし"
        }
}

internal fun PlayerRosterOptions.rateQualification(roster: PlayerRoster): RosterRateQualification? {
    if (teamScope.isMajorOnly) return null
    val kind =
        when {
            display == RosterDisplay.BATTING_STATS && current.sortKey in battingRateKeys -> RosterQualificationKind.BATTING
            display == RosterDisplay.PITCHING_STATS && current.sortKey in pitchingRateKeys -> RosterQualificationKind.PITCHING
            else -> return null
        }
    val games = roster.completedGames[statsSquad]?.takeIf { it > 0 } ?: return null
    return RosterRateQualification(kind, display, current.sortKey, statsSquad, games)
}

private val battingRateKeys = setOf("AVG", "OBP", "SLG", "OPS", "RISP", "RIGHT", "LEFT")
private val pitchingRateKeys = setOf("ERA", "AVG", "RISP", "RIGHT", "LEFT", "WHIP", "BABIP", "BB9", "K9")
