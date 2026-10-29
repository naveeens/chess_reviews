package wrklabs.chess.ui

import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move

/** "Show why": which of the engine's moves to play out. The review, Train and the best line all use it. */
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
}
