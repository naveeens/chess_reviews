/*
 * Copyright (C) bijoyv9 and contributors (Rookahead, https://github.com/rookahead/rookahead)
 * Copyright (C) 2026 naveen s
 *
 * Derived from Rookahead's src/lib/mistakes.ts (puzzles made from your own mistakes), ported to
 * Kotlin and changed for Android.
 * SPDX-License-Identifier: GPL-3.0-or-later. See NOTICE.
 */
package wrklabs.chess.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.classifier.MoveClassifier
import wrklabs.chess.classifier.ReviewedPosition
import wrklabs.chess.db.CardRepository
import wrklabs.chess.engine.AnalyzeRequest
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID
import kotlinx.coroutines.Job
import wrklabs.chess.db.CardSummary
import wrklabs.chess.db.MistakeCardPuzzle
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move
import wrklabs.chess.model.Square
import wrklabs.chess.sound.SoundPlayer

sealed class TrainState {
    object Loading : TrainState()
    object AllDone : TrainState()
    data class Question(
        val card: MistakeCardPuzzle,
        val board: ChessBoard,
        val selectedSquare: Square? = null,
        val userPlayedMove: Move? = null,
        val status: CardAnswerStatus = CardAnswerStatus.Unanswered,
        /** "Show best" was tapped on a move that was good but not best. */
        val bestShown: Boolean = false,
        /** What the engine made of the answer, and by how much it fell short of the best. */
        val grade: MoveClass? = null,
        val loss: Double = 0.0,
        /** "Show why": the opponent's best reply is being played (or was), and what it came to. */
        val whyStarted: Boolean = false,
        val whyText: String? = null,
        val lastMove: Pair<Square, Square>? = null
    ) : TrainState()
}

enum class CardAnswerStatus {
    Unanswered,
    /** The engine is looking at a move that isn't the stored best. */
    Checking,
    Correct,
    /** A good move, but not the best: nothing is recorded, and you are told there is a better one. */
    Better,
    Wrong
}

/** How an answer other than the stored best move counts. */
enum class Verdict { Solved, Better, Wrong }

/** As good as the best (Best, Brilliant, Great, or too close to tell), a good move that isn't, or not good enough. */
fun verdictFor(cls: MoveClass, loss: Double, checkmate: Boolean): Verdict = when {
    checkmate -> Verdict.Solved
    cls == MoveClass.BEST || cls == MoveClass.BRILLIANT || cls == MoveClass.GREAT || cls == MoveClass.FORCED || loss < SAME_AS_BEST -> Verdict.Solved
    cls == MoveClass.EXCELLENT || cls == MoveClass.GOOD -> Verdict.Better
    else -> Verdict.Wrong
}

/** An answer's verdict, and the class and shortfall behind it. */
data class Judged(val verdict: Verdict, val cls: MoveClass, val loss: Double)

/** How deep, and for how long at most, the engine looks when it judges an answer. */
const val JUDGE_DEPTH = 14
const val JUDGE_MS = 700L

/** Less than this of win chance behind the best move counts as just as good. */
const val SAME_AS_BEST = 0.005

