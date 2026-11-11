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
import wrklabs.chess.ui.OpeningsSection
import wrklabs.chess.ui.theme.ChessTheme

@RunWith(AndroidJUnit4::class)
class OpeningsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun init() { Graph.init(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test fun learnHasOneNextButtonPinnedAtTheBottomThatStepsTheLine() {
        val settings = AppSettings()
        rule.setContent { ChessTheme(settings.accent) { Column { OpeningsSection(settings) } } }
        rule.waitUntil(10_000) { rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasClickAction())[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Next: ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithText("Next: ", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Next $button should sit at the bottom of $screen", button.bottom > screen.bottom - 150f)
        rule.onNodeWithText("Next: ", substring = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Back").assertIsEnabled()                       // there is a move to go back from now
    }
}
