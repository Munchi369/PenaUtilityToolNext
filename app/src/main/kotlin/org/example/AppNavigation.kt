package org.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.team.TeamIdentity
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun AppNavigation(
    destination: AppDestination,
    playersEnabled: Boolean,
    team: TeamIdentity?,
    teamStatus: String,
    onPlayers: () -> Unit,
    onSettings: () -> Unit,
) {
    val teamColor = team?.let { Color(0xFF000000L or it.rgb.toLong()) }
    val foreground = MaterialTheme.colorScheme.onSurface
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.width(232.dp).fillMaxHeight(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 16.dp)) {
            TextButton(
                onClick = onSettings,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = foreground),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "設定を開く" },
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Spacer(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(teamColor ?: MaterialTheme.colorScheme.outline),
                    )
                    Text(
                        team?.name ?: teamStatus,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (team == null) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    NavigationGlyph(settings = true, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = onPlayers,
                enabled = playersEnabled,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors =
                    ButtonDefaults.textButtonColors(
                        containerColor =
                            if (destination == AppDestination.PLAYERS) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        contentColor = foreground,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NavigationGlyph(settings = false, color = androidx.compose.material3.LocalContentColor.current)
                    Text("現役選手一覧", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun NavigationGlyph(
    settings: Boolean,
    color: Color,
) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.4.dp.toPx()
        if (settings) {
            drawCircle(color, radius = size.width * 0.29f, style = Stroke(stroke))
            drawCircle(color, radius = size.width * 0.1f, style = Stroke(stroke))
            repeat(8) { index ->
                val angle = index * Math.PI / 4
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(color, center + direction * size.width * 0.29f, center + direction * size.width * 0.43f, stroke)
            }
        } else {
            repeat(3) { index ->
                val y = size.height * (0.25f + index * 0.25f)
                drawCircle(color, radius = stroke / 2, center = Offset(size.width * 0.15f, y))
                drawLine(color, Offset(size.width * 0.35f, y), Offset(size.width * 0.9f, y), stroke)
            }
        }
    }
}
