package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.ui.materialDiff

class MaterialTest {
    @Test fun startingPositionIsLevel() {
        assertEquals(0, materialDiff(ChessBoard.fromFen(), PieceColor.WHITE))
        assertEquals(0, materialDiff(ChessBoard.fromFen(), PieceColor.BLACK))
    }

    @Test fun aSideAheadIsPositiveAndTheOtherNegative() {
        // Black is missing both knights and the h-pawn.
        val b = ChessBoard.fromFen("r1bqkb1r/ppppppp1/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        assertEquals(7, materialDiff(b, PieceColor.WHITE))
        assertEquals(-7, materialDiff(b, PieceColor.BLACK))
    }

    @Test fun aPromotionCountsAsTheNewPiece() {
        // A pawn has become a second white queen: 8 ahead.
        val b = ChessBoard.fromFen("rnbqkbnr/pppppppp/8/8/8/8/QPPPPPPP/RNBQKBNR w KQkq - 0 1")
        assertEquals(8, materialDiff(b, PieceColor.WHITE))
    }

    @Test fun aCustomPositionHasATrueDifference() {
        assertEquals(5, materialDiff(ChessBoard.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1"), PieceColor.WHITE))
    }
}
