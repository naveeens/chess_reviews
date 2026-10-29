package wrklabs.chess.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours the app uses beyond Material's scheme, named as on the stylesheet of Rookahead's web app (https://github.com/rookahead/rookahead, GPL-3.0-or-later), whose palette this follows. */
@Immutable
data class AppColors(
    val bg: Color,
    val card: Color,
    val cardHigh: Color,
    val field: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val brand: Color,
    val brandInk: Color,
    val brandCard: Color,
    val brandCardText: Color,
    val button: Color,
    val win: Color,
    val danger: Color,
    val warn: Color,
    val barLight: Color,
    val barDark: Color,
    val dark: Boolean,
)

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("ChessTheme missing") }
