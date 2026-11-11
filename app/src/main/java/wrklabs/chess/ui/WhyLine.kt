package wrklabs.chess.ui

import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move

/** "Show why" and the best line: which of the engine's moves to play out. The review, Train and Puzzles all use it. */
object WhyLine {
    /** How many single moves (of either side) it plays unless you say otherwise in Settings. */
    const val DEFAULT = 10
    /** How long each move of a played-out line stays on the board before the next, in milliseconds. */
    const val PLY_MS = 800L
    /** The lengths Settings offers. The engine's line rarely goes further than this at the depth it is asked for. */
    val OPTIONS = listOf(4, 6, 8, 10, 12)
    /** How far past the length a run of captures may go, so the line does not stop on a capture that is about to be answered. */
    const val RECAPTURES = 2

    /** A search depth that leaves the engine's line long enough to play [plies] moves: the usual depth, deeper for the longest lengths. */
    fun depthFor(plies: Int, usual: Int = 14) = maxOf(usual, plies + 4)

    /** The time to give it: the usual, and a little more for the longest lengths, which need the deeper search. */
    fun msFor(plies: Int, usualMs: Long) = usualMs + maxOf(0, plies - DEFAULT) * 250L

    /** A line of moves as text, numbered from the position: "27. f4 Rxf4 28. exf4", or "27… Rxf4" when Black starts. */
    fun sanLine(fen: String, ucis: List<String>): String {
        var number = fen.split(" ").getOrNull(5)?.toIntOrNull() ?: 1
        var board = ChessBoard.fromFen(fen)
        val out = StringBuilder()
        var first = true
        for (uci in ucis) {
            val move = board.legalMoves().firstOrNull { it.uci == uci } ?: break
            val white = board.turn == wrklabs.chess.engine.PieceColor.WHITE
            if (white) out.append(number).append(". ") else if (first) out.append(number).append("… ")
            out.append(move.san).append(' ')
            if (!white) number++
            board = board.makeMove(move)
            first = false
        }
        return out.toString().trim()
    }

    /** The engine's line from `fen` as text, as far as it would be played out: [mover] is who plays its first move. */
    fun lineText(fen: String, pv: List<String>, score: wrklabs.chess.engine.Score, mover: wrklabs.chess.engine.PieceColor, plies: Int): String {
        val mating = (score as? wrklabs.chess.engine.Score.Mate)?.let { it.value != 0 && ((mover == wrklabs.chess.engine.PieceColor.WHITE) == (it.value > 0)) } == true
        return sanLine(fen, plan(fen, pv, mating, plies).map { it.uci })
    }

    /**
     * The first [plies] moves of the engine's line from `fen`, or fewer if the line is shorter or ends in checkmate.
     * If the last of them is a capture, the captures that follow it are played too (up to [RECAPTURES]), so the line never
     * ends on a capture that has an obvious answer. A line to mate (`mating`) is played to the end.
     */
    fun plan(fen: String, pv: List<String>, mating: Boolean = false, plies: Int = DEFAULT): List<Move> {
        var board = ChessBoard.fromFen(fen)
        val limit = if (mating) pv.size else plies.coerceAtLeast(1)
        val moves = mutableListOf<Move>()
        var extra = 0
        var lastCapture = false
        for (uci in pv) {
            val move = board.legalMoves().find { it.uci == uci } ?: break
            val capture = move.san.contains('x')
            if (moves.size >= limit) {
                if (!(lastCapture && capture && extra < RECAPTURES)) break
                extra++
            }
            moves += move
            lastCapture = capture
            board = board.makeMove(move)
            if (move.san.endsWith("#")) break
        }
        return moves
    }
}
