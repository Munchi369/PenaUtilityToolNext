package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataLoadPhase
import io.github.munchi369.penautilitytoolnext.ui.PlayerCell
import io.github.munchi369.penautilitytoolnext.ui.TechnicalDetails
import io.github.munchi369.penautilitytoolnext.ui.gameAbilityColor

@Composable
fun PlayerRosterScreen(
    state: PlayerRosterViewState,
    options: PlayerRosterOptions,
    onOptionsChange: (PlayerRosterOptions) -> Unit,
    onOpenSettings: () -> Unit,
    settingsNotice: PlayerRosterSettingsNotice? = null,
    onDismissSettingsNotice: () -> Unit = {},
    onOpenPlayer: (PlayerRosterPlayer) -> Unit = {},
) {
    when (state.phase) {
        SaveDataLoadPhase.IDLE, SaveDataLoadPhase.LOADING ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(state.message)
                }
            }
        SaveDataLoadPhase.LOADED ->
            state.roster?.let {
                LoadedRoster(it, options, onOptionsChange, settingsNotice, onDismissSettingsNotice, onOpenPlayer)
            }
        else ->
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.message, style = MaterialTheme.typography.titleLarge)
                Text("再読み込みするか、セーブデータの設定を確認してください。")
                TextButton(onClick = onOpenSettings) { Text("設定を開く") }
                state.technicalDetails?.let { TechnicalDetails(it) }
            }
    }
}

@Composable
private fun LoadedRoster(
    roster: PlayerRoster,
    options: PlayerRosterOptions,
    onChange: (PlayerRosterOptions) -> Unit,
    settingsNotice: PlayerRosterSettingsNotice?,
    onDismissSettingsNotice: () -> Unit,
    onOpenPlayer: (PlayerRosterPlayer) -> Unit,
) {
    val players = remember(roster, options) { options.players(roster) }
    val availableColumns =
        options.orderedColumns().filterNot { options.teamScope.isMajorOnly && it.key == "ITIGUN" }
    val columns = availableColumns.filter { it.key in options.current.visibleColumns }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        settingsNotice?.let { RosterSettingsNoticeBanner(it, onDismissSettingsNotice) }
        RosterToolbar(roster, players.size, options, availableColumns, onChange)
        if (
            options.teamScope != RosterTeamScope.All ||
            options.positionFilter != null ||
            (!options.teamScope.isMajorOnly && (options.squad != null || options.registration != null))
        ) {
            RosterFilterChips(roster, options, onChange, Modifier.fillMaxWidth())
        }
        RosterTable(
            players,
            roster.teamColors,
            columns,
            options,
            options.rateQualification(roster),
            onChange,
            Modifier.weight(1f),
            onOpenPlayer,
        )
    }
}

@Composable
internal fun RosterSettingsNoticeBanner(
    notice: PlayerRosterSettingsNotice,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notice.message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    }
}

@Composable
internal fun HeaderCell(
    column: RosterColumn,
    options: PlayerRosterOptions,
    onSort: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable {
                onSort(column.key)
            }.padding(horizontal = 8.dp),
        contentAlignment = if (column.isNumeric(options.display)) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            column.title +
                if (options.current.sortKey ==
                    column.key
                ) {
                    if (options.current.descending) " ↓" else " ↑"
                } else {
                    ""
                },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun BodyCell(
    cell: PlayerCell,
    column: RosterColumn,
    frozen: Boolean,
) {
    val rankColor = cell.abilityStyle?.rank?.let(::gameAbilityColor)
    val color =
        when {
            column.key == "INJURY" && cell.text.startsWith("故障") -> Color(0xFFBF3333)
            column.key == "INJURY" && cell.text.startsWith("違和感") -> Color(0xFFAA6900)
            cell.abilityStyle?.colorStart == 0 && rankColor != null -> rankColor
            else -> MaterialTheme.colorScheme.onSurface
        }
    val text =
        buildAnnotatedString {
            append(cell.text)
            val start = cell.abilityStyle?.colorStart
            if (rankColor != null && start != null && start > 0) {
                addStyle(SpanStyle(color = rankColor), start, cell.text.length)
            }
        }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight =
            if (frozen &&
                column.key == "NAME"
            ) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
        color = color,
        textAlign =
            if (cell.number != null &&
                cell.abilityStyle == null &&
                column.key != "POSITION" &&
                column.key != "FA"
            ) {
                TextAlign.End
            } else {
                TextAlign.Start
            },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun RosterColumn.isNumeric(display: RosterDisplay): Boolean =
    key in setOf("NUMBER", "ID", "SOUGOU", "AGE", "HIROU", "READY", "MAXMAX") ||
        (display.isStats && key !in setOf("TEAM", "NAME", "POSITION"))
