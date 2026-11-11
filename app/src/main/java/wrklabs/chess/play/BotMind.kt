package wrklabs.chess.play

import kotlin.math.exp

/** What Maia thinks of a position, from the point of view of the side to move: the chances of winning, drawing and losing. */
data class Outlook(val win: Float, val draw: Float, val loss: Float) {
    /** What the side to move can expect from the game: a win counts one, a draw a half. */
    val expected get() = win + draw / 2f

    companion object {
        /** Maia's value output is three scores in the order loss, draw, win; this is their softmax. */
        fun fromLogits(loss: Float, draw: Float, win: Float): Outlook {
            val top = maxOf(loss, draw, win)
            val l = exp((loss - top).toDouble()); val d = exp((draw - top).toDouble()); val w = exp((win - top).toDouble())
            val sum = l + d + w
            return Outlook((w / sum).toFloat(), (d / sum).toFloat(), (l / sum).toFloat())
        }
    }
}

/**
 * The bot's judgement about the game itself, which a person uses and an engine does not: when it is lost, and whether to
 * accept a draw. Rules, not chess knowledge: they only read the outlook Maia gives for each of the bot's own turns.
 */
object BotMind {
    /** No resigning or drawing in the opening, whatever the numbers say. */
    private const val MIN_PLIES = 24

    /** Bots rated below this hold on one move longer before giving up, as weaker players do. */
    private const val SLOW_TO_RESIGN_BELOW = 1200

    /**
     * Whether the bot gives up. [outlooks] are its own, one for each of its turns so far (the latest last). It resigns when the
     * last few of them leave it next to no chance, and has not drawn or won anything worth playing on for.
     */
    fun resigns(outlooks: List<Outlook>, plies: Int, elo: Int): Boolean {
        if (plies < MIN_PLIES) return false
        val needed = if (elo < SLOW_TO_RESIGN_BELOW) 4 else 3
        if (outlooks.size < needed) return false
        return outlooks.takeLast(needed).all { it.win < 0.03f && it.draw < 0.12f }
    }

    /** Whether the bot takes a draw offered now, given how it sees the position. It only accepts what it is not better than. */
    fun acceptsDraw(outlook: Outlook, plies: Int): Boolean = plies >= MIN_PLIES && outlook.expected <= 0.55f
}

/** The level to play the next game at. */
object AutoLevel {
    const val MIN = 600
    const val MAX = 2600
    const val STEP = 100

    /** After a game: a win moves it up a step, a loss down a step, a draw leaves it. [result] is "win", "loss" or "draw" for the user. */
    fun next(level: Int, result: String): Int = when (result) {
        "win" -> level + STEP
        "loss" -> level - STEP
        else -> level
    }.coerceIn(MIN, MAX)

    /** A rating rounded to the steps the slider has. */
    fun snap(rating: Int): Int = (Math.round(rating / STEP.toDouble()).toInt() * STEP).coerceIn(MIN, MAX)
}
