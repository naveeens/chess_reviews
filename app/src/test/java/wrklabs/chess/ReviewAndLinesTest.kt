package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.engine.Score
import wrklabs.chess.engine.winPercent
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.review.ReviewService
import wrklabs.chess.ui.pvToMoves

class ReviewAndLinesTest {
    @Test
    fun winChanceRoundTripsThroughCentipawns() {
        for (cp in listOf(-600, -120, -20, 0, 35, 250, 700)) {
            val win = Score.Centipawns(cp).winPercent()
            val back = ReviewService.cpFromWin(win)
            assertTrue("cp $cp came back as $back", kotlin.math.abs(back - cp) <= 2)
        }
    }

    @Test
    fun engineLineBecomesNumberedMoves() {
        val moves = pvToMoves(ChessBoard.DEFAULT_FEN, listOf("e2e4", "e7e5", "g1f3", "b8c6"))
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), moves.map { it.san })
        assertEquals(listOf("1.", null, "2.", null), moves.map { it.number })
    }

    @Test
    fun lineFromBlackToMoveStartsWithAnEllipsis() {
        val fen = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 1"
        val moves = pvToMoves(fen, listOf("g8f6", "c2c4"))
        assertEquals(listOf("1…", "2."), moves.map { it.number })
    }

    @Test
    fun anIllegalMoveEndsTheLine() {
        assertEquals(1, pvToMoves(ChessBoard.DEFAULT_FEN, listOf("e2e4", "e2e4", "g1f3")).size)
    }

    @Test
    fun sanOfTurnsUciIntoSan() {
        assertEquals("Nf3", ReviewService.sanOf(ChessBoard.DEFAULT_FEN, "g1f3"))
        assertEquals("zz", ReviewService.sanOf(ChessBoard.DEFAULT_FEN, "zz"))
    }
}
