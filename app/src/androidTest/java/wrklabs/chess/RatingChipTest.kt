package wrklabs.chess

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.ui.RatingChip
import wrklabs.chess.ui.theme.ChessTheme

@RunWith(AndroidJUnit4::class)
class RatingChipTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsTheGamesRatingAndAChosenLevelReplacesIt() {
        val pick = mutableStateOf<Int?>(null)
        val picked = mutableListOf<Int?>()
        rule.setContent { ChessTheme(wrklabs.chess.settings.AppSettings().accent) { RatingChip(pick.value, 1423, { picked += it; pick.value = it }) } }
        rule.onNodeWithText("1423").assertIsDisplayed()
        rule.onNodeWithText("1423").performClick()
        rule.onNodeWithText("1800").performClick()
        rule.onNodeWithText("1800").assertIsDisplayed()
        rule.onNodeWithText("1800").performClick()
        rule.onNodeWithText("From the game").performClick()
        assertEquals(listOf<Int?>(1800, null), picked)
        rule.onNodeWithText("1423").assertIsDisplayed()
    }
}
