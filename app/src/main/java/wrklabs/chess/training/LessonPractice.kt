/*
 * Copyright (C) 2026 naveen s
 * SPDX-License-Identifier: GPL-3.0-or-later. See NOTICE.
 */
package wrklabs.chess.training

import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.MoveNode
import wrklabs.chess.model.ROOT_ID
import kotlin.random.Random

/** Where a practice run stands. `visits` outlives a run, so the next one goes down a branch you have not seen. */
data class PracticeState(
    val nodeId: String = ROOT_ID,
    val finished: Boolean = false,
    /** Wrong moves in this run. */
    val mistakes: Int = 0,
    /** Wrong moves on the position now on the board; a second one shows the answer. */
    val triesHere: Int = 0,
    val visits: Map<String, Int> = emptyMap(),
    /** The moves that just happened: your move, then the reply, with what the lesson says about them. */
    val played: List<MoveNode> = emptyList(),
)

sealed interface PracticeResult {
    val state: PracticeState
    class Correct(override val state: PracticeState) : PracticeResult
    /** `expected` is the lesson's main move for this position. */
    class Wrong(override val state: PracticeState, val expected: MoveNode) : PracticeResult
}

/**
 * Drills one side of a lesson. You move; any move the lesson has at that point is right (the first is the main line).
 * The opponent answers with a move the lesson has. Without [humanShare] it takes the one taken least so far, so every
 * branch gets its turn. With it (a node id to the share of players who make that move), the replies people really play
 * come up more often, each less so the more it has already been seen, and a rare reply still turns up now and then.
 */
class LessonPractice(
    private val tree: GameTree,
    private val learner: PieceColor,
    private val random: Random = Random.Default,
    private val humanShare: Map<String, Float> = emptyMap(),
) {
    private fun node(id: String) = tree.nodes.getValue(id)

    private fun learnerToMove(n: MoveNode): Boolean {
        val white = n.fen.split(" ").getOrNull(1) != "b"
        return white == (learner == PieceColor.WHITE)
    }

    private fun opponentReply(n: MoveNode, visits: Map<String, Int>): MoveNode {
        val options = n.children.map(::node)
        if (options.size > 1 && options.all { it.id in humanShare }) {
            val weights = options.map { maxOf(humanShare.getValue(it.id), MIN_SHARE) / (1 + (visits[it.id] ?: 0)) }
            var pick = random.nextDouble() * weights.sum()
            for ((i, w) in weights.withIndex()) { pick -= w; if (pick < 0) return options[i] }
            return options.last()
        }
        val least = options.minOf { visits[it.id] ?: 0 }
        return options.filter { (visits[it.id] ?: 0) == least }.random(random)
    }

    /** Opponent moves until it is the learner's turn or the lesson runs out. */
    private fun advance(from: MoveNode, state: PracticeState, played: MutableList<MoveNode>): PracticeState {
        var n = from
        var visits = state.visits
        while (n.children.isNotEmpty() && !learnerToMove(n)) {
            n = opponentReply(n, visits)
            visits = visits + (n.id to (visits[n.id] ?: 0) + 1)
            played += n
        }
        return state.copy(nodeId = n.id, finished = n.children.isEmpty(), triesHere = 0, visits = visits, played = played.toList())
    }

    /** A fresh run. When you play Black the opponent has already made the first move. */
    fun start(previous: PracticeState? = null): PracticeState =
        advance(node(ROOT_ID), PracticeState(visits = previous?.visits.orEmpty()), mutableListOf())

    fun play(state: PracticeState, from: String, to: String): PracticeResult {
        val here = node(state.nodeId)
        val chosen = here.children.map(::node).firstOrNull { it.from == from && it.to == to }
        if (chosen == null) {
            val expected = node(here.children.first())
            return PracticeResult.Wrong(state.copy(mistakes = state.mistakes + 1, triesHere = state.triesHere + 1), expected)
        }
        val visits = state.visits + (chosen.id to (state.visits[chosen.id] ?: 0) + 1)
        return PracticeResult.Correct(advance(chosen, state.copy(visits = visits), mutableListOf(chosen)))
    }

    /** The move the lesson wants here, for a hint. */
    fun expected(state: PracticeState): MoveNode? = node(state.nodeId).children.firstOrNull()?.let(::node)

    companion object {
        /** A reply almost nobody plays is still worth meeting sometimes: it counts as at least this much. */
        const val MIN_SHARE = 0.03f
    }
}
