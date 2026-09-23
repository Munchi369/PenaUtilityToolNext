package org.example.player

data class RosterDisplayOptions(
    val sortKey: String,
    val descending: Boolean = true,
    val visibleColumns: Set<String>,
)

sealed interface RosterPositionFilter {
    val label: String

    data object Fielders : RosterPositionFilter {
        override val label: String = "野手"
    }

    data class MainPosition(
        val position: PlayerPosition,
    ) : RosterPositionFilter {
        override val label: String = position.displayName
    }

    fun matches(player: PlayerRosterPlayer): Boolean =
        when (this) {
            Fielders -> player.position != PlayerPosition.PITCHER
            is MainPosition -> player.position == position
        }
}

sealed interface RosterTeamScope {
    data object All : RosterTeamScope

    data object AllDomestic : RosterTeamScope

    data object FirstLeague : RosterTeamScope

    data object SecondLeague : RosterTeamScope

    data object Major : RosterTeamScope

    data class Team(
        val number: Int,
    ) : RosterTeamScope {
        init {
            require(number in 1..12) { "国内球団番号は1から12である必要があります: $number" }
        }
    }

    val isMajorOnly: Boolean get() = this == Major
    val isSingleDomesticTeam: Boolean get() = this is Team
    val showsTeamColumn: Boolean get() = this == All || this == AllDomestic || this == FirstLeague || this == SecondLeague
    val domesticTeamNumbers: IntRange
        get() =
            when (this) {
                All, AllDomestic -> 1..12
                FirstLeague -> 1..6
                SecondLeague -> 7..12
                Major -> IntRange.EMPTY
                is Team -> number..number
            }

    fun matches(player: PlayerRosterPlayer): Boolean =
        if (player.major) {
            this == All || this == Major
        } else {
            player.teamNumber in domesticTeamNumbers
        }
}

enum class RosterGrouping(
    val label: String,
) {
    NONE("なし"),
    SQUAD("軍別"),
    POSITION("ポジション別"),
    ;

    companion object {
        fun choicesForScope(scope: RosterTeamScope): List<RosterGrouping> =
            if (!scope.isSingleDomesticTeam) {
                listOf(NONE, POSITION)
            } else {
                entries.toList()
            }
    }
}

