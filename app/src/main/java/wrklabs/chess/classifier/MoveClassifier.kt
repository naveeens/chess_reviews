/*
 * Copyright (C) bijoyv9 and contributors (Rookahead, https://github.com/rookahead/rookahead)
 * Copyright (C) 2026 naveen s
 *
 * Derived from Rookahead's src/lib/classify.ts, ported from TypeScript to Kotlin and changed for this
 * app: the verdict names, the board model and the Android types.
 * SPDX-License-Identifier: GPL-3.0-or-later. See NOTICE.
 */
package wrklabs.chess.classifier

import kotlinx.serialization.Serializable
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.engine.winPercent
import wrklabs.chess.model.*
import kotlin.math.*

data class ReviewedPosition(
    val score: Score,
    val bestMove: String?,
    val pv: List<String>,
    val second: Score? = null
)

@Serializable
data class MoveReview(
    val nodeId: String,
    val ply: Int,
    val color: PieceColor,
    val cls: MoveClass,
    val winBefore: Double,
    val winAfter: Double,
    val loss: Double,
    val accuracy: Double,
    val bestMove: String? = null,
    val score: Score,
    val missedMate: Int? = null,
    val fasterMate: Int? = null
)

@Serializable
data class GameReport(
    val moves: Map<String, MoveReview>,
    val accuracy: Map<PieceColor, Double?>,
    val counts: Map<PieceColor, Map<MoveClass, Int>>
)

object MoveClassifier {

    const val RULES_VERSION = 11
    /** Win% from which a position counts as won (about +7 pawns). */
    const val WINNING = 93.0
    /** A mate this near is one you are expected to find. */
    const val SHORT_MATE = 3
    /** A Miss gave back between this much and this much of what the opponent's mistake handed you. */
    const val MISS_GAVE_BACK_MIN = 0.5
    const val MISS_GAVE_BACK_MAX = 1.25
    const val HOLDS = 45.0
    const val HOLDING = 30.0

    object LossBands {
        const val EXCELLENT = 0.02
        const val GOOD = 0.05
        const val INACCURACY = 0.1
        const val MISTAKE = 0.2
    }

    enum class SacrificeKind {
        MATERIAL, EXCHANGE
    }

    data class SacrificeDetail(
        val kind: SacrificeKind,
        val declined: Boolean
    )

    fun moverWin(score: Score, color: PieceColor): Double {
        val wp = score.winPercent()
        return if (color == PieceColor.WHITE) wp else 100.0 - wp
    }

    fun mateIn(score: Score, color: PieceColor): Int? {
        if (score !is Score.Mate || score.value == 0) return null
        val n = if (color == PieceColor.WHITE) score.value else -score.value
        return if (n > 0) n else null
    }

    /** A mate as the mover sees it: positive when the mover mates, negative when mated. */
    private fun moverMate(score: Score.Mate, color: PieceColor) = if (color == PieceColor.WHITE) score.value else -score.value

    /**
     * A mate in a few moves that is let go is a win thrown away, however big the edge that is left: a Miss, or a Blunder if
     * the move leaves you worse off. Null when the mate was kept, or was too far off to expect. Every other mate is graded by
     * its win chance (a mate is 100% for the side that has it), like any other move.
     */
    fun missedShortMate(before: Score, after: Score, color: PieceColor): MoveClass? {
        if (before !is Score.Mate) return null
        val was = moverMate(before, color)
        if (was !in 1..SHORT_MATE) return null
        if (after is Score.Mate && moverMate(after, color) > 0) return null
        return if (moverWin(after, color) >= 50.0) MoveClass.MISS else MoveClass.BLUNDER
    }

    fun moverPawns(score: Score, color: PieceColor): Double {
        return when (score) {
            is Score.Mate -> {
                val win = moverWin(score, color)
                if (win > 50.0) Double.POSITIVE_INFINITY
                else if (win < 50.0) Double.NEGATIVE_INFINITY
                else 0.0
            }
            is Score.Centipawns -> {
                val sign = if (color == PieceColor.WHITE) 1 else -1
                (sign * score.value) / 100.0
            }
        }
    }

