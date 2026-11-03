package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.model.Square
import wrklabs.chess.training.Vision
import kotlin.random.Random

class DiagonalDrillTest {
    private fun sq(name: String) = Square(name[0] - 'a', name[1] - '1')

    @Test fun theOtherSquareIsNeverTheSameSquare() {
        val random = Random(1)
        repeat(2000) {
            val t = Square(random.nextInt(8), random.nextInt(8))
            assertFalse(Vision.diagonalOther(t, random) == t)
        }
    }

    @Test fun aboutHalfAreOnTheDiagonal_andMostOfTheRestAreTheSameColour() {
        val random = Random(7)
        var yes = 0; var noSame = 0; var noOther = 0
        repeat(8000) {
            val t = Square(random.nextInt(8), random.nextInt(8))
            val o = Vision.diagonalOther(t, random)
            when {
                Vision.sameDiagonal(t, o) -> yes++
                (t.file + t.rank) % 2 == (o.file + o.rank) % 2 -> noSame++
                else -> noOther++
            }
        }
        assertTrue("yes=$yes", yes in 3700..4300)
        // Three quarters of the "no" questions are the hard kind.
        assertTrue("noSame=$noSame noOther=$noOther", noSame > 2 * noOther)
        assertTrue(noOther > 0)
    }

    @Test fun theExplanationGivesTheRuleAndTheNumbers() {
        assertEquals("c1 to h6: 5 files, 5 ranks. Equal, so yes.", Vision.diagonalWhy(sq("c1"), sq("h6")))
        assertEquals("c1 to e3: 2 files, 2 ranks. Equal, so yes.", Vision.diagonalWhy(sq("c1"), sq("e3")))
        assertEquals("c1 to g3: 4 files, 2 ranks. Not equal, so no.", Vision.diagonalWhy(sq("c1"), sq("g3")))
        assertEquals("c1 and c2 are different colours, so never on one diagonal.", Vision.diagonalWhy(sq("c1"), sq("c2")))
    }
}
