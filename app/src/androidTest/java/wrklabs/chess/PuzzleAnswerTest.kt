package wrklabs.chess

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.ui.LineOn
import wrklabs.chess.ui.PuzzleState
import wrklabs.chess.ui.PuzzleStatus
import wrklabs.chess.ui.PuzzleViewModel

/** A puzzle answered wrongly: the card gets the puzzle's own line, what followed the move and the engine's grade, and each plays on the board. */
@RunWith(AndroidJUnit4::class)
class PuzzleAnswerTest {
    @get:Rule val rule = createComposeRule()

    @Before fun init() { Graph.init(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test fun aWrongMoveGetsTheLineAndTheGradeAndEachPlaysOnTheBoard() {
        var vm: PuzzleViewModel? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = PuzzleViewModel(); vm!!.loadSession() }
        fun question() = vm!!.state.value as? PuzzleState.Question
        rule.waitUntil(20_000) { question()?.status == PuzzleStatus.Playing }
        val q = question()!!
        val wrong = q.board.legalMoves().first { it.uci != q.expected }
        rule.runOnIdle { vm!!.onDrag(wrong.from, wrong.to) }
        rule.waitUntil(10_000) { question()?.status == PuzzleStatus.Wrong }
        rule.waitUntil(60_000) { question()?.playedLine != null && question()?.grade != null }
        val w = question()!!
        assertNotNull(w.bestLine)
        assertTrue(w.bestLine!!, w.bestLine!!.isNotBlank())
        assertTrue(w.playedLine!!, w.playedLine!!.contains(wrong.san))

        rule.runOnIdle { vm!!.showBestLine() }
        assertEquals(LineOn.Best, question()!!.lineOn)
        assertEquals(w.beforeFen!!.substringBefore(' '), question()!!.board.toFen().substringBefore(' '))
        rule.runOnIdle { vm!!.showWhy() }
        assertEquals(LineOn.Played, question()!!.lineOn)
    }
}
