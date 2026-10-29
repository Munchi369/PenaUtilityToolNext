package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.game.GameTeams
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.ui.PlayerCellSortStatus

data class RosterDisplayOptions(
    val sortKey: String,
    val descending: Boolean = true,
    val visibleColumns: Set<String>,
    val columnOrder: List<String> = emptyList(),
    val columnWidths: Map<String, Float> = emptyMap(),
)

sealed interface RosterPositionFilter {
    val label: String
    val pitching: Boolean get() = false

    data object Fielders : RosterPositionFilter {
        override val label: String = "野手"
    }

    data class MainPosition(
        val position: PlayerPosition,
    ) : RosterPositionFilter {
        override val label: String = position.displayName
        override val pitching: Boolean get() = position == PlayerPosition.PITCHER
    }

    data object Starters : RosterPositionFilter {
        override val label: String = "先発"
        override val pitching: Boolean = true
    }

    data object Relievers : RosterPositionFilter {
        override val label: String = "中継ぎ"
        override val pitching: Boolean = true
    }

    fun matches(player: PlayerRosterPlayer): Boolean =
        when (this) {
            Fielders -> player.position != PlayerPosition.PITCHER
            is MainPosition -> player.position == position
            Starters -> player.starterType() == true
            Relievers -> player.starterType() == false
        }
}

fun RosterPositionFilter.storedValue(): String =
    when (this) {
        RosterPositionFilter.Fielders -> "FIELDERS"
        RosterPositionFilter.Starters -> "STARTERS"
        RosterPositionFilter.Relievers -> "RELIEVERS"
        is RosterPositionFilter.MainPosition -> position.name
    }

fun String.toRosterPositionFilter(): RosterPositionFilter? =
    when (this) {
        "FIELDERS" -> RosterPositionFilter.Fielders
        "STARTERS" -> RosterPositionFilter.Starters
        "RELIEVERS" -> RosterPositionFilter.Relievers
        else ->
            removePrefix("POSITION:")
                .let { runCatching { PlayerPosition.valueOf(it) }.getOrNull() }
                ?.let(RosterPositionFilter::MainPosition)
    }

private fun PlayerRosterPlayer.starterType(): Boolean? {
    if (position != PlayerPosition.PITCHER) return null
    val starter = information["SENPATUTEKISEI"]?.number ?: return null
    val reliever = information["NAKATUGITEKISEI"]?.number ?: return null
    return starter >= reliever
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
            require(number in GameTeams.domestic) { "国内球団番号は1から12である必要があります: $number" }
        }
    }

    val isMajorOnly: Boolean get() = this == Major
    val isSingleDomesticTeam: Boolean get() = this is Team
    val showsTeamColumn: Boolean get() = this == All || this == AllDomestic || this == FirstLeague || this == SecondLeague
    val domesticTeamNumbers: IntRange
        get() =
            when (this) {
                All, AllDomestic -> GameTeams.domestic
                FirstLeague -> GameTeams.firstLeague
                SecondLeague -> GameTeams.secondLeague
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

    fun orderedColumns(): List<RosterColumn> {
        val columns = RosterColumns.forDisplay(display).associateBy { it.key }
        val order = current.columnOrder.toMutableList()
        if (order.isNotEmpty() && "NUMBER" !in order) {
            order.add(order.indexOf("ID").coerceAtLeast(0), "NUMBER")
        }
        return (order + columns.keys).distinct().mapNotNull(columns::get)
    }

    fun hasCustomColumns(): Boolean {
        val available = RosterColumns.forDisplay(display).filterNot { teamScope.isMajorOnly && it.key == "ITIGUN" }
        val visible = orderedColumns().filter { column -> column in available && column.key in current.visibleColumns }
        val shownKeys = visible.map { it.key } + listOfNotNull("NAME", "TEAM".takeIf { teamScope.showsTeamColumn })
        return visible != available.filter { it.standard } || current.columnWidths.keys.any { it in shownKeys }
    }

    fun resizeColumns(widths: Map<String, Float>): PlayerRosterOptions =
        updateDisplay { it.copy(columnWidths = validRosterColumnWidths(display, widths)) }

    fun moveColumn(
        key: String,
        target: String,
    ): PlayerRosterOptions {
        val keys = orderedColumns().map { it.key }.toMutableList()
        val from = keys.indexOf(key)
        val to = keys.indexOf(target)
        if (from < 0 || to < 0 || from == to) return this
        keys.removeAt(from)
        keys.add(to, key)
        return updateDisplay { it.copy(columnOrder = keys) }
    }

    fun hideColumn(key: String): PlayerRosterOptions =
        if (RosterColumns.forDisplay(display).none { it.key == key }) {
            this
        } else {
            updateDisplay { it.copy(visibleColumns = it.visibleColumns - key) }
        }

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
        val qualification = rateQualification(roster)
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
            if (qualification != null) {
                val groupOrder = qualification.status(a).compareTo(qualification.status(b))
                if (groupOrder != 0) return@sortedWith groupOrder
            }
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

internal fun rosterColumnMinWidth(key: String): Int =
    when (key) {
        "NAME" -> 96
        else -> 48
    }

internal fun validRosterColumnWidths(
    display: RosterDisplay,
    widths: Map<String, Float>,
): Map<String, Float> {
    val defaults = RosterColumns.forDisplay(display).associate { it.key to it.width.toFloat() } + ("NAME" to 176f)
    val known = defaults.keys + "TEAM"
    return widths.filter { (key, width) ->
        key in known && width.isFinite() && width in rosterColumnMinWidth(key).toFloat()..1000f && width != defaults[key]
    }
}

private fun RosterDisplay.forPosition(position: RosterPositionFilter): RosterDisplay {
    val pitcher = position.pitching
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
            if (current?.pitching == true) current else RosterPositionFilter.MainPosition(PlayerPosition.PITCHER)
        RosterDisplay.BATTING_ABILITY, RosterDisplay.BATTING_STATS ->
            if (current == null || current.pitching) RosterPositionFilter.Fielders else current
    }
