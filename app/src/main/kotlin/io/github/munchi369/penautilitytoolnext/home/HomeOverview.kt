package io.github.munchi369.penautilitytoolnext.home

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun HomeOverview(data: HomeData) {
    HomeCard(Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            if (maxWidth >= 700.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(end = 32.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamOverview(data, Modifier.weight(1.8f).padding(12.dp))
                    OverviewDivider()
                    OverviewValues(data)
                }
            } else {
                Column(Modifier.padding(end = 32.dp)) {
                    TeamOverview(data, Modifier.padding(12.dp))
                    HorizontalDivider()
                    Row(Modifier.height(IntrinsicSize.Min)) { OverviewValues(data) }
                }
            }
        }
    }
}

@Composable
private fun TeamOverview(
    data: HomeData,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            leagueName(if (data.team <= 6) 0 else 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.background(data.teamTint(), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.teamName(data.team),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RowScope.OverviewValues(data: HomeData) {
    OverviewValue("年度", listOfNotNull(data.year?.let { "$it" to "年 / " }, "${data.season}" to "年目"), 1.5f)
    OverviewDivider()
    OverviewValue("日付", data.today?.let { listOf("${it.month}" to "月 ", "${it.date}" to "日") } ?: listOf("—" to ""), 1.1f)
    OverviewDivider()
    OverviewValue("所持金", listOf((data.money?.let { String.format(Locale.JAPAN, "%,d", it) } ?: "—") to "万円"), 1.3f)
    OverviewDivider()
    OverviewValue("残り試合", listOf("${data.remaining}" to "試合"), 1f)
}

@Composable
private fun OverviewDivider() {
    VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 12.dp))
}

@Composable
private fun RowScope.OverviewValue(
    label: String,
    parts: List<Pair<String, String>>,
    weight: Float,
) {
    Column(Modifier.weight(weight).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            buildAnnotatedString {
                parts.forEach { (number, unit) ->
                    append(number)
                    withStyle(
                        SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) {
                        append(unit)
                    }
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
