package wrklabs.chess.model

import wrklabs.chess.engine.PieceColor

class ChessBoard private constructor(
    private val pieces: Array<Piece?>,
    val turn: PieceColor,
    val whiteKingside: Boolean,
    val whiteQueenside: Boolean,
    val blackKingside: Boolean,
    val blackQueenside: Boolean,
    val enPassant: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {

    companion object {
        const val DEFAULT_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun fromFen(fen: String = DEFAULT_FEN): ChessBoard {
            val parts = fen.trim().split("\\s+".toRegex())
            val piecePlacement = parts[0]
            val activeColor = if (parts.getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE
            val castling = parts.getOrNull(2) ?: "-"
            val epStr = parts.getOrNull(3) ?: "-"
            val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1

            val pieces = Array<Piece?>(64) { null }
            val ranks = piecePlacement.split("/")
            for (r in 0 until 8) {
                val rankStr = ranks[r]
                val boardRank = 7 - r
                var file = 0
                for (ch in rankStr) {
                    if (ch.isDigit()) {
                        file += ch.digitToInt()
                    } else {
                        val color = if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                        val type = PieceType.fromChar(ch)
                        if (type != null) {
                            pieces[boardRank * 8 + file] = Piece(type, color)
                        }
                        file++
                    }
                }
            }

            val wK = castling.contains('K')
            val wQ = castling.contains('Q')
            val bK = castling.contains('k')
            val bQ = castling.contains('q')
            val ep = if (epStr != "-") Square.fromName(epStr) else null

            return ChessBoard(
                pieces = pieces,
                turn = activeColor,
                whiteKingside = wK,
                whiteQueenside = wQ,
                blackKingside = bK,
                blackQueenside = bQ,
                enPassant = ep,
                halfmoveClock = halfmove,
                fullmoveNumber = fullmove
            )
        }
    }

    fun getPiece(sq: Square): Piece? = pieces[sq.index]
    fun getPiece(file: Int, rank: Int): Piece? = pieces[rank * 8 + file]

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0 until 8) {
                val piece = pieces[r * 8 + f]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(piece.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }

        sb.append(if (turn == PieceColor.WHITE) " w " else " b ")

        val castle = StringBuilder()
        if (whiteKingside) castle.append('K')
        if (whiteQueenside) castle.append('Q')
        if (blackKingside) castle.append('k')
        if (blackQueenside) castle.append('q')
        sb.append(if (castle.isEmpty()) "-" else castle.toString())

        sb.append(" ")
        sb.append(enPassant?.name ?: "-")
        sb.append(" $halfmoveClock $fullmoveNumber")

        return sb.toString()
    }

    private fun findKing(color: PieceColor): Square {
        for (i in 0 until 64) {
            val p = pieces[i]
            if (p != null && p.type == PieceType.KING && p.color == color) {
                return Square.fromIndex(i)
            }
        }
        return Square(4, if (color == PieceColor.WHITE) 0 else 7)
    }

    fun isSquareAttacked(sq: Square, byColor: PieceColor): Boolean {
        // Pawns
        val pawnPawnRank = if (byColor == PieceColor.WHITE) sq.rank - 1 else sq.rank + 1
        if (pawnPawnRank in 0..7) {
            for (df in listOf(-1, 1)) {
                val f = sq.file + df
                if (f in 0..7) {
                    val p = pieces[pawnPawnRank * 8 + f]
                    if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
                }
            }
        }

        // Knights
        val knightOffsets = listOf(-17, -15, -10, -6, 6, 10, 15, 17)
        for (off in knightOffsets) {
            val toIdx = sq.index + off
            if (toIdx in 0..63) {
                val toSq = Square.fromIndex(toIdx)
                val df = kotlin.math.abs(toSq.file - sq.file)
                val dr = kotlin.math.abs(toSq.rank - sq.rank)
                if ((df == 1 && dr == 2) || (df == 2 && dr == 1)) {
                    val p = pieces[toIdx]
                    if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
                }
            }
        }

        // King
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val f = sq.file + df
                val r = sq.rank + dr
                if (f in 0..7 && r in 0..7) {
                    val p = pieces[r * 8 + f]
                    if (p != null && p.color == byColor && p.type == PieceType.KING) return true
                }
            }
        }

        // Straight rays (Rook, Queen)
        val straightDirs = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
        for ((df, dr) in straightDirs) {
            var f = sq.file + df
            var r = sq.rank + dr
            while (f in 0..7 && r in 0..7) {
                val p = pieces[r * 8 + f]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                f += df
                r += dr
            }
        }

        // Diagonal rays (Bishop, Queen)
        val diagDirs = listOf(Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1))
        for ((df, dr) in diagDirs) {
            var f = sq.file + df
            var r = sq.rank + dr
            while (f in 0..7 && r in 0..7) {
                val p = pieces[r * 8 + f]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                f += df
                r += dr
            }
        }

        return false
    }

    fun isCheck(): Boolean {
        val kingSq = findKing(turn)
        return isSquareAttacked(kingSq, turn.opponent())
    }

    fun generatePseudoLegalMoves(): List<Move> {
        val list = mutableListOf<Move>()
        val forward = if (turn == PieceColor.WHITE) 1 else -1
        val startPawnRank = if (turn == PieceColor.WHITE) 1 else 6
        val promoRank = if (turn == PieceColor.WHITE) 7 else 0

        for (idx in 0 until 64) {
            val p = pieces[idx] ?: continue
            if (p.color != turn) continue
            val sq = Square.fromIndex(idx)

            when (p.type) {
                PieceType.PAWN -> {
                    // 1 push
                    val nextRank = sq.rank + forward
                    if (nextRank in 0..7 && pieces[nextRank * 8 + sq.file] == null) {
                        val toSq = Square(sq.file, nextRank)
                        if (nextRank == promoRank) {
                            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { promo ->
                                list.add(Move(sq, toSq, promo))
                            }
                        } else {
                            list.add(Move(sq, toSq))
                            // 2 push
                            val doubleRank = sq.rank + 2 * forward
                            if (sq.rank == startPawnRank && pieces[doubleRank * 8 + sq.file] == null) {
                                list.add(Move(sq, Square(sq.file, doubleRank)))
                            }
                        }
                    }

                    // Captures
                    for (df in listOf(-1, 1)) {
                        val toFile = sq.file + df
                        if (toFile in 0..7 && nextRank in 0..7) {
                            val toSq = Square(toFile, nextRank)
                            val target = pieces[toSq.index]
                            if (target != null && target.color != turn) {
                                if (nextRank == promoRank) {
                                    listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { promo ->
                                        list.add(Move(sq, toSq, promo))
                                    }
                                } else {
                                    list.add(Move(sq, toSq))
                                }
                            } else if (enPassant == toSq) {
                                list.add(Move(sq, toSq))
                            }
                        }
                    }
                }

                PieceType.KNIGHT -> {
                    for (off in listOf(-17, -15, -10, -6, 6, 10, 15, 17)) {
                        val toIdx = idx + off
                        if (toIdx in 0..63) {
                            val toSq = Square.fromIndex(toIdx)
                            val df = kotlin.math.abs(toSq.file - sq.file)
                            val dr = kotlin.math.abs(toSq.rank - sq.rank)
                            if ((df == 1 && dr == 2) || (df == 2 && dr == 1)) {
                                val target = pieces[toIdx]
                                if (target == null || target.color != turn) {
                                    list.add(Move(sq, toSq))
                                }
                            }
                        }
                    }
                }

                PieceType.BISHOP -> {
                    for ((df, dr) in listOf(Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1))) {
                        var f = sq.file + df
                        var r = sq.rank + dr
                        while (f in 0..7 && r in 0..7) {
                            val toSq = Square(f, r)
                            val target = pieces[toSq.index]
                            if (target == null) {
                                list.add(Move(sq, toSq))
                            } else {
                                if (target.color != turn) list.add(Move(sq, toSq))
                                break
                            }
                            f += df
                            r += dr
                        }
                    }
                }

                PieceType.ROOK -> {
                    for ((df, dr) in listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))) {
                        var f = sq.file + df
                        var r = sq.rank + dr
                        while (f in 0..7 && r in 0..7) {
                            val toSq = Square(f, r)
                            val target = pieces[toSq.index]
                            if (target == null) {
                                list.add(Move(sq, toSq))
                            } else {
                                if (target.color != turn) list.add(Move(sq, toSq))
                                break
                            }
                            f += df
                            r += dr
                        }
                    }
                }

                PieceType.QUEEN -> {
                    for ((df, dr) in listOf(
                        Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0),
                        Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
                    )) {
                        var f = sq.file + df
                        var r = sq.rank + dr
                        while (f in 0..7 && r in 0..7) {
                            val toSq = Square(f, r)
                            val target = pieces[toSq.index]
                            if (target == null) {
                                list.add(Move(sq, toSq))
                            } else {
                                if (target.color != turn) list.add(Move(sq, toSq))
                                break
                            }
                            f += df
                            r += dr
                        }
                    }
                }

                PieceType.KING -> {
                    for (df in -1..1) {
                        for (dr in -1..1) {
                            if (df == 0 && dr == 0) continue
                            val f = sq.file + df
                            val r = sq.rank + dr
                            if (f in 0..7 && r in 0..7) {
                                val toSq = Square(f, r)
                                val target = pieces[toSq.index]
                                if (target == null || target.color != turn) {
                                    list.add(Move(sq, toSq))
                                }
                            }
                        }
                    }

                    // Castling
                    val kingRank = if (turn == PieceColor.WHITE) 0 else 7
                    val canK = if (turn == PieceColor.WHITE) whiteKingside else blackKingside
                    val canQ = if (turn == PieceColor.WHITE) whiteQueenside else blackQueenside

                    if (canK && sq.file == 4 && sq.rank == kingRank) {
                        if (pieces[kingRank * 8 + 5] == null && pieces[kingRank * 8 + 6] == null) {
                            if (!isSquareAttacked(sq, turn.opponent()) &&
                                !isSquareAttacked(Square(5, kingRank), turn.opponent()) &&
                                !isSquareAttacked(Square(6, kingRank), turn.opponent())
                            ) {
                                list.add(Move(sq, Square(6, kingRank)))
                            }
                        }
                    }

                    if (canQ && sq.file == 4 && sq.rank == kingRank) {
                        if (pieces[kingRank * 8 + 1] == null && pieces[kingRank * 8 + 2] == null && pieces[kingRank * 8 + 3] == null) {
                            if (!isSquareAttacked(sq, turn.opponent()) &&
                                !isSquareAttacked(Square(3, kingRank), turn.opponent()) &&
                                !isSquareAttacked(Square(2, kingRank), turn.opponent())
                            ) {
                                list.add(Move(sq, Square(2, kingRank)))
                            }
                        }
                    }
                }
            }
        }
        return list
    }

    fun makeMove(move: Move): ChessBoard {
        val newPieces = pieces.clone()
        val moving = newPieces[move.from.index] ?: throw IllegalArgumentException("No piece at ${move.from}")
        val captured = newPieces[move.to.index]

        newPieces[move.from.index] = null
        newPieces[move.to.index] = if (move.promotion != null) Piece(move.promotion, turn) else moving

        // En passant capture
        if (moving.type == PieceType.PAWN && move.to == enPassant) {
            val epPawnRank = move.from.rank
            newPieces[epPawnRank * 8 + move.to.file] = null
        }

        // Castling rook move
        if (moving.type == PieceType.KING && kotlin.math.abs(move.to.file - move.from.file) == 2) {
            val r = move.from.rank
            if (move.to.file == 6) { // Kingside
                newPieces[r * 8 + 7] = null
                newPieces[r * 8 + 5] = Piece(PieceType.ROOK, turn)
            } else if (move.to.file == 2) { // Queenside
                newPieces[r * 8 + 0] = null
                newPieces[r * 8 + 3] = Piece(PieceType.ROOK, turn)
            }
        }

        // Update castling rights
        var wK = whiteKingside
        var wQ = whiteQueenside
        var bK = blackKingside
        var bQ = blackQueenside

        if (moving.type == PieceType.KING) {
            if (turn == PieceColor.WHITE) { wK = false; wQ = false }
            else { bK = false; bQ = false }
        }
        if (moving.type == PieceType.ROOK) {
            if (move.from == Square(0, 0)) wQ = false
            if (move.from == Square(7, 0)) wK = false
            if (move.from == Square(0, 7)) bQ = false
            if (move.from == Square(7, 7)) bK = false
        }
        if (captured?.type == PieceType.ROOK) {
            if (move.to == Square(0, 0)) wQ = false
            if (move.to == Square(7, 0)) wK = false
            if (move.to == Square(0, 7)) bQ = false
            if (move.to == Square(7, 7)) bK = false
        }

        // En passant square
        val newEp = if (moving.type == PieceType.PAWN && kotlin.math.abs(move.to.rank - move.from.rank) == 2) {
            Square(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else null

        val isCaptureOrPawn = captured != null || moving.type == PieceType.PAWN
        val newHalfmove = if (isCaptureOrPawn) 0 else halfmoveClock + 1
        val newFullmove = if (turn == PieceColor.BLACK) fullmoveNumber + 1 else fullmoveNumber

        return ChessBoard(
            pieces = newPieces,
            turn = turn.opponent(),
            whiteKingside = wK,
            whiteQueenside = wQ,
            blackKingside = bK,
            blackQueenside = bQ,
            enPassant = newEp,
            halfmoveClock = newHalfmove,
            fullmoveNumber = newFullmove
        )
    }

    fun legalMoves(): List<Move> {
        val pseudo = generatePseudoLegalMoves()
        val legal = mutableListOf<Move>()
        for (m in pseudo) {
            val after = makeMove(m)
            if (keepsKingSafe(after)) {
                val san = formatSan(m, after)
                legal.add(m.copy(san = san))
            }
        }
        return legal
    }

    /** A move is legal if the moving side's king is NOT attacked after it: `after` is the board once it is played. */
    private fun keepsKingSafe(after: ChessBoard): Boolean = !after.isSquareAttacked(after.findKing(turn), turn.opponent())

    private fun formatSan(m: Move, after: ChessBoard): String {
        val p = pieces[m.from.index] ?: return m.uci
        if (p.type == PieceType.KING && kotlin.math.abs(m.to.file - m.from.file) == 2) {
            val base = if (m.to.file == 6) "O-O" else "O-O-O"
            return if (after.isCheckmate()) "$base#" else if (after.isCheck()) "$base+" else base
        }

        val sb = StringBuilder()
        if (p.type != PieceType.PAWN) {
            sb.append(p.type.char.uppercaseChar())

            // Disambiguation: only another piece that could really make the move counts, not one that is pinned.
            val others = generatePseudoLegalMoves().filter { other ->
                other.to == m.to && other.from != m.from && pieces[other.from.index] == p && keepsKingSafe(makeMove(other))
            }
            if (others.isNotEmpty()) {
                val sameFile = others.any { it.from.file == m.from.file }
                val sameRank = others.any { it.from.rank == m.from.rank }
                if (!sameFile) {
                    sb.append(('a' + m.from.file))
                } else if (!sameRank) {
                    sb.append(m.from.rank + 1)
                } else {
                    sb.append(('a' + m.from.file))
                    sb.append(m.from.rank + 1)
                }
            }
        }

        val isCapture = pieces[m.to.index] != null || (p.type == PieceType.PAWN && m.to == enPassant)
        if (isCapture) {
            if (p.type == PieceType.PAWN) {
                sb.append(('a' + m.from.file))
            }
            sb.append('x')
        }

        sb.append(m.to.name)

        if (m.promotion != null) {
            sb.append("=${m.promotion.char.uppercaseChar()}")
        }

        if (after.isCheckmate()) {
            sb.append('#')
        } else if (after.isCheck()) {
            sb.append('+')
        }

        return sb.toString()
    }

    fun isCheckmate(): Boolean = isCheck() && legalMoves().isEmpty()
    fun isStalemate(): Boolean = !isCheck() && legalMoves().isEmpty()
    fun isDraw(): Boolean = isStalemate() || halfmoveClock >= 100
}
