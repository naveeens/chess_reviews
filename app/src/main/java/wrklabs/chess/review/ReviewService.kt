package wrklabs.chess.review

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wrklabs.chess.classifier.*
import wrklabs.chess.db.GameRecord
import wrklabs.chess.db.GameRepository
import wrklabs.chess.db.MoveRecord
import wrklabs.chess.engine.*
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.MoveNode
import wrklabs.chess.model.ROOT_ID
import wrklabs.chess.ui.EvalPoint
import kotlin.math.abs
import kotlin.math.ln

/** What a game is, apart from its moves. */
data class GameInfo(
    val key: String,
    val source: String,
    val white: String,
    val black: String,
    val whiteRating: Int? = null,
    val blackRating: Int? = null,
    val result: String = "*",
    val timeClass: String = "",
    val date: Long = System.currentTimeMillis(),
    /** The side the user played, when it is known. */
    val myColor: PieceColor? = null,
    val pgn: String? = null,
)

/** A game's review, whether it was just made or read back from the database. */
data class StoredReview(val report: GameReport, val points: List<EvalPoint>)

/**
 * Reviews a game with the engine and keeps the result under the game's own key, so opening it again shows
 * the review at once. Mistakes of the user's own go into the training queue.
 */
class ReviewService(
    private val context: Context,
    private val engine: StockfishEngine,
    private val games: GameRepository,
) {
    private var book: OpeningsBook? = null

    suspend fun openings(): OpeningsBook = book ?: OpeningsBook.load(context).also { book = it }

    /** The saved review of a game, or null if it has none. */
    suspend fun stored(key: String, mainline: List<MoveNode>): StoredReview? = withContext(Dispatchers.Default) {
        val (game, moves) = games.getGameWithMoves(key) ?: return@withContext null
        // A review made by an older engine layer can't be trusted: do it again.
        if ((game.rules ?: 0) < VERSION) return@withContext null
        if (moves.isEmpty() || moves.size < mainline.size - 1) return@withContext null
        val reviews = mutableMapOf<String, MoveReview>()
        val counts = mapOf(
            PieceColor.WHITE to MoveClass.values().associateWith { 0 }.toMutableMap(),
            PieceColor.BLACK to MoveClass.values().associateWith { 0 }.toMutableMap(),
        )
        val evals = mutableMapOf<String, ReviewedPosition>()
        val points = mutableListOf<EvalPoint>()
        val first = moves.first()
        val firstWhite = ChessBoard.fromFen(mainline[0].fen).turn == PieceColor.WHITE
        val startWin = if (firstWhite) first.winBefore else 100 - first.winBefore
        evals[mainline[0].fen] = ReviewedPosition(Score.Centipawns(cpFromWin(startWin)), null, emptyList())
        points += EvalPoint(mainline[0].ply, startWin)
        for (m in moves) {
            val node = mainline.getOrNull(m.i) ?: break
            val color = if (ChessBoard.fromFen(mainline[m.i - 1].fen).turn == PieceColor.WHITE) PieceColor.WHITE else PieceColor.BLACK
            val cls = runCatching { MoveClass.valueOf(m.cls) }.getOrDefault(MoveClass.GOOD)
            reviews[node.id] = MoveReview(
                nodeId = node.id, ply = node.ply, color = color, cls = cls,
                winBefore = m.winBefore, winAfter = m.winAfter, loss = maxOf(0.0, m.winBefore - m.winAfter) / 100.0,
                accuracy = MoveClassifier.moveAccuracy(m.winBefore, m.winAfter),
                bestMove = m.best, score = Score.Centipawns(cpFromWin(if (color == PieceColor.WHITE) m.winAfter else 100 - m.winAfter)),
            )
            counts[color]!![cls] = counts[color]!![cls]!! + 1
            val whiteWin = if (color == PieceColor.WHITE) m.winAfter else 100 - m.winAfter
            evals[node.fen] = ReviewedPosition(Score.Centipawns(cpFromWin(whiteWin)), null, emptyList())
            points += EvalPoint(node.ply, whiteWin, cls)
        }
        val line = mainline.take(moves.size + 1)
        val report = GameReport(reviews, MoveClassifier.gameAccuracy(line, evals), counts)
        StoredReview(report, points)
    }

    /**
     * Runs the engine over every position of `mainline`, classifies the moves and saves the review.
     * `onProgress` is called after each position with how many are done and the evaluations so far.
     */
    suspend fun review(
        info: GameInfo,
        mainline: List<MoveNode>,
        depth: Int,
        moveMs: Long,
        onProgress: (done: Int, total: Int, points: List<EvalPoint>) -> Unit,
        /** The review of the part of the game looked at so far, every few positions: results can show while it runs. */
        onPartial: (GameReport) -> Unit = {},
    ): StoredReview = withContext(Dispatchers.Default) {
        val book = openings()
        val evals = mutableMapOf<String, ReviewedPosition>()
        val points = mutableListOf<EvalPoint>()
        for ((i, node) in mainline.withIndex()) {
            // Opening theory needs no hard look: its moves are graded as book unless they fall well short.
            val inBook = i > 0 && book.isBook(node.fen) && book.isBook(mainline[i - 1].fen)
            val position = evals[node.fen] ?: (if (inBook) evaluate(node.fen, 8, 40) else evaluate(node.fen, depth, moveMs)).also { evals[node.fen] = it }
            points += EvalPoint(node.ply, position.score.winPercent())
            onProgress(i + 1, mainline.size, points.toList())
            // Every position at first, where it is cheap, so the first moves show at once; later every third.
            if (i >= 1 && (i < 12 || i % 3 == 0) && i < mainline.size - 1) onPartial(MoveClassifier.classifyGame(mainline.take(i + 1), evals) { book.isBook(it) })
        }
        val report = MoveClassifier.classifyGame(mainline, evals) { book.isBook(it) }
        val withVerdicts = points.mapIndexed { i, p -> p.copy(verdict = mainline.getOrNull(i)?.let { report.moves[it.id]?.cls }) }
        save(info, mainline, report, book)
        StoredReview(report, withVerdicts)
    }

    private suspend fun evaluate(fen: String, depth: Int, moveMs: Long): ReviewedPosition {
        val board = ChessBoard.fromFen(fen)
        if (board.isCheckmate()) return ReviewedPosition(checkmatedScore(board.turn), null, emptyList())
        if (board.isStalemate()) return ReviewedPosition(Score.Centipawns(0), null, emptyList())
        val result = engine.analyze(AnalyzeRequest(fen = fen, depth = depth, movetime = moveMs, multiPv = 2))
        val lines = result?.lines.orEmpty()
        val top = lines.firstOrNull() ?: return ReviewedPosition(Score.Centipawns(0), null, emptyList())
        return ReviewedPosition(top.score, result?.bestMove ?: top.pv.firstOrNull(), top.pv, lines.getOrNull(1)?.score)
    }

    private suspend fun save(info: GameInfo, mainline: List<MoveNode>, report: GameReport, book: OpeningsBook) {
        val mine = info.myColor ?: PieceColor.WHITE
        val opening = book.openingOf(mainline)
        val record = GameRecord(
            key = info.key,
            rules = VERSION,
            source = info.source,
            color = if (mine == PieceColor.WHITE) "white" else "black",
            me = if (mine == PieceColor.WHITE) info.white else info.black,
            opponent = if (mine == PieceColor.WHITE) info.black else info.white,
            result = info.result,
            endedAt = info.date,
            exactTime = info.date,
            rating = if (mine == PieceColor.WHITE) info.whiteRating else info.blackRating,
            oppRating = if (mine == PieceColor.WHITE) info.blackRating else info.whiteRating,
            timeClass = info.timeClass.ifEmpty { "rapid" },
            accuracy = report.accuracy[mine],
            openingEco = opening?.eco,
            openingName = opening?.name,
            bookPlies = mainline.takeWhile { book.isBook(it.fen) }.size,
            plies = mainline.size - 1,
            pgn = info.pgn,
        )
        val rows = mutableListOf<MoveRecord>()
        for (i in 1 until mainline.size) {
            val node = mainline[i]
            val review = report.moves[node.id] ?: continue
            rows += MoveRecord(
                gameKey = info.key, i = i, cls = review.cls.name,
                winBefore = review.winBefore, winAfter = review.winAfter,
                phase = "middlegame", piece = node.san?.firstOrNull()?.takeIf { it.isUpperCase() }?.toString() ?: "P",
                uci = node.uci, san = node.san, fen = mainline[i - 1].fen,
                best = review.bestMove, bestSan = review.bestMove?.let { sanOf(mainline[i - 1].fen, it) },
            )
        }
        games.insertOrReplaceGame(record, rows)
    }

    companion object {
        /** Bumped when reviews made by earlier versions should be made again. */
        const val VERSION = 11

        /** The SAN of `uci` in the position `fen`, or the UCI text when it isn't legal there. */
        fun sanOf(fen: String, uci: String): String =
            GameTree.create(fen).addMove(ROOT_ID, uci)?.let { (tree, id) -> tree.nodes[id]?.san } ?: uci

        /** The centipawn score whose win chance is `win` (0–100): the inverse of [winPercent]. */
        fun cpFromWin(win: Double): Int {
            val w = win.coerceIn(0.5, 99.5)
            val x = (w - 50.0) / 50.0
            return (ln((1 + x) / (1 - x)) / 0.00368208).toInt().let { if (abs(it) > 1000) 1000 * it / abs(it) else it }
        }
    }
}