    /**
     * Lichess's per-move accuracy from mover's win% before and after (both 0–100).
     */
    fun moveAccuracy(before: Double, after: Double): Double {
        if (after >= before) return 100.0
        val raw = 103.1668100711649 * exp(-0.04354415386753951 * (before - after)) - 3.166924740191411
        return max(0.0, min(100.0, raw + 1.0))
    }

    fun materialBalance(fen: String, color: PieceColor): Int {
        val placement = fen.split(" ")[0]
        var balance = 0
        for (ch in placement) {
            val type = PieceType.fromChar(ch) ?: continue
            val isWhite = ch.isUpperCase()
            val sign = if (isWhite == (color == PieceColor.WHITE)) 1 else -1
            balance += sign * type.value
        }
        return balance
    }

    private fun isHanging(board: ChessBoard, sq: Square, type: PieceType, color: PieceColor): Boolean {
        val p = board.getPiece(sq) ?: return false
        if (p.color != color || p.type != type) return false
        val opponent = color.opponent()
        val attacked = board.isSquareAttacked(sq, opponent)
        if (!attacked) return false
        val defended = board.isSquareAttacked(sq, color)
        return !defended
    }

    /**
     * Did this move give up material for something other than a trade? Checkmates and moves the
     * opponent must answer in check are never sacrifices. Otherwise the engine's reply line is
     * played out: material still lost once the captures settle is a sacrifice, and a piece the
     * opponent could win but the engine declines counts as an offered one.
     */
    fun sacrificeDetail(
        beforeFen: String,
        afterFen: String,
        pv: List<String>,
        color: PieceColor,
        stillHanging: Boolean,
        olderFen: String?
    ): SacrificeDetail? {
        val before = ChessBoard.fromFen(beforeFen)
        val older = olderFen?.let { ChessBoard.fromFen(it) }
        if (ChessBoard.fromFen(afterFen).isCheckmate()) return null

        val hungAlready: (Square, PieceType) -> Boolean = { sq, type ->
            !stillHanging && isHanging(before, sq, type, color) && (older == null || isHanging(older, sq, type, color))
        }

        val accepted = acceptedSacrifice(beforeFen, afterFen, pv, color, hungAlready)
        if (accepted != null) return SacrificeDetail(accepted, declined = false)

        val offered = offeredSacrifice(beforeFen, afterFen, pv, color, hungAlready)
        return if (offered != null) SacrificeDetail(offered, declined = true) else null
    }

    private const val SACRIFICE_PLIES = 6
    private const val SACRIFICE_MIN = 2

    /**
     * A piece worth three or more given up for less than it cost once the first recapture is made, inside a forced mate. Material alone calls a rook for a knight
     * and a pawn a trade, but with the king being hunted it is a sacrifice.
     */
    fun matingSacrifice(beforeFen: String, afterFen: String, pv: List<String>, color: PieceColor): Boolean {
        if (ChessBoard.fromFen(afterFen).isCheckmate()) return false
        return acceptedSacrifice(beforeFen, afterFen, pv, color, { _, _ -> false }, minLost = 1, minPiece = 3, plies = 2) != null
    }

    private fun acceptedSacrifice(
        beforeFen: String,
        afterFen: String,
        pv: List<String>,
        color: PieceColor,
        hungAlready: (Square, PieceType) -> Boolean,
        minLost: Int = SACRIFICE_MIN,
        minPiece: Int = 0,
        plies: Int = SACRIFICE_PLIES
    ): SacrificeKind? {
        val start = materialBalance(beforeFen, color)
        var board = ChessBoard.fromFen(afterFen)
        var settled = materialBalance(afterFen, color)
        var forgiven = 0
        var settledForgiven = 0
        var captured = 0
        var lastTaken: PieceType? = null
        var takenCount = 0
        var biggest = 0

        for (uci in pv.take(plies)) {
            if (uci.length < 4) break
            val from = Square.fromName(uci.substring(0, 2))
            val to = Square.fromName(uci.substring(2, 4))
            val promo = if (uci.length > 4) PieceType.fromChar(uci[4]) else null
            val moving = board.getPiece(from) ?: break
            val target = board.getPiece(to)
            val ours = moving.color == color

            if (!ours && target == null && captured == 0) return null
            if (!ours && target != null) {
                if (hungAlready(to, target.type)) forgiven += target.type.value
                else { takenCount++; lastTaken = target.type; biggest = max(biggest, target.type.value) }
                captured++
            }
            if (ours && target != null) captured++

            board = board.makeMove(Move(from, to, promo))
            if (target != null) {
                settled = materialBalance(board.toFen(), color)
                settledForgiven = forgiven
            }
        }

        val lost = start - settled - settledForgiven
        if (takenCount == 0 || lost < minLost || biggest < minPiece) return null
        return if (takenCount == 1 && lastTaken == PieceType.ROOK && lost < 3) SacrificeKind.EXCHANGE else SacrificeKind.MATERIAL
    }

