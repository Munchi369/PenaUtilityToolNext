package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun CalendarPlayerText(
    data: HomeData,
    playerId: Int?,
    text: String,
    onOpenPlayer: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val availableId = playerId?.takeIf { it > 0 && it in data.players }
    Text(
        text,
        modifier.then(
            if (availableId != null) {
                Modifier.clickable(role = Role.Button, onClickLabel = "${data.playerName(availableId)}の詳細を開く") {
                    onOpenPlayer(availableId)
                }
            } else {
                Modifier
            },
        ),
        style = style ?: MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
