package wrklabs.chess.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wrklabs.chess.db.CardRepository
import wrklabs.chess.db.PuzzleCard
import wrklabs.chess.db.PuzzleRepository
import wrklabs.chess.db.PuzzleSummary
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.Square
import wrklabs.chess.sound.SoundPlayer

sealed class PuzzleState {
    object Loading : PuzzleState()
    object AllDone : PuzzleState()
    data class Question(
        val card: PuzzleCard,
        val board: ChessBoard,
        /** Index in the puzzle's line of the move you have to find next. */
        val step: Int,
        val selectedSquare: Square? = null,
        val status: PuzzleStatus = PuzzleStatus.Playing,
        val lastMove: Pair<Square, Square>? = null,
        /** A move was missed on an earlier try: it was counted then, and trying again counts for nothing. */
        val failed: Boolean = false,
        /** The opponent's reply is on its way. */
        val replying: Boolean = false,
        /** The move you got wrong, and the one that was wanted, worked out before "Show why" moves the pieces. */
        val wrongMove: Move? = null,
        val bestSan: String? = null,
        /** "Show why": the opponent's best reply to your move is being played (or was), and what it came to. */
        val whyStarted: Boolean = false,
        val whyText: String? = null,
        /** For a wrong move, as text for the card: the puzzle's line, what followed the move that was played, and the engine's grade for it. */
        val bestLine: String? = null,
        val playedLine: String? = null,
        val grade: wrklabs.chess.classifier.MoveClass? = null,
        val loss: Double = 0.0,
        /** The position the wrong move was made in. */
        val beforeFen: String? = null,
        /** Which of the two lines is on the board. */
        val lineOn: LineOn? = null,
        /** A pawn is on its way to the last rank and the piece to promote to is being asked. */
        val promotion: Pair<Square, Square>? = null,
    ) : PuzzleState() {
        /** The move the puzzle wants next. */
        val expected: String get() = card.puzzle.moves[step]
        /** Which of your moves this is, counting from 1. */
        val yourMove: Int get() = step / 2 + 1
    }
}

enum class PuzzleStatus { Playing, Solved, Wrong }

