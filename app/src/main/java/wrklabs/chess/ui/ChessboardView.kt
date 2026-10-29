package wrklabs.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.*
import wrklabs.chess.ui.theme.Sans
import kotlin.math.sqrt

data class EngineArrow(
    val from: Square,
    val to: Square,
    val color: Color? = null,
    val faint: Boolean = false,
)

/** The six piece types of both colours, as painters made once for the board. */
@Composable
private fun rememberPiecePainters(): Map<Pair<PieceColor, PieceType>, Painter> {
    val out = mutableMapOf<Pair<PieceColor, PieceType>, Painter>()
    for (color in PieceColor.values()) for (type in PieceType.values()) {
        val res = pieceDrawableRes(Piece(type, color))
        out[color to type] = rememberVectorPainter(ImageVector.vectorResource(id = res))
    }
    return out
}

/**
 * The board: squares, last move, legal moves, arrows, the review badge and the pieces, drawn on one canvas.
 * Tap a piece then a square, or drag. `interactive = false` makes it a picture.
 */
@Composable
fun ChessboardView(
    board: ChessBoard,
    colors: BoardColors,
    modifier: Modifier = Modifier,
    orientation: PieceColor = PieceColor.WHITE,
    lastMove: Pair<Square, Square>? = null,
    selectedSquare: Square? = null,
    legalMoves: List<Move> = emptyList(),
    arrows: List<EngineArrow> = emptyList(),
    badge: Pair<Square, MoveClass>? = null,
    /** The king that has been checkmated. */
    mated: Square? = null,
    coordinates: Boolean = true,
    highlightLastMove: Boolean = true,
    showLegalMoves: Boolean = true,
    interactive: Boolean = true,
    onSquareClick: (Square) -> Unit = {},
    onMove: (from: Square, to: Square) -> Unit = { _, _ -> },
) {
    val painters = rememberPiecePainters()
    val badgeIcon = badge?.second?.icon?.let { rememberVectorPainter(it) }
    // The gesture handlers outlive recompositions, so they read the newest board and callbacks through these.
    val currentBoard by rememberUpdatedState(board)
    val clicked by rememberUpdatedState(onSquareClick)
    val moved by rememberUpdatedState(onMove)
    val measurer = rememberTextMeasurer()
    var dragFrom by remember { mutableStateOf<Square?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val white = orientation == PieceColor.WHITE
        val input = if (!interactive) Modifier else Modifier
            .pointerInput(orientation) {
                val sq = size.width / 8f
                fun at(p: Offset): Square {
                    val col = (p.x / sq).toInt().coerceIn(0, 7)
                    val row = (p.y / sq).toInt().coerceIn(0, 7)
                    return Square(if (white) col else 7 - col, if (white) 7 - row else row)
                }
                detectTapGestures { clicked(at(it)) }
            }
            .pointerInput(orientation) {
                val sq = size.width / 8f
                fun at(p: Offset): Square {
                    val col = (p.x / sq).toInt().coerceIn(0, 7)
                    val row = (p.y / sq).toInt().coerceIn(0, 7)
                    return Square(if (white) col else 7 - col, if (white) 7 - row else row)
                }
                detectDragGestures(
                    onDragStart = { p ->
                        val s = at(p)
                        if (currentBoard.getPiece(s) != null) {
                            dragFrom = s
                            dragAt = p
                            clicked(s)
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragAt += amount
                    },
                    onDragEnd = {
                        val from = dragFrom
                        if (from != null) {
                            val to = at(dragAt)
                            if (to != from) moved(from, to)
                        }
                        dragFrom = null
                    },
                    onDragCancel = { dragFrom = null },
                )
            }

        Canvas(modifier = Modifier.matchParentSize().then(input)) {
            val sq = size.width / 8f
            fun colOf(s: Square) = if (white) s.file else 7 - s.file
            fun rowOf(s: Square) = if (white) 7 - s.rank else s.rank
            fun topLeft(s: Square) = Offset(colOf(s) * sq, rowOf(s) * sq)
            fun center(s: Square) = topLeft(s) + Offset(sq / 2f, sq / 2f)
            val cell = Size(sq, sq)

            for (r in 0 until 8) for (f in 0 until 8) {
                drawRect(if ((r + f) % 2 == 0) colors.light else colors.dark, Offset(f * sq, r * sq), cell)
            }

            if (highlightLastMove) lastMove?.let { (a, b) ->
                drawRect(colors.lastMove, topLeft(a), cell)
                drawRect(colors.lastMove, topLeft(b), cell)
            }
            selectedSquare?.let { drawRect(colors.selected, topLeft(it), cell) }

            if (board.isCheck()) {
                for (i in 0 until 64) {
                    val s = Square.fromIndex(i)
                    val p = board.getPiece(s)
                    if (p != null && p.type == PieceType.KING && p.color == board.turn) {
                        drawCircle(Color(0xAAFF3B30), radius = sq * 0.46f, center = center(s))
                    }
                }
            }

            // The mated king sits on a red disc, drawn under the pieces so it stays visible.
            mated?.let { drawCircle(Color(0xCCC62828), sq * 0.48f, center(it)) }

            if (showLegalMoves) for (m in legalMoves) {
                val capture = board.getPiece(m.to) != null || m.to == board.enPassant
                if (!capture) drawCircle(Color(0x59000000), sq * 0.16f, center(m.to))
                else drawCircle(Color(0x59000000), sq * 0.43f, center(m.to), style = Stroke(sq * 0.09f))
            }

            for (arrow in arrows) {
                drawArrow(center(arrow.from), center(arrow.to), (arrow.color ?: colors.arrow).copy(alpha = if (arrow.faint) 0.5f else 0.9f), sq, if (arrow.faint) 0.15f else 0.2f)
            }

            val lifted = dragFrom
            for (i in 0 until 64) {
                val s = Square.fromIndex(i)
                if (s == lifted) continue
                val p = board.getPiece(s) ?: continue
                val painter = painters[p.color to p.type] ?: continue
                val inset = sq * 0.04f
                translate(topLeft(s).x + inset, topLeft(s).y + inset) {
                    with(painter) { draw(Size(sq - inset * 2, sq - inset * 2)) }
                }
            }

            if (coordinates) drawCoordinates(measurer, sq, white, colors)

            badge?.let { (s, cls) -> drawBadge(measurer, badgeIcon, cls, topLeft(s) + Offset(sq * 0.98f, sq * 0.02f), sq) }
            mated?.let { s ->
                val text = measurer.measure("#", TextStyle(color = Color.White, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = (sq * 0.3f).toSp()))
                val r = sq * 0.22f
                val tag = Offset(topLeft(s).x + sq - r * 0.9f, topLeft(s).y + r * 0.9f)
                drawCircle(Color(0xFF8E1B1B), r, tag)
                drawText(text, topLeft = Offset(tag.x - text.size.width / 2f, tag.y - text.size.height / 2f))
            }

            if (lifted != null) {
                val p = board.getPiece(lifted)
                val painter = p?.let { painters[it.color to it.type] }
                if (painter != null) {
                    // The painter is shared with the pieces on the board, and a vector painter drawn at two sizes in one frame
                    // leaves its cached image at the wrong size for the others. So it is drawn at their size and scaled up.
                    val side = sq - sq * 0.04f * 2
                    val lift = Offset(dragAt.x - side / 2f, dragAt.y - side / 2f - sq * 0.35f)
                    withTransform({
                        translate(lift.x, lift.y)
                        scale(1.25f, 1.25f, pivot = Offset(side / 2f, side / 2f))
                    }) { with(painter) { draw(Size(side, side)) } }
                }
            }
        }
    }
}

private fun DrawScope.drawCoordinates(measurer: TextMeasurer, sq: Float, white: Boolean, colors: BoardColors) {
    val base = TextStyle(fontFamily = Sans, fontSize = (sq / 4.1f).toSp(), fontWeight = FontWeight.SemiBold)
    // Drawn over the pieces, so each label carries a soft shadow in its own square's colour: it stays legible on a piece's base.
    fun label(text: String, at: (androidx.compose.ui.unit.IntSize) -> Offset, onSquare: Color, ink: Color) {
        val shadow = androidx.compose.ui.graphics.Shadow(color = onSquare, offset = Offset.Zero, blurRadius = sq * 0.05f)
        val layout = measurer.measure(text, base.copy(color = ink, shadow = shadow))
        drawText(layout, topLeft = at(layout.size))
    }
    for (i in 0 until 8) {
        val rank = if (white) 8 - i else i + 1
        val file = if (white) 'a' + i else 'h' - i
        val rankSquare = if (i % 2 == 0) colors.light else colors.dark
        val fileSquare = if ((7 + i) % 2 == 0) colors.light else colors.dark
        label(rank.toString(), { Offset(sq * 0.06f, i * sq + sq * 0.02f) }, rankSquare, if (rankSquare == colors.light) colors.dark else colors.light)
        label(file.toString(), { size -> Offset(i * sq + sq * 0.94f - size.width, 7 * sq + sq * 0.98f - size.height) }, fileSquare, if (fileSquare == colors.light) colors.dark else colors.light)
    }
}

private fun DrawScope.drawBadge(measurer: TextMeasurer, icon: Painter?, cls: MoveClass, topRight: Offset, sq: Float) {
    val r = sq * 0.24f
    val c = Offset(topRight.x - r * 0.75f, topRight.y + r * 0.75f)
    drawCircle(Color.Black.copy(alpha = 0.25f), r + sq * 0.02f, c + Offset(0f, sq * 0.015f))
    drawCircle(cls.composeColor, r, c)
    if (icon != null) {
        val side = r * 1.25f
        translate(c.x - side / 2f, c.y - side / 2f) { with(icon) { draw(Size(side, side), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)) } }
        return
    }
    val text = measurer.measure(cls.glyph, TextStyle(color = Color.White, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = (sq * 0.26f).toSp()))
    drawText(text, topLeft = Offset(c.x - text.size.width / 2f, c.y - text.size.height / 2f))
}

private fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color, sq: Float, width: Float) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val ux = dx / length
    val uy = dy / length
    val head = sq * 0.42f
    val shaftEnd = Offset(end.x - ux * head * 0.8f, end.y - uy * head * 0.8f)
    val from = Offset(start.x + ux * sq * 0.1f, start.y + uy * sq * 0.1f)
    drawLine(color, from, shaftEnd, strokeWidth = sq * width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val px = -uy
    val py = ux
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(end.x - ux * head + px * head * 0.5f, end.y - uy * head + py * head * 0.5f)
        lineTo(end.x - ux * head - px * head * 0.5f, end.y - uy * head - py * head * 0.5f)
        close()
    }
    drawPath(path, color)
}
