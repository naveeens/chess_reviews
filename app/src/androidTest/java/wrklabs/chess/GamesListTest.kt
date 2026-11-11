package wrklabs.chess

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.GamesScreen
import wrklabs.chess.ui.GamesViewModel
import wrklabs.chess.ui.theme.ChessTheme

/** Search, filter and sort on the Games list, with games played here as the data (they need no account). */
@RunWith(AndroidJUnit4::class)
class GamesListTest {
    @get:Rule val rule = createComposeRule()

    @Before fun seed() {
        Graph.init(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            Graph.settings.update { copy(chesscomUser = "", lichessUser = "") }
            Graph.play.clearAll()
            val now = System.currentTimeMillis()
            listOf(
                Triple("1-0", 1500, "Maia"), Triple("0-1", 1600, "Maia"), Triple("1/2-1/2", 1400, "Maia"), Triple("0-1", 1300, "Maia Two"),
            ).forEachIndexed { i, (result, elo, name) ->
                Graph.play.addGame(ImportedGame("play:$i", "play", "You", name, 1500, elo, result, "casual", "1. e4 *", now - i * 1000L))
            }
        }
    }

    @Test fun searchFilterSortAndClear() {
        val settings = AppSettings()
        rule.setContent { ChessTheme(settings.accent) { GamesScreen(viewModel<GamesViewModel>(), settings, {}, {}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("4 games", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("4 games · 1 won · 2 lost · 1 drawn").assertExists()

        // A filter: lost games only.
        rule.onNodeWithTag("filter-Result").performClick()
        rule.onNodeWithTag("filter-Result-Lost").performClick()
        rule.onNodeWithText("2 games · 0 won · 2 lost · 0 drawn").assertExists()

        // A second filter on top, and the chip shows its choice.
        rule.onNodeWithTag("filter-Opponent").performScrollTo().performClick()
        rule.onNodeWithTag("filter-Opponent-Higher rated than me").performClick()
        rule.onNodeWithText("1 game · 0 won · 1 lost · 0 drawn").assertExists()

        // Clearing leaves everything again.
        rule.onNodeWithText("Clear").performScrollTo().performClick()
        rule.onNodeWithText("4 games · 1 won · 2 lost · 1 drawn").assertExists()

        // Search by opponent name.
        rule.onNodeWithContentDescription("Search games").performClick()
        rule.onNodeWithTag("games-search").performTextInput("two")
        rule.onNodeWithText("1 game · 0 won · 1 lost · 0 drawn").assertExists()
        rule.onNodeWithTag("games-search").performTextReplacement("zzz")
        rule.onNodeWithText("No games match").assertExists()
        rule.onNodeWithText("Clear filters").performClick()
        rule.onNodeWithText("4 games · 1 won · 2 lost · 1 drawn").assertExists()

        // The sort menu offers the orders.
        rule.onNodeWithContentDescription("Sort games").performClick()
        rule.onNodeWithText("Oldest first").assertExists()
        rule.onNodeWithText("Oldest first").performClick()
    }
}
