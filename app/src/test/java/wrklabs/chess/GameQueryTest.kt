package wrklabs.chess

import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.ui.*

class GameQueryTest {
    private val now = 1_800_000_000_000L
    private val day = GameQuery.DAY_MS

    private fun item(
        key: String, opp: String, mineWhite: Boolean = true, result: String = "1-0", myR: Int? = 1500, oppR: Int? = 1500,
        time: String = "blitz", source: String = "lichess", ageDays: Int = 1, accuracy: Double? = null, reviewed: Boolean = accuracy != null,
    ): GameItem {
        val white = if (mineWhite) "me" else opp; val black = if (mineWhite) opp else "me"
        val g = ImportedGame(key, source, white, black, if (mineWhite) myR else oppR, if (mineWhite) oppR else myR, result, time, "1. e4 *", now - ageDays * day)
        return GameItem(g, "me", accuracy, reviewed)
    }

    private val games = listOf(
        item("a", "Alice", result = "1-0", oppR = 1700, time = "blitz", ageDays = 2, accuracy = 90.0),
        item("b", "bob", mineWhite = false, result = "1-0", oppR = 1300, time = "bullet", ageDays = 10, accuracy = 70.0),   // I was Black and lost
        item("c", "Carol", result = "1/2-1/2", oppR = 1500, time = "rapid", source = "chesscom", ageDays = 40),
        item("d", "alice2", result = "0-1", oppR = 1900, time = "daily", source = "chesscom", ageDays = 400, accuracy = 55.0),
        item("e", "Maia", result = "1-0", oppR = 1400, time = "casual", source = "play", ageDays = 0, myR = 1500),
        item("f", "Frank", result = "1-0", oppR = null, time = "ultraBullet", ageDays = 5),
    )
    private fun keys(q: GameQuery) = q.apply(games, now).map { it.game.key }

    @Test fun noQueryShowsEverythingNewestFirst() = assertEquals(listOf("e", "a", "f", "b", "c", "d"), keys(GameQuery()))

    @Test fun searchMatchesPartOfTheOpponentsNameWithoutCase() {
        assertEquals(listOf("a", "d"), keys(GameQuery(search = " ALIC ")))
        assertTrue(keys(GameQuery(search = "zzz")).isEmpty())
    }

    @Test fun resultIsRelativeToMyColour() {
        assertEquals(listOf("e", "a", "f"), keys(GameQuery(result = ResultFilter.Won)))
        assertEquals(listOf("b", "d"), keys(GameQuery(result = ResultFilter.Lost)))     // b: 1-0 with me as Black
        assertEquals(listOf("c"), keys(GameQuery(result = ResultFilter.Drawn)))
    }

    @Test fun colourTimeSourceAndReviewedFilters() {
        assertEquals(listOf("b"), keys(GameQuery(colour = ColourFilter.Black)))
        assertEquals(listOf("f", "b"), keys(GameQuery(time = TimeGroup.Bullet)))          // ultraBullet counts as bullet
        assertEquals(listOf("d"), keys(GameQuery(time = TimeGroup.Classical)))            // daily counts as classical
        assertEquals(listOf("e"), keys(GameQuery(source = SourceFilter.Played)))
        assertEquals(listOf("c", "d"), keys(GameQuery(source = SourceFilter.ChessCom)))
        assertEquals(listOf("a", "b", "d"), keys(GameQuery(reviewed = ReviewedFilter.Reviewed)))
        assertEquals(listOf("e", "f", "c"), keys(GameQuery(reviewed = ReviewedFilter.NotYet)))
    }

    @Test fun dateFiltersAreRollingPeriods() {
        assertEquals(listOf("e", "a", "f"), keys(GameQuery(date = DateFilter.Week)))
        assertEquals(listOf("e", "a", "f", "b"), keys(GameQuery(date = DateFilter.Month)))
        assertEquals(listOf("e", "a", "f", "b", "c"), keys(GameQuery(date = DateFilter.Year)))
    }

    @Test fun opponentRatedAboveOrBelowMeLeavesOutGamesWithoutRatings() {
        assertEquals(listOf("a", "d"), keys(GameQuery(opponent = OpponentFilter.Stronger)))
        assertEquals(listOf("e", "b"), keys(GameQuery(opponent = OpponentFilter.Weaker)))
    }

    @Test fun filtersCombine() {
        assertEquals(listOf("a"), keys(GameQuery(search = "alice", result = ResultFilter.Won, time = TimeGroup.Blitz)))
        assertEquals(1, GameQuery(search = "x", result = ResultFilter.Won).filtersOn)
        assertTrue(GameQuery(search = "x").active); assertFalse(GameQuery().active)
        assertFalse(GameQuery(search = "x", result = ResultFilter.Won, sort = GameSort.Oldest).cleared().active)
        assertEquals(GameSort.Oldest, GameQuery(sort = GameSort.Oldest).cleared().sort)   // clearing keeps the order
    }

    @Test fun sortsPutGamesWithoutTheSortedValueLast() {
        assertEquals(listOf("d", "c", "b", "f", "a", "e"), keys(GameQuery(sort = GameSort.Oldest)))
        assertEquals(listOf("a", "b", "d", "e", "f", "c"), keys(GameQuery(sort = GameSort.BestAccuracy)))
        assertEquals(listOf("d", "b", "a", "e", "f", "c"), keys(GameQuery(sort = GameSort.WorstAccuracy)))
        assertEquals(listOf("d", "a", "c", "e", "b", "f"), keys(GameQuery(sort = GameSort.HighestOpponent)))
        assertEquals(listOf("b", "e", "c", "a", "d", "f"), keys(GameQuery(sort = GameSort.LowestOpponent)))
        assertEquals(listOf("d", "a", "c", "e", "b", "f"), keys(GameQuery(sort = GameSort.ToughestForMe)))
    }

    @Test fun theSummaryCountsWhatIsShown() {
        assertEquals("No games", summaryOf(emptyList()))
        assertEquals("1 game · 1 won · 0 lost · 0 drawn · accuracy 90%", summaryOf(listOf(games[0])))
        val all = summaryOf(games)
        assertTrue(all, all.startsWith("6 games · 3 won · 2 lost · 1 drawn"))
        assertTrue(all, all.endsWith("accuracy 71%"))     // (90 + 70 + 55) / 3
    }

    @Test fun timeGroupsCoverWhatTheSitesSend() {
        assertEquals(TimeGroup.Rapid, TimeGroup.of("rapid")); assertEquals(TimeGroup.Rapid, TimeGroup.of("something new"))
        assertEquals(TimeGroup.Classical, TimeGroup.of("correspondence")); assertEquals(TimeGroup.Casual, TimeGroup.of("casual"))
    }
}
