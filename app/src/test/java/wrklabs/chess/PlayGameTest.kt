package wrklabs.chess

import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.importer.PgnParser
import wrklabs.chess.maia.MaiaBot
import wrklabs.chess.play.EndReason
import wrklabs.chess.play.PlayGame
import kotlin.random.Random

class PlayGameTest {
    private fun game(vararg ucis: String, fen: String? = null) = if (fen == null) PlayGame.replay(ucis.toList()) else PlayGame.replay(ucis.toList(), fen = fen)

    @Test fun aGoingGameHasNoEnd() { assertNull(game("e2e4", "e7e5").end) }

    @Test fun foolsMateIsCheckmateForBlack() {
        val end = game("f2f3", "e7e5", "g2g4", "d8h4").end!!
        assertEquals("0-1", end.result); assertEquals(EndReason.Checkmate, end.reason); assertEquals(PieceColor.BLACK, end.winner)
    }

    @Test fun stalemateIsADraw() {
        // Black's king on a8, White's on b6 and a queen on c1: Qc7 takes away every square without giving check.
        val stale = game("c1c7", fen = "k7/8/1K6/8/8/8/8/2Q5 w - - 0 1")
        assertEquals(EndReason.Stalemate, stale.end!!.reason)
        assertTrue(stale.end!!.draw)
        assertNull(stale.end!!.winner)
    }

    @Test fun threeTimesTheSamePositionIsADraw() {
        val g = game("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(EndReason.Repetition, g.end!!.reason)
        assertNull(game("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1").end)
    }

    @Test fun bareKingsAndASingleMinorPieceCannotMate() {
        assertEquals(EndReason.Insufficient, game("a1b1", fen = "k7/8/8/8/8/8/8/K1N5 w - - 0 1").end!!.reason)
        // With a pawn left there is still play.
        assertNull(game("a1b1", fen = "k7/8/8/8/8/8/p7/K1N5 w - - 0 1").end)
        // Two knights can, in theory, so the game goes on.
        assertNull(game("a1b1", fen = "k7/8/8/8/8/8/8/K1NN4 w - - 0 1").end)
    }

    @Test fun theFiftyMoveRuleDrawsTheGame() {
        val g = game("a1a2", fen = "k7/8/8/8/8/8/8/K6R w - - 99 80")
        assertEquals(EndReason.FiftyMoves, g.end!!.reason)
    }

    @Test fun resigningEndsItForTheOtherSide() {
        val g = game("e2e4").resign(PieceColor.BLACK)
        assertEquals("1-0", g.end!!.result); assertEquals(EndReason.Resignation, g.end!!.reason)
    }

    @Test fun anIllegalMoveOrAMoveAfterTheEndIsRefused() {
        assertNull(PlayGame.start().playUci("e2e5"))
        assertNull(game("f2f3", "e7e5", "g2g4", "d8h4").playUci("a2a3"))
    }

    @Test fun thePgnReadsBackToTheSameMoves() {
        val g = game("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5c6", "d7c6", "e1g1")
        val pgn = g.pgn("You", "Maia", 1500, 1400, "2026.11.04")
        val parsed = PgnParser.parse(pgn)
        assertEquals(g.moves.map { it.san }, parsed.moves)
        assertEquals("You", parsed.headers["White"]); assertEquals("1400", parsed.headers["BlackElo"]); assertEquals("*", parsed.headers["Result"])
        assertTrue(pgn.contains("3. Bb5 a6 4. Bxc6 dxc6 5. O-O"))
    }

    @Test fun castlingAndPromotionAreWritten() {
        val g = game("a7a8q", fen = "8/P6k/8/8/8/8/8/K7 w - - 0 1")
        assertEquals("a8=Q", g.moves.single().san)
    }
}

class MaiaBotTest {
    @Test fun movesAreDrawnInProportionToTheirOdds() {
        val probs = mapOf("e2e4" to 0.6f, "d2d4" to 0.3f, "g1f3" to 0.1f)
        val r = Random(5)
        val n = (1..6000).map { MaiaBot.sample(probs, r) }.groupingBy { it }.eachCount()
        assertEquals(0.6, n.getValue("e2e4") / 6000.0, 0.03)
        assertEquals(0.1, n.getValue("g1f3") / 6000.0, 0.02)
    }

    @Test fun freakMovesBelowTheFloorAreNeverPlayed() {
        val probs = mapOf("e2e4" to 0.995f, "h2h4" to 0.005f)
        val r = Random(1)
        assertTrue((1..2000).all { MaiaBot.sample(probs, r) == "e2e4" })
    }

    @Test fun noMovesGivesNothingAndTinyOddsStillGiveAMove() {
        assertNull(MaiaBot.sample(emptyMap(), Random(1)))
        assertNotNull(MaiaBot.sample(mapOf("a" to 0.004f, "b" to 0.003f), Random(1)))
    }
}
