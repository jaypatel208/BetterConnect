package dev.jay.betterconnect.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jay.betterconnect.core.designsystem.theme.StatusColors

/** The families of arrow the cluster can draw, derived from the symbol byte. */
enum class GlyphShape {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    KEEP_LEFT,
    KEEP_RIGHT,
    U_TURN_LEFT,
    U_TURN_RIGHT,
    ROUNDABOUT,
    RAMP_LEFT,
    RAMP_RIGHT,
    ARRIVE,
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps a raw symbol byte to a shape. Codes outside the documented set render as
         * [UNKNOWN], which is the honest answer during a sweep - guessing would defeat
         * the purpose of running one.
         */
        fun forCode(code: Int): GlyphShape = when (code.toChar().uppercaseChar()) {
            'I' -> LEFT
            'J' -> RIGHT
            'E' -> SHARP_LEFT
            'F' -> SHARP_RIGHT
            'Z' -> SLIGHT_LEFT
            'X' -> SLIGHT_RIGHT
            'Q', 'C' -> KEEP_LEFT
            'R', 'D' -> KEEP_RIGHT
            'G' -> STRAIGHT
            'H' -> ARRIVE
            'P' -> U_TURN_LEFT
            'O' -> U_TURN_RIGHT
            'U', 'N', 'B' -> ROUNDABOUT
            'K' -> RAMP_LEFT
            'L' -> RAMP_RIGHT
            else -> UNKNOWN
        }
    }
}

/**
 * Draws the manoeuvre. Vector paths rather than icon assets, so the shapes stay legible
 * at cluster-panel size and no shape silently falls back to a generic arrow.
 */
@Composable
fun ManeuverGlyph(
    symbolCode: Int,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = StatusColors.ClusterInk,
) {
    val shape = GlyphShape.forCode(symbolCode)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (shape == GlyphShape.UNKNOWN) {
            // No arrow exists for this code - show the byte itself so a sweep can record it.
            Text(
                text = symbolCode.toChar().toString(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                ),
                color = color,
            )
        } else {
            Canvas(Modifier.size(size)) { drawGlyph(shape, color) }
        }
    }
}

private fun DrawScope.drawGlyph(shape: GlyphShape, color: Color) {
    val w = this.size.width
    val stroke = Stroke(width = w * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round)

    when (shape) {
        GlyphShape.STRAIGHT -> drawArrow(color, stroke, angleDeg = 0f)
        GlyphShape.SLIGHT_LEFT -> drawArrow(color, stroke, angleDeg = -30f)
        GlyphShape.SLIGHT_RIGHT -> drawArrow(color, stroke, angleDeg = 30f)
        GlyphShape.LEFT -> drawTurn(color, stroke, mirrored = false, sharp = false)
        GlyphShape.RIGHT -> drawTurn(color, stroke, mirrored = true, sharp = false)
        GlyphShape.SHARP_LEFT -> drawTurn(color, stroke, mirrored = false, sharp = true)
        GlyphShape.SHARP_RIGHT -> drawTurn(color, stroke, mirrored = true, sharp = true)
        GlyphShape.KEEP_LEFT -> drawFork(color, stroke, mirrored = false)
        GlyphShape.KEEP_RIGHT -> drawFork(color, stroke, mirrored = true)
        GlyphShape.RAMP_LEFT -> drawRamp(color, stroke, mirrored = false)
        GlyphShape.RAMP_RIGHT -> drawRamp(color, stroke, mirrored = true)
        GlyphShape.U_TURN_LEFT -> drawUTurn(color, stroke, mirrored = false)
        GlyphShape.U_TURN_RIGHT -> drawUTurn(color, stroke, mirrored = true)
        GlyphShape.ROUNDABOUT -> drawRoundabout(color, stroke)
        GlyphShape.ARRIVE -> drawArrive(color, stroke)
        GlyphShape.UNKNOWN -> Unit
    }
}

private fun DrawScope.drawArrow(color: Color, stroke: Stroke, angleDeg: Float) {
    val w = size.width
    rotate(angleDeg, pivot = Offset(w / 2f, w * 0.72f)) {
        drawLine(color, Offset(w / 2f, w * 0.85f), Offset(w / 2f, w * 0.22f), stroke.width, stroke.cap)
        drawPath(headPath(w / 2f, w * 0.15f, w * 0.20f), color, style = stroke)
    }
}

private fun DrawScope.headPath(tipX: Float, tipY: Float, spread: Float): Path = Path().apply {
    moveTo(tipX - spread, tipY + spread)
    lineTo(tipX, tipY)
    lineTo(tipX + spread, tipY + spread)
}

private fun DrawScope.drawTurn(color: Color, stroke: Stroke, mirrored: Boolean, sharp: Boolean) {
    val w = size.width
    val sign = if (mirrored) 1f else -1f
    val stemX = w / 2f
    val cornerY = if (sharp) w * 0.52f else w * 0.42f
    val tipX = stemX + sign * w * 0.30f
    val tipY = if (sharp) w * 0.68f else cornerY

    val path = Path().apply {
        moveTo(stemX, w * 0.88f)
        lineTo(stemX, cornerY)
        if (sharp) {
            lineTo(tipX, tipY)
        } else {
            quadraticTo(stemX, cornerY - w * 0.08f, stemX + sign * w * 0.12f, cornerY - w * 0.10f)
            lineTo(tipX, cornerY - w * 0.10f)
        }
    }
    drawPath(path, color, style = stroke)

    val headY = if (sharp) tipY else cornerY - w * 0.10f
    val head = Path().apply {
        val s = w * 0.16f
        if (sharp) {
            moveTo(tipX - sign * s * 0.2f, headY - s)
            lineTo(tipX + sign * s * 0.4f, headY + s * 0.4f)
            lineTo(tipX - sign * s, headY + s * 0.7f)
        } else {
            moveTo(tipX - sign * s, headY - s)
            lineTo(tipX + sign * s * 0.35f, headY)
            lineTo(tipX - sign * s, headY + s)
        }
    }
    drawPath(head, color, style = stroke)
}

