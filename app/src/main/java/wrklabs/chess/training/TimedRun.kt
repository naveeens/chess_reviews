package wrklabs.chess.training

import android.content.Context

/**
 * How fast a drill asks. Each problem gets its own time limit; a set with no misses moves you
 * one step faster, a set with two or more moves you one step slower.
 */
object Pace {
    /** Time per problem at each level, slowest first. */
    val LIMITS_MS = listOf(20000, 15000, 12000, 10000, 8000, 6000, 5000, 4000, 3000, 2500, 2000, 1500)
    const val SET_SIZE = 10

    val top get() = LIMITS_MS.lastIndex
    fun limit(level: Int) = LIMITS_MS[level.coerceIn(0, top)]

    /** "5 s" or "2.5 s". */
    fun label(level: Int): String {
        val ms = limit(level)
        return if (ms % 1000 == 0) "${ms / 1000} s" else "${ms / 1000.0} s"
    }

    /** The level after a set in which [missed] problems went wrong. */
    fun after(level: Int, missed: Int): Int = when {
        missed == 0 -> (level + 1).coerceAtMost(top)
        missed >= 2 -> (level - 1).coerceAtLeast(0)
        else -> level
    }

    private const val FILE = "squares"
    /** A fresh drill starts at 10 s, slow enough to think; a clean set shortens it. */
    const val START_LEVEL = 3
    fun load(context: Context, key: String) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("pace_$key", START_LEVEL).coerceIn(0, top)
    fun save(context: Context, key: String, level: Int) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("pace_$key", level).apply()
}

/**
 * One set of problems, each asked once. A wrong or late answer shows the right one and the set
 * moves on, so a set is always exactly [size] problems long.
 */
class TimedRun<T>(val size: Int, make: () -> T) {
    private val queue = ArrayDeque<T>().also { q -> repeat(size) { q.addLast(make()) } }

    val done get() = queue.isEmpty()
    val current: T get() = queue.first()
    /** Problems answered right. */
    var firstTry = 0
        private set
    /** Problems answered wrong or too late. */
    var missed = 0
        private set
    /** How many problems have been answered. */
    var seen = 0
        private set

    fun answer(right: Boolean) {
        queue.removeFirst()
        seen++
        if (right) firstTry++ else missed++
    }
}