class TrainViewModel(
    private val cardRepository: CardRepository = wrklabs.chess.Graph.cards
) : ViewModel() {

    private val _cards = MutableStateFlow<List<MistakeCardPuzzle>>(emptyList())
    private val _currentIndex = MutableStateFlow(0)

    private val _trainState = MutableStateFlow<TrainState>(TrainState.Loading)
    val trainState: StateFlow<TrainState> = _trainState.asStateFlow()

    private val _summary = MutableStateFlow(CardSummary(0, 0, 0))
    val summary: StateFlow<CardSummary> = _summary.asStateFlow()

    init {
        loadSession()
    }

    fun loadSession() {
        viewModelScope.launch {
            _trainState.value = TrainState.Loading
            _summary.value = cardRepository.getSummary()
            val list = cardRepository.getDueCards()
            _cards.value = list
            _currentIndex.value = 0
            showCurrentCard()
        }
    }

    private fun showCurrentCard() {
        val list = _cards.value
        val idx = _currentIndex.value
        if (list.isEmpty() || idx >= list.size) {
            _trainState.value = TrainState.AllDone
            return
        }
        val card = list[idx]
        val board = ChessBoard.fromFen(card.fen)
        _trainState.value = TrainState.Question(
            card = card,
            board = board,
            selectedSquare = null,
            userPlayedMove = null,
            status = CardAnswerStatus.Unanswered,
            lastMove = card.opponentMove()
        )
    }

    fun onSquareClick(sq: Square) {
        val state = _trainState.value as? TrainState.Question ?: return
        if (state.status != CardAnswerStatus.Unanswered) return

        val selected = state.selectedSquare
        if (selected == null) {
            val p = state.board.getPiece(sq)
            if (p != null && p.color == state.board.turn) {
                _trainState.value = state.copy(selectedSquare = sq)
            }
        } else {
            if (selected == sq) {
                _trainState.value = state.copy(selectedSquare = null)
            } else {
                val legal = state.board.legalMoves().find { it.from == selected && it.to == sq }
                if (legal != null) {
                    onUserMove(legal)
                } else {
                    val p = state.board.getPiece(sq)
                    if (p != null && p.color == state.board.turn) {
                        _trainState.value = state.copy(selectedSquare = sq)
                    } else {
                        _trainState.value = state.copy(selectedSquare = null)
                    }
                }
            }
        }
    }

    private var checkJob: Job? = null

    fun onUserMove(move: Move) {
        val state = _trainState.value as? TrainState.Question ?: return
        if (state.status != CardAnswerStatus.Unanswered) return

        val newBoard = state.board.makeMove(move)
        val isBest = move.uci.equals(state.card.bestUci, ignoreCase = true) || move.san.equals(state.card.bestSan, ignoreCase = true)
        val isTheMistake = move.uci.equals(state.card.playedUci, ignoreCase = true)
        if (isBest || isTheMistake) {
            val was = runCatching { MoveClass.valueOf(state.card.cls) }.getOrDefault(MoveClass.MISTAKE)
            finish(state, move, newBoard, if (isBest) Judged(Verdict.Solved, MoveClass.BEST, 0.0) else Judged(Verdict.Wrong, was, (state.card.winBefore - state.card.winAfter) / 100.0))
            return
        }
        // Another move: the engine says whether it is as good, good but not the best, or not good enough.
        _trainState.value = state.copy(board = newBoard, selectedSquare = null, userPlayedMove = move, status = CardAnswerStatus.Checking)
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val judged = judge(state.card, move) ?: Judged(Verdict.Wrong, MoveClass.MISTAKE, 0.0)
            val now = _trainState.value as? TrainState.Question
            if (now == null || now.card.id != state.card.id || now.status != CardAnswerStatus.Checking) return@launch
            finish(now, move, newBoard, judged)
        }
    }

    /** What the engine makes of each card's position before the move, worked out once at the depth the answers are judged at. */
    private val bestCache = mutableMapOf<String, ReviewedPosition>()

    private suspend fun search(fen: String): ReviewedPosition? {
        val settings = wrklabs.chess.Graph.settings.value
        val board = ChessBoard.fromFen(fen)
        if (board.isCheckmate()) return ReviewedPosition(wrklabs.chess.engine.checkmatedScore(board.turn), null, emptyList())
        if (board.isStalemate()) return ReviewedPosition(Score.Centipawns(0), null, emptyList())
        val result = wrklabs.chess.Graph.engine.analyze(
            AnalyzeRequest(fen = fen, multiPv = 1, depth = JUDGE_DEPTH, movetime = JUDGE_MS),
        )
        val line = result?.lines?.firstOrNull() ?: return null
        return ReviewedPosition(line.score, result.bestMove ?: line.pv.firstOrNull(), line.pv, null)
    }

    /**
     * The engine's view of a move other than the stored best, by the same rules as the review. The position before and the
     * position after are searched here, at the same depth, so the gap is the engine's and not the review's shallower one.
     * Null when it can't say.
     */
    private suspend fun judge(card: MistakeCardPuzzle, move: Move): Judged? {
        val (tree, id) = GameTree.create(card.fen).addMove(ROOT_ID, move.uci) ?: return null
        val parent = tree.nodes.getValue(ROOT_ID)
        val node = tree.nodes.getValue(id)
        val before = bestCache[card.id] ?: search(card.fen)?.copy(bestMove = card.bestUci)?.also { bestCache[card.id] = it } ?: return null
        val after = search(node.fen) ?: return null
        val review = MoveClassifier.classifyGame(listOf(parent, node), mapOf(parent.fen to before, node.fen to after)).moves[node.id] ?: return null
        return Judged(verdictFor(review.cls, review.loss, node.san?.endsWith("#") == true), review.cls, review.loss)
    }

    private fun finish(state: TrainState.Question, move: Move, board: ChessBoard, judged: Judged) {
        val verdict = judged.verdict
        val status = when (verdict) {
            Verdict.Solved -> CardAnswerStatus.Correct
            Verdict.Better -> CardAnswerStatus.Better
            Verdict.Wrong -> CardAnswerStatus.Wrong
        }
        when (verdict) {
            Verdict.Solved -> SoundPlayer.playSolved()
            Verdict.Better -> SoundPlayer.playMove()
            Verdict.Wrong -> SoundPlayer.playFailed()
        }
        _trainState.value = state.copy(board = board, selectedSquare = null, userPlayedMove = move, status = status, grade = judged.cls, loss = judged.loss)
        // A good move that isn't the best is not an answer yet: the card stays as it was.
        if (verdict == Verdict.Better) return
        viewModelScope.launch {
            cardRepository.recordResult(state.card.id, verdict == Verdict.Solved)
            _summary.value = cardRepository.getSummary()
        }
    }

    private var whyJob: Job? = null

    /** "Show why" on a wrong answer: the opponent's best reply to it, played out on the board. */
    fun showWhy() {
        val state = _trainState.value as? TrainState.Question ?: return
        if (state.status != CardAnswerStatus.Wrong || state.whyStarted) return
        _trainState.value = state.copy(whyStarted = true, whyText = null)
        val startFen = state.board.toFen()
        val mover = if (state.card.fen.split(" ").getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE
        whyJob?.cancel()
        whyJob = viewModelScope.launch {
            val position = search(startFen)
            fun update(change: (TrainState.Question) -> TrainState.Question) {
                val now = _trainState.value as? TrainState.Question
                if (now != null && now.card.id == state.card.id && now.whyStarted) _trainState.value = change(now)
            }
            if (position == null) { update { it.copy(whyText = "The engine had nothing to add here.") }; return@launch }
            var board = state.board
            var last: Move? = null
            for ((i, move) in WhyLine.plan(startFen, position.pv).withIndex()) {
                kotlinx.coroutines.delay(if (i == 0) 500 else 900)
                if ((_trainState.value as? TrainState.Question)?.whyStarted != true) return@launch
                board = board.makeMove(move)
                last = move
                SoundPlayer.playMove()
                val b = board
                update { it.copy(board = b, lastMove = move.from to move.to) }
            }
            val text = WhyLine.text(startFen, board.toFen(), last?.san, mover, state.card.opponent, position.score)
            update { it.copy(whyText = text) }
        }
    }

    /** "Show best" on a good move that wasn't the best. */
    fun showBest() {
        val state = _trainState.value as? TrainState.Question ?: return
        if (state.status == CardAnswerStatus.Better) _trainState.value = state.copy(bestShown = true)
    }

    fun nextCard() {
        checkJob?.cancel()
        whyJob?.cancel()
        _currentIndex.value += 1
        showCurrentCard()
    }

    fun retryCurrentCard() {
        checkJob?.cancel()
        whyJob?.cancel()
        val state = _trainState.value as? TrainState.Question ?: return
        val originalBoard = ChessBoard.fromFen(state.card.fen)
        _trainState.value = state.copy(
            board = originalBoard,
            selectedSquare = null,
            userPlayedMove = null,
            status = CardAnswerStatus.Unanswered,
            bestShown = false,
            grade = null,
            loss = 0.0,
            whyStarted = false,
            whyText = null,
            lastMove = state.card.opponentMove()
        )
    }
}

/** The squares of the opponent's move that led to this card's position: what en passant, a check or a recapture hangs on. */
private fun MistakeCardPuzzle.opponentMove(): Pair<Square, Square>? =
    prevUci?.takeIf { it.length >= 4 }?.let { Square.fromName(it.substring(0, 2)) to Square.fromName(it.substring(2, 4)) }