class PuzzleViewModel(
    private val puzzles: PuzzleRepository = wrklabs.chess.Graph.puzzles,
    private val cards: CardRepository = wrklabs.chess.Graph.cards,
) : ViewModel() {

    private var queue: List<PuzzleCard> = emptyList()
    private var index = 0

    private val _state = MutableStateFlow<PuzzleState>(PuzzleState.Loading)
    val state: StateFlow<PuzzleState> = _state.asStateFlow()

    private val _summary = MutableStateFlow(PuzzleSummary(0, 0, 0))
    val summary: StateFlow<PuzzleSummary> = _summary.asStateFlow()

    private var replyJob: Job? = null

    init {
        loadSession()
    }

    fun loadSession() {
        replyJob?.cancel()
        whyJob?.cancel()
        viewModelScope.launch {
            _state.value = PuzzleState.Loading
            _summary.value = puzzles.summary()
            queue = puzzles.session()
            index = 0
            show()
        }
    }

    private fun show() {
        while (index < queue.size) {
            val q = start(queue[index])
            if (q != null) { _state.value = q; return }
            index++   // a row whose line doesn't play: skipped
        }
        _state.value = PuzzleState.AllDone
    }

    /** The puzzle as you meet it: the opponent's first move already played. */
    private fun start(card: PuzzleCard): PuzzleState.Question? {
        val board = ChessBoard.fromFen(card.puzzle.fen)
        val first = board.legalMoves().find { it.uci == card.puzzle.moves[0] } ?: return null
        return PuzzleState.Question(card, board.makeMove(first), step = 1, lastMove = first.from to first.to)
    }

    fun onSquareClick(sq: Square) {
        val q = _state.value as? PuzzleState.Question ?: return
        if (q.status != PuzzleStatus.Playing || q.replying) return
        val selected = q.selectedSquare
        val own = q.board.getPiece(sq)?.takeIf { it.color == q.board.turn }
        if (selected == null) {
            if (own != null) _state.value = q.copy(selectedSquare = sq)
        } else if (selected == sq) {
            _state.value = q.copy(selectedSquare = null)
        } else if (q.board.legalMoves().any { it.from == selected && it.to == sq }) {
            onDrag(selected, sq)
        } else {
            _state.value = q.copy(selectedSquare = if (own != null) sq else null)
        }
    }

    fun onDrag(from: Square, to: Square) {
        val q = _state.value as? PuzzleState.Question ?: return
        if (q.status != PuzzleStatus.Playing || q.replying) return
        val candidates = q.board.legalMoves().filter { it.from == from && it.to == to }
        if (promotionMoves(q.board, from, to).isNotEmpty()) {
            _state.value = q.copy(selectedSquare = null, promotion = from to to)
            return
        }
        val move = candidates.firstOrNull() ?: return
        play(q, move)
    }

    fun choosePromotion(type: PieceType) {
        val q = _state.value as? PuzzleState.Question ?: return
        val (from, to) = q.promotion ?: return
        val move = promotionMoves(q.board, from, to).find { it.promotion == type } ?: return
        play(q.copy(promotion = null), move)
    }

    fun cancelPromotion() {
        val q = _state.value as? PuzzleState.Question ?: return
        _state.value = q.copy(promotion = null)
    }

    private fun play(q: PuzzleState.Question, move: Move) {
        val after = q.board.makeMove(move)
        val mates = after.isCheckmate()
        if (move.uci != q.expected && !mates) {
            SoundPlayer.playFailed()
            val len = wrklabs.chess.Graph.settings.value.lineLength
            _state.value = q.copy(
                selectedSquare = null, status = PuzzleStatus.Wrong, failed = true, wrongMove = move, bestSan = q.board.legalMoves().find { it.uci == q.expected }?.san,
                beforeFen = q.board.toFen(), bestLine = WhyLine.sanLine(q.board.toFen(), q.card.puzzle.moves.drop(q.step).take(len)),
            )
            explain(q, move)
            if (!q.failed) record(q, solved = false)
            return
        }
        // Another mate is as good as the one in the line.
        val next = q.step + 1
        val solved = mates || next >= q.card.puzzle.moves.size
        if (solved) {
            SoundPlayer.playSolved()
            _state.value = q.copy(board = after, step = next, selectedSquare = null, status = PuzzleStatus.Solved, lastMove = move.from to move.to)
            if (!q.failed) record(q, solved = true)
            return
        }
        SoundPlayer.playMove()
        _state.value = q.copy(board = after, step = next, selectedSquare = null, lastMove = move.from to move.to, replying = true)
        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            delay(600)
            val now = _state.value as? PuzzleState.Question ?: return@launch
            if (now.card.puzzle.id != q.card.puzzle.id || !now.replying) return@launch
            val reply = now.board.legalMoves().find { it.uci == now.expected }
            if (reply == null) { _state.value = now.copy(replying = false, status = PuzzleStatus.Solved); return@launch }
            SoundPlayer.playMove()
            _state.value = now.copy(board = now.board.makeMove(reply), step = now.step + 1, lastMove = reply.from to reply.to, replying = false)
        }
    }

    private fun record(q: PuzzleState.Question, solved: Boolean) {
        viewModelScope.launch {
            cards.recordResult(PuzzleRepository.cardId(q.card.puzzle.id), solved, kind = "puzzle")
            _summary.value = puzzles.summary()
        }
    }

    private var whyJob: Job? = null
    private var explainJob: Job? = null

    /** After a wrong move: what followed it, and the engine's grade for it, as text for the card. */
    private fun explain(q: PuzzleState.Question, move: Move) {
        explainJob?.cancel()
        explainJob = viewModelScope.launch {
            val len = wrklabs.chess.Graph.settings.value.lineLength
            val depth = WhyLine.depthFor(len, JUDGE_DEPTH)
            val ms = WhyLine.msFor(len, JUDGE_MS)
            val fen = q.board.toFen()
            val afterFen = q.board.makeMove(move).toFen()
            fun update(change: (PuzzleState.Question) -> PuzzleState.Question) {
                val now = _state.value as? PuzzleState.Question
                if (now != null && now.card.puzzle.id == q.card.puzzle.id && now.status == PuzzleStatus.Wrong) _state.value = change(now)
            }
            val after = searchPosition(afterFen, depth, ms) ?: return@launch
            val rest = WhyLine.plan(afterFen, after.pv, WhyLine.isMating(after.score, q.board.turn.opponent()), len).map { it.uci }
            update { it.copy(playedLine = WhyLine.sanLine(fen, listOf(move.uci) + rest)) }
            // The grade: the same judgement the review makes, between the position the move was made in and the one it led to.
            val before = searchPosition(fen, depth, ms)?.copy(bestMove = q.expected) ?: return@launch
            val (tree, id) = wrklabs.chess.model.GameTree.create(fen).addMove(wrklabs.chess.model.ROOT_ID, move.uci) ?: return@launch
            val parent = tree.nodes.getValue(wrklabs.chess.model.ROOT_ID)
            val node = tree.nodes.getValue(id)
            val review = wrklabs.chess.classifier.MoveClassifier.classifyGame(listOf(parent, node), mapOf(parent.fen to before, node.fen to after)).moves[node.id] ?: return@launch
            update { it.copy(grade = review.cls, loss = review.loss) }
        }
    }

    /** What followed the wrong move: it is played, then the opponent's best reply to it, as far as the line explains itself. */
    fun showWhy() = playOut(LineOn.Played)

    /** The puzzle's own line, played out from the position the wrong move was made in. */
    fun showBestLine() = playOut(LineOn.Best)

    private fun playOut(on: LineOn) {
        val q = _state.value as? PuzzleState.Question ?: return
        val wrong = q.wrongMove ?: return
        if (q.status != PuzzleStatus.Wrong) return
        val before = ChessBoard.fromFen(q.beforeFen ?: return)
        val start = if (on == LineOn.Best) before else before.makeMove(wrong)
        _state.value = q.copy(board = start, whyStarted = true, whyText = null, lineOn = on, lastMove = if (on == LineOn.Played) wrong.from to wrong.to else null, selectedSquare = null)
        if (on == LineOn.Played) SoundPlayer.playMove()
        whyJob?.cancel()
        whyJob = viewModelScope.launch {
            fun update(change: (PuzzleState.Question) -> PuzzleState.Question) {
                val now = _state.value as? PuzzleState.Question
                if (now != null && now.card.puzzle.id == q.card.puzzle.id && now.lineOn == on) _state.value = change(now)
            }
            val len = wrklabs.chess.Graph.settings.value.lineLength
            val moves: List<Move> = if (on == LineOn.Best) {
                // The puzzle's solution, with the opponent's replies in it.
                var b = before
                q.card.puzzle.moves.drop(q.step).take(len).mapNotNull { uci -> b.legalMoves().find { it.uci == uci }?.also { b = b.makeMove(it) } }
            } else {
                val startFen = start.toFen()
                val position = searchPosition(startFen, WhyLine.depthFor(len, JUDGE_DEPTH), WhyLine.msFor(len, JUDGE_MS))
                if (position == null) { update { it.copy(whyText = "The engine had nothing to add here.") }; return@launch }
                WhyLine.plan(startFen, position.pv, WhyLine.isMating(position.score, before.turn.opponent()), len)
            }
            var board = start
            for ((i, move) in moves.withIndex()) {
                delay(if (i == 0) 500 else WhyLine.PLY_MS)
                if ((_state.value as? PuzzleState.Question)?.lineOn != on) return@launch
                board = board.makeMove(move)
                SoundPlayer.playMove()
                val b = board
                update { it.copy(board = b, lastMove = move.from to move.to) }
            }
        }
    }

    fun retry() {
        replyJob?.cancel()
        whyJob?.cancel()
        explainJob?.cancel()
        val q = _state.value as? PuzzleState.Question ?: return
        _state.value = (start(q.card) ?: return).copy(failed = q.failed)
    }

    fun next() {
        replyJob?.cancel()
        whyJob?.cancel()
        explainJob?.cancel()
        index++
        show()
    }
}

