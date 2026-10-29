package io.github.munchi369.penautilitytoolnext.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.munchi369.penautilitytoolnext.home.HomeCard

@Composable
internal fun PlayerDetailOverview(
    name: String,
    teamName: String,
    number: Int?,
    position: String?,
    age: Int?,
    playerId: Int?,
    teamColor: Int?,
) {
    val surface = MaterialTheme.colorScheme.surface
    val source = teamColor?.let { Color(0xFF000000L or it.toLong()) } ?: MaterialTheme.colorScheme.outline
    val tint = source.copy(alpha = 0.07f).compositeOver(surface)
    HomeCard(Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            if (maxWidth >= 700.dp) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    PlayerIdentity(name, teamName, number, tint, Modifier.weight(2.2f).padding(12.dp))
                    OverviewDivider()
                    PlayerFacts(position, age, playerId, Modifier.weight(2.2f))
                }
            } else {
                Column {
                    PlayerIdentity(name, teamName, number, tint, Modifier.padding(12.dp))
                    HorizontalDivider()
                    PlayerFacts(position, age, playerId, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PlayerIdentity(
    name: String,
    teamName: String,
    number: Int?,
    tint: Color,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("所属球団", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                teamName.ifBlank { "所属球団不明" },
                Modifier.background(tint, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                number?.let { "#$it" } ?: "#—",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerFacts(
    position: String?,
    age: Int?,
    playerId: Int?,
    modifier: Modifier,
) {
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Fact("ポジション", position ?: "—")
        OverviewDivider()
        Fact("年齢", age?.toString() ?: "—", "歳")
        OverviewDivider()
        Fact("選手ID", playerId?.toString() ?: "—")
    }
}

@Composable
private fun RowScope.Fact(
    label: String,
    value: String,
    suffix: String = "",
) {
    Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            buildAnnotatedString {
                append(value)
                withStyle(
                    SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    append(suffix)
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverviewDivider() {
    VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 12.dp))
}
