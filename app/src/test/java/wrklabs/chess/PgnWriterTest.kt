package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID

class PgnWriterTest {
    private fun play(tree: GameTree, from: String, vararg ucis: String): Pair<GameTree, String> {
        var t = tree
        var id = from
        for (u in ucis) { val (n, i) = t.addMove(id, u)!!; t = n; id = i }
        return t to id
    }

    @Test
    fun writesTheMainLine() {
        val (t, _) = play(GameTree.create(), ROOT_ID, "e2e4", "e7e5", "g1f3")
        assertEquals("1. e4 e5 2. Nf3", t.toPgn())
    }

    @Test
    fun writesVariationsInBrackets() {
        val (t, e4) = play(GameTree.create(), ROOT_ID, "e2e4")
        val (t2, _) = play(t, e4, "e7e5", "g1f3")
        val (t3, _) = play(t2, e4, "c7c5")
        assertEquals("1. e4 e5 (1... c5) 2. Nf3", t3.toPgn())
    }

    @Test
    fun numbersABlackMoveThatFollowsAVariation() {
        val (t, _) = play(GameTree.create(), ROOT_ID, "e2e4", "e7e5")
        val (t2, _) = play(t, ROOT_ID, "d2d4")
        assertEquals("1. e4 (1. d4) 1... e5", t2.toPgn())
    }

    @Test
    fun startsFromAnyPositionWithTheRightMoveNumber() {
        val tree = GameTree.create("6k1/8/8/8/8/8/8/R3K3 b - - 0 20")
        val (t, _) = play(tree, ROOT_ID, "g8f8")
        assertEquals("20... Kf8", t.toPgn())
    }
}
