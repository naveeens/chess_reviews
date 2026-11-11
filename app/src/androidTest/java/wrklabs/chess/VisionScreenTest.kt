package wrklabs.chess

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.VisionSection
import wrklabs.chess.ui.theme.ChessTheme

@RunWith(AndroidJUnit4::class)
class VisionScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun init() { Graph.init(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test fun theQuestionIsAskedBelowTheBoardAndNextIsPinnedAfterAMiss() {
        val settings = AppSettings()
        rule.setContent { ChessTheme(settings.accent) { Column { VisionSection(settings) } } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Start").performClick()
        repeat(20) { rule.mainClock.advanceTimeBy(250) }
        val question = rule.onNodeWithText("under attack", substring = true).fetchSemanticsNode().boundsInRoot
        val progress = rule.onNodeWithText("1 of 10").fetchSemanticsNode().boundsInRoot
        assertTrue("question $question should be below progress $progress", question.top > progress.top)
        // Let the clock run out: a miss, with Next pinned at the bottom of the screen.
        repeat(80) { rule.mainClock.advanceTimeBy(250) }
        val next = rule.onNodeWithText("Next").fetchSemanticsNode().boundsInRoot
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Next $next should sit at the bottom of $screen", next.bottom > screen.bottom - 150f)
    }
}
