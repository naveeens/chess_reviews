package wrklabs.chess

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.ReviewScreen
import wrklabs.chess.ui.ReviewViewModel
import wrklabs.chess.ui.theme.ChessTheme

@RunWith(AndroidJUnit4::class)
class ReviewCardTest {
    @get:Rule val rule = createComposeRule()

    @Before fun init() { Graph.init(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test fun aBlunderCardOffersTheBestLineAndTheLineThatFollowedWhatWasPlayed() {
        val pgn = "[Event \"t\"]\n[White \"A\"]\n[Black \"B\"]\n[Result \"1-0\"]\n\n1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"
        val game = ImportedGame("test:card", "test", "A", "B", 1500, 1500, "1-0", "casual", pgn, System.currentTimeMillis())
        lateinit var vm: ReviewViewModel
        val settings = AppSettings()
        rule.setContent {
            ChessTheme(settings.accent) {
                vm = viewModel()
                ReviewScreen(vm, settings, onBack = {}, onAnalyseFromHere = {})
            }
        }
        rule.runOnIdle { vm.open(game, "B") }
        rule.waitUntil(120_000) { vm.state.value.report != null && !vm.state.value.reviewing }
        rule.runOnIdle { vm.showBoard(); vm.seekToPly(6) }                  // 3... Nf6??
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Played").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("3… Nf6 4. Qxf7#", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Show why").assertDoesNotExist()
    }
}
