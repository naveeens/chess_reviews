package wrklabs.chess.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wrklabs.chess.Graph
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.classifier.MoveClassifier
import wrklabs.chess.classifier.Opening
import wrklabs.chess.classifier.ReviewedPosition
import wrklabs.chess.engine.AnalyzeRequest
import wrklabs.chess.engine.EngineLine
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.engine.checkmatedScore
import wrklabs.chess.importer.PgnParser
import wrklabs.chess.model.*
import wrklabs.chess.review.GameInfo
import wrklabs.chess.sound.SoundPlayer

data class AnalysisUiState(
    /** The game the position came from, when it came from one: for the names, ratings and result on the bars. */
    val info: GameInfo? = null,
    val tree: GameTree = GameTree.create(),
    val currentNodeId: String = ROOT_ID,
    val selectedSquare: Square? = null,
    val orientation: PieceColor = PieceColor.WHITE,
    val lines: List<EngineLine> = emptyList(),
    val liveDepth: Int = 0,
    val opening: Opening? = null,
    /** What the opponent would play if it were their move here (UCI), when threat arrows are on. */
    val threat: String? = null,
    /** The marks of the moves the engine has looked at, when move feedback is on. */
    val grades: Map<String, MoveClass> = emptyMap(),
    /** The last evaluation, kept while the engine works on the new position so the bar doesn't flash to 0.0. */
    val staleScore: Score? = null,
    /** Moves the engine added to show something ("Show why"): taken away again once you leave them. */
    val engineIds: Set<String> = emptySet(),
    /** "Show why": the move being explained, and what the engine's line came to (null while it plays out). */
    val whyFrom: String? = null,
    val whyText: String? = null,
    /** "Opponent replies": the engine is working out its answer to your move. */
    val thinking: Boolean = false,
) {
    val currentNode: MoveNode get() = tree.nodes[currentNodeId] ?: tree.nodes.getValue(ROOT_ID)
    val board: ChessBoard get() = ChessBoard.fromFen(currentNode.fen)
    val legalMoves: List<Move> get() = selectedSquare?.let { sq -> board.legalMoves().filter { it.from == sq } } ?: emptyList()
    val lastMove: Pair<Square, Square>?
        get() = currentNode.let { n -> if (n.from != null && n.to != null) Square.fromName(n.from) to Square.fromName(n.to) else null }

    /** The line being looked at: from the start to here, then on along the main continuation. */
    val line: List<MoveNode>
        get() {
            val path = mutableListOf<MoveNode>()
            var n: MoveNode? = currentNode
            while (n != null && n.id != ROOT_ID) { path.add(0, n); n = n.parentId?.let { tree.nodes[it] } }
            var next = currentNode.children.firstOrNull()?.let { tree.nodes[it] }
            while (next != null) { path += next; next = next.children.firstOrNull()?.let { tree.nodes[it] } }
            return path
        }

    val isCheckmate: Boolean get() = currentNode.san?.endsWith("#") == true
    val matedColor: PieceColor? get() = if (isCheckmate) (if (currentNode.ply % 2 == 1) PieceColor.BLACK else PieceColor.WHITE) else null
    val score: Score? get() = if (isCheckmate) checkmatedScore(matedColor ?: PieceColor.WHITE) else lines.firstOrNull()?.score ?: staleScore

    /** The result to show on the bars: the game's, or the one a checkmate on the board settles. */
    val result: String?
        get() = when (matedColor) {
            PieceColor.BLACK -> "1-0"
            PieceColor.WHITE -> "0-1"
            else -> info?.result
        }
}

