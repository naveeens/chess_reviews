package wrklabs.chess.ui.theme

import androidx.compose.ui.graphics.Color

/** The tones of a theme: the neutral surfaces and the accent's own four. */
data class Tones(
    val surface: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val on: Color,
    val onVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

/** Backgrounds don't take the accent's tint: almost black. */
private val DarkSurfaces = Tones(
    Color(0xFF0E0E10), Color(0xFF09090B), Color(0xFF141417), Color(0xFF18181B), Color(0xFF222226), Color(0xFF2C2C31),
    Color(0xFFECECEE), Color(0xFFB6B6BC), Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
)

/** Text and icons on the accent: the page's own black. */
private val AccentInk = Color(0xFF0E0E10)

private fun Tones.with(accent: Color) = copy(primary = accent, onPrimary = AccentInk, primaryContainer = accent, onPrimaryContainer = AccentInk)

/** The app's accent colour, used as it is everywhere. */
val Accent = Color(0xFF41A4D0)

val AppTones: Tones = DarkSurfaces.with(Accent)
