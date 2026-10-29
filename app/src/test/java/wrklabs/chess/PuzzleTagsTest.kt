package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.ui.puzzleTags

class PuzzleTagsTest {
    @Test
    fun onlyTheTacticsAreKeptInReadableWords() {
        assertEquals(listOf("Quiet move", "Advanced pawn", "Pawn endgame"), puzzleTags("advancedPawn crushing endgame pawnEndgame quietMove veryLong"))
    }

    @Test
    fun aMateComesFirstAndIsWrittenOut() {
        assertEquals(listOf("Mate in 2", "Fork", "Pin"), puzzleTags("pin fork mateIn2 short master kingsideAttack"))
        assertEquals(listOf("X-ray attack"), puzzleTags("xRayAttack long"))
    }

    @Test
    fun nothingButNoiseLeavesNothing() {
        assertEquals(emptyList<String>(), puzzleTags("crushing short opening"))
    }
}
