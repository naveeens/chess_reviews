package wrklabs.chess.maia

import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.PieceType
import kotlin.math.exp

/**
 * Turns positions into what Maia-3 reads and its answer into moves. Pure arithmetic, so it is tested against
 * the reference implementation without a model.
 *
 * Maia always sees the board from the side to move: with Black to move the board is flipped and the colours
 * swapped, so "my pieces" are always the first six planes. Each of the last [HISTORY] positions gets twelve
 * values per square (a plane per piece type and colour), oldest first, then one column for the clock, which is zero.
 * A move is one of 4096 from-to pairs, then 256 promotions (from the seventh rank to the eighth, queen/rook/bishop/knight).
 */
object MaiaEncoder {
    const val HISTORY = 8
    const val PLANES = 12
    /** Values per square: the history, then the clock. */
    const val WIDTH = PLANES * HISTORY + 1
    const val MOVES = 4096 + 256

    private val PROMOTION = "qrbn"

    /** `fens` oldest first, the position to predict last. Only the last [HISTORY] are used; a short game repeats its first position. */
    fun tokens(fens: List<String>): FloatArray {
        require(fens.isNotEmpty()) { "At least the position itself" }
        val boards = fens.takeLast(HISTORY).map { ChessBoard.fromFen(it) }
        val padded = List(HISTORY - boards.size) { boards.first() } + boards
        val out = FloatArray(64 * WIDTH)
        padded.forEachIndexed { slot, board ->
            val flip = board.turn == PieceColor.BLACK
            for (index in 0 until 64) {
                val piece = board.getPiece(index % 8, index / 8) ?: continue
                val square = if (flip) index xor 56 else index
                val mine = (piece.color == board.turn)
                val plane = planeOf(piece.type) + if (mine) 0 else 6
                out[square * WIDTH + slot * PLANES + plane] = 1f
            }
        }
        return out
    }

    private fun planeOf(type: PieceType) = when (type) {
        PieceType.PAWN -> 0
        PieceType.KNIGHT -> 1
        PieceType.BISHOP -> 2
        PieceType.ROOK -> 3
        PieceType.QUEEN -> 4
        PieceType.KING -> 5
    }

    /** The move's slot in Maia's 4352 outputs, or null if it is not one Maia has (a promotion off the last rank, say). */
    fun moveIndex(uci: String, turn: PieceColor): Int? {
        val u = if (turn == PieceColor.BLACK) mirror(uci) else uci
        val from = squareIndex(u, 0) ?: return null
        val to = squareIndex(u, 2) ?: return null
        if (u.length == 4) return from * 64 + to
        if (u.length != 5 || u[1] != '7' || u[3] != '8') return null
        val piece = PROMOTION.indexOf(u[4]).takeIf { it >= 0 } ?: return null
        return 4096 + ((u[0] - 'a') * 8 + (u[2] - 'a')) * 4 + piece
    }

    /** The move as seen from the other side of the board: ranks turned over. */
    fun mirror(uci: String): String = buildString {
        append(uci[0]); append('9' - (uci[1] - '0')); append(uci[2]); append('9' - (uci[3] - '0'))
        append(uci.substring(4))
    }

    private fun squareIndex(u: String, at: Int): Int? {
        val file = u[at] - 'a'; val rank = u[at + 1] - '1'
        return if (file in 0..7 && rank in 0..7) rank * 8 + file else null
    }

    /** The legal moves of the position with the share of Maia's weight each gets, summing to one. */
    fun probabilities(fen: String, logits: FloatArray): Map<String, Float> {
        val board = ChessBoard.fromFen(fen)
        val legal = board.legalMoves().mapNotNull { m -> moveIndex(m.uci, board.turn)?.let { m.uci to it } }
        if (legal.isEmpty()) return emptyMap()
        val top = legal.maxOf { logits[it.second] }
        val weights = legal.map { exp((logits[it.second] - top).toDouble()) }
        val sum = weights.sum()
        return legal.indices.associate { legal[it].first to (weights[it] / sum).toFloat() }
    }
}