private fun DrawScope.drawFork(color: Color, stroke: Stroke, mirrored: Boolean) {
    val w = size.width
    val sign = if (mirrored) 1f else -1f
    drawLine(color, Offset(w / 2f, w * 0.88f), Offset(w / 2f, w * 0.55f), stroke.width, stroke.cap)

    // Taken branch
    val taken = Path().apply {
        moveTo(w / 2f, w * 0.55f)
        quadraticTo(w / 2f, w * 0.36f, w / 2f + sign * w * 0.20f, w * 0.22f)
    }
    drawPath(taken, color, style = stroke)
    val tipX = w / 2f + sign * w * 0.20f
    drawPath(
        Path().apply {
            val s = w * 0.14f
            moveTo(tipX - sign * s * 1.2f, w * 0.22f + s * 0.2f)
            lineTo(tipX, w * 0.14f)
            lineTo(tipX + sign * s * 0.4f, w * 0.30f)
        },
        color,
        style = stroke,
    )

    // Ignored branch, drawn faint so the choice reads instantly
    val other = Path().apply {
        moveTo(w / 2f, w * 0.55f)
        quadraticTo(w / 2f, w * 0.38f, w / 2f - sign * w * 0.20f, w * 0.26f)
    }
    drawPath(other, color.copy(alpha = 0.28f), style = stroke)
}

private fun DrawScope.drawRamp(color: Color, stroke: Stroke, mirrored: Boolean) {
    val w = size.width
    val sign = if (mirrored) 1f else -1f
    drawLine(
        color.copy(alpha = 0.28f),
        Offset(w / 2f, w * 0.88f),
        Offset(w / 2f, w * 0.16f),
        stroke.width,
        stroke.cap,
    )
    val path = Path().apply {
        moveTo(w / 2f, w * 0.82f)
        quadraticTo(w / 2f + sign * w * 0.06f, w * 0.50f, w / 2f + sign * w * 0.26f, w * 0.24f)
    }
    drawPath(path, color, style = stroke)
    val tipX = w / 2f + sign * w * 0.26f
    drawPath(
        Path().apply {
            val s = w * 0.14f
            moveTo(tipX - sign * s * 1.1f, w * 0.26f)
            lineTo(tipX, w * 0.16f)
            lineTo(tipX + sign * s * 0.5f, w * 0.32f)
        },
        color,
        style = stroke,
    )
}

private fun DrawScope.drawUTurn(color: Color, stroke: Stroke, mirrored: Boolean) {
    val w = size.width
    val sign = if (mirrored) 1f else -1f
    val path = Path().apply {
        moveTo(w / 2f + sign * w * 0.18f, w * 0.88f)
        lineTo(w / 2f + sign * w * 0.18f, w * 0.44f)
        quadraticTo(
            w / 2f + sign * w * 0.18f,
            w * 0.18f,
            w / 2f - sign * w * 0.18f,
            w * 0.18f,
        )
        quadraticTo(
            w / 2f - sign * w * 0.18f,
            w * 0.18f,
            w / 2f - sign * w * 0.18f,
            w * 0.40f,
        )
    }
    drawPath(path, color, style = stroke)
    val tipX = w / 2f - sign * w * 0.18f
    drawPath(
        Path().apply {
            val s = w * 0.14f
            moveTo(tipX - s, w * 0.36f)
            lineTo(tipX, w * 0.50f)
            lineTo(tipX + s, w * 0.36f)
        },
        color,
        style = stroke,
    )
}

private fun DrawScope.drawRoundabout(color: Color, stroke: Stroke) {
    val w = size.width
    val radius = w * 0.20f
    val centre = Offset(w / 2f, w * 0.42f)

    drawCircle(color, radius = radius, center = centre, style = stroke)
    drawLine(color, Offset(w / 2f, w * 0.88f), Offset(w / 2f, centre.y + radius), stroke.width, stroke.cap)

    // Exit arrow leaving to the right of the circle
    drawLine(
        color,
        Offset(centre.x + radius, centre.y),
        Offset(centre.x + radius + w * 0.18f, centre.y),
        stroke.width,
        stroke.cap,
    )
    val tipX = centre.x + radius + w * 0.18f
    drawPath(
        Path().apply {
            val s = w * 0.11f
            moveTo(tipX - s, centre.y - s)
            lineTo(tipX + s * 0.4f, centre.y)
            lineTo(tipX - s, centre.y + s)
        },
        color,
        style = stroke,
    )
}

private fun DrawScope.drawArrive(color: Color, stroke: Stroke) {
    val w = size.width
    val centre = Offset(w / 2f, w * 0.40f)
    drawCircle(color, radius = w * 0.22f, center = centre, style = stroke)
    drawCircle(color, radius = w * 0.07f, center = centre)
    drawLine(
        color,
        Offset(w / 2f, w * 0.88f),
        Offset(w / 2f, centre.y + w * 0.22f),
        stroke.width,
        stroke.cap,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.06f, w * 0.06f)),
    )
}

@Suppress("unused")
private fun Rect.unusedGuard(): Size = size
