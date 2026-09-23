package org.example.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin

private val awardGold = Color(0xFFC9A227)
private val awardGoldEdge = Color(0xFF8A6D08)
private val awardGreen = Color(0xFF2E7D32)
private val awardBrown = Color(0xFFC4A574)
private val awardBrownEdge = Color(0xFF8B5E3C)
private val awardSilver = Color(0xFF8A9096)
private val awardSilverEdge = Color(0xFF5C6166)
private val awardBronze = Color(0xFFB87333)
private val awardYellow = Color(0xFFF5C518)
private val awardStitch = Color(0xFFE53935)
private val awardBallEdge = Color(0xFF8D8D8D)
private val awardMono = Color(0xFF9A9A9A)
private val awardMonoEdge = Color(0xFF5C5C5C)
private val awardMonoFill = Color(0xFFD0D0D0)

@Composable
internal fun DetailAwardMarkIcon(
    mark: DetailAwardMark,
    monochrome: Boolean = false,
) {
    val iconSize =
        with(LocalDensity.current) {
            (MaterialTheme.typography.bodyLarge.fontSize * 0.88f)
                .toDp()
        }
    Canvas(Modifier.size(iconSize)) {
        when (mark) {
            DetailAwardMark.Crown -> drawCrown(monochrome)
            DetailAwardMark.Sprout -> drawSprout(monochrome)
            DetailAwardMark.Bat -> drawBat(monochrome)
            DetailAwardMark.Ball -> drawBall(monochrome)
            DetailAwardMark.Shield -> drawShield()
            DetailAwardMark.Glove -> drawGlove()
            DetailAwardMark.Medal -> drawMedal()
            DetailAwardMark.Star -> drawStar()
        }
    }
}

private fun DrawScope.drawCrown(monochrome: Boolean) {
    val fill = if (monochrome) awardMono else awardGold
    val width = size.width
    val height = size.height
    drawPath(
        Path().apply {
            moveTo(width * 0.08f, height * 0.72f)
            lineTo(width * 0.08f, height * 0.38f)
            lineTo(width * 0.28f, height * 0.56f)
            lineTo(width * 0.5f, height * 0.18f)
            lineTo(width * 0.72f, height * 0.56f)
            lineTo(width * 0.92f, height * 0.38f)
            lineTo(width * 0.92f, height * 0.72f)
            close()
        },
        fill,
    )
    drawRect(
        fill,
        topLeft = Offset(width * 0.08f, height * 0.78f),
        size = Size(width * 0.84f, height * 0.14f),
    )
}

private fun DrawScope.drawSprout(monochrome: Boolean) {
    val fill = if (monochrome) awardMono else awardGreen
    val width = size.width
    val height = size.height
    drawLine(
        fill,
        start = Offset(width * 0.5f, height * 0.92f),
        end = Offset(width * 0.5f, height * 0.42f),
        strokeWidth = width * 0.1f,
    )
    drawOval(
        fill,
        topLeft = Offset(width * 0.08f, height * 0.28f),
        size = Size(width * 0.4f, height * 0.28f),
    )
    drawOval(
        fill,
        topLeft = Offset(width * 0.52f, height * 0.12f),
        size = Size(width * 0.4f, height * 0.28f),
    )
}

private fun DrawScope.drawBat(monochrome: Boolean) {
    val fill = if (monochrome) awardMono else awardBrown
    val edge = if (monochrome) awardMonoEdge else awardBrownEdge
    val width = size.width
    val height = size.height
    // Draw a short horizontal bat first, then tilt. Length/thickness stay inside the square after rotate.
    rotate(degrees = 138f) {
        val body =
            Path().apply {
                // Barrel tip (left, rounded)
                moveTo(width * 0.14f, height * 0.50f)
                lineTo(width * 0.18f, height * 0.38f)
                lineTo(width * 0.34f, height * 0.36f)
                // Taper through shaft to handle
                lineTo(width * 0.62f, height * 0.44f)
                lineTo(width * 0.76f, height * 0.46f)
                // Knob
                lineTo(width * 0.84f, height * 0.42f)
                lineTo(width * 0.88f, height * 0.50f)
                lineTo(width * 0.84f, height * 0.58f)
                lineTo(width * 0.76f, height * 0.54f)
                // Underside back to barrel
                lineTo(width * 0.62f, height * 0.56f)
                lineTo(width * 0.34f, height * 0.64f)
                lineTo(width * 0.18f, height * 0.62f)
                close()
            }
        drawPath(body, fill)
        drawPath(body, edge, style = Stroke(width = width * 0.04f))
        drawLine(
            edge,
            start = Offset(width * 0.24f, height * 0.42f),
            end = Offset(width * 0.24f, height * 0.58f),
            strokeWidth = width * 0.028f,
        )
    }
}

