package wrklabs.chess.ui

import wrklabs.chess.R
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.Piece
import wrklabs.chess.model.PieceType

fun pieceDrawableRes(piece: Piece): Int {
    val isWhite = piece.color == PieceColor.WHITE
    return when (piece.type) {
        PieceType.PAWN -> if (isWhite) R.drawable.ic_chess_w_pawn else R.drawable.ic_chess_b_pawn
        PieceType.KNIGHT -> if (isWhite) R.drawable.ic_chess_w_knight else R.drawable.ic_chess_b_knight
        PieceType.BISHOP -> if (isWhite) R.drawable.ic_chess_w_bishop else R.drawable.ic_chess_b_bishop
        PieceType.ROOK -> if (isWhite) R.drawable.ic_chess_w_rook else R.drawable.ic_chess_b_rook
        PieceType.QUEEN -> if (isWhite) R.drawable.ic_chess_w_queen else R.drawable.ic_chess_b_queen
        PieceType.KING -> if (isWhite) R.drawable.ic_chess_w_king else R.drawable.ic_chess_b_king
    }
}
