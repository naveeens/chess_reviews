package wrklabs.chess

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.model.Square
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.CardAnswerStatus
import wrklabs.chess.ui.LineOn
import wrklabs.chess.ui.ReviewScreen
import wrklabs.chess.ui.ReviewViewModel
import wrklabs.chess.ui.TrainState
import wrklabs.chess.ui.TrainViewModel
import wrklabs.chess.ui.theme.ChessTheme

/** A mistake drill answered wrongly: the card gets the engine's better line and what followed the move, and each plays on the board. */
@RunWith(AndroidJUnit4::class)
class MistakeAnswerTest {
    @get:Rule val rule = createComposeRule()

    @Before fun init() { Graph.init(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test fun aWrongAnswerGetsBothLinesAndEachPlaysOnTheBoard() {
        // The game from the review card test: Black's 3...Nf6?? walks into Qxf7#, so there is a mistake of Black's to drill.
        val pgn = "[Event \"t\"]\n[White \"A\"]\n[Black \"B\"]\n[Result \"1-0\"]\n\n1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"
        val game = ImportedGame("test:drill", "test", "A", "B", 1500, 1500, "1-0", "casual", pgn, System.currentTimeMillis())
        lateinit var review: ReviewViewModel
        val settings = AppSettings()
        rule.setContent { ChessTheme(settings.accent) { review = viewModel(); ReviewScreen(review, settings, onBack = {}, onAnalyseFromHere = {}) } }
        rule.runOnIdle { review.open(game, "B") }
        rule.waitUntil(120_000) { review.state.value.report != null && !review.state.value.reviewing }

        var vm: TrainViewModel? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = TrainViewModel(); vm!!.loadSession() }
        fun question() = vm!!.trainState.value as? TrainState.Question
        rule.waitUntil(20_000) { question() != null }
        val card = question()!!.card
        assertEquals("f6", card.playedSan.takeLast(2))                      // the mistake being drilled

        // Answer with the mistake itself.
        rule.runOnIdle { vm!!.onMove(Square.fromName("g8"), Square.fromName("f6")) }
        rule.waitUntil(30_000) { question()?.status == CardAnswerStatus.Wrong }
        rule.waitUntil(60_000) { question()?.bestLine != null && question()?.playedLine != null }
        val q = question()!!
        assertNotNull(q.bestScore)
        assertTrue(q.playedLine!!, q.playedLine!!.contains("Nf6") && q.playedLine!!.contains("Qxf7"))

        rule.runOnIdle { vm!!.showBestLine() }
        assertEquals(LineOn.Best, question()!!.lineOn)
        // The board is back at the position the answer was asked in, before the line starts.
        assertEquals(card.fen.substringBefore(' '), question()!!.board.toFen().substringBefore(' '))
        rule.runOnIdle { vm!!.showWhy() }
        assertEquals(LineOn.Played, question()!!.lineOn)
    }
}
