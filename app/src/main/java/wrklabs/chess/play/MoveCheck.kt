package wrklabs.chess.play

import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.engine.format
import wrklabs.chess.engine.winPercent

/** Whether a move gives the game away, judged by the engine's view of the position before it and after it. */
object MoveCheck {
    /** The fall in your chances, in percentage points, that is worth stopping for. About a piece in an even game. */
    const val WARN_DROP = 20.0

    /** How many points of winning chances [mover] lost between two scores (both from White's side). Zero if it gained. */
    fun drop(before: Score, after: Score, mover: PieceColor): Double {
        val b = before.winPercent().let { if (mover == PieceColor.WHITE) it else 100 - it }
        val a = after.winPercent().let { if (mover == PieceColor.WHITE) it else 100 - it }
        return (b - a).coerceAtLeast(0.0)
    }

    fun isBlunder(before: Score, after: Score, mover: PieceColor) = drop(before, after, mover) >= WARN_DROP

    /** A score from [side]'s point of view, as text: "+0.3", "-1.2", "Mate in 3". */
    fun textFor(score: Score, side: PieceColor): String {
        val sign = if (side == PieceColor.WHITE) 1 else -1
        return when (score) {
            is Score.Centipawns -> Score.Centipawns(score.value * sign).format()
            is Score.Mate -> Score.Mate(score.value * sign, score.matedColor).format()
        }
    }
}
