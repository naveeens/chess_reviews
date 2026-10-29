package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.classifier.MoveClassifier
import wrklabs.chess.classifier.ReviewedPosition
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID

class ClassifierTest {
    private fun grade(fen: String, uci: String, before: ReviewedPosition, after: ReviewedPosition): MoveClass {
        val (tree, id) = GameTree.create(fen).addMove(ROOT_ID, uci)!!
        val line = listOf(tree.nodes.getValue(ROOT_ID), tree.nodes.getValue(id))
        return MoveClassifier.classifyGame(line, mapOf(line[0].fen to before, line[1].fen to after)).moves.getValue(id).cls
    }

    private val start = "6k1/5ppp/8/8/8/8/5PPP/R3K3 w - - 0 1"
    private fun pos(score: Score, best: String? = null, second: Score? = null) = ReviewedPosition(score, best, emptyList(), second)

    @Test
    fun missingAShortMateIsAMiss() {
        assertEquals(MoveClass.MISS, grade(start, "a1a7", pos(Score.Mate(2), "a1a8"), pos(Score.Centipawns(250))))
        assertEquals(MoveClass.MISS, grade(start, "a1a7", pos(Score.Mate(3), "a1a8"), pos(Score.Centipawns(900))))
        assertEquals(MoveClass.BLUNDER, grade(start, "a1a7", pos(Score.Mate(2), "a1a8"), pos(Score.Centipawns(-100))))
    }

    @Test
    fun keepingAShortMateIsNotAMiss() {
        // A slower mate is still a mate: nothing was let go.
        assertEquals(MoveClass.EXCELLENT, grade(start, "a1a7", pos(Score.Mate(2), "a1a8"), pos(Score.Mate(4))))
    }

    @Test
    fun aLongMateLetGoIsGradedByWhatItCost() {
        assertEquals(MoveClass.MISTAKE, grade(start, "a1a7", pos(Score.Mate(6), "a1a8"), pos(Score.Centipawns(450))))
        assertEquals(MoveClass.BLUNDER, grade(start, "a1a7", pos(Score.Mate(6), "a1a8"), pos(Score.Centipawns(100))))
    }

    @Test
    fun beingMatedFromAPositionThatWasNotLostIsABlunderHoweverFarOff() {
        assertEquals(MoveClass.BLUNDER, grade(start, "a1a7", pos(Score.Centipawns(0), "a1a8"), pos(Score.Mate(-2))))
        assertEquals(MoveClass.BLUNDER, grade(start, "a1a7", pos(Score.Centipawns(0), "a1a8"), pos(Score.Mate(-6))))
        assertEquals(MoveClass.BLUNDER, grade(start, "a1a7", pos(Score.Centipawns(400), "a1a8"), pos(Score.Mate(-4))))
        assertEquals(MoveClass.BLUNDER, grade(start, "a1a7", pos(Score.Mate(5), "a1a8"), pos(Score.Mate(-8))))
    }

    @Test
    fun whenAlreadyLostBeingMatedCostsLittle() {
        assertEquals(MoveClass.GOOD, grade(start, "a1a7", pos(Score.Centipawns(-900), "a1a8"), pos(Score.Mate(-2))))
    }

    private fun gradeReply(first: ReviewedPosition, second: ReviewedPosition, third: ReviewedPosition): MoveClass {
        val (t1, id1) = GameTree.create(start).addMove(ROOT_ID, "a1a7")!!
        val (t2, id2) = t1.addMove(id1, "g8f8")!!
        val line = listOf(t2.nodes.getValue(ROOT_ID), t2.nodes.getValue(id1), t2.nodes.getValue(id2))
        val evals = mapOf(line[0].fen to first, line[1].fen to second, line[2].fen to third)
        return MoveClassifier.classifyGame(line, evals).moves.getValue(id2).cls
    }

    // White errs from level to -300 (25%), handing Black 25 points. Black's own move then gives back some of them.
    private val whiteErrs get() = listOf(pos(Score.Centipawns(0), "a1a8"), pos(Score.Centipawns(-300)))

    @Test
    fun givingBackAboutWhatTheOpponentHandedYouIsAMiss() {
        assertEquals(MoveClass.MISS, gradeReply(whiteErrs[0], whiteErrs[1], pos(Score.Centipawns(0))))
    }

    @Test
    fun aSmallerMistakeOfTheirsLeavesNothingToHaveMissed() {
        // White errs from level to -150 (a Mistake, 13 points); Black giving that back is a Mistake, not a Miss.
        assertEquals(MoveClass.MISTAKE, gradeReply(pos(Score.Centipawns(0), "a1a8"), pos(Score.Centipawns(-150)), pos(Score.Centipawns(0))))
    }

    @Test
    fun givingBackFarMoreThanYouWereHandedIsABlunder() {
        assertEquals(MoveClass.BLUNDER, gradeReply(whiteErrs[0], whiteErrs[1], pos(Score.Centipawns(600))))
    }

    @Test
    fun givingBackLittleIsJustAMistake() {
        assertEquals(MoveClass.MISTAKE, gradeReply(whiteErrs[0], whiteErrs[1], pos(Score.Centipawns(-150))))
    }

    @Test
    fun theOnlyWayOutOfACheckIsNotGreat() {
        val inCheck = "4r1k1/8/8/8/8/8/8/4K3 w - - 0 1"
        val cls = grade(inCheck, "e1f1", pos(Score.Centipawns(50), "e1f1", Score.Centipawns(-300)), pos(Score.Centipawns(50)))
        assertEquals(MoveClass.BEST, cls)
    }

    @Test
    fun theOnlyGoodMoveWithoutACheckIsStillGreat() {
        val quiet = "6k1/8/8/8/8/8/8/4K2R w - - 0 1"
        val cls = grade(quiet, "e1f1", pos(Score.Centipawns(50), "e1f1", Score.Centipawns(-300)), pos(Score.Centipawns(50)))
        assertEquals(MoveClass.GREAT, cls)
    }

    private val rookSac = "6k1/8/8/4p3/8/8/8/3R2K1 w - - 0 1"

    @Test
    fun aSoundSacrificeThatTheEngineLikesIsBrilliant() {
        val before = pos(Score.Centipawns(100), "d1d4", Score.Centipawns(0))
        val after = ReviewedPosition(Score.Centipawns(120), null, listOf("e5d4"))
        assertEquals(MoveClass.BRILLIANT, grade(rookSac, "d1d4", before, after))
    }

    @Test
    fun aSacrificeWhenAlreadyWinningWithoutItIsNotBrilliant() {
        val before = pos(Score.Centipawns(1000), "d1d4", Score.Centipawns(900))
        val after = ReviewedPosition(Score.Centipawns(1000), null, listOf("e5d4"))
        assertEquals(MoveClass.BEST, grade(rookSac, "d1d4", before, after))
    }

    @Test
    fun aSacrificeThatIsNotTheBestMoveIsNotBrilliant() {
        val before = pos(Score.Centipawns(100), "d1d5", Score.Centipawns(0))
        val after = ReviewedPosition(Score.Centipawns(95), null, listOf("e5d4"))
        assertEquals(true, grade(rookSac, "d1d4", before, after) != MoveClass.BRILLIANT)
    }
}
