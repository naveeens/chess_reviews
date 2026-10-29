package wrklabs.chess.ui

import wrklabs.chess.classifier.MoveClassifier
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move
import kotlin.math.abs

/** "Show why": which of the engine's moves to play out, and what to say about where they lead. The review and Train both use it. */
object WhyLine {
    /** The most single moves (of either side) it plays. */
    const val PLIES = 8

    /**
     * The engine's line from `fen`, as far as it explains itself: through the whole run of captures once one starts (a
     * fork's check and forced reply come first), a checkmate, or the limit. A line to mate (`mating`) is played to the end.
     */
    fun plan(fen: String, pv: List<String>, mating: Boolean = false): List<Move> {
        var board = ChessBoard.fromFen(fen)
        var capturing = false
        val moves = mutableListOf<Move>()
        for (uci in pv.take(PLIES)) {
            val move = board.legalMoves().find { it.uci == uci } ?: break
            val capture = move.san.contains('x')
            if (!mating && capturing && !capture) break
            moves += move
            if (capture) capturing = true
            board = board.makeMove(move)
            if (move.san.endsWith("#")) break
        }
        return moves
    }

    /** What the line came to for `mover`, whose move was `before` the line began (or, for `missed`, who could have played it). */
    fun text(
        startFen: String, endFen: String, endSan: String?, mover: PieceColor, opponent: String, score: Score,
        missed: Boolean = false, mating: Boolean = false,
    ): String {
        val matedFor = (score as? Score.Mate)?.takeIf { it.value != 0 && ((mover == PieceColor.WHITE) == (it.value < 0)) }
        val lost = MoveClassifier.materialBalance(startFen, mover) - MoveClassifier.materialBalance(endFen, mover)
        return when {
            mating -> "You had checkmate in ${abs((score as Score.Mate).value)}"
            missed && -lost >= 1 -> "You could have won ${materialWords(-lost)}"
            missed -> "The best line keeps your advantage"
            matedFor != null -> "$opponent has a forced checkmate in ${abs(matedFor.value)}"
            endSan?.endsWith("#") == true -> "$opponent delivers checkmate"
            lost >= 1 -> "$opponent wins ${materialWords(lost)}"
            else -> "No material is lost, but the position gets worse"
        }
    }

    fun materialWords(points: Int) = when (points) {
        1 -> "a pawn"
        2 -> "two pawns"
        3 -> "a knight or bishop"
        4 -> "a piece and a pawn"
        5 -> "a rook"
        9 -> "the queen"
        else -> "$points points of material"
    }
}
