package wrklabs.chess.training

import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Piece
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.Square
import kotlin.math.abs
import kotlin.random.Random

/** The questions of the vision drills, answered from a position or two squares alone. */
object Vision {
    private val JUMPS = listOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1)

    /** How many knight moves it takes to get from [a] to [b] on an empty board. */
    fun knightDistance(a: Square, b: Square): Int {
        if (a == b) return 0
        val seen = HashSet<Square>().apply { add(a) }
        var ring = listOf(a)
        var steps = 0
        while (ring.isNotEmpty()) {
            steps++
            val next = ArrayList<Square>()
            for (s in ring) for ((df, dr) in JUMPS) {
                val f = s.file + df
                val r = s.rank + dr
                if (f !in 0..7 || r !in 0..7) continue
                val to = Square(f, r)
                if (to == b) return steps
                if (seen.add(to)) next += to
            }
            ring = next
        }
        error("A knight reaches every square")
    }

    private fun colourOf(s: Square) = (s.file + s.rank) % 2

    /**
     * A square to ask about against [target]: on its diagonal half the time. The rest are mostly
     * the same colour but off the diagonal, which take a real check, and a few the other colour,
     * which are quick to rule out.
     */
    fun diagonalOther(target: Square, random: kotlin.random.Random): Square {
        val all = (0 until 64).map { Square.fromIndex(it) }
        val on = all.filter { sameDiagonal(target, it) }
        if (random.nextBoolean()) return on.random(random)
        val off = all.filter { it != target && !sameDiagonal(target, it) }
        val sameColour = off.filter { colourOf(it) == colourOf(target) }
        val otherColour = off.filter { colourOf(it) != colourOf(target) }
        return (if (random.nextInt(4) < 3) sameColour else otherColour).random(random)
    }

    /** Why [a] and [b] are, or are not, on one diagonal: the rule, with the numbers. */
    fun diagonalWhy(a: Square, b: Square): String {
        val files = abs(a.file - b.file)
        val ranks = abs(a.rank - b.rank)
        val plural = { n: Int, w: String -> "$n $w${if (n == 1) "" else "s"}" }
        return when {
            sameDiagonal(a, b) -> "${a.name} to ${b.name}: ${plural(files, "file")}, ${plural(ranks, "rank")}. Equal, so yes."
            colourOf(a) != colourOf(b) -> "${a.name} and ${b.name} are different colours, so never on one diagonal."
            else -> "${a.name} to ${b.name}: ${plural(files, "file")}, ${plural(ranks, "rank")}. Not equal, so no."
        }
    }

    /** Whether a bishop on [a] could go to [b] on an empty board. */
    fun sameDiagonal(a: Square, b: Square) = a != b && abs(a.file - b.file) == abs(a.rank - b.rank)

    private fun pieces(board: ChessBoard, color: PieceColor): List<Square> =
        (0 until 64).map { Square.fromIndex(it) }.filter { s -> board.getPiece(s)?.let { it.color == color && it.type != PieceType.KING } == true }

    /** The pieces of [color] (not the king) that the other side attacks. */
    fun attacked(board: ChessBoard, color: PieceColor): Set<Square> =
        pieces(board, color).filter { board.isSquareAttacked(it, color.opponent()) }.toSet()

    /** The pieces of [color] that are attacked and have nothing guarding them. */
    fun hanging(board: ChessBoard, color: PieceColor): Set<Square> =
        attacked(board, color).filter { !board.isSquareAttacked(it, color) }.toSet()

    /** The piece-placement part of a FEN for [pieces]. */
    fun placementFen(pieces: Map<Square, Piece>): String =
        (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var gap = 0
                for (file in 0..7) {
                    val p = pieces[Square(file, rank)]
                    if (p == null) gap++ else { if (gap > 0) append(gap); gap = 0; append(p.fenChar) }
                }
                if (gap > 0) append(gap)
            }
        }

    fun boardOf(pieces: Map<Square, Piece>): ChessBoard = ChessBoard.fromFen("${placementFen(pieces)} w - - 0 1")

    /** [count] pieces of [board] picked at random, kings included in the pick like any other. */
    fun pick(board: ChessBoard, count: Int, random: Random): Map<Square, Piece> =
        (0 until 64).map { Square.fromIndex(it) }
            .mapNotNull { s -> board.getPiece(s)?.let { s to it } }
            .shuffled(random).take(count).toMap()
}
