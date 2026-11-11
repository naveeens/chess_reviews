package wrklabs.chess.play

import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.Square

/** How a game ended. */
enum class EndReason(val text: String) {
    Checkmate("checkmate"),
    Stalemate("stalemate"),
    Repetition("repetition"),
    FiftyMoves("the fifty-move rule"),
    Insufficient("insufficient material"),
    Resignation("resignation"),
    Agreement("agreement"),
}

/** [result] is PGN's: "1-0", "0-1" or "1/2-1/2". */
data class GameEnd(val result: String, val reason: EndReason) {
    val draw get() = result == "1/2-1/2"
    /** The side that won, or null for a draw. */
    val winner: PieceColor? get() = when (result) { "1-0" -> PieceColor.WHITE; "0-1" -> PieceColor.BLACK; else -> null }
}

/**
 * A game of chess being played: every position so far and the moves between them. Immutable, so a screen can hold it as state;
 * [play] gives the game after a move. Knows when the game is over, and writes itself as PGN.
 */
class PlayGame private constructor(
    /** Every position, the start first. */
    val fens: List<String>,
    val moves: List<Move>,
    private val resigned: PieceColor?,
    private val agreed: Boolean = false,
) {
    val board: ChessBoard get() = ChessBoard.fromFen(fens.last())
    val turn: PieceColor get() = board.turn
    val startFen: String get() = fens.first()
    val ucis: List<String> get() = moves.map { it.uci }

    /** The position after [ply] moves (0 is the start). */
    fun fenAt(ply: Int): String = fens[ply.coerceIn(0, fens.size - 1)]

    /** Null while the game goes on. */
    val end: GameEnd? by lazy { findEnd() }

    /** The game after [move], or null if the move is not legal here or the game is over. Promotions must name their piece. */
    fun play(move: Move): PlayGame? {
        if (end != null) return null
        val b = board
        val legal = b.legalMoves().firstOrNull { it.from == move.from && it.to == move.to && it.promotion == move.promotion } ?: return null
        return PlayGame(fens + b.makeMove(legal).toFen(), moves + legal, null)
    }

    /** The game with the last [plies] moves taken back, still going. */
    fun takeback(plies: Int): PlayGame {
        val n = plies.coerceIn(0, moves.size)
        return PlayGame(fens.dropLast(n), moves.dropLast(n), null)
    }

    /** The game ended by agreement to a draw. */
    fun agreeDraw(): PlayGame = if (end != null) this else PlayGame(fens, moves, null, agreed = true)

    /** The game after the move written as [uci], or null if it is not legal here. */
    fun playUci(uci: String): PlayGame? {
        val b = board
        val m = b.legalMoves().firstOrNull { it.uci == uci } ?: return null
        return play(m)
    }

    fun resign(color: PieceColor): PlayGame = if (end != null) this else PlayGame(fens, moves, color)

    private fun findEnd(): GameEnd? {
        resigned?.let { return GameEnd(if (it == PieceColor.WHITE) "0-1" else "1-0", EndReason.Resignation) }
        if (agreed) return GameEnd("1/2-1/2", EndReason.Agreement)
        val b = board
        if (b.isCheckmate()) return GameEnd(if (b.turn == PieceColor.WHITE) "0-1" else "1-0", EndReason.Checkmate)
        if (b.isStalemate()) return GameEnd("1/2-1/2", EndReason.Stalemate)
        if (insufficient(b)) return GameEnd("1/2-1/2", EndReason.Insufficient)
        val key = key(fens.last())
        if (fens.count { key(it) == key } >= 3) return GameEnd("1/2-1/2", EndReason.Repetition)
        if (b.halfmoveClock >= 100) return GameEnd("1/2-1/2", EndReason.FiftyMoves)
        return null
    }

    /**
     * The game as PGN, the way the app reads imported games. A game that did not start from the usual position says so
     * with FEN and SetUp tags, and its move numbers follow from there.
     */
    fun pgn(white: String, black: String, whiteElo: Int?, blackElo: Int?, date: String, event: String = "Casual game"): String {
        val result = end?.result ?: "*"
        val first = ChessBoard.fromFen(startFen)
        var whiteMoves = first.turn == PieceColor.WHITE
        var number = first.fullmoveNumber
        val text = StringBuilder()
        moves.forEachIndexed { i, m ->
            if (whiteMoves) text.append(number).append(". ")
            else if (i == 0) text.append(number).append("... ")
            text.append(m.san).append(' ')
            if (!whiteMoves) number++
            whiteMoves = !whiteMoves
        }
        return buildString {
            append("[Event \"$event\"]\n[Site \"Chess Reviews\"]\n[Date \"$date\"]\n[White \"$white\"]\n[Black \"$black\"]\n[Result \"$result\"]\n")
            whiteElo?.let { append("[WhiteElo \"$it\"]\n") }
            blackElo?.let { append("[BlackElo \"$it\"]\n") }
            if (startFen != ChessBoard.DEFAULT_FEN) append("[SetUp \"1\"]\n[FEN \"$startFen\"]\n")
            append('\n').append(text).append(result).append('\n')
        }
    }

    companion object {
        fun start(fen: String = ChessBoard.DEFAULT_FEN) = PlayGame(listOf(fen), emptyList(), null)

        /** The game after [ucis] from the start, stopping at the first that is not legal; [resigned] ends it. */
        fun replay(ucis: List<String>, resigned: PieceColor? = null, fen: String = ChessBoard.DEFAULT_FEN, agreed: Boolean = false): PlayGame {
            var g = start(fen)
            for (u in ucis) g = g.playUci(u) ?: break
            if (agreed) return g.agreeDraw()
            return resigned?.let { g.resign(it) } ?: g
        }

        /** The position without its move counters: two positions are the same for repetition when this is. */
        private fun key(fen: String) = fen.split(" ").take(4).joinToString(" ")

        /** Neither side can mate: bare kings, one minor piece, or bishops all on one colour of square. */
        internal fun insufficient(b: ChessBoard): Boolean {
            val others = (0 until 64).mapNotNull { i -> b.getPiece(i % 8, i / 8)?.let { it to i } }.filter { it.first.type != PieceType.KING }
            if (others.isEmpty()) return true
            if (others.any { it.first.type !in setOf(PieceType.BISHOP, PieceType.KNIGHT) }) return false
            if (others.size == 1) return true
            if (others.any { it.first.type == PieceType.KNIGHT }) return false
            return others.map { (it.second % 8 + it.second / 8) % 2 }.distinct().size == 1
        }
    }
}

/** The square of [color]'s king, for marking a mate. */
fun kingSquare(b: ChessBoard, color: PieceColor): Square? =
    (0 until 64).firstOrNull { i -> b.getPiece(i % 8, i / 8)?.let { it.type == PieceType.KING && it.color == color } == true }?.let { Square.fromIndex(it) }
