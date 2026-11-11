package wrklabs.chess.maia

import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.MoveNode

/** What players of a rating do where a lesson branches: for each reply the lesson has, the share of them who play it. */
object HumanReplies {
    /**
     * For every position of [tree] where the opponent of [learner] has more than one reply in the lesson, the share of players
     * rated [elo] who play each of those replies, keyed by the reply's node id. A position Maia fails on is left out.
     */
    suspend fun compute(engine: MaiaEngine, tree: GameTree, learner: PieceColor, elo: Int): Map<String, Float> {
        val out = mutableMapOf<String, Float>()
        for (node in tree.nodes.values) {
            if (node.children.size < 2 || learnerToMove(node, learner)) continue
            val probs = runCatching { engine.predict(positionsTo(tree, node), elo) }.getOrNull() ?: continue
            for (id in node.children) {
                val child = tree.nodes.getValue(id)
                out[id] = probs[child.uci] ?: 0f
            }
        }
        return out
    }

    private fun learnerToMove(n: MoveNode, learner: PieceColor) = (n.fen.split(" ").getOrNull(1) != "b") == (learner == PieceColor.WHITE)

    /** The positions leading to [node] and it, oldest first: what Maia reads of the game so far. */
    internal fun positionsTo(tree: GameTree, node: MoveNode): List<String> =
        generateSequence(node) { n -> n.parentId?.let { tree.nodes[it] } }.take(MaiaEncoder.HISTORY).map { it.fen }.toList().asReversed()
}
