package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import wrklabs.chess.classifier.MoveClassifier
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move
import wrklabs.chess.model.Square

class SacrificeTest {
    private fun after(fen: String, uci: String): String =
        ChessBoard.fromFen(fen).makeMove(Move(Square.fromName(uci.substring(0, 2)), Square.fromName(uci.substring(2, 4)))).toFen()

    private fun detail(fen: String, uci: String, pv: List<String>) =
        MoveClassifier.sacrificeDetail(fen, after(fen, uci), pv, ChessBoard.fromFen(fen).turn, false, null)

    @Test
    fun aCheckThatLeavesARookUnderAttackIsNotASacrifice() {
        // ...Rb1+ with the g1 rook en prise: White must answer the check, and Rxg1 would only be a trade.
        assertNull(detail("1r6/8/k7/8/8/8/8/4KRr1 b - - 0 1", "b8b1", listOf("e1e2", "g1f1")))
    }

    @Test
    fun aCheckmateIsNeverASacrifice() {
        assertNull(detail("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1", "a1a8", emptyList()))
    }

    @Test
    fun aRookThatTheEngineLineLosesIsASacrifice() {
        val d = detail("6k1/8/8/4p3/8/8/8/3R2K1 w - - 0 1", "d1d4", listOf("e5d4"))
        assertNotNull(d)
        assertEquals(false, d!!.declined)
    }

    @Test
    fun aTradeIsNotASacrifice() {
        // Bxe5 dxe5: a bishop for a knight.
        assertNull(detail("6k1/8/3p4/4n3/8/2B5/8/6K1 w - - 0 1", "c3e5", listOf("d6e5")))
    }

    @Test
    fun leavingAPieceThatWasAlreadyAttackedIsNotASacrifice() {
        // The knight on h4 was attacked by the bishop before White moved; the king move offers nothing.
        assertNull(detail("6k1/4b3/8/8/7N/8/8/R3K3 w - - 0 1", "e1d2", listOf("g8f8")))
    }

    @Test
    fun movingAPieceToASquareWhereItCanBeTakenForFreeIsAnOfferedSacrifice() {
        val d = detail("6k1/6p1/8/8/8/8/8/2B1K3 w - - 0 1", "c1h6", listOf("g8h7"))
        assertNotNull(d)
        assertEquals(true, d!!.declined)
    }
}
