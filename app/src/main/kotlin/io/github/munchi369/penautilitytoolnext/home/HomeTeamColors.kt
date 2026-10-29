package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun HomeData.teamAccent(team: Int = this.team): Color =
    readableTeamAccent(savedTeamColor(team), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)

@Composable
internal fun HomeData.teamTextColor(
    team: Int,
    background: Color,
): Color = readableTeamAccent(savedTeamColor(team), background, MaterialTheme.colorScheme.onSurface, minimumContrast = 4.5f)

@Composable
internal fun HomeData.teamTint(): Color {
    val source = savedTeamColor(team)?.let { Color(0xFF000000L or it.toLong()) } ?: MaterialTheme.colorScheme.outline
    return source.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface)
}

private fun HomeData.savedTeamColor(team: Int): Int? = if (team == this.team) color ?: teamColors[team] else teamColors[team]

/** Small marks remain visible even when the game saves white or yellow as the team color. */
internal fun readableTeamAccent(
    rgb: Int?,
    surface: Color,
    foreground: Color,
    minimumContrast: Float = 3f,
): Color {
    val source = rgb?.let { Color(0xFF000000L or it.toLong()) } ?: foreground
    val backgroundLuminance = surface.luminance()
    for (step in 0..20) {
        val candidate = lerp(source, foreground, step / 20f)
        val luminance = candidate.luminance()
        val contrast = (max(luminance, backgroundLuminance) + 0.05f) / (min(luminance, backgroundLuminance) + 0.05f)
        if (contrast >= minimumContrast) return candidate
    }
    return foreground
}
