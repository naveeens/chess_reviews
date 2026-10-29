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

/** The accents to choose from: muted, mid-tone colours that sit on near-black. Each is used as it is everywhere. */
enum class AccentChoice(val title: String, val color: Color) {
    OCHRE("Ochre", Color(0xFFCEAD40)),
    SAGE("Sage", Color(0xFF6E9C65)),
    DENIM("Denim", Color(0xFF6A85C2)),
    VERDIGRIS("Verdigris", Color(0xFF589D9D)),
    PLUM("Plum", Color(0xFFA07CB5)),
    TAUPE("Taupe", Color(0xFF9E917F)),
}

fun tonesFor(accent: AccentChoice): Tones = DarkSurfaces.with(accent.color)
