package wrklabs.chess.ui

import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID

/** One move of an engine line, written out. */
data class PvMove(val uci: String, val san: String, /** "12." or "12…" when this move starts a pair or the line. */ val number: String?)

/** An engine line (UCI moves) from `fen` as numbered SAN moves, as far as it stays legal. */
fun pvToMoves(fen: String, pv: List<String>, max: Int = 8): List<PvMove> {
    var tree = GameTree.create(fen)
    var id = ROOT_ID
    val out = mutableListOf<PvMove>()
    for (uci in pv.take(max)) {
        val (next, nextId) = tree.addMove(id, uci) ?: break
        val parent = tree.nodes[id]!!
        val node = next.nodes[nextId]!!
        val white = parent.fen.split(" ")[1] == "w"
        val number = (parent.ply / 2 + 1).toString()
        out += PvMove(uci, node.san ?: uci, if (white) "$number." else if (out.isEmpty()) "$number…" else null)
        tree = next
        id = nextId
    }
    return out
}