private fun DrawScope.drawBall(monochrome: Boolean) {
    val fill = if (monochrome) awardMonoFill else Color.White
    val edge = if (monochrome) awardMonoEdge else awardBallEdge
    val stitch = if (monochrome) awardMonoEdge else awardStitch
    drawCircle(fill)
    drawCircle(edge, style = Stroke(width = size.width * 0.08f))
    val width = size.width
    val height = size.height
    for (side in listOf(-1f, 1f)) {
        drawPath(
            Path().apply {
                moveTo(width * (0.5f + side * 0.34f), height * 0.16f)
                quadraticBezierTo(
                    width * (0.5f + side * 0.02f),
                    height * 0.5f,
                    width * (0.5f + side * 0.34f),
                    height * 0.84f,
                )
            },
            stitch,
            style = Stroke(width = width * 0.055f),
        )
        for (step in 1..3) {
            val t = step / 4f
            val x = 0.5f + side * (0.34f - 0.64f * t * (1f - t))
            val y = 0.16f + 0.68f * t
            drawLine(
                stitch,
                start = Offset(width * (x - 0.065f), height * (y - side * 0.025f)),
                end = Offset(width * (x + 0.065f), height * (y + side * 0.025f)),
                strokeWidth = width * 0.045f,
            )
        }
    }
}

private fun DrawScope.drawShield() {
    val width = size.width
    val height = size.height
    val body =
        Path().apply {
            moveTo(width * 0.5f, height * 0.04f)
            lineTo(width * 0.86f, height * 0.14f)
            lineTo(width * 0.86f, height * 0.42f)
            quadraticBezierTo(width * 0.86f, height * 0.68f, width * 0.5f, height * 0.94f)
            quadraticBezierTo(width * 0.14f, height * 0.68f, width * 0.14f, height * 0.42f)
            lineTo(width * 0.14f, height * 0.14f)
            close()
        }
    drawPath(body, awardSilver)
    drawPath(body, awardSilverEdge, style = Stroke(width = width * 0.07f))
    drawLine(
        awardSilverEdge,
        start = Offset(width * 0.22f, height * 0.28f),
        end = Offset(width * 0.78f, height * 0.28f),
        strokeWidth = width * 0.05f,
    )
}

private fun DrawScope.drawGlove() {
    val width = size.width
    val height = size.height
    val palm =
        Path().apply {
            moveTo(width * 0.16f, height * 0.56f)
            quadraticBezierTo(width * 0.10f, height * 0.40f, width * 0.24f, height * 0.24f)
            lineTo(width * 0.34f, height * 0.10f)
            lineTo(width * 0.46f, height * 0.06f)
            lineTo(width * 0.58f, height * 0.08f)
            lineTo(width * 0.70f, height * 0.06f)
            lineTo(width * 0.82f, height * 0.12f)
            quadraticBezierTo(width * 0.90f, height * 0.34f, width * 0.78f, height * 0.56f)
            lineTo(width * 0.70f, height * 0.72f)
            lineTo(width * 0.30f, height * 0.72f)
            close()
        }
    drawPath(palm, awardGold)
    drawPath(palm, awardGoldEdge, style = Stroke(width = width * 0.06f))
    drawLine(
        awardGoldEdge,
        start = Offset(width * 0.34f, height * 0.20f),
        end = Offset(width * 0.62f, height * 0.46f),
        strokeWidth = width * 0.04f,
    )
    drawLine(
        awardGoldEdge,
        start = Offset(width * 0.42f, height * 0.16f),
        end = Offset(width * 0.52f, height * 0.50f),
        strokeWidth = width * 0.035f,
    )
    drawLine(
        awardGoldEdge,
        start = Offset(width * 0.48f, height * 0.08f),
        end = Offset(width * 0.48f, height * 0.34f),
        strokeWidth = width * 0.03f,
    )
}

private fun DrawScope.drawMedal() {
    val width = size.width
    val height = size.height
    drawRect(awardBronze, Offset(width * 0.3f, height * 0.02f), Size(width * 0.14f, height * 0.28f))
    drawRect(awardBronze, Offset(width * 0.56f, height * 0.02f), Size(width * 0.14f, height * 0.28f))
    drawCircle(awardBronze, radius = width * 0.32f, center = Offset(width * 0.5f, height * 0.64f))
}

private fun DrawScope.drawStar() {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outer = size.minDimension * 0.46f
    val inner = outer * 0.4f
    drawPath(
        Path().apply {
            for (index in 0 until 10) {
                val radius = if (index % 2 == 0) outer else inner
                val angle = Math.toRadians(-90.0 + index * 36.0)
                val x = centerX + (cos(angle) * radius).toFloat()
                val y = centerY + (sin(angle) * radius).toFloat()
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        },
        awardYellow,
    )
}
