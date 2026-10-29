package io.github.munchi369.penautilitytoolnext.lineup

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
import io.github.munchi369.penautilitytoolnext.team.TeamIdentity

@Composable
internal fun LineupOverview(
    teamNumber: Int,
    team: TeamIdentity?,
    counts: RosterCounts,
    contracts: ContractCounts,
    notices: List<String>,
) {
    val surface = MaterialTheme.colorScheme.surface
    val source = team?.rgb?.let { Color(0xFF000000L or it.toLong()) }
    val tint = source?.copy(alpha = 0.07f)?.compositeOver(surface) ?: surface
    val metrics =
        listOf(
            OverviewMetric("1軍", "${counts.total}", "/${LineupRules.FIRST_TEAM_MAX}"),
            OverviewMetric("投手", "${counts.pitchers}"),
            OverviewMetric("野手", "${counts.fielders}"),
            OverviewMetric("外国人", "${counts.foreignTotal}", "/${LineupRules.FOREIGN_TOTAL_MAX}"),
            OverviewMetric("支配下", "${contracts.controlled}", "/70"),
            OverviewMetric("育成", "${contracts.development}", "/99"),
        )
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = surface,
        border = BorderStroke(1.dp, Color(0xFFE8E8ED)),
    ) {
        BoxWithConstraints {
            val wide = maxWidth >= 760.dp
            val singleMetricRow = maxWidth >= 600.dp
            Column {
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth().padding(end = 32.dp).height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamName(teamNumber, team?.name, tint, Modifier.weight(1.5f).padding(12.dp))
                        OverviewDivider()
                        Metrics(metrics, Modifier.weight(5.5f))
                    }
                } else {
                    TeamName(
                        teamNumber,
                        team?.name,
                        tint,
                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 44.dp, bottom = 12.dp),
                    )
                    HorizontalDivider()
                    if (singleMetricRow) {
                        Metrics(metrics, Modifier.fillMaxWidth())
                    } else {
                        metrics.chunked(3).forEach { Metrics(it, Modifier.fillMaxWidth()) }
                    }
                }
                if (notices.isNotEmpty()) {
                    Text(
                        notices.joinToString(" · "),
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamName(
    teamNumber: Int,
    name: String?,
    tint: Color,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (teamNumber <= 6) "1stリーグ" else "2ndリーグ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name ?: "球団$teamNumber",
            Modifier.background(tint, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class OverviewMetric(
    val label: String,
    val value: String,
    val suffix: String = "",
)

@Composable
private fun Metrics(
    metrics: List<OverviewMetric>,
    modifier: Modifier,
) {
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        metrics.forEachIndexed { index, metric ->
            if (index > 0) OverviewDivider()
            Metric(metric)
        }
    }
}

@Composable
private fun RowScope.Metric(metric: OverviewMetric) {
    Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            buildAnnotatedString {
                append(metric.value)
                withStyle(
                    SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    append(metric.suffix)
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun OverviewDivider() {
    VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 12.dp))
}
