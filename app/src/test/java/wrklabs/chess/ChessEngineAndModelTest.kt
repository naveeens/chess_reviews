package wrklabs.chess

import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.classifier.MoveClassifier
import wrklabs.chess.classifier.ReviewedPosition
import wrklabs.chess.engine.*
import wrklabs.chess.model.*

class ChessEngineAndModelTest {

    @Test
    fun testScoreEvaluationMath() {
        val evenCp = Score.Centipawns(0)
        assertEquals(50.0, evenCp.winPercent(), 0.1)
        assertEquals("0.00", evenCp.format())

        val whiteAdv = Score.Centipawns(124)
        val blackAdvTurn = whiteAdv.toWhitePov(PieceColor.BLACK)
        assertEquals(-124, (blackAdvTurn as Score.Centipawns).value)
        assertEquals("+1.24", whiteAdv.format())
        assertEquals("−1.24", blackAdvTurn.format())

        val mateIn2 = Score.Mate(2)
        assertEquals(100.0, mateIn2.winPercent(), 0.01)
        assertEquals("M2", mateIn2.format())
        assertEquals("M2", mateIn2.formatShort())

        val gettingMatedIn1 = Score.Mate(-1)
        assertEquals(0.0, gettingMatedIn1.winPercent(), 0.01)
        assertEquals("−M1", gettingMatedIn1.format())
    }

    @Test
    fun testUciParser() {
        val infoLine = "info depth 18 seldepth 22 multipv 1 score cp 35 nodes 24000 nps 1200000 time 20 pv e2e4 e7e5 g1f3"
        val parsed = UciParser.parseInfo(infoLine)
        assertNotNull(parsed)
        assertEquals(18, parsed?.depth)
        assertEquals(1, parsed?.multipv)
        assertTrue(parsed?.score is Score.Centipawns)
        assertEquals(35, (parsed?.score as Score.Centipawns).value)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), parsed?.pv)

        val bestMoveLine = "bestmove e2e4 ponder e7e5"
        assertEquals("e2e4", UciParser.parseBestMove(bestMoveLine))
    }

    @Test
    fun testBoardMoveGenerationAndCheckmate() {
        val board = ChessBoard.fromFen()
        val legalMoves = board.legalMoves()
        assertEquals(20, legalMoves.size)

        // Scholar's mate: 1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7#
        val b1 = board.makeMove(legalMoves.first { it.uci == "e2e4" })
        val b2 = b1.makeMove(b1.legalMoves().first { it.uci == "e7e5" })
        val b3 = b2.makeMove(b2.legalMoves().first { it.uci == "d1h5" })
        val b4 = b3.makeMove(b3.legalMoves().first { it.uci == "b8c6" })
        val b5 = b4.makeMove(b4.legalMoves().first { it.uci == "f1c4" })
        val b6 = b5.makeMove(b5.legalMoves().first { it.uci == "g8f6" })
        val mateMove = b6.legalMoves().first { it.uci == "h5f7" }
        assertEquals("Qxf7#", mateMove.san)

        val b7 = b6.makeMove(mateMove)
        assertTrue(b7.isCheck())
        assertTrue(b7.isCheckmate())
        assertEquals(0, b7.legalMoves().size)
    }

    @Test
    fun testGameTreeAndVariations() {
        var tree = GameTree.create()
        val res1 = tree.addMove(ROOT_ID, "e2e4")
        assertNotNull(res1)
        tree = res1!!.first
        val e4Id = res1.second

        // Add main line e7e5
        val res2 = tree.addMove(e4Id, "e7e5")
        assertNotNull(res2)
        tree = res2!!.first

        // Add variation c7c5 (Sicilian)
        val res3 = tree.addMove(e4Id, "c7c5")
        assertNotNull(res3)
        tree = res3!!.first
        val c5Id = res3.second

        val parentNode = tree.nodes[e4Id]!!
        assertEquals(2, parentNode.children.size)

        // Promote Sicilian variation to main line
        tree = tree.promoteVariation(c5Id)
        val promotedParent = tree.nodes[e4Id]!!
        assertEquals(c5Id, promotedParent.children[0])
    }

    @Test
    fun testMoveClassifierAccuracy() {
        // Equal evaluation: 100% accuracy
        assertEquals(100.0, MoveClassifier.moveAccuracy(50.0, 50.0), 0.1)
        // Gain: 100% accuracy
        assertEquals(100.0, MoveClassifier.moveAccuracy(50.0, 70.0), 0.1)
        // Drop in win % lowers accuracy
        val acc = MoveClassifier.moveAccuracy(70.0, 40.0)
        assertTrue(acc < 50.0)
    }
}
