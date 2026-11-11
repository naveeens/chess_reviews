package wrklabs.chess.maia

import wrklabs.chess.classifier.MoveClass
import kotlin.math.roundToInt

/** How players of one rating play a position: what share chose the move that was played, and the move most of them chose. */
data class HumanMove(val elo: Int, val playedShare: Float, val topUci: String, val topShare: Float) {
    val playedWasTop get() = playedShare >= topShare
}

/** What Maia's numbers are worth saying about a move. */
object Human {
    /** Ratings Maia was trained on; a rating outside is held at the edge, and an unknown one is taken as [fallback]. */
    private const val LOW = 600
    private const val HIGH = 2600

    fun clampElo(rating: Int?, fallback: Int = 1500): Int = (rating ?: fallback).coerceIn(LOW, HIGH)

    /** "41%", or "<1%" for what rounds to nothing. */
    fun share(p: Float): String {
        val pct = (p * 100).roundToInt()
        return if (pct < 1) "<1%" else "$pct%"
    }

    /**
     * The line under a move on the card, or null when the numbers add nothing: the grades that are about a player's
     * choices (a slip, or a find) get what people at the rating do; the ordinary good moves need no comment.
     */
    fun note(h: HumanMove, topSan: String, cls: MoveClass): String? = when (cls) {
        MoveClass.INACCURACY, MoveClass.MISTAKE, MoveClass.MISS, MoveClass.BLUNDER ->
            if (h.playedWasTop) "The most common move at ${h.elo} (${share(h.playedShare)})"
            else "${share(h.playedShare)} play this at ${h.elo} · ${share(h.topShare)} play $topSan"
        MoveClass.BRILLIANT, MoveClass.GREAT ->
            "Only ${share(h.playedShare)} find this at ${h.elo}"
        else -> null
    }

    /** The rating to play opponents at: the average of the latest rated games (most recent first), 1500 for someone with none. */
    fun userElo(ratings: List<Int?>): Int = ratings.filterNotNull().take(20).let { r -> if (r.isEmpty()) 1500 else clampElo(r.average().roundToInt()) }
}
