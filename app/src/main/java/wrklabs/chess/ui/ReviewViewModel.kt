package wrklabs.chess.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wrklabs.chess.Graph
import wrklabs.chess.billing.ReviewQuota
import wrklabs.chess.classifier.GameReport
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.classifier.MoveClassifier
import wrklabs.chess.classifier.MoveReview
import wrklabs.chess.classifier.ReviewedPosition
import wrklabs.chess.classifier.Opening
import wrklabs.chess.engine.AnalyzeRequest
import wrklabs.chess.engine.EngineLine
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.engine.winPercent
import wrklabs.chess.engine.checkmatedScore
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.importer.PgnParser
import wrklabs.chess.model.*
import wrklabs.chess.review.GameInfo
import wrklabs.chess.review.ReviewService
import wrklabs.chess.sound.SoundPlayer

/** One of the engine's best moves in a position, with its evaluation (White's point of view). */
data class Candidate(val uci: String, val san: String, val score: Score, val pv: List<String> = emptyList())

/** The one engine move shown on the card, and what to call it: "Best", "Also good" or "Next best". */
data class Suggestion(val candidate: Candidate, val label: String)

data class ReviewUiState(
    val info: GameInfo? = null,
    val tree: GameTree = GameTree.create(),
    /** The game's moves, without the start position. */
    val mainline: List<MoveNode> = emptyList(),
    /** Every position of the game, the start included: anything else in the tree is a variation. */
    val gameIds: Set<String> = setOf(ROOT_ID),
    val currentNodeId: String = ROOT_ID,
    val selectedSquare: Square? = null,
    val orientation: PieceColor = PieceColor.WHITE,
    val report: GameReport? = null,
    val points: List<EvalPoint> = emptyList(),
    val reviewing: Boolean = false,
    val progress: Float = 0f,
    val reviewStart: Long = 0,
    val message: String? = null,
    /** Set when the free weekly reviews are used up: when the next one comes back, in ms. */
    val limitUntil: Long? = null,
    /** False: the summary screen; true: the board. A game opens on its summary. */
    val showBoard: Boolean = false,
    /** The live engine's lines on a variation, for the bar. */
    val lines: List<EngineLine> = emptyList(),
    /** The engine's best moves in the position before the move on the card, and which position that was. */
    val candidates: List<Candidate> = emptyList(),
    val candidatesFor: String? = null,
    /** "Show why": the game move being explained, and what the engine's reply to it comes to (null while it plays out). */
    val whyFrom: String? = null,
    val whyText: String? = null,
    /** The engine's move shown in place of the one played; null while the game's own move is shown. */
    val bestNodeId: String? = null,
    /** The move the engine's move on show stands in for. */
    val bestOf: String? = null,
    /** Moves the engine added to show something (its move on the card, the "Show why" line): taken away again once you leave them. */
    val engineIds: Set<String> = emptySet(),
    /** The engine is working out the opponent's reply to a move of yours. */
    val thinking: Boolean = false,
    /** Grades given to moves off the game (your variation, the engine's moves on the card), by node. */
    val grades: Map<String, MoveReview> = emptyMap(),
    /** The last evaluation we had, kept on the bar until the engine answers for the new position of a variation. */
    val staleScore: Score? = null,
    val opening: Opening? = null,
    /** The opening the whole game came to, for the header. */
    val gameOpening: Opening? = null,
) {
    val currentNode: MoveNode get() = tree.nodes[currentNodeId] ?: tree.nodes.getValue(ROOT_ID)
    val board: ChessBoard get() = ChessBoard.fromFen(currentNode.fen)
    val legalMoves: List<Move> get() = selectedSquare?.let { sq -> board.legalMoves().filter { it.from == sq } } ?: emptyList()
    val lastMove: Pair<Square, Square>?
        get() = currentNode.let { n -> if (n.from != null && n.to != null) Square.fromName(n.from) to Square.fromName(n.to) else null }

    val showingBest: Boolean get() = bestNodeId != null && currentNodeId == bestNodeId

    /** On a line of your own, off the game: Resume goes back. */
    val onBranch: Boolean get() = currentNodeId !in gameIds && !showingBest

    /** Resume has somewhere to go: a variation of yours, or the line "Show why" is playing. */
    val canResume: Boolean get() = onBranch || whyFrom != null

    /** Where Resume goes: the game position the line being looked at began from. */
    val resumeTo: String?
        get() {
            whyFrom?.takeIf { it in gameIds }?.let { return it }
            var n: MoveNode? = whyFrom?.let { tree.nodes[it] } ?: currentNode
            while (n != null && n.id !in gameIds) n = n.parentId?.let { tree.nodes[it] }
            return n?.id
        }

    /** Moves of your own are in the tree, off the game. */
    val hasVariations: Boolean get() = tree.nodes.keys.any { it !in gameIds }

    /** The move of the game that the card is about: the played one, also while its better alternative or a variation is on the board. */
    val playedNode: MoveNode
        get() = when {
            whyFrom != null -> tree.nodes[whyFrom] ?: currentNode
            showingBest -> bestOf?.let { tree.nodes[it] } ?: currentNode
            onBranch -> whyFrom?.let { tree.nodes[it] } ?: currentNode
            else -> currentNode
        }
    val playedReview: MoveReview? get() = report?.moves?.get(playedNode.id) ?: grades[playedNode.id]

    /** The game position a variation grew from, for the moment of the key-moment arrows and the graph. */
    val gamePly: Int get() = if (onBranch) (resumeTo?.let { tree.nodes[it]?.ply } ?: playedNode.ply) else playedNode.ply

    /** The engine's best move for the card, when the move played was something else. Null until the engine has answered. */
    val suggestion: Suggestion?
        get() {
            if (candidatesFor == null || candidatesFor != playedNode.parentId) return null
            val best = candidates.firstOrNull() ?: return null
            return if (best.uci != playedNode.uci) Suggestion(best, "Best") else null
        }

    /** The engine is still working out the moves for the card. */
    val candidatesPending: Boolean get() = playedNode.parentId != null && candidatesFor != playedNode.parentId

    val isCheckmate: Boolean get() = currentNode.san?.endsWith("#") == true
    val matedColor: PieceColor? get() = if (isCheckmate) (if (currentNode.ply % 2 == 1) PieceColor.BLACK else PieceColor.WHITE) else null
    val atGameEnd: Boolean get() = info != null && !showingBest && !onBranch && currentNode.children.isEmpty() && currentNodeId != ROOT_ID
    val termination: String? get() = info?.pgn?.let { PgnParser.parse(it).headers["Termination"] }

    /** The moves in the strip: from the start to here, then on along the line. */
    val strip: List<MoveNode>
        get() {
            val path = mutableListOf<MoveNode>()
            var n: MoveNode? = currentNode
            while (n != null && n.id != ROOT_ID) { path.add(0, n); n = n.parentId?.let { tree.nodes[it] } }
            var next = currentNode.children.firstOrNull()?.let { tree.nodes[it] }
            while (next != null) { path += next; next = next.children.firstOrNull()?.let { tree.nodes[it] } }
            return path
        }

    /** The evaluation for the bar: a mate, the live engine (on a variation it is always on), the engine's move when it is shown, else what the review found. */
    val score: Score?
        get() {
            if (isCheckmate) return checkmatedScore(matedColor ?: PieceColor.WHITE)
            // Until the engine's line for "Show why" arrives, the review's own score for the move stands in.
            if (whyFrom != null) (lines.firstOrNull()?.score ?: staleScore)?.let { return it }
            if (onBranch) return lines.firstOrNull()?.score ?: staleScore
            val review = playedReview
            if (showingBest && review != null) {
                val moverWhite = playedNode.ply % 2 == 1
                return Score.Centipawns(ReviewService.cpFromWin(if (moverWhite) review.winBefore else 100 - review.winBefore))
            }
            review?.let { return it.score }
            return points.firstOrNull { it.ply == currentNode.ply }?.let { Score.Centipawns(ReviewService.cpFromWin(it.winPercent)) }
        }

    val keyMoments: List<MoveNode>
        get() = report?.let { r -> mainline.filter { r.moves[it.id]?.cls in KEY_CLASSES } } ?: emptyList()

    companion object {
        val KEY_CLASSES = setOf(MoveClass.BRILLIANT, MoveClass.GREAT, MoveClass.BLUNDER, MoveClass.MISTAKE, MoveClass.MISS)
    }
}