    private fun offeredSacrifice(
        beforeFen: String,
        afterFen: String,
        pv: List<String>,
        color: PieceColor,
        hungAlready: (Square, PieceType) -> Boolean
    ): SacrificeKind? {
        val before = ChessBoard.fromFen(beforeFen)
        val after = ChessBoard.fromFen(afterFen)
        if (after.isCheck()) return null
        val gained = materialBalance(afterFen, color) - materialBalance(beforeFen, color)
        val reply = pv.firstOrNull()
        val replies = after.legalMoves()

        for (idx in 0 until 64) {
            val sq = Square.fromIndex(idx)
            val piece = after.getPiece(sq) ?: continue
            if (piece.color != color || piece.type.value < 3 || gained >= piece.type.value) continue
            if (reply?.substring(2, 4) == sq.name) continue
            if (hungAlready(sq, piece.type)) continue
            // A threat that was already there is a threat ignored, not a piece offered.
            if (before.getPiece(sq) == piece && before.isSquareAttacked(sq, color.opponent())) continue

            val takers = replies.filter { it.to == sq }.mapNotNull { after.getPiece(it.from)?.type?.value }
            if (takers.isEmpty()) continue
            val loss = if (after.isSquareAttacked(sq, color)) piece.type.value - takers.min() else piece.type.value
            if (loss < SACRIFICE_MIN) continue
            return if (piece.type == PieceType.ROOK && loss < 3) SacrificeKind.EXCHANGE else SacrificeKind.MATERIAL
        }
        return null
    }

    /**
     * Only moves worth a mark for being found: not one made to answer a check, not a queen promotion, and not the capture of a
     * piece that was simply hanging.
     */
    private fun isCritical(parent: MoveNode, node: MoveNode, color: PieceColor): Boolean {
        val board = ChessBoard.fromFen(parent.fen)
        if (board.isCheck()) return false
        if (node.uci?.length == 5 && node.uci[4] == 'q') return false
        val to = node.to?.let { Square.fromName(it) } ?: return true
        val taken = board.getPiece(to) ?: return true
        return !isHanging(board, to, taken.type, taken.color)
    }

    private fun takesBack(older: MoveNode?, prev: MoveNode, node: MoveNode, color: PieceColor): Boolean {
        if (prev.san?.contains("x") != true || node.san?.contains("x") != true) return false
        if (prev.to == node.to) return true
        val before = ChessBoard.fromFen(prev.fen)
        val fromSq = node.from?.let { Square.fromName(it) } ?: return false
        val piece = before.getPiece(fromSq)
        if (piece != null && isHanging(before, fromSq, piece.type, piece.color)) return false
        return older != null && materialBalance(node.fen, color) <= materialBalance(older.fen, color)
    }

    /** What the rules look at for one move: the position before and after, and the win chances either side of it. */
    private class MoveFacts(
        val line: List<MoveNode>,
        val index: Int,
        val before: ReviewedPosition,
        val after: ReviewedPosition,
        val color: PieceColor,
    ) {
        val parent: MoveNode get() = line[index - 1]
        val node: MoveNode get() = line[index]
        val winBefore = moverWin(before.score, color)
        val winAfter = moverWin(after.score, color)
        val loss = max(0.0, winBefore - winAfter) / 100.0
        val playedBest = node.uci == before.bestMove
        val legalMoves = ChessBoard.fromFen(parent.fen).legalMoves().size
        val mates = after.score is Score.Mate && after.score.value == 0
    }

