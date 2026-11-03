package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Square
import wrklabs.chess.training.Vision
import kotlin.random.Random

class VisionTest {
    private fun sq(n: String) = Square.fromName(n)

    @Test fun `knight distances`() {
        assertEquals(0, Vision.knightDistance(sq("b1"), sq("b1")))
        assertEquals(1, Vision.knightDistance(sq("b1"), sq("c3")))
        assertEquals(2, Vision.knightDistance(sq("b1"), sq("c2")))
        assertEquals(6, Vision.knightDistance(sq("a1"), sq("h8")))
        assertEquals(Vision.knightDistance(sq("c3"), sq("f6")), Vision.knightDistance(sq("f6"), sq("c3")))
    }

    @Test fun `diagonals`() {
        assertTrue(Vision.sameDiagonal(sq("a1"), sq("h8")))
        assertTrue(Vision.sameDiagonal(sq("c3"), sq("a5")))
        assertFalse(Vision.sameDiagonal(sq("a1"), sq("a2")))
        assertFalse(Vision.sameDiagonal(sq("e4"), sq("e4")))
    }

    @Test fun `a rook attacked and not guarded hangs, a guarded one does not`() {
        // Black rook on d5 attacked by the white queen on d1 down the file; nothing guards it.
        val loose = ChessBoard.fromFen("4k3/8/8/3r4/8/8/8/3QK3 w - - 0 1")
        assertEquals(setOf(sq("d5")), Vision.attacked(loose, PieceColor.BLACK))
        assertEquals(setOf(sq("d5")), Vision.hanging(loose, PieceColor.BLACK))
        // The same rook guarded by a black queen on d8 is attacked but not hanging.
        val guarded = ChessBoard.fromFen("3qk3/8/8/3r4/8/8/8/3QK3 w - - 0 1")
        assertEquals(setOf(sq("d5")), Vision.attacked(guarded, PieceColor.BLACK))
        assertTrue(Vision.hanging(guarded, PieceColor.BLACK).isEmpty())
    }

    @Test fun `a king is never one of the attacked pieces`() {
        val b = ChessBoard.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1")
        assertTrue(Vision.attacked(b, PieceColor.BLACK).isEmpty())
    }

    @Test fun `placement fen round trips through the board`() {
        val board = ChessBoard.fromFen("r1b2rk1/1p4pp/p1nb4/4Pp2/2P4q/1P3R2/PB2B1PP/R2Q1NK1 b - - 0 16")
        val all = Vision.pick(board, 64, Random(1))
        assertEquals(board.toFen().substringBefore(' '), Vision.placementFen(all))
    }

    @Test fun `picking takes that many pieces`() {
        val board = ChessBoard.fromFen(ChessBoard.DEFAULT_FEN)
        assertEquals(6, Vision.pick(board, 6, Random(7)).size)
    }
}
