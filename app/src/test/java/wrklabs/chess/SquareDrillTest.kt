package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.model.Square
import wrklabs.chess.ui.isLightSquare
import wrklabs.chess.ui.nameChoices
import kotlin.random.Random

class SquareDrillTest {
    @Test fun `a1 is dark and h1 is light`() {
        assertFalse(isLightSquare(Square.fromName("a1")))
        assertTrue(isLightSquare(Square.fromName("h1")))
        assertTrue(isLightSquare(Square.fromName("a8")))
        assertFalse(isLightSquare(Square.fromName("h8")))
    }

    @Test fun `the choices hold the answer once and three others from its file or rank`() {
        val target = Square.fromName("e4")
        repeat(50) { seed ->
            val c = nameChoices(target, Random(seed))
            assertEquals(4, c.size)
            assertEquals(4, c.toSet().size)
            assertEquals(1, c.count { it == "e4" })
            c.filter { it != "e4" }.forEach { assertTrue(it[0] == 'e' || it[1] == '4') }
        }
    }
}
