package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.Square
import wrklabs.chess.ui.promotionMoves

class PromotionTest {
    private val board = ChessBoard.fromFen("8/4P2k/8/8/8/8/8/4K3 w - - 0 1")

    @Test fun aPawnReachingTheLastRankOffersFourPieces() {
        val moves = promotionMoves(board, Square.fromName("e7"), Square.fromName("e8"))
        assertEquals(setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), moves.map { it.promotion }.toSet())
    }

    @Test fun anOrdinaryMoveOffersNone() {
        assertEquals(0, promotionMoves(board, Square.fromName("e1"), Square.fromName("e2")).size)
    }
}
