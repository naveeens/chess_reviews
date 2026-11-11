package wrklabs.chess

import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.classifier.GameReport
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.classifier.MoveReview
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID
import wrklabs.chess.review.GameInfo
import wrklabs.chess.ui.ReviewUiState
import wrklabs.chess.ui.playRequestFrom

class PlayFromReviewTest {
    private val sans = listOf("e4", "e5", "Qh5", "Nc6")
    private var tree = GameTree.create()
    private val ids = mutableListOf<String>()

    init {
        var id = ROOT_ID
        for (san in sans) { val (t, n) = tree.addMoveBySan(id, san)!!; tree = t; id = n; ids += n }
    }

    private fun state(at: Int, cls: MoveClass, mine: PieceColor) = ReviewUiState(
        info = GameInfo("k", "chesscom", "me", "opp", 1500, 1600, "*", "blitz", 0L, mine),
        tree = tree, mainline = tree.getMainLine(), gameIds = tree.getMainLine().map { it.id }.toSet() + ROOT_ID,
        currentNodeId = ids[at],
        report = GameReport(mapOf(ids[at] to MoveReview(ids[at], at + 1, if (at % 2 == 0) PieceColor.WHITE else PieceColor.BLACK, cls, 50.0, 30.0, 0.2, 60.0, null, Score.Centipawns(0))), emptyMap(), emptyMap()),
    )

    @Test fun aMistakeOfMineIsPlayedAgainFromBeforeIt() {
        val (req, retry) = playRequestFrom(state(2, MoveClass.MISTAKE, PieceColor.WHITE))
        assertTrue(retry)
        assertEquals(tree.nodes.getValue(ids[1]).fen, req.fen)
        assertEquals(true, req.userWhite); assertEquals(1600, req.botElo)
        assertTrue(req.label, req.label.contains("Qh5"))
    }

    @Test fun aGoodMoveIsPlayedOnFromWhereItLeftTheBoard() {
        val (req, retry) = playRequestFrom(state(2, MoveClass.BEST, PieceColor.WHITE))
        assertFalse(retry)
        assertEquals(tree.nodes.getValue(ids[2]).fen, req.fen); assertNull(req.userWhite)
    }

    @Test fun anOpponentsMistakeIsNotWhatIRetry() {
        val (req, retry) = playRequestFrom(state(2, MoveClass.BLUNDER, PieceColor.BLACK))   // White erred, and I am Black
        assertFalse(retry)
        assertEquals(tree.nodes.getValue(ids[2]).fen, req.fen)
        assertEquals(1500, req.botElo)                                                       // the opponent is White, rated 1500
    }
}