/** The ids from the root down to `id`. */
internal fun pathTo(tree: GameTree, id: String): List<String> =
    generateSequence(tree.nodes[id]) { n -> n.parentId?.let { tree.nodes[it] } }.map { it.id }.toList()

/**
 * The tree without the moves the engine added that you have left: every engine move off the way to `target`, with what follows
 * it. Standing on an engine move keeps the rest of its line, so you can step back and forward along it.
 */
internal fun withoutEngine(tree: GameTree, engineIds: Set<String>, target: String): GameTree {
    if (engineIds.isEmpty()) return tree
    val keep = pathTo(tree, target).toMutableSet()
    fun keepDown(id: String) { keep += id; tree.nodes[id]?.children?.forEach(::keepDown) }
    if (target in engineIds) keepDown(target)
    val gone = mutableSetOf<String>()
    fun mark(id: String) { gone += id; tree.nodes[id]?.children?.forEach(::mark) }
    engineIds.filter { it !in keep && it in tree.nodes }.forEach(::mark)
    if (gone.isEmpty()) return tree
    return GameTree(tree.nodes.filterKeys { it !in gone }.mapValues { (_, n) -> n.copy(children = n.children.filter { it !in gone }) })
}

/** One game's review: opened from the Games list, on its own screen. */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Graph.settings
    private val engine = Graph.engine
    private val service = Graph.reviews

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    val engineStatus = engine.status

    private var liveJob: Job? = null
    private var reviewJob: Job? = null
    private var replyJob: Job? = null
    private var whyJob: Job? = null
    private val candidateCache = mutableMapOf<String, List<Candidate>>()
    /** What the engine said about positions off the game, by FEN, for grading the move after them. */
    private val evalCache = mutableMapOf<String, ReviewedPosition>()
    private var book: wrklabs.chess.classifier.OpeningsBook? = null

    init {
        viewModelScope.launch { book = service.openings() }
        // Buying Pro while the limit is showing lets the review go ahead.
        viewModelScope.launch {
            Graph.pro.owned.collect { owned ->
                if (owned && _state.value.limitUntil != null) {
                    _state.update { it.copy(limitUntil = null) }
                    review()
                }
            }
        }
    }

    /** Opens a game: its saved review if it has one, else a review is started when the settings say so. */
    fun open(game: ImportedGame, me: String) {
        reviewJob?.cancel()
        liveJob?.cancel()
        replyJob?.cancel()
        candidateCache.clear()
        evalCache.clear()
        val color = if (game.black.equals(me, true)) PieceColor.BLACK else if (game.white.equals(me, true)) PieceColor.WHITE else null
        val info = GameInfo(game.key, game.source, game.white, game.black, game.whiteRating, game.blackRating, game.result, game.timeClass, game.date, color, game.pgn)
        val parsed = PgnParser.parse(game.pgn)
        var tree = GameTree.create()
        var id = ROOT_ID
        for (san in parsed.moves) {
            val (t, n) = tree.addMoveBySan(id, san) ?: break
            tree = t
            id = n
        }
        val mainline = tree.getMainLine()
        _state.value = ReviewUiState(info = info, tree = tree, mainline = mainline, gameIds = mainline.map { it.id }.toSet() + ROOT_ID, orientation = color ?: PieceColor.WHITE)
        goTo(ROOT_ID, quiet = true)
        viewModelScope.launch {
            val line = listOf(tree.nodes.getValue(ROOT_ID)) + mainline
            val named = service.openings().openingOf(line)
            _state.update { it.copy(gameOpening = named) }
            val stored = service.stored(info.key, line)
            if (stored != null) _state.update { it.copy(report = stored.report, points = stored.points) }
            else if (settings.value.autoReview && mainline.isNotEmpty()) review()
        }
    }

    /** Leaving the screen: nothing is left running on the engine. */
    fun close() {
        reviewJob?.cancel()
        liveJob?.cancel()
        replyJob?.cancel()
        whyJob?.cancel()
        _state.update { it.copy(lines = emptyList(), reviewing = false, thinking = false) }
    }

    // ── Review ──

    fun review() {
        val s = _state.value
        val info = s.info ?: return
        if (s.reviewing || s.mainline.isEmpty()) return
        val line = listOf(s.tree.nodes.getValue(ROOT_ID)) + s.mainline
        liveJob?.cancel()
        replyJob?.cancel()
        reviewJob = viewModelScope.launch {
            // Free accounts have a few reviews a week; a game already reviewed can always be done again.
            val now = System.currentTimeMillis()
            val times = Graph.games.reviewTimesSince(now - ReviewQuota.WEEK_MS)
            if (!ReviewQuota.allowed(Graph.pro.owned.value, times) && !Graph.games.hasGame(info.key)) {
                _state.update { it.copy(limitUntil = ReviewQuota.nextFreeAt(times)) }
                return@launch
            }
            _state.update { it.copy(limitUntil = null, reviewing = true, thinking = false, progress = 0f, reviewStart = System.currentTimeMillis(), lines = emptyList(), message = null) }
            val cfg = settings.value
            try {
                val idByPly = s.mainline.associate { it.ply to it.id }
                val result = service.review(info, line, cfg.reviewSpeed.depth, cfg.reviewSpeed.moveMs, { done, total, points ->
                    _state.update { it.copy(progress = done.toFloat() / total, points = points) }
                }) { partial ->
                    // Results so far: the table, the accuracy, the key moments and the marks on the graph fill in as they are found.
                    _state.update { st -> st.copy(report = partial, points = st.points.map { p -> p.copy(verdict = idByPly[p.ply]?.let { partial.moves[it]?.cls }) }) }
                }
                _state.update { it.copy(report = result.report, points = result.points, reviewing = false, progress = 1f) }
            } catch (e: CancellationException) {
                // A review stopped part-way isn't kept: back to what there was before.
                _state.update { it.copy(reviewing = false, report = s.report, points = if (s.report == null) emptyList() else it.points) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(reviewing = false, report = s.report, message = e.message ?: "The review failed.") }
            }
            refresh()
        }
    }

    fun stopReview() {
        reviewJob?.cancel()
    }

    // ── The engine: the two moves on the card, and the bar while you explore a variation ──

    /** Starts whichever searches the position on screen needs: the card's two moves first, then (on a variation) the bar. */
    private fun refresh() {
        liveJob?.cancel()
        val s = _state.value
        if (s.reviewing || s.thinking) return
        liveJob = viewModelScope.launch {
            loadCandidates()
            if (_state.value.onBranch) liveEval()
        }
    }

    /** On a variation of yours: one line, to feed the evaluation bar and to grade the move. */
    private suspend fun liveEval() {
        val s = _state.value
        val fen = s.currentNode.fen
        val nodeId = s.currentNodeId
        val board = ChessBoard.fromFen(fen)
        if (board.isCheckmate() || board.isStalemate()) {
            val after = ReviewedPosition(if (board.isCheckmate()) checkmatedScore(board.turn) else Score.Centipawns(0), null, emptyList())
            evalCache[fen] = after
            _state.update { it.copy(lines = emptyList()) }
            grade(nodeId, after)
            return
        }
        val cfg = settings.value
        val result = engine.analyze(
            AnalyzeRequest(
                fen = fen, multiPv = 1, depth = minOf(cfg.reviewSpeed.liveDepth, 14),
                onUpdate = { r -> _state.update { if (it.currentNode.fen == fen) it.copy(lines = r.lines) else it } },
            ),
        )
        val line = result?.lines?.firstOrNull() ?: return
        val after = ReviewedPosition(line.score, result.bestMove ?: line.pv.firstOrNull(), line.pv, null)
        evalCache[fen] = after
        grade(nodeId, after)
    }

    /** What the review knew of a position of the game: its evaluation, and the move the engine liked there. */
    private fun gameEval(node: MoveNode, s: ReviewUiState): ReviewedPosition? {
        val report = s.report ?: return null
        if (node.id !in s.gameIds) return null
        val score = if (node.id == ROOT_ID) s.points.firstOrNull()?.let { Score.Centipawns(ReviewService.cpFromWin(it.winPercent)) } else report.moves[node.id]?.score
        val child = s.mainline.firstOrNull { it.parentId == node.id }
        val best = child?.let { report.moves[it.id]?.bestMove ?: it.uci }
        return score?.let { ReviewedPosition(it, best, emptyList(), null) }
    }

    /** Gives a move off the game its mark, once the engine has said how the position after it stands. */
    private fun grade(nodeId: String, after: ReviewedPosition) {
        val s = _state.value
        val node = s.tree.nodes[nodeId] ?: return
        if (nodeId in s.gameIds || node.uci == null) return
        val parent = node.parentId?.let { s.tree.nodes[it] } ?: return
        val before = evalCache[parent.fen] ?: gameEval(parent, s) ?: return
        val review = MoveClassifier.classifyGame(listOf(parent, node), mapOf(parent.fen to before, node.fen to after)).moves[node.id] ?: return
        _state.update { it.copy(grades = it.grades + (nodeId to review)) }
    }

    /** The engine's best three in the position the move on the card was played from. */
    private suspend fun loadCandidates() {
        val s = _state.value
        val parentId = s.playedNode.parentId ?: return
        candidateCache[parentId]?.let { cached ->
            _state.update { it.copy(candidates = cached, candidatesFor = parentId) }
            return
        }
        val fen = s.tree.nodes[parentId]?.fen ?: return
        val cfg = settings.value
        val result = engine.analyze(
            AnalyzeRequest(fen = fen, multiPv = 2, depth = 14, movetime = CANDIDATE_MS),
        )
        val found = result?.lines.orEmpty().mapNotNull { line ->
            val uci = line.pv.firstOrNull() ?: return@mapNotNull null
            Candidate(uci, ReviewService.sanOf(fen, uci), line.score, line.pv)
        }
        if (found.isEmpty()) return
        candidateCache[parentId] = found
        _state.update { if (it.playedNode.parentId == parentId) it.copy(candidates = found, candidatesFor = parentId) else it }
    }

    // ── Moving about ──

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

    /**
     * Shows a position. A step of any kind leaves Summary for Moves, since that is where the move is explained
     * (`switchTab = false` for scrubbing the graph, which has to stay under the finger). Your own variations stay;
     * the moves the engine added are taken away once you leave them. The engine's pending reply is cancelled unless it is the reply being played.
     */
    private fun goTo(nodeId: String, quiet: Boolean = false, tree: GameTree = _state.value.tree, fromReply: Boolean = false, keepWhy: Boolean = false) {
        val ids = _state.value.gameIds
        val t = withoutEngine(tree, _state.value.engineIds, nodeId)
        val node = t.nodes[nodeId] ?: return
        val prev = _state.value.currentNodeId
        val path = generateSequence(node) { n -> n.parentId?.let { t.nodes[it] } }.toList().asReversed()
        if (!fromReply) replyJob?.cancel()
        _state.update {
            it.copy(
                tree = t, currentNodeId = nodeId, selectedSquare = null, lines = if (prev == nodeId) it.lines else emptyList(),
                engineIds = it.engineIds.filter { id -> id in t.nodes }.toSet(),
                bestNodeId = if (nodeId == it.bestNodeId) it.bestNodeId else null,
                whyFrom = if (keepWhy || nodeId in it.engineIds) it.whyFrom else null,
                whyText = if (keepWhy || nodeId in it.engineIds) it.whyText else null,
                staleScore = if (nodeId in ids) null else (it.lines.firstOrNull()?.score ?: it.score ?: it.staleScore),
                thinking = if (fromReply) it.thinking else false,
                opening = book?.openingOf(path),
            )
        }
        if (!quiet && nodeId != prev) sound(node)
        // While a line is being shown the engine is left alone: the line is what is being looked at.
        if (_state.value.whyFrom == null) refresh()
    }

    fun select(nodeId: String) = goTo(nodeId)
    fun first() = goTo(ROOT_ID)

    fun previous() {
        val s = _state.value
        val parent = s.currentNode.parentId ?: return
        goTo(parent)
    }

    fun next() {
        val s = _state.value
        // From the engine's move, "next" goes back on with the game.
        val target = if (s.showingBest) s.playedNode.id else s.currentNode.children.firstOrNull()
        target?.let { goTo(it) }
    }

    fun last() {
        val s = _state.value
        goTo(s.mainline.lastOrNull()?.id ?: return)
    }

    /** From the graph: the tab stays where it is. */
    fun seekToPly(ply: Int) {
        val target = _state.value.mainline.firstOrNull { it.ply == ply } ?: return
        goTo(target.id)
    }

    fun nextKeyMoment(forward: Boolean) {
        val s = _state.value
        val here = s.gamePly
        val target = if (forward) s.keyMoments.firstOrNull { it.ply > here } else s.keyMoments.lastOrNull { it.ply < here }
        target?.let { goTo(it.id) }
    }

    // ── Played and best ──

    /**
     * Puts one of the engine's moves on the board in place of the one on the card. Tapping it again (or the move you
     * played) goes back to that move. The engine's move is a line of its own, taken away once you leave it.
     */
    fun showCandidate(uci: String) {
        val s = _state.value
        // From a line being shown, back to the move first.
        if (s.whyFrom != null) { goTo(s.whyFrom); showCandidate(uci); return }
        val played = s.playedNode
        val parent = played.parentId ?: return
        // The move you played, or the one already on show: back to it.
        if (uci == played.uci || (s.showingBest && s.currentNode.uci == uci)) {
            goTo(played.id)
            return
        }
        val (tree, id) = s.tree.addMove(parent, uci) ?: return
        val added = id !in s.tree.nodes
        _state.update { it.copy(bestNodeId = id, bestOf = played.id, engineIds = if (added) it.engineIds + id else it.engineIds) }
        goTo(id, tree = tree)
        gradeCandidate(s, tree, parent, id, uci)
    }

    /** An engine move is graded against the engine's own best for that position. */
    private fun gradeCandidate(s: ReviewUiState, tree: GameTree, parent: String, id: String, uci: String) {
        val top = s.candidates.firstOrNull()
        val cand = s.candidates.firstOrNull { it.uci == uci }
        val parentNode = tree.nodes[parent]
        val node = tree.nodes[id]
        if (top == null || cand == null || parentNode == null || node == null) return
        val before = ReviewedPosition(top.score, top.uci, emptyList(), s.candidates.getOrNull(1)?.score)
        val after = ReviewedPosition(cand.score, null, emptyList(), null)
        MoveClassifier.classifyGame(listOf(parentNode, node), mapOf(parentNode.fen to before, node.fen to after)).moves[node.id]?.let { review ->
            _state.update { it.copy(grades = it.grades + (id to review)) }
        }
    }

    // ── Show why ──

    /**
     * Plays the opponent's best reply to the move on the card, one move at a time, as far as it explains itself: through
     * the whole run of captures once one starts (a fork's check and forced reply come first), a checkmate, or the limit.
     * Then says what it came to.
     */
    fun showWhy() {
        val s = _state.value
        if (s.reviewing || s.thinking) return
        val played = (s.whyFrom ?: s.playedNode.id).let { s.tree.nodes[it] } ?: return
        // For a Miss the point is what you could have done, so the line starts before your move; otherwise it follows it.
        val missed = (s.report?.moves?.get(played.id) ?: s.grades[played.id])?.cls == MoveClass.MISS
        val start = (if (missed) played.parentId?.let { s.tree.nodes[it] } else played) ?: return
        whyJob?.cancel()
        liveJob?.cancel()
        replyJob?.cancel()
        goTo(start.id)
        _state.update { it.copy(whyFrom = played.id, whyText = null) }
        val fen = start.fen
        val mover = if (played.ply % 2 == 1) PieceColor.WHITE else PieceColor.BLACK
        val info = s.info
        val cfg = settings.value
        whyJob = viewModelScope.launch {
            val cached = if (missed) candidateCache[start.id]?.firstOrNull()?.takeIf { it.pv.isNotEmpty() } else null
            val line = cached?.let { EngineLine(1, 0, it.score, null, it.pv) }
                ?: engine.analyze(AnalyzeRequest(fen = fen, multiPv = 1, depth = 14, movetime = WHY_MS))?.lines?.firstOrNull()
            if (line == null) {
                _state.update { if (it.whyFrom == played.id) it.copy(whyText = "The engine had nothing to add here.") else it }
                return@launch
            }
            _state.update { if (it.whyFrom == played.id) it.copy(staleScore = line.score) else it }

            // Which of the engine's moves to play.
            val mating = missed && (line.score as? Score.Mate)?.let { it.value != 0 && ((mover == PieceColor.WHITE) == (it.value > 0)) } == true
            val plan = WhyLine.plan(fen, line.pv, mating).map { it.uci }

            for ((i, uci) in plan.withIndex()) {
                delay(if (i == 0) 500 else 900)
                val st = _state.value
                if (st.whyFrom != played.id) return@launch   // you moved on
                val (t, n) = st.tree.addMove(st.currentNodeId, uci) ?: break
                // The line is the engine's, unless the move is one of yours that was already there.
                if (n !in st.tree.nodes) _state.update { it.copy(engineIds = it.engineIds + n) }
                goTo(n, tree = t, keepWhy = true)
            }

            val st = _state.value
            if (st.whyFrom != played.id) return@launch
            val opponent = if (mover == PieceColor.WHITE) (info?.black ?: "Black") else (info?.white ?: "White")
            val text = WhyLine.text(fen, st.currentNode.fen, st.currentNode.san, mover, opponent, line.score, missed, mating)
            _state.update { if (it.whyFrom == played.id) it.copy(whyText = text) else it }
        }
    }

    // ── Your own moves ──

    fun onSquareClick(sq: Square) {
        val s = _state.value
        if (s.reviewing || s.thinking) return
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

    /** A move of yours. The game's own next move steps forward; anything else starts (or extends) a variation. */
    private fun play(from: Square, to: Square, promotion: PieceType?) {
        val s = _state.value
        if (s.reviewing || s.thinking) return
        val board = s.board
        val promo = promotion ?: if (board.getPiece(from)?.type == PieceType.PAWN && (to.rank == 7 || to.rank == 0)) PieceType.QUEEN else null
        if (s.whyFrom != null) _state.update { it.copy(whyFrom = null, whyText = null) }
        val (tree, id) = s.tree.addMove(s.currentNodeId, "${from.name}${to.name}${promo?.char ?: ""}") ?: return
        val isGameMove = id in s.gameIds
        // A move of yours from one of the engine's lines makes that line your own.
        _state.update { it.copy(engineIds = it.engineIds - pathTo(tree, id).toSet()) }
        goTo(id, tree = tree)
        if (!isGameMove) replyIfAsked()
    }

    /** Resume: back to the game, where the variation started. Your variation stays in the strip. */
    fun resume() {
        _state.value.resumeTo?.let { goTo(it) }
    }

    /** Takes your variations out of the tree, leaving the game. */
    fun clearVariations() {
        val s = _state.value
        if (!s.hasVariations) return
        val ids = s.gameIds
        val t = GameTree(s.tree.nodes.filterKeys { it in ids }.mapValues { (_, n) -> n.copy(children = n.children.filter { it in ids }) })
        val here = if (s.currentNodeId in ids) s.currentNodeId else s.resumeTo ?: ROOT_ID
        _state.update { it.copy(tree = t, engineIds = emptySet(), bestNodeId = null, bestOf = null, whyFrom = null, whyText = null, grades = emptyMap()) }
        goTo(here, tree = t)
    }

    /** With "Opponent replies" on, the engine answers a move of the side towards you with the opponent's. */
    private fun replyIfAsked() {
        if (!settings.value.opponentReply) return
        val s = _state.value
        val startId = s.currentNodeId
        val board = s.board
        if (board.isCheckmate() || board.isStalemate() || s.reviewing) return
        // Only moves of the side towards you are answered: moving the far side's pieces is you exploring.
        if (board.turn == s.orientation) return
        liveJob?.cancel()
        replyJob?.cancel()
        _state.update { it.copy(thinking = true) }
        val cfg = settings.value
        replyJob = viewModelScope.launch {
            val result = engine.analyze(AnalyzeRequest(fen = s.currentNode.fen, multiPv = 1, movetime = REPLY_MS))
            val best = result?.bestMove
            // The search for the reply also says how your move stands: that grades it.
            result?.lines?.firstOrNull()?.let { line ->
                val after = ReviewedPosition(line.score, best ?: line.pv.firstOrNull(), line.pv, null)
                evalCache[s.currentNode.fen] = after
                grade(startId, after)
            }
            val now = _state.value
            if (best != null && now.currentNodeId == startId && now.thinking) {
                val added = now.tree.addMove(startId, best)
                if (added != null) {
                    goTo(added.second, tree = added.first, fromReply = true)
                    _state.update { it.copy(thinking = false) }
                    refresh()
                    return@launch
                }
            }
            _state.update { it.copy(thinking = false) }
            refresh()
        }
    }

    // ── Odds and ends ──

    fun flip() {
        _state.update { it.copy(orientation = it.orientation.opponent()) }
    }

    /** Start Review, or a tap on the graph or a key moment: from the summary to the board. */
    fun showBoard() {
        _state.update { it.copy(showBoard = true) }
    }

    fun showSummary() {
        replyJob?.cancel()
        _state.update { it.copy(showBoard = false, thinking = false) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** The moves from the start to the position on screen, for carrying it to the analysis board. */
    fun lineToHere(): List<String> {
        val s = _state.value
        return generateSequence(s.currentNode) { n -> n.parentId?.let { s.tree.nodes[it] } }.toList().asReversed().mapNotNull { it.uci }
    }

    private companion object {
        /** How long the engine thinks over the opponent's reply. */
        const val REPLY_MS = 500L

        /** The most time the engine gets for the moves on the card. */
        const val CANDIDATE_MS = 700L

        /** The engine's time for the reply "Show why" plays out, and the most it plays (plies: single moves of either side). */
        const val WHY_MS = 800L
    }
}