    /** 1. The grade from the numbers alone: a forced move, the best move, a missed short mate, or the band the loss falls in. */
    private fun gradeByLoss(f: MoveFacts): MoveClass {
        if (f.legalMoves == 1) return MoveClass.FORCED
        if (f.playedBest || f.mates) return MoveClass.BEST
        missedShortMate(f.before.score, f.after.score, f.color)?.let { return it }
        return when {
            f.loss < LossBands.EXCELLENT -> MoveClass.EXCELLENT
            f.loss < LossBands.GOOD -> MoveClass.GOOD
            f.loss < LossBands.INACCURACY -> MoveClass.INACCURACY
            f.loss < LossBands.MISTAKE -> MoveClass.MISTAKE
            else -> MoveClass.BLUNDER
        }
    }

    /** 2. A move worth a mark for being found: Brilliant (a sound sacrifice) or Great (the only move that holds), else null. */
    private fun standout(f: MoveFacts, base: MoveClass): MoveClass? {
        var onlyGood = false
        var onlyMate = false
        var fastestMate = false
        val forcedMate = f.playedBest && (mateIn(f.before.score, f.color) ?: 0) >= 2
        if (f.playedBest && f.before.second != null && f.legalMoves > 1) {
            val secondWin = moverWin(f.before.second, f.color)
            onlyGood = f.winAfter >= HOLDING && (f.winBefore - secondWin) / 100.0 >= LossBands.INACCURACY && secondWin < 85.0
            val mate = mateIn(f.before.score, f.color)
            val secondMate = mateIn(f.before.second, f.color)
            onlyMate = mate != null && mate >= 2 && (secondMate == null || secondMate >= mate + 3)
            fastestMate = mate != null && mate >= 2 && (secondMate == null || secondMate >= mate + 2)
        }

        val gradable = base == MoveClass.BEST || base == MoveClass.EXCELLENT || base == MoveClass.GOOD
        val olderFen = if (f.index >= 2) f.line[f.index - 2].fen else null
        val sac = if (gradable) sacrificeDetail(f.parent.fen, f.node.fen, f.after.pv, f.color, onlyGood, olderFen) else null

        // Already winning without it, even if this move had not been found: nothing brilliant about it.
        val alreadyWinning = (f.before.second?.let { moverWin(it, f.color) } ?: f.winBefore) >= WINNING
        val soundSac = sac != null && f.playedBest && f.winAfter >= HOLDS && !alreadyWinning && (sac.kind != SacrificeKind.EXCHANGE || onlyGood)
        // A mating sacrifice that material alone would call a trade, so there is no detail to hold it to the usual rules.
        val matingSac = gradable && forcedMate && sac == null &&
            matingSacrifice(f.parent.fen, f.node.fen, f.after.pv, f.color)
        if (soundSac || (sac != null && fastestMate) || matingSac) return MoveClass.BRILLIANT
        if ((onlyGood || onlyMate) && !f.mates && !takesBack(f.line.getOrNull(f.index - 2), f.parent, f.node, f.color) && isCritical(f.parent, f.node, f.color)) return MoveClass.GREAT
        return null
    }

    /**
     * 3. A Miss: the opponent blundered, which handed you a winning position, and your mistake gave back about as much as that. Gave
     * back a lot more and it is a Blunder of its own; gave back little and it is just the Mistake it was. A smaller error of theirs
     * handed you too little to have missed.
     */
    private fun isMiss(grade: MoveClass, loss: Double, previous: MoveReview?): Boolean =
        (grade == MoveClass.MISTAKE || grade == MoveClass.BLUNDER) && previous != null &&
            previous.loss >= LossBands.MISTAKE &&
            loss >= MISS_GAVE_BACK_MIN * previous.loss && loss <= MISS_GAVE_BACK_MAX * previous.loss

