package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID
import wrklabs.chess.ui.pathTo
import wrklabs.chess.ui.withoutEngine

class EngineLinesTest {
    private fun add(tree: GameTree, from: String, vararg ucis: String): Pair<GameTree, String> {
        var t = tree; var id = from
        for (u in ucis) { val (n, i) = t.addMove(id, u)!!; t = n; id = i }
        return t to id
    }

    @Test
    fun pathRunsFromTheRootDown() {
        val (t, id) = add(GameTree.create(), ROOT_ID, "e2e4", "e7e5")
        assertEquals(listOf("e2e4.e7e5", "e2e4", ROOT_ID), pathTo(t, id))
    }

    @Test
    fun yourVariationsStayWhenYouLeaveThem() {
        val (t, game) = add(GameTree.create(), ROOT_ID, "e2e4", "e7e5")
        val (t2, mine) = add(t, "e2e4", "c7c5")
        val kept = withoutEngine(t2, emptySet(), game)
        assertTrue(mine in kept.nodes)
    }

    @Test
    fun theEnginesMovesGoWhenYouLeaveThem() {
        val (t, game) = add(GameTree.create(), ROOT_ID, "e2e4", "e7e5")
        val (t2, engine) = add(t, "e2e4", "c7c5")
        val kept = withoutEngine(t2, setOf(engine), game)
        assertFalse(engine in kept.nodes)
        assertFalse(engine in kept.nodes["e2e4"]!!.children)
        assertTrue(game in kept.nodes)
    }

    @Test
    fun standingOnAnEngineLineKeepsTheRestOfIt() {
        val (t, _) = add(GameTree.create(), ROOT_ID, "e2e4")
        val (t2, first) = add(t, "e2e4", "c7c5")
        val (t3, last) = add(t2, first, "g1f3")
        val kept = withoutEngine(t3, setOf(first, last), first)
        assertTrue(first in kept.nodes && last in kept.nodes)
    }

    @Test
    fun movesYouPlayedFromAnEngineLineAreKept() {
        // The engine's node is no longer the engine's once you play on from it: it is not in the set, so it stays.
        val (t, _) = add(GameTree.create(), ROOT_ID, "e2e4")
        val (t2, first) = add(t, "e2e4", "c7c5")
        val (t3, mine) = add(t2, first, "g1f3")
        val kept = withoutEngine(t3, emptySet(), "e2e4")
        assertTrue(first in kept.nodes && mine in kept.nodes)
    }
}
