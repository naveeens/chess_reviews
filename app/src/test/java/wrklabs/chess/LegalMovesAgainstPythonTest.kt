package wrklabs.chess

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.model.ChessBoard

/** The move generator against python-chess on thousands of positions from random games. */
class LegalMovesAgainstPythonTest {
    @Test fun legalMovesMatchPythonChessEverywhere() {
        val cases = Json.parseToJsonElement(javaClass.getResourceAsStream("/legal_fixtures.json")!!.bufferedReader().readText()).jsonArray.map { it.jsonObject }
        val wrong = mutableListOf<String>()
        for (c in cases) {
            val fen = c["fen"]!!.jsonPrimitive.content
            val want = c["legal"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
            val got = ChessBoard.fromFen(fen).legalMoves().map { it.uci }.toSet()
            if (want != got) wrong += "$fen\n   missing=${want - got} extra=${got - want}"
        }
        println("MISMATCHES ${wrong.size} of ${cases.size}")
        wrong.take(12).forEach { println("MISMATCH $it") }
        assertTrue("${wrong.size} of ${cases.size} positions differ", wrong.isEmpty())
    }
}