data class PlayerRosterOptions(
    val teamScope: RosterTeamScope = RosterTeamScope.All,
    val display: RosterDisplay = RosterDisplay.BASIC,
    val search: String = "",
    val squad: PlayerSquad? = null,
    val registration: PlayerRegistration? = null,
    val positionFilter: RosterPositionFilter? = null,
    val automaticDisplaySwitch: Boolean = false,
    val statsSquad: Int = 1,
    val grouping: RosterGrouping = RosterGrouping.NONE,
    val expandedSquads: Set<PlayerSquad> = setOf(PlayerSquad.FIRST, PlayerSquad.SECOND),
    val expandedPositions: Set<PlayerPosition> = PlayerPosition.entries.toSet(),
    val revealGroups: Boolean = false,
    val displays: Map<RosterDisplay, RosterDisplayOptions> =
        RosterDisplay.entries.associateWith { display ->
            RosterDisplayOptions(
                sortKey = display.initialSort,
                visibleColumns =
                    RosterColumns
                        .forDisplay(display)
                        .filter { it.standard }
                        .map { it.key }
                        .toSet(),
            )
        },
) {
    val current: RosterDisplayOptions get() = displays.getValue(display)

    fun updateDisplay(change: (RosterDisplayOptions) -> RosterDisplayOptions): PlayerRosterOptions =
        copy(displays = displays + (display to change(current)))

    fun selectGrouping(value: RosterGrouping): PlayerRosterOptions {
        if (value !in RosterGrouping.choicesForScope(teamScope) || value == grouping) {
            return this
        }
        return when (value) {
            RosterGrouping.NONE -> copy(grouping = value, revealGroups = false)
            RosterGrouping.SQUAD ->
                copy(
                    grouping = value,
                    expandedSquads = setOf(PlayerSquad.FIRST, PlayerSquad.SECOND),
                    revealGroups = true,
                )
            RosterGrouping.POSITION ->
                copy(
                    grouping = value,
                    expandedPositions = PlayerPosition.entries.toSet(),
                    revealGroups = true,
                )
        }
    }

    fun toggleSquadExpanded(squad: PlayerSquad): PlayerRosterOptions =
        copy(
            expandedSquads =
                if (squad in expandedSquads) {
                    expandedSquads - squad
                } else {
                    expandedSquads + squad
                },
        )

    fun togglePositionExpanded(position: PlayerPosition): PlayerRosterOptions =
        copy(
            expandedPositions =
                if (position in expandedPositions) {
                    expandedPositions - position
                } else {
                    expandedPositions + position
                },
        )

    fun clearRevealGroups(): PlayerRosterOptions = copy(revealGroups = false)

    fun selectTeamScope(value: RosterTeamScope): PlayerRosterOptions = copy(teamScope = value).coercedGrouping()

    fun coercedGrouping(): PlayerRosterOptions =
        if (!teamScope.isSingleDomesticTeam && grouping == RosterGrouping.SQUAD) {
            copy(grouping = RosterGrouping.NONE, revealGroups = false)
        } else {
            this
        }

    companion object {
        fun forTeam(team: Int): PlayerRosterOptions =
            PlayerRosterOptions(
                teamScope = RosterTeamScope.Team(team),
                grouping = RosterGrouping.SQUAD,
            )
    }

    fun sort(key: String): PlayerRosterOptions =
        updateDisplay {
            it.copy(sortKey = key, descending = if (it.sortKey == key) !it.descending else true)
        }

    fun selectPosition(value: RosterPositionFilter?): PlayerRosterOptions =
        copy(
            positionFilter = value,
            display = if (automaticDisplaySwitch && value != null) display.forPosition(value) else display,
        )

    fun selectDisplay(value: RosterDisplay): PlayerRosterOptions {
        if (value == display) {
            return this
        }
        return copy(
            display = value,
            positionFilter = if (automaticDisplaySwitch) value.positionFor(positionFilter) else positionFilter,
        )
    }

    fun toggleAutomaticDisplaySwitch(): PlayerRosterOptions =
        copy(automaticDisplaySwitch = !automaticDisplaySwitch).let { next ->
            if (next.automaticDisplaySwitch) next.selectPosition(next.positionFilter) else next
        }

    fun players(roster: PlayerRoster): List<PlayerRosterPlayer> {
        val query = search.trim()
        val filtered =
            roster.players.filter { player ->
                teamScope.matches(player) &&
                    domesticFiltersMatch(player) &&
                    (positionFilter == null || positionFilter.matches(player)) &&
                    (
                        query.isEmpty() ||
                            player.name.contains(query, ignoreCase = true) ||
                            player.playerId.toString().contains(query) ||
                            player.uniformNumber.toString().contains(query)
                    )
            }
        return filtered.sortedWith { a, b ->
            val x = a.cell(display, current.sortKey, statsSquad)
            val y = b.cell(display, current.sortKey, statsSquad)
            val missingX = x.sortStatus != PlayerCellSortStatus.VALUE
            val missingY = y.sortStatus != PlayerCellSortStatus.VALUE
            val comparison =
                if (missingX != missingY) {
                    if (missingX) 1 else -1
                } else {
                    val value = if (x.number != null && y.number != null) x.number.compareTo(y.number) else x.text.compareTo(y.text)
                    if (current.descending) -value else value
                }
            if (comparison == 0) a.playerId.compareTo(b.playerId) else comparison
        }
    }

    private fun domesticFiltersMatch(player: PlayerRosterPlayer): Boolean =
        when {
            teamScope.isMajorOnly -> true
            player.major -> squad == null && registration == null
            else -> (squad == null || player.squad == squad) && (registration == null || player.registration == registration)
        }
}

private fun RosterDisplay.forPosition(position: RosterPositionFilter): RosterDisplay {
    val pitcher = position == RosterPositionFilter.MainPosition(PlayerPosition.PITCHER)
    return when (this) {
        RosterDisplay.BASIC -> RosterDisplay.BASIC
        RosterDisplay.BATTING_ABILITY, RosterDisplay.PITCHING_ABILITY ->
            if (pitcher) RosterDisplay.PITCHING_ABILITY else RosterDisplay.BATTING_ABILITY
        RosterDisplay.BATTING_STATS, RosterDisplay.PITCHING_STATS ->
            if (pitcher) RosterDisplay.PITCHING_STATS else RosterDisplay.BATTING_STATS
    }
}

private fun RosterDisplay.positionFor(current: RosterPositionFilter?): RosterPositionFilter? =
    when (this) {
        RosterDisplay.BASIC -> current
        RosterDisplay.PITCHING_ABILITY, RosterDisplay.PITCHING_STATS ->
            RosterPositionFilter.MainPosition(PlayerPosition.PITCHER)
        RosterDisplay.BATTING_ABILITY, RosterDisplay.BATTING_STATS ->
            when (current) {
                RosterPositionFilter.Fielders -> current
                is RosterPositionFilter.MainPosition ->
                    if (current.position == PlayerPosition.PITCHER) RosterPositionFilter.Fielders else current
                null -> RosterPositionFilter.Fielders
            }
    }
