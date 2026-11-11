package wrklabs.chess.ui

/** The time control of a game, grouped the way players think of them. */
enum class TimeGroup(val label: String) {
    Bullet("Bullet"), Blitz("Blitz"), Rapid("Rapid"), Classical("Classical"), Casual("Casual");

    companion object {
        /** What the sites call it ("ultraBullet", "daily", "correspondence"…) mapped to a group; anything unknown counts as rapid. */
        fun of(timeClass: String): TimeGroup = when (timeClass.lowercase()) {
            "bullet", "ultrabullet" -> Bullet
            "blitz" -> Blitz
            "classical", "daily", "correspondence" -> Classical
            "casual" -> Casual
            else -> Rapid
        }
    }
}

enum class ResultFilter(val label: String, val outcome: String) { Won("Won", "win"), Lost("Lost", "loss"), Drawn("Drawn", "draw") }
enum class ColourFilter(val label: String) { White("White"), Black("Black") }
enum class SourceFilter(val label: String, val source: String) { ChessCom("Chess.com", "chesscom"), Lichess("Lichess", "lichess"), Played("Played here", "play") }
enum class ReviewedFilter(val label: String) { Reviewed("Reviewed"), NotYet("Not reviewed yet") }
enum class DateFilter(val label: String, val days: Int) { Week("Last 7 days", 7), Month("Last 30 days", 30), Year("Last year", 365) }
enum class OpponentFilter(val label: String) { Stronger("Higher rated than me"), Weaker("Lower rated than me") }

enum class GameSort(val label: String) {
    Newest("Newest first"),
    Oldest("Oldest first"),
    BestAccuracy("Best accuracy"),
    WorstAccuracy("Worst accuracy"),
    HighestOpponent("Strongest opponent"),
    LowestOpponent("Weakest opponent"),
    ToughestForMe("Toughest compared with me"),
}

/** What the Games list is showing: a search, the filters (each one off when null), and the order. Applied in memory. */
data class GameQuery(
    val search: String = "",
    val result: ResultFilter? = null,
    val colour: ColourFilter? = null,
    val time: TimeGroup? = null,
    val source: SourceFilter? = null,
    val reviewed: ReviewedFilter? = null,
    val date: DateFilter? = null,
    val opponent: OpponentFilter? = null,
    val sort: GameSort = GameSort.Newest,
) {
    /** How many filters are on (the search and the order are not filters). */
    val filtersOn: Int get() = listOfNotNull(result, colour, time, source, reviewed, date, opponent).size
    val active: Boolean get() = filtersOn > 0 || search.isNotBlank()
    fun cleared() = copy(search = "", result = null, colour = null, time = null, source = null, reviewed = null, date = null, opponent = null)

    fun matches(item: GameItem, now: Long): Boolean {
        if (search.isNotBlank() && !item.opponent.contains(search.trim(), ignoreCase = true)) return false
        if (result != null && item.outcome != result.outcome) return false
        if (colour != null && item.myColorIsWhite != (colour == ColourFilter.White)) return false
        if (time != null && TimeGroup.of(item.game.timeClass) != time) return false
        if (source != null && item.game.source != source.source) return false
        if (reviewed != null && item.reviewed != (reviewed == ReviewedFilter.Reviewed)) return false
        if (date != null && item.game.date < now - date.days * DAY_MS) return false
        if (opponent != null) {
            val theirs = item.opponentRating ?: return false
            val mine = item.myRating ?: return false
            if (if (opponent == OpponentFilter.Stronger) theirs <= mine else theirs >= mine) return false
        }
        return true
    }

    /** The games that match, in the chosen order. Games lacking what a sort needs (an accuracy, a rating) go last. */
    fun apply(items: List<GameItem>, now: Long = System.currentTimeMillis()): List<GameItem> {
        val kept = if (active) items.filter { matches(it, now) } else items
        fun gap(i: GameItem): Int? { val theirs = i.opponentRating ?: return null; val mine = i.myRating ?: return null; return theirs - mine }
        return when (sort) {
            GameSort.Newest -> kept.sortedByDescending { it.game.date }
            GameSort.Oldest -> kept.sortedBy { it.game.date }
            GameSort.BestAccuracy -> kept.sortedWith(compareByDescending<GameItem> { it.accuracy != null }.thenByDescending { it.accuracy ?: 0.0 }.thenByDescending { it.game.date })
            GameSort.WorstAccuracy -> kept.sortedWith(compareByDescending<GameItem> { it.accuracy != null }.thenBy { it.accuracy ?: 0.0 }.thenByDescending { it.game.date })
            GameSort.HighestOpponent -> kept.sortedWith(compareByDescending<GameItem> { it.opponentRating != null }.thenByDescending { it.opponentRating ?: 0 }.thenByDescending { it.game.date })
            GameSort.LowestOpponent -> kept.sortedWith(compareByDescending<GameItem> { it.opponentRating != null }.thenBy { it.opponentRating ?: 0 }.thenByDescending { it.game.date })
            GameSort.ToughestForMe -> kept.sortedWith(compareByDescending<GameItem> { gap(it) != null }.thenByDescending { gap(it) ?: 0 }.thenByDescending { it.game.date })
        }
    }

    companion object { const val DAY_MS = 24L * 60 * 60 * 1000 }
}

/** The line over the list: how many games, how they went, and the average accuracy of the reviewed ones. */
fun summaryOf(items: List<GameItem>): String {
    if (items.isEmpty()) return "No games"
    val won = items.count { it.outcome == "win" }
    val lost = items.count { it.outcome == "loss" }
    val drawn = items.size - won - lost
    val accuracies = items.mapNotNull { it.accuracy }
    return buildString {
        append("%,d game%s · %d won · %d lost · %d drawn".format(items.size, if (items.size == 1) "" else "s", won, lost, drawn))
        if (accuracies.isNotEmpty()) append(" · accuracy ${accuracies.average().toInt()}%")
    }
}
