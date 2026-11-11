package wrklabs.chess

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.maia.MaiaEncoder
import wrklabs.chess.model.ChessBoard

/**
 * The encoder against Maia-3's own Python code: for 63 positions (lesson games, castling, en passant,
 * promotions, short and full histories, both colours) the one-hot input and the move numbers must be identical.
 */
class MaiaEncoderTest {
    private val fixtures = Json.parseToJsonElement(
        javaClass.getResourceAsStream("/maia_fixtures.json")!!.bufferedReader().readText()
    ).jsonArray.map { it.jsonObject }

    @Test fun inputMatchesThePythonImplementationExactly() {
        assertTrue(fixtures.size >= 60)
        for ((n, f) in fixtures.withIndex()) {
            val fens = f["fens"]!!.jsonArray.map { it.jsonPrimitive.content }
            val expected = f["ones"]!!.jsonArray.map { it.jsonPrimitive.int }.toSet()
            val got = MaiaEncoder.tokens(fens)
            assertEquals(64 * 97, got.size)
            val ones = got.indices.filter { got[it] == 1f }.toSet()
            assertEquals("fixture $n (${fens.last()})", expected, ones)
            assertTrue("only zeros and ones", got.all { it == 0f || it == 1f })
        }
    }

    @Test fun legalMovesAndTheirNumbersMatch() {
        for ((n, f) in fixtures.withIndex()) {
            val fen = f["fens"]!!.jsonArray.last().jsonPrimitive.content
            val board = ChessBoard.fromFen(fen)
            val expected = f["moves"]!!.jsonObject.mapValues { it.value.jsonArray[0].jsonPrimitive.int }
            val got = board.legalMoves().associate { it.uci to MaiaEncoder.moveIndex(it.uci, board.turn) }
            assertEquals("fixture $n ($fen)", expected, got)
        }
    }

    @Test fun movesAreNumberedTheSameForBothColours() {
        assertEquals(12 * 64 + 28, MaiaEncoder.moveIndex("e2e4", PieceColor.WHITE))
        // Black's e7e5 is white's e2e4 turned over.
        assertEquals(12 * 64 + 28, MaiaEncoder.moveIndex("e7e5", PieceColor.BLACK))
        assertEquals(4096 + (0 * 8 + 1) * 4 + 0, MaiaEncoder.moveIndex("a7b8q", PieceColor.WHITE))
        assertEquals(4096 + (7 * 8 + 7) * 4 + 3, MaiaEncoder.moveIndex("h2h1n", PieceColor.BLACK))
        assertNull(MaiaEncoder.moveIndex("a2a3q", PieceColor.WHITE))
    }

    @Test fun probabilitiesCoverTheLegalMovesAndSumToOne() {
        val fen = ChessBoard.DEFAULT_FEN
        val logits = FloatArray(MaiaEncoder.MOVES) { (it % 17) * 0.3f }
        val p = MaiaEncoder.probabilities(fen, logits)
        assertEquals(20, p.size)
        assertEquals(1.0, p.values.sum().toDouble(), 1e-5)
    }

    @Test fun aShortGameRepeatsItsFirstPosition() {
        val a = MaiaEncoder.tokens(listOf(ChessBoard.DEFAULT_FEN))
        val b = MaiaEncoder.tokens(List(8) { ChessBoard.DEFAULT_FEN })
        assertArrayEquals(a, b, 0f)
    }
}
