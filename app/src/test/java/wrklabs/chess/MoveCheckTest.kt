package wrklabs.chess

import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.play.MoveCheck

class MoveCheckTest {
    private fun cp(v: Int) = Score.Centipawns(v)

    @Test fun losingAPieceInAnEvenGameIsABlunder() {
        assertTrue(MoveCheck.isBlunder(cp(20), cp(-320), PieceColor.WHITE))
    }

    @Test fun blackLosesWhenTheScoreRisesForWhite() {
        assertTrue(MoveCheck.isBlunder(cp(-20), cp(320), PieceColor.BLACK))
        assertFalse(MoveCheck.isBlunder(cp(-20), cp(-320), PieceColor.BLACK))
    }

    @Test fun aSmallSlipIsLeftAlone() {
        assertFalse(MoveCheck.isBlunder(cp(30), cp(-20), PieceColor.WHITE))
    }

    @Test fun throwingAwayAWinIsCaughtButSimplifyingWhileAheadIsNot() {
        assertTrue(MoveCheck.isBlunder(cp(500), cp(0), PieceColor.WHITE))
        assertFalse(MoveCheck.isBlunder(cp(1200), cp(800), PieceColor.WHITE))
    }

    @Test fun allowingMateIsABlunder() {
        assertTrue(MoveCheck.isBlunder(cp(100), Score.Mate(-2), PieceColor.WHITE))
    }

    @Test fun aGainIsNeverADrop() {
        assertEquals(0.0, MoveCheck.drop(cp(-300), cp(200), PieceColor.WHITE), 0.0)
    }

    @Test fun scoresAreShownFromYourSide() {
        assertTrue(MoveCheck.textFor(cp(30), PieceColor.WHITE).startsWith("+"))
        assertTrue(MoveCheck.textFor(cp(30), PieceColor.BLACK).startsWith("−"))
    }
}
