package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Piece
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.PositionDraft
import wrklabs.chess.model.Square

class PositionDraftTest {
    private fun sq(name: String) = Square.fromName(name)
    private fun start() = PositionDraft().also { it.startingPosition() }

    @Test fun theStartingPositionRoundTripsAndHasNoProblems() {
        val d = start()
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", d.toFen())
        assertTrue(d.problems().isEmpty())
    }

    @Test fun aFenIsReadBackAsWritten() {
        val fen = "r3k2r/pp3ppp/8/8/8/8/PP3PPP/R3K2R b Kq - 0 1"
        assertEquals(fen, PositionDraft.fromFen(fen)!!.toFen())
    }

    @Test fun somethingThatIsNotAPositionIsRefused() {
        assertNull(PositionDraft.fromFen("hello"))
        assertNull(PositionDraft.fromFen("8/8/8/8/8/8/8 w - - 0 1"))       // seven ranks
        assertNull(PositionDraft.fromFen("9/8/8/8/8/8/8/8 w - - 0 1"))     // a rank too long
        assertNull(PositionDraft.fromFen("ppppppppp/8/8/8/8/8/8/8 w - - 0 1"))
        assertNull(PositionDraft.fromFen("x7/8/8/8/8/8/8/8 w - - 0 1"))
        assertFalse(start().load("nonsense"))
    }

    @Test fun aRightWithNoKingOrRookToUseItIsLeftOut() {
        val d = start()
        d.put(sq("h1"), null)                                      // the white king's rook
        assertTrue(d.toFen().contains(" Qkq "))
        d.move(sq("e1"), sq("e2"))                                  // the king steps off its square
        assertTrue(d.toFen().contains(" kq "))
        // The wish is kept: put the pieces back and the rights return.
        d.move(sq("e2"), sq("e1")); d.put(sq("h1"), Piece(PieceType.ROOK, PieceColor.WHITE))
        assertTrue(d.toFen().contains(" KQkq "))
    }

    @Test fun castlingCanBeSwitchedOff() {
        val d = start()
        d.setCastling(PieceColor.BLACK, kingside = true, on = false)
        assertEquals("KQq", d.toFen().split(" ")[2])
    }

    @Test fun anEmptyBoardHasNoKings() {
        val d = PositionDraft()
        assertEquals(listOf("White has no king.", "Black has no king."), d.problems())
    }

    @Test fun twoKingsOfOneColourIsAProblem() {
        val d = start()
        d.put(sq("e4"), Piece(PieceType.KING, PieceColor.WHITE))
        assertTrue(d.problems().any { it.contains("White has 2 kings") })
    }

    @Test fun pawnsCannotStandOnTheFirstOrLastRank() {
        val d = start()
        d.put(sq("a1"), Piece(PieceType.PAWN, PieceColor.WHITE))
        assertTrue(d.problems().any { it.contains("first or last rank") })
    }

    @Test fun theSideNotToMoveCannotBeInCheck() {
        // White's rook gives check to the black king, and it is White's move: Black would have left its king in check.
        val d = PositionDraft.fromFen("4k3/8/8/8/8/8/8/4RK2 w - - 0 1")!!
        assertEquals(listOf("Black is in check, but it is White's move."), d.problems())
        d.turn = PieceColor.BLACK
        assertTrue(d.problems().isEmpty())
    }

    @Test fun movingAPieceOntoAnotherTakesIt() {
        val d = start()
        d.move(sq("e2"), sq("e7"))
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), d.pieceAt(sq("e7")))
        assertNull(d.pieceAt(sq("e2")))
    }

    @Test fun everyFenItWritesIsOneTheBoardTakes() {
        val d = PositionDraft.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")!!
        assertNotNull(ChessBoard.fromFen(d.toFen()))
        d.clear()
        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", d.toFen())
    }
}
