package wrklabs.chess.model

import wrklabs.chess.engine.PieceColor

enum class PieceType(val char: Char, val value: Int) {
    PAWN('p', 1),
    KNIGHT('n', 3),
    BISHOP('b', 3),
    ROOK('r', 5),
    QUEEN('q', 9),
    KING('k', 0);

    companion object {
        fun fromChar(c: Char): PieceType? = when (c.lowercaseChar()) {
            'p' -> PAWN
            'n' -> KNIGHT
            'b' -> BISHOP
            'r' -> ROOK
            'q' -> QUEEN
            'k' -> KING
            else -> null
        }
    }
}

data class Piece(val type: PieceType, val color: PieceColor) {
    val fenChar: Char = if (color == PieceColor.WHITE) type.char.uppercaseChar() else type.char
}

data class Square(val file: Int, val rank: Int) {
    init {
        require(file in 0..7 && rank in 0..7) { "Invalid file=$file rank=$rank" }
    }

    val index: Int = rank * 8 + file
    val name: String = "${('a' + file)}${rank + 1}"

    companion object {
        fun fromName(name: String): Square {
            require(name.length == 2) { "Invalid square name: $name" }
            val file = name[0] - 'a'
            val rank = name[1] - '1'
            return Square(file, rank)
        }

        fun fromIndex(index: Int): Square {
            require(index in 0..63) { "Invalid index: $index" }
            return Square(index % 8, index / 8)
        }
    }
}

data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val san: String = "",
    val uci: String = "${from.name}${to.name}${promotion?.char ?: ""}"
)
