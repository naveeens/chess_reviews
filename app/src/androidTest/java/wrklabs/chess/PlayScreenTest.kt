package wrklabs.chess

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.importer.PgnParser
import wrklabs.chess.play.PlayLaunch
import wrklabs.chess.play.PlayRequest
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.PlaySection
import wrklabs.chess.ui.PlayViewModel
import wrklabs.chess.ui.theme.ChessTheme

/** The Play screen end to end, driven in-process. */
@RunWith(AndroidJUnit4::class)
class PlayScreenTest {
    @get:Rule val rule = createComposeRule()
    private var reviewed: Pair<ImportedGame, String>? = null

    @Before fun fresh() {
        Graph.init(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { Graph.play.clearAll() }
        PlayLaunch.request.value = null
        reviewed = null
        Graph.settings.update { copy(blunderCheck = false) }
    }

    @After fun restore() { Graph.settings.update { copy(blunderCheck = false) } }

    private fun show() {
        val settings = AppSettings()
        rule.setContent { ChessTheme(settings.accent) { Column { PlaySection(settings) { g, me -> reviewed = g to me } } } }
    }

    /** A square of the board as the user (White) sees it. */
    private fun click(file: Int, rankFromBottom: Int) = rule.onNodeWithTag("board").performTouchInput {
        val sq = width / 8f
        click(Offset((file + 0.5f) * sq, (8 - rankFromBottom + 0.5f) * sq))
    }
    private fun move(f1: Int, r1: Int, f2: Int, r2: Int) { click(f1, r1); click(f2, r2) }

    private fun waitForMyTurn() = rule.waitUntil(20_000) {
        rule.onAllNodesWithText("Your move", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            rule.onAllNodesWithText("thinking", substring = true).fetchSemanticsNodes().isEmpty()
    }

    private fun resignAndReview(): List<String> {
        rule.onNodeWithContentDescription("Resign").performClick()
        rule.onAllNodesWithText("Resign").onLast().performClick()
        rule.onNodeWithText("You resigned").assertExists()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Review game").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Review game").performClick()
        return PgnParser.parse(reviewed!!.first.pgn).moves
    }

    @Test fun playAMoveGetAReplyResignAndReview() {
        show()
        rule.onNodeWithText("Start").performClick()
        rule.onNodeWithTag("board").assertExists()
        move(4, 2, 4, 4)                                                  // e4
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Maia played", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val moves = resignAndReview()
        assertEquals("e4", moves.first()); assertEquals(2, moves.size)
        val (game, me) = reviewed!!
        assertEquals("You", me); assertEquals("0-1", game.result); assertEquals("play", game.source)
        assertEquals("You", game.white); assertEquals("Maia", game.black)
    }

    @Test fun aBishopCanMoveOnceItsPawnHasGone() {
        show()
        rule.onNodeWithText("Start").performClick()
        move(3, 2, 3, 4); waitForMyTurn()                                 // d4
        move(4, 2, 4, 3); waitForMyTurn()                                 // e3
        move(5, 1, 3, 3); waitForMyTurn()                                 // Bf1-d3
        val moves = resignAndReview()
        assertEquals("moves were $moves", listOf("d4", "e3", "Bd3"), moves.filterIndexed { i, _ -> i % 2 == 0 })
    }

    @Test fun undoTakesBackMyMoveAndTheReplyAndTheGameGoesOn() {
        show()
        rule.onNodeWithText("Start").performClick()
        move(4, 2, 4, 4); waitForMyTurn()                                 // e4, a reply
        rule.onNodeWithContentDescription("Undo").performClick()
        rule.onNodeWithText("Your move.").assertExists()                  // no "Maia played" any more: back at the start
        move(3, 2, 3, 4); waitForMyTurn()                                 // d4 instead
        val moves = resignAndReview()
        assertEquals("d4", moves.first()); assertEquals(2, moves.size)
    }

    @Test fun aHintShowsWhatPlayersOfMyRatingPlay() {
        show()
        rule.onNodeWithText("Start").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Hint").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Hint").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("players choose", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // At the start the three most common are among the usual first moves.
        val shown = listOf("e4", "d4", "Nf3", "c4", "g3", "b3", "e3").count { rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("moves shown: $shown", shown >= 2)
    }

    @Test fun resigningMovesTheAutoLevelDownAndTheGameJoinsTheList() {
        show()
        rule.onNodeWithText("Start").performClick()
        move(4, 2, 4, 4); waitForMyTurn()
        val before = runBlocking { Graph.play.level() }                    // null until a game has been finished
        resignAndReview()
        val level = runBlocking { Graph.play.level() }
        assertEquals((before ?: 1500).let { maxOf(600, it - 100) }.let { it }, level)
        rule.onNodeWithText("Next game", substring = true).assertExists()
        val key = reviewed!!.first.key
        assertTrue(runBlocking { Graph.play.games() }.any { it.key == key })
        assertEquals(1, runBlocking { Graph.play.results() }.size)
    }

    @Test fun playingFromAPositionStartsThereAndDoesNotCountTowardsTheLevel() {
        // White to play and mate with Rd8.
        PlayLaunch.request.value = PlayRequest("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", true, 1200, "Your game, move 30")
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Your game, move 30").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Start").performClick()
        move(3, 1, 3, 8)                                                   // Rd8#
        rule.waitUntil(10_000) { rule.onAllNodesWithText("You won by checkmate").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Review game").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Review game").performClick()
        val pgn = reviewed!!.first.pgn
        assertTrue(pgn, pgn.contains("[FEN \"6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1\"]"))
        assertEquals("1-0", reviewed!!.first.result)
        assertEquals(0, runBlocking { Graph.play.results() }.size)
        assertNull(runBlocking { Graph.play.level() })
        assertTrue(runBlocking { Graph.play.games() }.any { it.key == reviewed!!.first.key })
    }

    @Test fun anUnfinishedGameCanBeContinuedAfterTheScreenIsGone() {
        show()
        rule.onNodeWithText("Start").performClick()
        move(4, 2, 4, 4); waitForMyTurn()
        // A new view-model, as after the app was closed: it finds the game and can carry on from it.
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        var vm: PlayViewModel? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = PlayViewModel(app) }
        val deadline = System.currentTimeMillis() + 10_000
        while (vm!!.state.value.resume == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val saved = vm!!.state.value.resume
        assertNotNull(saved)
        assertEquals("e2e4", saved!!.ucis.first()); assertTrue(saved.ucis.size >= 2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm!!.continueGame() }
        assertTrue(vm!!.state.value.inGame)
        assertEquals(saved.ucis, vm!!.state.value.game.ucis.take(saved.ucis.size))
    }

    @Test fun aGameWithNoMoveYetIsNotKeptToContinue() {
        show()
        rule.onNodeWithText("Start").performClick()
        waitForMyTurn()
        // Playing White, nothing has been moved: there is nothing to come back to.
        assertNull(runBlocking { Graph.play.active() })
    }

    /** White has a queen and Black a pawn on c6; Qd5 walks into it. */
    private fun startFromQueenPosition() {
        PlayLaunch.request.value = PlayRequest("4k3/8/2p5/8/8/8/8/3QK3 w - - 0 1", true, 600, "A test position")
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("A test position").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Start").performClick()
    }

    @Test fun aBlunderIsHeldBackUntilYouSayToPlayIt() {
        Graph.settings.update { copy(blunderCheck = true) }
        startFromQueenPosition()
        move(3, 1, 3, 5)                                                   // Qd5??, taken by the pawn
        rule.waitUntil(30_000) { rule.onAllNodesWithText("This looks like a blunder").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Choose another").performClick()
        rule.onNodeWithText("This looks like a blunder").assertDoesNotExist()
        rule.onNodeWithText("Your move", substring = true).assertExists()   // nothing was played
        move(3, 1, 3, 5)
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Play it anyway").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Play it anyway").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("This looks like a blunder").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("This looks like a blunder").assertDoesNotExist()
    }

    @Test fun aSoundMoveIsNotStopped() {
        Graph.settings.update { copy(blunderCheck = true) }
        startFromQueenPosition()
        move(3, 1, 3, 2)                                                   // Qd2, safe
        rule.waitUntil(30_000) { rule.onAllNodesWithText("thinking", substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("played", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("This looks like a blunder").assertDoesNotExist()
    }

    @Test fun theHintCanShowTheEnginesBestMove() {
        startFromQueenPosition()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Hint").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Hint").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Best move").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Best move").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("The engine plays").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theEngineLooksAtTheGameWhileYouPlay() {
        Graph.settings.update { copy(analyseWhilePlaying = true) }
        val fen = "4k3/6p1/8/8/8/8/1P6/R3K3 w - - 0 1"                   // a position no other test uses
        PlayLaunch.request.value = PlayRequest(fen, true, 600, "Analysis test")
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Analysis test").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Start").performClick()
        val speed = Graph.settings.value.reviewSpeed
        Thread.sleep(6_000)
        val t = System.nanoTime()
        runBlocking { Graph.reviews.position(fen, speed.depth, speed.moveMs) }
        val ms = (System.nanoTime() - t) / 1_000_000
        assertTrue("took $ms ms: not already looked at", ms < 50)
    }
}