    /** Every move of the line graded, in this order: theory, then the numbers, then a standout, then a Miss. */
    fun classifyGame(
        line: List<MoveNode>,
        evals: Map<String, ReviewedPosition>,
        isBook: (String) -> Boolean = { false }
    ): GameReport {
        val moves = mutableMapOf<String, MoveReview>()
        val counts = mutableMapOf(
            PieceColor.WHITE to MoveClass.values().associateWith { 0 }.toMutableMap(),
            PieceColor.BLACK to MoveClass.values().associateWith { 0 }.toMutableMap()
        )
        var stillInBook = true

        for (i in 1 until line.size) {
            val node = line[i]
            val before = evals[line[i - 1].fen]
            val after = evals[node.fen]
            if (before == null || after == null || node.uci == null) {
                stillInBook = false
                continue
            }

            val color = if (line[i - 1].fen.split(" ")[1] == "b") PieceColor.BLACK else PieceColor.WHITE
            val f = MoveFacts(line, i, before, after, color)

            val cls = if (stillInBook && isBook(node.fen) && (f.playedBest || f.loss < LossBands.INACCURACY)) {
                MoveClass.BOOK
            } else {
                stillInBook = false
                val graded = gradeByLoss(f)
                val marked = standout(f, graded) ?: graded
                if (isMiss(marked, f.loss, moves[f.parent.id])) MoveClass.MISS else marked
            }

            val review = MoveReview(
                nodeId = node.id,
                ply = node.ply,
                color = color,
                cls = cls,
                winBefore = f.winBefore,
                winAfter = f.winAfter,
                loss = f.loss,
                accuracy = moveAccuracy(f.winBefore, f.winAfter),
                bestMove = if (f.playedBest || before.bestMove == null) null else before.bestMove,
                score = after.score,
                missedMate = null,
                fasterMate = null
            )
            moves[node.id] = review
            counts[color]?.let { it[cls] = (it[cls] ?: 0) + 1 }
        }

        return GameReport(
            moves = moves,
            accuracy = gameAccuracy(line, evals),
            counts = counts
        )
    }

    private fun stdDev(xs: List<Double>): Double {
        val mean = xs.average()
        return sqrt(xs.map { (it - mean).pow(2) }.average())
    }

    fun gameAccuracy(line: List<MoveNode>, evals: Map<String, ReviewedPosition>): Map<PieceColor, Double?> {
        val wins = mutableListOf<Double>()
        for (node in line) {
            val e = evals[node.fen] ?: return mapOf(PieceColor.WHITE to null, PieceColor.BLACK to null)
            wins.add(e.score.winPercent())
        }
        if (wins.size < 2) return mapOf(PieceColor.WHITE to null, PieceColor.BLACK to null)

        val windowSize = max(2, min(8, wins.size / 10))
        val windows = mutableListOf<List<Double>>()
        for (i in 0 until min(windowSize, wins.size) - 2) {
            windows.add(wins.subList(0, windowSize))
        }
        for (i in 0..wins.size - windowSize) {
            windows.add(wins.subList(i, i + windowSize))
        }
        val weights = windows.map { max(0.5, min(12.0, stdDev(it))) }

        val perWhite = mutableListOf<Pair<Double, Double>>()
        val perBlack = mutableListOf<Pair<Double, Double>>()
        val firstMover = if (line[0].fen.split(" ")[1] == "b") PieceColor.BLACK else PieceColor.WHITE

        for (i in 0 until wins.size - 1) {
            val color = if ((i % 2 == 0) == (firstMover == PieceColor.WHITE)) PieceColor.WHITE else PieceColor.BLACK
            val before = if (color == PieceColor.WHITE) wins[i] else 100.0 - wins[i]
            val after = if (color == PieceColor.WHITE) wins[i + 1] else 100.0 - wins[i + 1]
            val w = weights.getOrNull(i) ?: 0.5
            val entry = Pair(moveAccuracy(before, after), w)
            if (color == PieceColor.WHITE) perWhite.add(entry) else perBlack.add(entry)
        }

        fun combine(list: List<Pair<Double, Double>>): Double? {
            if (list.isEmpty()) return null
            val weightedSum = list.sumOf { it.first * it.second }
            val weightSum = list.sumOf { it.second }
            val weighted = weightedSum / weightSum
            val harmonicSum = list.sumOf { 1.0 / max(it.first, 1.0) }
            val harmonic = list.size / harmonicSum
            return (weighted + harmonic) / 2.0
        }

        return mapOf(
            PieceColor.WHITE to combine(perWhite),
            PieceColor.BLACK to combine(perBlack)
        )
    }
}