/** The analysis board: any position, the engine's lines for it, and your own moves. Reviewing a game is its own screen. */
class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Graph.settings
    private val engine = Graph.engine
    private val service = Graph.reviews

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()
    val engineStatus = engine.status

    private var liveJob: Job? = null
    private var whyJob: Job? = null
    private var replyJob: Job? = null
    private var active = true
    private var book: wrklabs.chess.classifier.OpeningsBook? = null

    init {
        viewModelScope.launch {
            book = service.openings()
            _state.update { it.copy(opening = book?.openingOf(listOf(it.currentNode))) }
            engine.start()
            live()
        }
    }

    /** The screen is on or off: off, the engine is left alone (a review may be using it). */
    fun setActive(on: Boolean) {
        active = on
        if (on) live() else liveJob?.cancel()
    }

    // ── What is on the board ──

    /** Pasted PGN text. */
    fun openPgn(pgn: String): Boolean {
        val parsed = PgnParser.parse(pgn)
        if (parsed.moves.isEmpty()) return false
        var tree = GameTree.create()
        var id = ROOT_ID
        for (san in parsed.moves) {
            val (t, n) = tree.addMoveBySan(id, san) ?: break
            tree = t
            id = n
        }
        val me = listOf(settings.value.chesscomUser, settings.value.lichessUser).filter { it.isNotBlank() }
        val color = when {
            me.any { it.equals(parsed.white, true) } -> PieceColor.WHITE
            me.any { it.equals(parsed.black, true) } -> PieceColor.BLACK
            else -> null
        }
        val info = GameInfo("pgn", "pgn", parsed.white, parsed.black, parsed.whiteElo, parsed.blackElo, parsed.result, "", System.currentTimeMillis(), color, pgn)
        replyJob?.cancel(); whyJob?.cancel(); _state.value = AnalysisUiState(info = info, tree = tree, orientation = color ?: PieceColor.WHITE)
        goTo(tree, id, quiet = true)
        return true
    }

    /** A position reached by `ucis` from the start, as when the review screen hands one over. */
    fun openLine(info: GameInfo?, ucis: List<String>, orientation: PieceColor) {
        var tree = GameTree.create()
        var id = ROOT_ID
        for (uci in ucis) {
            val (t, n) = tree.addMove(id, uci) ?: break
            tree = t
            id = n
        }
        replyJob?.cancel(); whyJob?.cancel(); _state.value = AnalysisUiState(info = info, tree = tree, orientation = orientation)
        goTo(tree, id, quiet = true)
    }

    /** A position typed or pasted in as FEN. False when it isn't a position the board can hold. */
    fun openFen(text: String): Boolean {
        val fen = text.trim().replace("\\s+".toRegex(), " ")
        val ranks = fen.substringBefore(' ').split('/')
        val sane = ranks.size == 8 && ranks.all { r -> r.sumOf { ch -> if (ch.isDigit()) ch.digitToInt() else if (ch.isLetter()) 1 else 99 } == 8 }
        if (!sane) return false
        val board = try { ChessBoard.fromFen(fen) } catch (e: Exception) { return false }
        val kings = (0 until 64).mapNotNull { board.getPiece(Square.fromIndex(it)) }.filter { it.type == PieceType.KING }
        if (kings.count { it.color == PieceColor.WHITE } != 1 || kings.count { it.color == PieceColor.BLACK } != 1) return false
        val tree = GameTree.create(board.toFen())
        replyJob?.cancel(); whyJob?.cancel(); _state.value = AnalysisUiState(tree = tree)
        goTo(tree, ROOT_ID, quiet = true)
        return true
    }

    /** The position on the board. */
    fun fen(): String = _state.value.currentNode.fen

    /** The game with every variation, as PGN text. */
    fun pgn(): String {
        val s = _state.value
        val headers = linkedMapOf<String, String>()
        s.info?.let { headers["White"] = it.white; headers["Black"] = it.black; headers["Result"] = it.result }
        val start = s.tree.nodes.getValue(ROOT_ID).fen
        if (start != ChessBoard.DEFAULT_FEN) { headers["SetUp"] = "1"; headers["FEN"] = start }
        return s.tree.toPgn(headers)
    }

    /** The engine was switched on or off, or asked for another number of lines. */
    fun restartEngine() {
        if (settings.value.analysisEngine) live() else { liveJob?.cancel(); _state.update { it.copy(lines = emptyList(), liveDepth = 0, threat = null) } }
    }

    fun reset() {
        replyJob?.cancel(); whyJob?.cancel(); _state.value = AnalysisUiState()
        goTo(_state.value.tree, ROOT_ID, quiet = true)
    }

    // ── Live engine ──

    /** What the engine last made of each position it has searched, and how deep: the marks are worked out from these. */
    private val evals = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, ReviewedPosition>>()

    private fun live() {
        liveJob?.cancel()
        if (!active || !settings.value.analysisEngine || _state.value.whyFrom != null || _state.value.thinking) return
        val fen = _state.value.currentNode.fen
        val board = ChessBoard.fromFen(fen)
        if (board.isCheckmate() || board.isStalemate()) {
            _state.update { it.copy(lines = emptyList(), liveDepth = 0, threat = null, staleScore = Score.Centipawns(0)) }
            return
        }
        val cfg = settings.value
        liveJob = viewModelScope.launch {
            // The threat: the best move for the side that isn't to move, were it to move. Not when in check.
            if (cfg.threatArrows && !board.isCheck()) {
                val parts = fen.split(" ").toMutableList()
                if (parts.size >= 2) {
                    parts[1] = if (parts[1] == "w") "b" else "w"
                    if (parts.size > 3) parts[3] = "-"
                    val result = engine.analyze(AnalyzeRequest(fen = parts.joinToString(" "), multiPv = 1, depth = 12, movetime = THREAT_MS))
                    val move = result?.bestMove?.takeIf { it.length >= 4 }
                    _state.update { if (it.currentNode.fen == fen) it.copy(threat = move) else it }
                }
            }
            val result = engine.analyze(
                AnalyzeRequest(
                    fen = fen, multiPv = LINES, depth = cfg.reviewSpeed.liveDepth,
                    onUpdate = { result ->
                        _state.update { if (it.currentNode.fen == fen) it.copy(lines = result.lines, liveDepth = result.depth) else it }
                        remember(fen, result)
                    },
                ),
            )
            if (result != null) remember(fen, result)
        }
    }

    /** Keeps what the engine found for a position and, with move feedback on, marks the moves into and out of it. */
    private fun remember(fen: String, result: wrklabs.chess.engine.AnalysisResult) {
        val top = result.lines.firstOrNull() ?: return
        evals[fen] = result.depth to ReviewedPosition(top.score, top.pv.firstOrNull(), top.pv, result.lines.getOrNull(1)?.score)
        if (settings.value.moveFeedback && result.depth >= GRADE_DEPTH) grade(fen)
    }

    private fun grade(fen: String) {
        val s = _state.value
        val book = book
        val marks = mutableMapOf<String, MoveClass>()
        for (node in s.tree.nodes.values) {
            val parent = node.parentId?.let { s.tree.nodes[it] } ?: continue
            if (node.uci == null || (node.fen != fen && parent.fen != fen)) continue
            val before = evals[parent.fen]?.takeIf { it.first >= GRADE_DEPTH }?.second ?: continue
            val after = evals[node.fen]?.takeIf { it.first >= GRADE_DEPTH }?.second ?: continue
            val review = MoveClassifier.classifyGame(listOf(parent, node), mapOf(parent.fen to before, node.fen to after)) { f -> book != null && book.isBook(f) && book.isBook(parent.fen) }
            review.moves[node.id]?.let { marks[node.id] = it.cls }
        }
        if (marks.isNotEmpty()) _state.update { it.copy(grades = it.grades + marks) }
    }

    // ── Board ──

    fun onSquareClick(sq: Square) {
        val s = _state.value
        val board = s.board
        val from = s.selectedSquare
        val own = board.getPiece(sq)?.takeIf { it.color == board.turn }
        when {
            from == null -> if (own != null) _state.update { it.copy(selectedSquare = sq) }
            from == sq -> _state.update { it.copy(selectedSquare = null) }
            else -> {
                val move = board.legalMoves().find { it.from == from && it.to == sq }
                when {
                    move != null -> play(from, sq, move.promotion)
                    own != null -> _state.update { it.copy(selectedSquare = sq) }
                    else -> _state.update { it.copy(selectedSquare = null) }
                }
            }
        }
    }

    fun onDrag(from: Square, to: Square) {
        val move = _state.value.board.legalMoves().find { it.from == from && it.to == to }
        if (move != null) play(from, to, move.promotion) else _state.update { it.copy(selectedSquare = null) }
    }

    private fun play(from: Square, to: Square, promotion: PieceType?) {
        val s = _state.value
        val board = s.board
        val promo = promotion ?: if (board.getPiece(from)?.type == PieceType.PAWN && (to.rank == 7 || to.rank == 0)) PieceType.QUEEN else null
        val (tree, id) = s.tree.addMove(s.currentNodeId, "${from.name}${to.name}${promo?.char ?: ""}") ?: return
        takeOver(tree, id)
        goTo(tree, id)
        replyIfAsked()
    }

    /** With "Opponent replies" on, the engine answers a move of the side towards you with the other side's; moving the far side's pieces is you exploring. */
    private fun replyIfAsked() {
        val cfg = settings.value
        if (!cfg.opponentReply || !cfg.analysisEngine || !active) return
        val s = _state.value
        val board = s.board
        if (board.isCheckmate() || board.isStalemate() || board.turn == s.orientation) return
        liveJob?.cancel()
        val startId = s.currentNodeId
        val fen = s.currentNode.fen
        _state.update { it.copy(thinking = true, lines = emptyList(), threat = null, liveDepth = 0) }
        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            val result = engine.analyze(AnalyzeRequest(fen = fen, multiPv = 1, movetime = REPLY_MS))
            // The search for the reply also says how your move stands, so it is kept for the marks.
            result?.let { remember(fen, it) }
            val best = result?.bestMove
            val now = _state.value
            if (best != null && now.currentNodeId == startId && now.thinking) {
                val added = now.tree.addMove(startId, best)
                if (added != null) { goTo(added.first, added.second, fromReply = true); return@launch }
            }
            _state.update { if (it.thinking) it.copy(thinking = false) else it }
            live()
        }
    }

    /** Plays a run of engine moves from the current position, as when a line is tapped. */
    fun playLine(ucis: List<String>) {
        var tree = _state.value.tree
        var id = _state.value.currentNodeId
        for (uci in ucis) {
            val (t, n) = tree.addMove(id, uci) ?: break
            tree = t
            id = n
        }
        if (id != _state.value.currentNodeId) { takeOver(tree, id); goTo(tree, id) }
    }

    /** A move of yours from one of the engine's lines makes that line your own. */
    private fun takeOver(tree: GameTree, id: String) {
        _state.update { it.copy(engineIds = it.engineIds - pathTo(tree, id).toSet(), whyFrom = null, whyText = null) }
    }

    /** The sound of the move that led to `node`: on the way forward, on the way back, and for a move you play. */
    private fun sound(node: MoveNode) {
        if (!settings.value.sound || node.san.isNullOrEmpty()) return
        val san = node.san
        when {
            san.endsWith("#") -> SoundPlayer.playGameEnd()
            san.endsWith("+") -> SoundPlayer.playCheck()
            san.contains('x') -> SoundPlayer.playCapture()
            san.startsWith("O-O") -> SoundPlayer.playCastle()
            else -> SoundPlayer.playMove()
        }
    }

    // ── Moving about ──

    fun select(nodeId: String) = goTo(_state.value.tree, nodeId)

    private fun goTo(tree: GameTree, nodeId: String, quiet: Boolean = false, keepWhy: Boolean = false, fromReply: Boolean = false) {
        // Going anywhere else drops a reply that is still being worked out.
        if (!fromReply) replyJob?.cancel()
        // The moves the engine added are taken away once you leave them; yours stay.
        val kept = withoutEngine(tree, _state.value.engineIds, nodeId)
        val node = kept.nodes[nodeId] ?: return
        val path = pathTo(kept, nodeId).asReversed().mapNotNull { kept.nodes[it] }
        val prev = _state.value.currentNodeId
        _state.update {
            it.copy(
                tree = kept, currentNodeId = nodeId, selectedSquare = null, thinking = false,
                engineIds = it.engineIds.filter { id -> id in kept.nodes }.toSet(),
                whyFrom = if (keepWhy || nodeId in it.engineIds) it.whyFrom else null,
                whyText = if (keepWhy || nodeId in it.engineIds) it.whyText else null,
                staleScore = if (prev == nodeId) it.staleScore else (it.lines.firstOrNull()?.score ?: it.staleScore),
                lines = if (prev == nodeId) it.lines else emptyList(), threat = if (prev == nodeId) it.threat else null, liveDepth = 0,
                opening = book?.openingOf(path),
            )
        }
        if (!quiet && nodeId != prev) sound(node)
        // While a line is being shown the engine is left alone: the line is what is being looked at.
        if (_state.value.whyFrom == null) live()
    }

    fun first() = select(ROOT_ID)
    fun previous() { _state.value.currentNode.parentId?.let(::select) }
    fun next() { _state.value.currentNode.children.firstOrNull()?.let(::select) }
    fun last() {
        var n = _state.value.currentNode
        while (true) n = n.children.firstOrNull()?.let { _state.value.tree.nodes[it] } ?: break
        select(n.id)
    }

    companion object {
        /** How long the engine looks for a threat. */
        const val THREAT_MS = 250L
        /** A position has to be searched this deep before a move into or out of it is marked. */
        const val GRADE_DEPTH = 10
        /** The engine lines shown above the board. */
        const val LINES = 2
        /** How long the engine thinks over its reply to your move. */
        const val REPLY_MS = 500L
    }

    fun flip() {
        _state.update { it.copy(orientation = it.orientation.opponent()) }
    }
}
