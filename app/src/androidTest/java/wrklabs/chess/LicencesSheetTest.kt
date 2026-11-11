package wrklabs.chess

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.LicencesSheet
import wrklabs.chess.ui.theme.ChessTheme

@RunWith(AndroidJUnit4::class)
class LicencesSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun thePictureReadingCreditCarriesTheCopyrightLineAndTheLicenceText() {
        rule.setContent { ChessTheme(AppSettings().accent) { LicencesSheet(onDismiss = {}) } }
        rule.onNodeWithText("fenshot and tensorflow_chessbot").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Copyright (c) 2026 SORTINO LABS S.R.L.", substring = true).assertExists()
        rule.onNodeWithText("Permission is hereby granted", substring = true).assertExists()
    }
}
