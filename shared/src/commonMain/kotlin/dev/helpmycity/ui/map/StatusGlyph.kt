package dev.helpmycity.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import dev.helpmycity.domain.model.IssueStatus

/**
 * A white mark per status, drawn over the status color so markers stay
 * tellable apart without color vision: an ellipsis for in review, `!` for
 * open, a half-filled ring for in progress, a check for complete and a cross
 * for rejected.
 */
internal class StatusGlyphPainter(private val status: IssueStatus) : Painter() {

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(GLYPH_COLOR, at(x1, y1), at(x2, y2), stroke.width, StrokeCap.Round)

        when (status) {
            IssueStatus.IN_REVIEW -> listOf(0.2f, 0.5f, 0.8f).forEach {
                drawCircle(GLYPH_COLOR, radius = s * 0.1f, center = at(it, 0.5f))
            }

            IssueStatus.OPEN -> {
                line(0.5f, 0.15f, 0.5f, 0.55f)
                drawCircle(GLYPH_COLOR, radius = s * 0.09f, center = at(0.5f, 0.85f))
            }

            IssueStatus.IN_PROGRESS -> {
                val inset = at(0.15f, 0.15f)
                val box = Size(s * 0.7f, s * 0.7f)
                drawArc(GLYPH_COLOR, 90f, 180f, useCenter = true, topLeft = inset, size = box)
                drawCircle(
                    GLYPH_COLOR,
                    radius = s * 0.35f,
                    center = at(0.5f, 0.5f),
                    style = Stroke(width = s * 0.1f),
                )
            }

            IssueStatus.COMPLETE -> drawPath(
                Path().apply {
                    moveTo(0.15f * s, 0.52f * s)
                    lineTo(0.4f * s, 0.77f * s)
                    lineTo(0.85f * s, 0.27f * s)
                },
                GLYPH_COLOR,
                style = stroke,
            )

            IssueStatus.REJECTED -> {
                line(0.22f, 0.22f, 0.78f, 0.78f)
                line(0.78f, 0.22f, 0.22f, 0.78f)
            }
        }
    }

    private companion object {
        val GLYPH_COLOR = Color.White
    }
}
