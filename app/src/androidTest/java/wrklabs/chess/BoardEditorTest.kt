package wrklabs.chess

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.BoardEditor
import wrklabs.chess.ui.theme.ChessTheme

/** The board editor, driven in-process: put pieces down, move them, and see what it will hand on. */
@RunWith(AndroidJUnit4::class)
class BoardEditorTest {
    @get:Rule val rule = createComposeRule()
    private var analysed: String? = null

    private fun show(fen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") {
        val settings = AppSettings()
        rule.setContent { ChessTheme(settings.accent) { BoardEditor(fen, settings, onBack = {}, onAnalyse = { analysed = it }, onPlay = {}) } }
    }

    private fun click(file: Int, rankFromBottom: Int) = rule.onNodeWithTag("board").performTouchInput {
        val sq = width / 8f
        click(Offset((file + 0.5f) * sq, (8 - rankFromBottom + 0.5f) * sq))
    }

    private fun tap(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun piecesPickedFromThePaletteAreDownWhereYouTap() {
        show()
        rule.onNodeWithText("Clear").performClick()
        tap("palette-K"); click(4, 1)                 // e1
        tap("palette-k"); click(4, 8)                 // e8
        tap("palette-R"); click(0, 1); click(7, 1)    // a1 and h1: the same piece stays picked
        rule.onNodeWithText("Analyse").performClick()
        assertEquals("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", analysed)
    }

    @Test fun aPieceIsPickedUpAndPutDownOnAnEmptySquare() {
        show()
        click(4, 2); click(4, 4)                      // e2, then e4
        rule.onNodeWithText("Analyse").performClick()
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1", analysed)
    }

    @Test fun theEraserClearsASquare() {
        show()
        tap("tool-erase"); click(0, 8)                // the black rook on a8
        rule.onNodeWithText("Analyse").performClick()
        assertEquals("1nbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQk - 0 1", analysed)
    }

    @Test fun aPositionWithNoKingsCannotBeSentOn() {
        show()
        rule.onNodeWithText("Clear").performClick()
        rule.onNodeWithText("Analyse").assertIsNotEnabled()
        rule.onNodeWithText("White has no king.", substring = true).assertExists()
    }

    @Test fun sideToMoveIsWrittenIntoThePosition() {
        show()
        rule.onNodeWithText("Black to move").performScrollTo().performClick()
        rule.onNodeWithText("Analyse").performClick()
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1", analysed)
    }
}
