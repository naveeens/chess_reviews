package wrklabs.chess.model

import kotlinx.serialization.Serializable

const val ROOT_ID = "root"

@Serializable
data class MoveNode(
    val id: String,
    val parentId: String?,
    val children: List<String> = emptyList(),
    val ply: Int,
    val fen: String,
    val san: String? = null,
    val uci: String? = null,
    val from: String? = null,
    val to: String? = null,
    val clock: Double? = null
)

@Serializable
data class GameTree(
    val nodes: Map<String, MoveNode> = emptyMap()
) {
    companion object {
        fun create(fen: String = ChessBoard.DEFAULT_FEN): GameTree {
            val ply = plyFromFen(fen)
            val root = MoveNode(
                id = ROOT_ID,
                parentId = null,
                children = emptyList(),
                ply = ply,
                fen = fen
            )
            return GameTree(mapOf(ROOT_ID to root))
        }

        fun plyFromFen(fen: String): Int {
            val parts = fen.split("\\s+".toRegex())
            val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1
            val isBlack = parts.getOrNull(1) == "b"
            return (fullmove - 1) * 2 + (if (isBlack) 1 else 0)
        }
    }

    fun addMove(
        parentId: String,
        uciMove: String,
        clock: Double? = null
    ): Pair<GameTree, String>? {
        val parent = nodes[parentId] ?: return null
        if (uciMove.length < 4) return null
        val board = ChessBoard.fromFen(parent.fen)
        val fromName = uciMove.substring(0, 2)
        val toName = uciMove.substring(2, 4)
        val promoChar = if (uciMove.length > 4) uciMove[4] else null
        val promoType = promoChar?.let { PieceType.fromChar(it) }

        val legal = board.legalMoves().find {
            it.from.name == fromName && it.to.name == toName && it.promotion == promoType
        } ?: return null

        val id = if (parentId == ROOT_ID) legal.uci else "$parentId.${legal.uci}"
        if (nodes.containsKey(id)) {
            return Pair(this, id)
        }

        val afterBoard = board.makeMove(legal)
        val newNode = MoveNode(
            id = id,
            parentId = parentId,
            children = emptyList(),
            ply = parent.ply + 1,
            fen = afterBoard.toFen(),
            san = legal.san,
            uci = legal.uci,
            from = legal.from.name,
            to = legal.to.name,
            clock = clock
        )

        val updatedParent = parent.copy(children = parent.children + id)
        val newNodes = nodes.toMutableMap()
        newNodes[parentId] = updatedParent
        newNodes[id] = newNode

        return Pair(GameTree(newNodes), id)
    }

    fun addMoveBySan(
        parentId: String,
        san: String,
        clock: Double? = null
    ): Pair<GameTree, String>? {
        val parent = nodes[parentId] ?: return null
        val board = ChessBoard.fromFen(parent.fen)
        val cleanSan = san.replace("+", "").replace("#", "").replace("x", "")
        val legal = board.legalMoves().find {
            it.san == san || 
            it.san.replace("+", "").replace("#", "") == san.replace("+", "").replace("#", "") ||
            it.san.replace("+", "").replace("#", "").replace("x", "") == cleanSan
        } ?: return null

        return addMove(parentId, legal.uci, clock)
    }

    fun getMainLine(): List<MoveNode> {
        val list = mutableListOf<MoveNode>()
        var curr = nodes[ROOT_ID]
        while (curr != null) {
            val nextId = curr.children.firstOrNull() ?: break
            curr = nodes[nextId]
            curr?.let { list.add(it) }
        }
        return list
    }

    fun promoteVariation(nodeId: String): GameTree {
        val node = nodes[nodeId] ?: return this
        val parentId = node.parentId ?: return this
        val parent = nodes[parentId] ?: return this

        val idx = parent.children.indexOf(nodeId)
        if (idx <= 0) return this

        val newChildren = parent.children.toMutableList()
        newChildren.removeAt(idx)
        newChildren.add(0, nodeId)

        val updatedParent = parent.copy(children = newChildren)
        val newNodes = nodes.toMutableMap()
        newNodes[parentId] = updatedParent
        return GameTree(newNodes)
    }

    fun deleteVariation(nodeId: String): GameTree {
        if (nodeId == ROOT_ID) return this
        val node = nodes[nodeId] ?: return this
        val parentId = node.parentId ?: return this
        val parent = nodes[parentId] ?: return this

        val newChildren = parent.children.filter { it != nodeId }
        val updatedParent = parent.copy(children = newChildren)

        val newNodes = nodes.toMutableMap()
        newNodes[parentId] = updatedParent

        // Remove subtree
        val toRemove = mutableListOf(nodeId)
        var head = 0
        while (head < toRemove.size) {
            val currId = toRemove[head++]
            val n = newNodes.remove(currId)
            if (n != null) {
                toRemove.addAll(n.children)
            }
        }

        return GameTree(newNodes)
    }

    /** Headers, then every move with its variations in brackets. */
    fun toPgn(headers: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder()
        for ((k, v) in headers) sb.append("[$k \"$v\"]\n")
        if (headers.isNotEmpty()) sb.append("\n")
        val moves = StringBuilder()

        fun move(node: MoveNode, forceNumber: Boolean) {
            if (moves.isNotEmpty() && !moves.endsWith("(")) moves.append(' ')
            if (node.ply % 2 == 1) moves.append("${(node.ply + 1) / 2}. ")
            else if (forceNumber) moves.append("${node.ply / 2}... ")
            moves.append(node.san ?: node.uci ?: "")
        }

        fun write(nodeId: String, forceNumber: Boolean) {
            val node = nodes[nodeId] ?: return
            val main = node.children.firstOrNull()?.let { nodes[it] } ?: return
            move(main, forceNumber || moves.isEmpty())
            for (alt in node.children.drop(1).mapNotNull { nodes[it] }) {
                moves.append(" (")
                move(alt, true)
                write(alt.id, false)
                moves.append(")")
            }
            write(main.id, node.children.size > 1)
        }
        write(ROOT_ID, true)
        sb.append(moves)
        headers["Result"]?.let { sb.append(' ').append(it) }
        return sb.toString().trim()
    }
}
