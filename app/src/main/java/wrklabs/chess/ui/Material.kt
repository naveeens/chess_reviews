package wrklabs.chess.ui

import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.PieceType

/** The points on the board for [color] minus the opponent's: positive when ahead, negative when behind, 0 when level. */
fun materialDiff(board: ChessBoard, color: PieceColor): Int {
    var diff = 0
    for (rank in 0 until 8) for (file in 0 until 8) {
        val p = board.getPiece(file, rank) ?: continue
        if (p.type == PieceType.KING) continue
        diff += if (p.color == color) p.type.value else -p.type.value
    }
    return diff
}
