package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.db.Puzzle
import wrklabs.chess.model.ChessBoard
import java.io.File

class PuzzleSetTest {
    private val puzzles: List<Puzzle> by lazy {
        File("src/main/assets/puzzles.csv").readLines().drop(1).map { Puzzle.parse(it) ?: error("bad row: $it") }
    }

    @Test
    fun theSetHasTheLevelsItWasBuiltWith() {
        assertEquals(1000, puzzles.size)
        assertEquals(1000, puzzles.map { it.id }.toSet().size)
        assertEquals(mapOf("easy" to 300, "medium" to 450, "hard" to 250), puzzles.groupingBy { it.level }.eachCount())
        assertTrue(puzzles.all { it.rating >= 1800 })
        assertEquals((1..1000).toList(), puzzles.map { it.seq })
    }

    @Test
    fun everyLinePlaysOutAndEndsOnYourMove() {
        for (p in puzzles) {
            assertTrue("${p.id}: a line ends on your move", p.moves.size % 2 == 0 && p.yourMoves >= 2)
            var board = ChessBoard.fromFen(p.fen)
            for (uci in p.moves) {
                val move = board.legalMoves().find { it.uci == uci }
                assertNotNull("${p.id}: $uci is not legal in ${board.toFen()}", move)
                board = board.makeMove(move!!)
            }
        }
    }

    @Test
    fun aRowIsParsedAndABadOneIsNot() {
        val p = Puzzle.parse("1,easy,9sSDa,r1rq4/pp2ppkp/3p1np1/4P3/1Pb5/2N5/P2QBPPP/1R3RK1 b - - 0 16,c4e2 e5f6 e7f6 c3e2,1800,advantage intermezzo,https://lichess.org/3vYyPxUj/black#32")!!
        assertEquals(listOf("c4e2", "e5f6", "e7f6", "c3e2"), p.moves)
        assertEquals(2, p.yourMoves)
        assertEquals(null, Puzzle.parse("not,a,row"))
    }
}
