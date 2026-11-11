package wrklabs.chess.model

import wrklabs.chess.engine.PieceColor

/**
 * A position being set up by hand: the pieces, whose move it is, and which castling rights the user wants.
 * A right that cannot exist (no king or rook on its home square) is left out of the FEN, so the FEN is always one the board accepts.
 * No en passant square: a position set up by hand has no last move to take it from.
 */
class PositionDraft(
    private val pieces: Array<Piece?> = arrayOfNulls(64),
    var turn: PieceColor = PieceColor.WHITE,
    var whiteKingside: Boolean = true,
    var whiteQueenside: Boolean = true,
    var blackKingside: Boolean = true,
    var blackQueenside: Boolean = true,
) {
    fun pieceAt(sq: Square): Piece? = pieces[sq.index]

    fun put(sq: Square, piece: Piece?) { pieces[sq.index] = piece }

    /** Takes the piece on [from] and puts it on [to], taking what was there. */
    fun move(from: Square, to: Square) {
        if (from == to) return
        pieces[to.index] = pieces[from.index]
        pieces[from.index] = null
    }

    fun clear() { pieces.fill(null) }

    fun startingPosition() { load(ChessBoard.DEFAULT_FEN) }

    /** Replaces everything with the position in [fen]. False, with nothing changed, when it is not a position. */
    fun load(fen: String): Boolean {
        val other = fromFen(fen) ?: return false
        for (i in 0 until 64) pieces[i] = other.pieces[i]
        turn = other.turn
        whiteKingside = other.whiteKingside; whiteQueenside = other.whiteQueenside
        blackKingside = other.blackKingside; blackQueenside = other.blackQueenside
        return true
    }

    private fun at(file: Int, rank: Int) = pieces[rank * 8 + file]

    /** Whether the right could be used: the king and that rook are on their home squares. */
    fun canCastle(color: PieceColor, kingside: Boolean): Boolean {
        val rank = if (color == PieceColor.WHITE) 0 else 7
        return at(4, rank) == Piece(PieceType.KING, color) && at(if (kingside) 7 else 0, rank) == Piece(PieceType.ROOK, color)
    }

    /** The right as it will be written: wanted, and possible. */
    fun castling(color: PieceColor, kingside: Boolean): Boolean {
        val wanted = when {
            color == PieceColor.WHITE && kingside -> whiteKingside
            color == PieceColor.WHITE -> whiteQueenside
            kingside -> blackKingside
            else -> blackQueenside
        }
        return wanted && canCastle(color, kingside)
    }

    fun setCastling(color: PieceColor, kingside: Boolean, on: Boolean) {
        when {
            color == PieceColor.WHITE && kingside -> whiteKingside = on
            color == PieceColor.WHITE -> whiteQueenside = on
            kingside -> blackKingside = on
            else -> blackQueenside = on
        }
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0 until 8) {
                val p = at(f, r)
                if (p == null) empty++ else { if (empty > 0) { sb.append(empty); empty = 0 }; sb.append(p.fenChar) }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (turn == PieceColor.WHITE) " w " else " b ")
        val rights = buildString {
            if (castling(PieceColor.WHITE, true)) append('K')
            if (castling(PieceColor.WHITE, false)) append('Q')
            if (castling(PieceColor.BLACK, true)) append('k')
            if (castling(PieceColor.BLACK, false)) append('q')
        }
        sb.append(rights.ifEmpty { "-" }).append(" - 0 1")
        return sb.toString()
    }

    /** What is wrong with the position, in words, or nothing when it can be played from. */
    fun problems(): List<String> {
        val out = mutableListOf<String>()
        val all = pieces.filterNotNull()
        for (color in PieceColor.values()) {
            val name = if (color == PieceColor.WHITE) "White" else "Black"
            val mine = all.filter { it.color == color }
            val kings = mine.count { it.type == PieceType.KING }
            if (kings != 1) out += if (kings == 0) "$name has no king." else "$name has $kings kings."
            if (mine.count { it.type == PieceType.PAWN } > 8) out += "$name has more than eight pawns."
            if (mine.size > 16) out += "$name has more than sixteen pieces."
        }
        if ((0 until 8).any { f -> at(f, 0)?.type == PieceType.PAWN || at(f, 7)?.type == PieceType.PAWN }) out += "A pawn cannot stand on the first or last rank."
        if (out.isEmpty()) {
            // The side that has just moved cannot have left its own king in check.
            val board = ChessBoard.fromFen(toFen())
            val waiting = turn.opponent()
            val king = (0 until 64).map { Square.fromIndex(it) }.first { sq -> board.getPiece(sq) == Piece(PieceType.KING, waiting) }
            if (board.isSquareAttacked(king, turn)) {
                out += "${if (waiting == PieceColor.WHITE) "White" else "Black"} is in check, but it is ${if (turn == PieceColor.WHITE) "White" else "Black"}'s move."
            }
        }
        return out
    }

    companion object {
        /** The position in [fen], or null when the piece placement is not eight ranks of eight squares. */
        fun fromFen(fen: String): PositionDraft? {
            val parts = fen.trim().split(Regex("\\s+"))
            val ranks = parts.firstOrNull()?.split('/') ?: return null
            if (ranks.size != 8) return null
            val pieces = arrayOfNulls<Piece>(64)
            for ((i, rank) in ranks.withIndex()) {
                var file = 0
                for (ch in rank) {
                    if (ch.isDigit()) file += ch.digitToInt()
                    else {
                        val type = PieceType.fromChar(ch) ?: return null
                        if (file > 7) return null
                        pieces[(7 - i) * 8 + file] = Piece(type, if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK)
                        file++
                    }
                }
                if (file != 8) return null
            }
            val rights = parts.getOrNull(2) ?: "-"
            return PositionDraft(
                pieces, if (parts.getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE,
                'K' in rights, 'Q' in rights, 'k' in rights, 'q' in rights,
            )
        }
    }
}
