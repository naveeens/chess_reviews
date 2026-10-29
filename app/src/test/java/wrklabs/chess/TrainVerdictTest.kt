package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.ui.Verdict
import wrklabs.chess.ui.verdictFor

class TrainVerdictTest {
    @Test
    fun aMoveAsGoodAsTheBestIsSolved() {
        assertEquals(Verdict.Solved, verdictFor(MoveClass.BEST, 0.0, false))
        assertEquals(Verdict.Solved, verdictFor(MoveClass.GREAT, 0.0, false))
        assertEquals(Verdict.Solved, verdictFor(MoveClass.EXCELLENT, 0.003, false))
    }

    @Test
    fun aCheckmateIsSolved() {
        assertEquals(Verdict.Solved, verdictFor(MoveClass.GOOD, 0.04, true))
    }

    @Test
    fun aGoodMoveThatIsNotTheBestIsNeitherSolvedNorWrong() {
        assertEquals(Verdict.Better, verdictFor(MoveClass.EXCELLENT, 0.015, false))
        assertEquals(Verdict.Better, verdictFor(MoveClass.GOOD, 0.04, false))
    }

    @Test
    fun anythingWorseIsWrong() {
        for (cls in listOf(MoveClass.INACCURACY, MoveClass.MISTAKE, MoveClass.MISS, MoveClass.BLUNDER)) {
            assertEquals(Verdict.Wrong, verdictFor(cls, 0.3, false))
        }
    }
}
