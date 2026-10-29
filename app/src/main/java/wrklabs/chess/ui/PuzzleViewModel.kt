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
        // An underpromotion is a different move: the one the puzzle wants wins if it is among them.
        val move = candidates.find { it.uci == q.expected } ?: candidates.firstOrNull() ?: return
        play(q, move)
    }

    private fun play(q: PuzzleState.Question, move: Move) {
        val after = q.board.makeMove(move)
        val mates = after.isCheckmate()
        if (move.uci != q.expected && !mates) {
            SoundPlayer.playFailed()
            _state.value = q.copy(selectedSquare = null, status = PuzzleStatus.Wrong, failed = true, wrongMove = move, bestSan = q.board.legalMoves().find { it.uci == q.expected }?.san)
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

    /** "Show why" on a wrong move: it is played, then the opponent's best reply to it, as far as the line explains itself. */
    fun showWhy() {
        val q = _state.value as? PuzzleState.Question ?: return
        val wrong = q.wrongMove
        if (q.status != PuzzleStatus.Wrong || q.whyStarted || wrong == null) return
        val after = q.board.makeMove(wrong)
        val startFen = after.toFen()
        val mover = q.board.turn
        _state.value = q.copy(board = after, whyStarted = true, whyText = null, lastMove = wrong.from to wrong.to, selectedSquare = null)
        SoundPlayer.playMove()
        whyJob?.cancel()
        whyJob = viewModelScope.launch {
            fun update(change: (PuzzleState.Question) -> PuzzleState.Question) {
                val now = _state.value as? PuzzleState.Question
                if (now != null && now.card.puzzle.id == q.card.puzzle.id && now.whyStarted) _state.value = change(now)
            }
            val result = wrklabs.chess.Graph.engine.analyze(
                wrklabs.chess.engine.AnalyzeRequest(fen = startFen, multiPv = 1, depth = JUDGE_DEPTH, movetime = JUDGE_MS),
            )
            val line = result?.lines?.firstOrNull()
            if (line == null) { update { it.copy(whyText = "The engine had nothing to add here.") }; return@launch }
            var board = after
            var last: Move? = null
            for ((i, move) in WhyLine.plan(startFen, line.pv).withIndex()) {
                delay(if (i == 0) 500 else 900)
                if ((_state.value as? PuzzleState.Question)?.whyStarted != true) return@launch
                board = board.makeMove(move)
                last = move
                SoundPlayer.playMove()
                val b = board
                update { it.copy(board = b, lastMove = move.from to move.to) }
            }
            update { it.copy(whyText = WhyLine.text(startFen, board.toFen(), last?.san, mover, "Your opponent", line.score)) }
        }
    }

    /** From the start again; a miss already counted stays counted. */
    fun retry() {
        replyJob?.cancel()
        whyJob?.cancel()
        val q = _state.value as? PuzzleState.Question ?: return
        _state.value = (start(q.card) ?: return).copy(failed = q.failed)
    }

    fun next() {
        replyJob?.cancel()
        whyJob?.cancel()
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
