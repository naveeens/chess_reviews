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
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.maia.Human
import wrklabs.chess.maia.MaiaBot
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.Square
import kotlinx.coroutines.ensureActive
import wrklabs.chess.engine.Score
import wrklabs.chess.review.ReviewService
import wrklabs.chess.play.*
import wrklabs.chess.sound.SoundPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class SideChoice(val label: String) { White("White"), Black("Black"), Random("Random") }

/** One of the moves people of the user's rating play here, for the hint. */
data class HintLine(val san: String, val from: Square, val to: Square, val share: Float)

enum class HintKind { People, Best }

/** The engine's best move here, with its evaluation from your side: "+0.30". */
data class BestHint(val san: String, val from: Square, val to: Square, val eval: String)

/** A move held back because the engine thinks it gives the game away, until you say to play it anyway. */
data class BlunderWarning(val move: Move, val lost: Int, val bestSan: String?)

/** A game that has ended: how it went, what to say about it, and the game ready to be reviewed. */
data class Finished(val game: ImportedGame, val result: String, val title: String, val levelNote: String?)

data class PlayUi(
    val inGame: Boolean = false,
    val loaded: Boolean = false,
    // The setup.
    val botElo: Int = 1500,
    val auto: Boolean = true,
    val side: SideChoice = SideChoice.White,
    val userElo: Int = 1500,
    val results: List<PlayResult> = emptyList(),
    /** A game left unfinished, which can be continued. */
    val resume: PlayRecord? = null,
    /** A position to play from instead of the usual start, with where it came from. */
    val custom: PlayRequest? = null,
    // The game.
    val game: PlayGame = PlayGame.start(),
    val startedAt: Long = 0,
    val userWhite: Boolean = true,
    val flipped: Boolean = false,
    /** The position being looked at instead of the live one: how many moves in. */
    val viewPly: Int? = null,
    val selected: Square? = null,
    val promotion: Pair<Square, Square>? = null,
    val thinking: Boolean = false,
    val hints: List<HintLine>? = null,
    val hintsUsed: Int = 0,
    val hintKind: HintKind = HintKind.People,
    val best: BestHint? = null,
    val bestLoading: Boolean = false,
    /** The engine is looking at the move you just made. */
    val checking: Boolean = false,
    val warning: BlunderWarning? = null,
    val takebacks: Int = 0,
    /** Something the bot did that deserves a line: "Maia declines the draw". */
    val notice: String? = null,
    val finished: Finished? = null,
) {
    val userColor get() = if (userWhite) PieceColor.WHITE else PieceColor.BLACK
    val botColor get() = userColor.opponent()
    val live get() = viewPly == null
    val myTurn get() = game.end == null && game.turn == userColor
    /** Whether the board takes moves from you now. */
    val canMove get() = myTurn && live && !checking && warning == null
    /** The level the bot plays at, as the setup shows it. */
    val autoOn get() = auto && custom == null
}

/**
 * Play against Maia. All the state is here, not in the composables, so a handler never works from an old copy of the game;
 * the game in progress is also written to storage after every move, to be continued later.
 */
class PlayViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Graph.play
    private val maia = Graph.maia
    private val reviews = Graph.reviews
    private val settings = Graph.settings
    private var bgJob: Job? = null
    private var checkJob: Job? = null
    private var bestJob: Job? = null
    private val _state = MutableStateFlow(PlayUi())
    val state: StateFlow<PlayUi> = _state.asStateFlow()
    private val random = Random(System.nanoTime())
    private var botJob: Job? = null
    /** The bot's own outlook on each of its turns, for deciding whether to resign. */
    private val botOutlooks = mutableListOf<Outlook>()
    private val loadJob = viewModelScope.launch { load() }

    private suspend fun load() {
        val userElo = Human.userElo(Graph.games.getAllGames().map { it.rating })
        val level = store.level() ?: AutoLevel.snap(userElo)
        _state.update { it.copy(userElo = userElo, botElo = level, results = store.results(), resume = if (it.inGame) null else store.active(), loaded = true) }
    }

    // ── Setup ──

    fun setElo(elo: Int) = _state.update { it.copy(botElo = elo, auto = false) }
    fun setAuto(on: Boolean) = viewModelScope.launch {
        loadJob.join()
        val level = store.level() ?: AutoLevel.snap(_state.value.userElo)
        _state.update { it.copy(auto = on, botElo = if (on) level else it.botElo) }
    }
    fun setSide(side: SideChoice) = _state.update { it.copy(side = side) }
    fun clearCustom() = _state.update { it.copy(custom = null) }

    /** A position asked for from a review or a lesson: the setup shows it and waits for Start. */
    fun prepare(request: PlayRequest) {
        val toMove = ChessBoard.fromFen(request.fen).turn == PieceColor.WHITE
        _state.update {
            it.copy(
                inGame = false, custom = request,
                side = when (request.userWhite ?: toMove) { true -> SideChoice.White; false -> SideChoice.Black },
                botElo = request.botElo?.let(AutoLevel::snap) ?: it.botElo,
                auto = false,
            )
        }
    }

    fun start() {
        viewModelScope.launch {
            loadJob.join()
            val s = _state.value
            val userWhite = when (s.side) { SideChoice.White -> true; SideChoice.Black -> false; SideChoice.Random -> random.nextBoolean() }
            val fen = s.custom?.fen ?: ChessBoard.DEFAULT_FEN
            begin(PlayRecord(fen, emptyList(), userWhite, s.botElo, s.userElo, System.currentTimeMillis(), auto = s.autoOn))
        }
    }

    fun continueGame() { _state.value.resume?.let(::begin) }
    fun discardSaved() { _state.update { it.copy(resume = null) }; viewModelScope.launch { store.clearActive() } }

    private fun begin(rec: PlayRecord) {
        botJob?.cancel()
        botOutlooks.clear()
        _state.update {
            it.copy(
                inGame = true, resume = null, game = PlayGame.replay(rec.ucis, fen = rec.startFen), startedAt = rec.startedAt, userWhite = rec.userWhite,
                botElo = rec.botElo, userElo = rec.userElo, auto = rec.auto, flipped = false, viewPly = null, selected = null, promotion = null,
                thinking = false, hints = null, best = null, bestLoading = false, checking = false, warning = null, hintsUsed = rec.hints, takebacks = rec.takebacks, notice = null, finished = null,
            )
        }
        afterChange()
    }

    /** Back to the setup, once the game is over (or abandoned: it stays saved to continue). */
    fun leave() {
        botJob?.cancel(); bgJob?.cancel(); checkJob?.cancel(); bestJob?.cancel()
        _state.update { it.copy(inGame = false, thinking = false, viewPly = null, finished = null, custom = null) }
        viewModelScope.launch { _state.update { it.copy(resume = store.active(), results = store.results(), botElo = if (it.auto) (store.level() ?: it.botElo) else it.botElo) } }
    }

    // ── Moves ──

    fun click(sq: Square) {
        val s = _state.value
        if (!s.canMove) return
        val b = s.game.board
        val from = s.selected
        val own = b.getPiece(sq)?.takeIf { it.color == s.userColor }
        when {
            from == null -> if (own != null) _state.update { it.copy(selected = sq) }
            from == sq -> _state.update { it.copy(selected = null) }
            b.legalMoves().any { it.from == from && it.to == sq } -> attempt(from, sq)
            own != null -> _state.update { it.copy(selected = sq) }
            else -> _state.update { it.copy(selected = null) }
        }
    }

    fun attempt(from: Square, to: Square) {
        val s = _state.value
        if (!s.canMove) return
        when {
            promotionMoves(s.game.board, from, to).isNotEmpty() -> _state.update { it.copy(selected = null, promotion = from to to) }
            s.game.board.legalMoves().any { it.from == from && it.to == to } -> play(from, to, null)
            else -> _state.update { it.copy(selected = null) }
        }
    }

    fun choosePromotion(type: PieceType) {
        val (from, to) = _state.value.promotion ?: return
        play(from, to, type)
    }
    fun cancelPromotion() = _state.update { it.copy(promotion = null) }

    private fun play(from: Square, to: Square, promo: PieceType?) {
        val s = _state.value
        val move = s.game.board.legalMoves().firstOrNull { it.from == from && it.to == to && it.promotion == promo } ?: return
        val next = s.game.play(move) ?: return
        if (settings.value.blunderCheck) check(s.game, move, next) else commit(next, s.game, move)
    }

    /** Asks the engine whether [move] gives the game away before it is played; if it does, holds it for a say from you. */
    private fun check(before: PlayGame, move: Move, next: PlayGame) {
        checkJob?.cancel()
        bgJob?.cancel()
        _state.update { it.copy(checking = true, selected = null, promotion = null, hints = null, best = null) }
        checkJob = viewModelScope.launch {
            val warning = runCatching { judge(before, move, next) }.getOrNull()
            if (_state.value.game !== before) return@launch
            if (warning == null) {
                _state.update { it.copy(checking = false) }
                commit(next, before, move)
            } else {
                _state.update { it.copy(checking = false, warning = warning) }
                startBackground()
            }
        }
    }

    private suspend fun judge(before: PlayGame, move: Move, next: PlayGame): BlunderWarning? {
        val speed = settings.value.reviewSpeed
        val depth = minOf(speed.depth, CHECK_DEPTH)
        val ms = minOf(speed.moveMs, CHECK_MS)
        val was = reviews.position(before.fenAt(before.moves.size), depth, ms)
        val end = next.end
        val now = when {
            end == null -> reviews.position(next.fenAt(next.moves.size), depth, ms).score
            end.draw -> Score.Centipawns(0)
            else -> return null          // it ends the game in a win
        }
        val lost = MoveCheck.drop(was.score, now, before.turn)
        if (lost < MoveCheck.WARN_DROP || was.bestMove == move.uci) return null
        val fen = before.fenAt(before.moves.size)
        return BlunderWarning(move, lost.toInt(), was.bestMove?.let { ReviewService.sanOf(fen, it) })
    }

    /** Plays the move the warning held back. */
    fun playAnyway() {
        val s = _state.value
        val w = s.warning ?: return
        val next = s.game.play(w.move) ?: return
        _state.update { it.copy(warning = null) }
        commit(next, s.game, w.move)
    }

    /** Drops the move the warning held back, for you to choose another. */
    fun chooseAnother() = _state.update { it.copy(warning = null) }

    /** The engine looks at each position of the game, newest first, for the review and for the checks: cheap when it has seen one before. */
    private fun startBackground() {
        bgJob?.cancel()
        val cfg = settings.value
        if (!cfg.analyseWhilePlaying) return
        val g = _state.value.game
        val fens = (0..g.moves.size).map { g.fenAt(it) }.reversed().distinct()
        bgJob = viewModelScope.launch {
            for (f in fens) {
                ensureActive()
                runCatching { reviews.position(f, cfg.reviewSpeed.depth, cfg.reviewSpeed.moveMs) }
            }
        }
    }

    /** Puts [next] on the board in place of [before]; [move] is the one just made, for the sound. */
    private fun commit(next: PlayGame, before: PlayGame, move: Move?, notice: String? = null) {
        _state.update { it.copy(game = next, selected = null, promotion = null, hints = null, best = null, viewPly = null, thinking = false, notice = notice) }
        if (move != null) sound(before, move)
        afterChange()
    }

    private fun sound(before: PlayGame, move: Move) {
        val capture = move.san.contains('x')
        if (move.san.endsWith("#")) SoundPlayer.playGameEnd() else if (capture) SoundPlayer.playCapture() else SoundPlayer.playMove()
    }

    /** What follows any change to the game: save it, and see to the end of it or to the bot's move. */
    private fun afterChange() {
        val s = _state.value
        val end = s.game.end
        startBackground()
        if (end != null) { finish(s, end); return }
        persist(s)
        if (s.game.turn == s.botColor) startBot()
    }

    private fun persist(s: PlayUi) {
        // A game with no move yet is not worth coming back to: drop any older save instead of keeping an empty one.
        if (s.game.ucis.isEmpty()) { viewModelScope.launch { store.clearActive() }; return }
        val rec = PlayRecord(s.game.startFen, s.game.ucis, s.userWhite, s.botElo, s.userElo, s.startedAt, s.takebacks, s.hintsUsed, s.auto)
        viewModelScope.launch { store.saveActive(rec) }
    }

    private fun startBot() {
        botJob?.cancel()
        _state.update { it.copy(thinking = true) }
        botJob = viewModelScope.launch {
            delay(350)
            val s = _state.value
            val g = s.game
            val pred = runCatching { maia.predictFull(g.fens.takeLast(MAIA_HISTORY), s.botElo, s.userElo) }.getOrNull()
            if (_state.value.game !== g) return@launch               // the game moved on (a takeback) while it thought
            if (pred != null) {
                botOutlooks += pred.outlook
                if (BotMind.resigns(botOutlooks, g.moves.size, s.botElo)) { commit(g.resign(s.botColor), g, null, "Maia resigns."); return@launch }
            }
            val uci = pred?.let { MaiaBot.sample(it.moves, random) } ?: g.board.legalMoves().randomOrNull(random)?.uci ?: return@launch
            val move = g.board.legalMoves().firstOrNull { it.uci == uci } ?: return@launch
            val next = g.play(move) ?: return@launch
            commit(next, g, move)
        }
    }

    // ── The things to do in a game ──

    fun flip() = _state.update { it.copy(flipped = !it.flipped) }

    /** Takes back your last move, and the bot's answer to it if it has made one, so it is your turn again. */
    fun takeback() {
        val s = _state.value
        if (s.game.end != null || s.game.moves.isEmpty() || s.checking) return
        val plies = if (s.game.turn == s.userColor) 2 else 1
        val back = s.game.takeback(plies)
        if (back.turn != s.userColor || back.moves.size == s.game.moves.size) return   // nothing of yours to take back
        botJob?.cancel()
        botOutlooks.clear()
        _state.update { it.copy(game = back, takebacks = it.takebacks + 1, selected = null, promotion = null, hints = null, best = null, warning = null, viewPly = null, thinking = false, notice = null) }
        persist(_state.value)
    }

    /** Looks at an earlier position: [ply] moves in. The game itself waits. */
    fun view(ply: Int?) = _state.update { it.copy(viewPly = ply?.takeIf { p -> p < it.game.moves.size }, selected = null, promotion = null) }
    fun viewPrevious() { val s = _state.value; view(((s.viewPly ?: s.game.moves.size) - 1).coerceAtLeast(0)) }
    fun viewNext() { val s = _state.value; s.viewPly?.let { view(it + 1) } }

    /** What players of your rating play here: the three most common moves. */
    fun hint() {
        val s = _state.value
        if (!s.canMove) return
        viewModelScope.launch {
            val g = s.game
            val pred = runCatching { maia.predictFull(g.fens.takeLast(MAIA_HISTORY), s.userElo, s.botElo) }.getOrNull() ?: return@launch
            val top = g.board.legalMoves().mapNotNull { m -> pred.moves[m.uci]?.let { m to it } }.sortedByDescending { it.second }.take(3)
            val lines = top.map { (m, p) -> HintLine(m.san, m.from, m.to, p) }
            _state.update { if (it.game === g) it.copy(hints = lines, best = null, hintsUsed = it.hintsUsed + 1) else it }
            if (_state.value.hintKind == HintKind.Best) loadBest()
        }
    }

    /** Switches the hint between what people play and what the engine would. */
    fun setHintKind(kind: HintKind) {
        _state.update { it.copy(hintKind = kind) }
        if (kind == HintKind.Best && _state.value.best == null) loadBest()
    }

    private fun loadBest() {
        val s = _state.value
        val g = s.game
        bestJob?.cancel()
        bgJob?.cancel()
        _state.update { it.copy(bestLoading = true) }
        bestJob = viewModelScope.launch {
            val fen = g.fenAt(g.moves.size)
            val found = runCatching { reviews.position(fen, BEST_DEPTH, BEST_MS) }.getOrNull()
            val move = found?.bestMove?.let { uci -> g.board.legalMoves().firstOrNull { it.uci == uci } }
            val hint = if (found != null && move != null) BestHint(move.san, move.from, move.to, MoveCheck.textFor(found.score, s.userColor)) else null
            _state.update { if (it.game === g) it.copy(best = hint, bestLoading = false) else it }
            startBackground()
        }
    }

    fun resign() { val s = _state.value; if (s.game.end == null) commit(s.game.resign(s.userColor), s.game, null) }

    /** Offers a draw: the bot takes it if it does not think it is better. */
    fun offerDraw() {
        val s = _state.value
        if (!s.canMove) return
        viewModelScope.launch {
            val g = s.game
            val pred = runCatching { maia.predictFull(g.fens.takeLast(MAIA_HISTORY), s.userElo, s.botElo) }.getOrNull()
            val mine = pred?.outlook ?: return@launch
            // The outlook is the user's, who is to move; the bot's is the other way round.
            val bots = Outlook(win = mine.loss, draw = mine.draw, loss = mine.win)
            if (_state.value.game !== g) return@launch
            if (BotMind.acceptsDraw(bots, g.moves.size)) commit(g.agreeDraw(), g, null, "Maia accepts the draw.")
            else _state.update { it.copy(notice = "Maia declines the draw.") }
        }
    }

    // ── The end ──

    private var recorded: Long? = null

    private fun finish(s: PlayUi, end: GameEnd) {
        botJob?.cancel()
        val g = s.game
        val result = when { end.draw -> "draw"; end.winner == s.userColor -> "win"; else -> "loss" }
        val white = if (s.userWhite) ME else BOT
        val black = if (s.userWhite) BOT else ME
        val date = Date(s.startedAt)
        val pgn = g.pgn(white, black, if (s.userWhite) s.userElo else s.botElo, if (s.userWhite) s.botElo else s.userElo, SimpleDateFormat("yyyy.MM.dd", Locale.US).format(date), "Casual game against $BOT ${s.botElo}")
        val game = ImportedGame("play:${s.startedAt}", "play", white, black, if (s.userWhite) s.userElo else s.botElo, if (s.userWhite) s.botElo else s.userElo, end.result, "casual", pgn, System.currentTimeMillis())
        val counts = g.startFen == ChessBoard.DEFAULT_FEN
        val firstTime = recorded != s.startedAt
        viewModelScope.launch {
            var note: String? = null
            if (firstTime) {
                recorded = s.startedAt
                store.addGame(game)
                if (counts && g.moves.isNotEmpty()) {
                    store.addResult(PlayResult(s.startedAt, s.botElo, s.userElo, result, s.userWhite, s.hintsUsed, s.takebacks))
                    if (s.auto) { val next = AutoLevel.next(s.botElo, result); store.setLevel(next); note = "Next game: $next." }
                }
                store.clearActive()
            }
            val results = store.results()
            _state.update { it.copy(finished = Finished(game, result, resultTitle(end, s.userColor), note), results = results) }
        }
    }

    companion object {
        const val BOT = "Maia"
        const val ME = "You"
        private const val MAIA_HISTORY = 8
        private const val CHECK_DEPTH = 12
        private const val CHECK_MS = 300L
        private const val BEST_DEPTH = 16
        private const val BEST_MS = 800L
    }
}

fun resultTitle(end: GameEnd, user: PieceColor): String {
    val how = end.reason.text
    return when {
        end.reason == EndReason.Agreement -> "Draw by agreement"
        end.draw -> "Draw by $how"
        end.winner == user -> if (end.reason == EndReason.Resignation) "Maia resigned. You won" else "You won by $how"
        else -> if (end.reason == EndReason.Resignation) "You resigned" else "You lost by $how"
    }
}