/** Lichess tags that say how a puzzle ends, how long it is, what stage of the game it is or where it came from, not what it teaches. */
private val NOT_A_TACTIC = setOf("crushing", "advantage", "equality", "short", "long", "veryLong", "oneMove", "opening", "middlegame", "endgame", "master", "masterVsMaster", "superGM")

/** The tactics people look for first, in the order a puzzle's few tags are worth showing. The rest follow. */
private val TACTIC_ORDER = listOf(
    "mateIn1", "mateIn2", "mateIn3", "mateIn4", "mateIn5", "mate", "smotheredMate", "backRankMate", "anastasiaMate", "arabianMate", "bodenMate", "doubleBishopMate", "hookMate", "dovetailMate",
    "fork", "pin", "skewer", "discoveredAttack", "doubleCheck", "deflection", "attraction", "interference", "clearance", "xRayAttack", "sacrifice", "quietMove",
    "zugzwang", "trappedPiece", "hangingPiece", "capturingDefender", "intermezzo", "defensiveMove", "exposedKing", "kingsideAttack", "queensideAttack",
    "promotion", "underPromotion", "advancedPawn", "enPassant", "castling",
)

/** "advancedPawn" as a person would write it: "Advanced pawn"; "mateIn2" as "Mate in 2". */
private fun tagWords(tag: String): String = when (tag) {
    "xRayAttack" -> "X-ray attack"
    "attackingF2F7" -> "Attacking f2/f7"
    "morphysMate" -> "Morphy's mate"
    "pillsburysMate" -> "Pillsbury's mate"
    else -> tag.replace(Regex("(?<=[a-z])(?=[A-Z0-9])"), " ").lowercase().replaceFirstChar { it.uppercase() }
}

/** The tactics a solved puzzle was about, in readable words: at most three, the best known first. */
fun puzzleTags(themes: String, max: Int = 3): List<String> =
    themes.split(' ').filter { it.isNotEmpty() && it !in NOT_A_TACTIC }
        .sortedBy { TACTIC_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        .take(max).map(::tagWords)
