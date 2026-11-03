package wrklabs.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.ui.theme.Palette

data class EvalPoint(
    val ply: Int,
    /** White's win chance, 0–100. */
    val winPercent: Double,
    val verdict: MoveClass? = null,
)

/** White's winning chances over the game: tap to jump to a move, or drag a cursor along it and jump where you let go. */
@Composable
fun EvalGraphView(points: List<EvalPoint>, currentPly: Int, onSeekPly: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val c = Palette.colors
    val first = points.first().ply
    val last = points.last().ply
    val span = (last - first).coerceAtLeast(1)
    val seek by rememberUpdatedState(onSeekPly)
    // While a finger is down this is the move under it; nothing navigates until it lifts.
    var scrub by remember { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.field)
            .pointerInput(points) { detectTapGestures { o -> seek(first + (o.x / size.width * span).toInt().coerceIn(0, span)) } }
            .pointerInput(points) {
                fun plyAt(x: Float) = first + (x / size.width * span).toInt().coerceIn(0, span)
                detectHorizontalDragGestures(
                    onDragStart = { scrub = plyAt(it.x) },
                    onDragEnd = { scrub?.let(seek); scrub = null },
                    onDragCancel = { scrub = null },
                    onHorizontalDrag = { change, _ -> change.consume(); scrub = plyAt(change.position.x) },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        fun x(ply: Int) = (ply - first).toFloat() / span * w
        fun y(win: Double) = h - (win / 100.0).toFloat() * h

        // White's share is the area under the line.
        val area = Path().apply {
            moveTo(x(points.first().ply), h)
            points.forEach { lineTo(x(it.ply), y(it.winPercent)) }
            lineTo(x(points.last().ply), h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(c.barLight.copy(alpha = 0.55f), c.barLight.copy(alpha = 0.18f))))
        drawLine(c.muted.copy(alpha = 0.35f), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.dp.toPx())

        val line = Path()
        points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.ply), y(p.winPercent)) else line.lineTo(x(p.ply), y(p.winPercent)) }
        drawPath(line, c.text.copy(alpha = 0.9f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        points.forEach { p ->
            val v = p.verdict
            if (v != null && v.isNotable) drawCircle(v.composeColor, 3.5.dp.toPx(), Offset(x(p.ply), y(p.winPercent)))
        }
        val live = scrub
        val cx = x((live ?: currentPly).coerceIn(first, last))
        drawLine(c.brand, Offset(cx, 0f), Offset(cx, h), strokeWidth = 2.dp.toPx())
        // A handle on the line while dragging, so it is clear where letting go will land.
        if (live != null) {
            val p = points.minByOrNull { kotlin.math.abs(it.ply - live) }
            if (p != null) drawCircle(c.brand, 6.dp.toPx(), Offset(cx, y(p.winPercent)))
        }
    }
}
