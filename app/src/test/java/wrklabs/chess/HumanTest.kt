package wrklabs.chess

import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.maia.Human
import wrklabs.chess.maia.HumanMove

class HumanTest {
    private fun h(played: Float, top: Float) = HumanMove(1500, played, "g1f3", top)

    @Test fun aSlipSaysHowOftenItIsPlayedAndWhatPeoplePlayInstead() {
        assertEquals("3% play this at 1500 · 41% play Nf3", Human.note(h(0.03f, 0.41f), "Nf3", MoveClass.MISTAKE))
    }

    @Test fun aSlipThatIsTheCommonMoveSaysSo() {
        assertEquals("The most common move at 1500 (41%)", Human.note(h(0.41f, 0.41f), "Nf3", MoveClass.BLUNDER))
    }

    @Test fun aFindSaysHowRareItIs() {
        assertEquals("Only 2% find this at 1500", Human.note(h(0.02f, 0.5f), "Nf3", MoveClass.GREAT))
        assertEquals("Only <1% find this at 1500", Human.note(h(0.002f, 0.5f), "Nf3", MoveClass.BRILLIANT))
    }

    @Test fun ordinaryMovesNeedNoComment() {
        for (c in listOf(MoveClass.BEST, MoveClass.EXCELLENT, MoveClass.GOOD, MoveClass.BOOK)) assertNull(Human.note(h(0.1f, 0.4f), "Nf3", c))
    }

    @Test fun ratingsAreHeldInsideWhatTheModelKnows() {
        assertEquals(600, Human.clampElo(300))
        assertEquals(2600, Human.clampElo(3200))
        assertEquals(1500, Human.clampElo(null))
        assertEquals(1234, Human.clampElo(null, 1234))
        assertEquals(1800, Human.clampElo(1800))
    }
}

class HumanRepliesTest {
    private val tree = wrklabs.chess.training.LessonPgn.parseAll(
        "[Event \"x\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 (3... Nf6 4. Ng5) 4. c3 *"
    ).single().tree

    @Test fun maiaReadsTheLastEightPositionsEndingWithTheOneToReplyIn() {
        val after3 = tree.nodes.values.first { it.san == "Bc4" }
        val fens = wrklabs.chess.maia.HumanReplies.positionsTo(tree, after3)
        assertEquals(after3.fen, fens.last())
        assertEquals(6, fens.size) // the start, then 1.e4 e5 2.Nf3 Nc6 3.Bc4
        assertEquals(tree.nodes.getValue("root").fen, fens.first())
    }

    @Test fun longGamesAreCutToEightPositions() {
        val last = tree.nodes.values.maxByOrNull { it.ply }!!
        val long = wrklabs.chess.maia.HumanReplies.positionsTo(tree, last)
        assertTrue(long.size <= 8)
        assertEquals(last.fen, long.last())
    }

    @Test fun theRatingIsTheAverageOfTheLatestRatedGames() {
        assertEquals(1500, Human.userElo(emptyList()))
        assertEquals(1500, Human.userElo(listOf(null, null)))
        assertEquals(1200, Human.userElo(listOf(1100, null, 1300)))
        assertEquals(2600, Human.userElo(listOf(3000, 3200)))
    }
}

class WeightedRepliesTest {
    private val tree = wrklabs.chess.training.LessonPgn.parseAll(
        "[Event \"x\"]\n\n1. e4 e5 (1... c5) (1... e6) *"
    ).single().tree
    private val replies = tree.nodes.getValue("e2e4").children.map { tree.nodes.getValue(it) }
    private fun share(e5: Float, c5: Float, e6: Float) = mapOf(
        replies.first { it.san == "e5" }.id to e5, replies.first { it.san == "c5" }.id to c5, replies.first { it.san == "e6" }.id to e6,
    )

    private fun counts(shares: Map<String, Float>, trials: Int = 6000, visits: Map<String, Int> = emptyMap()): Map<String?, Int> {
        val p = wrklabs.chess.training.LessonPractice(tree, wrklabs.chess.engine.PieceColor.WHITE, kotlin.random.Random(7), shares)
        val start = wrklabs.chess.training.PracticeState(visits = visits)
        return (1..trials).map { (p.play(start, "e2", "e4") as wrklabs.chess.training.PracticeResult.Correct).state.played.last().san }.groupingBy { it }.eachCount()
    }

    @Test fun commonRepliesComeUpMoreOften() {
        val c = counts(share(0.6f, 0.3f, 0.1f))
        assertTrue("e5 ${c["e5"]} c5 ${c["c5"]} e6 ${c["e6"]}", c.getValue("e5") > c.getValue("c5") && c.getValue("c5") > c.getValue("e6"))
        assertEquals(0.6, c.getValue("e5") / 6000.0, 0.03)
        assertEquals(0.1, c.getValue("e6") / 6000.0, 0.02)
    }

    @Test fun aReplyAlreadySeenIsLessLikelyAgain() {
        val seen = replies.first { it.san == "e5" }.id
        val fresh = counts(share(0.6f, 0.3f, 0.1f)).getValue("e5")
        val after = counts(share(0.6f, 0.3f, 0.1f), visits = mapOf(seen to 4)).getValue("e5")
        assertTrue("$fresh -> $after", after < fresh / 2)
    }

    @Test fun aReplyNobodyPlaysStillTurnsUp() {
        val c = counts(share(0.9f, 0.1f, 0f))
        assertTrue(c.getValue("e6") > 0)
    }

    @Test fun withoutNumbersEveryBranchStillGetsItsTurn() {
        val p = wrklabs.chess.training.LessonPractice(tree, wrklabs.chess.engine.PieceColor.WHITE, kotlin.random.Random(1))
        var s = wrklabs.chess.training.PracticeState()
        val seen = mutableSetOf<String?>()
        repeat(3) { val r = (p.play(s, "e2", "e4") as wrklabs.chess.training.PracticeResult.Correct).state; seen += r.played.last().san; s = s.copy(visits = r.visits) }
        assertEquals(setOf("e5", "c5", "e6"), seen)
    }
}
