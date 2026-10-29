package wrklabs.chess.ui

import androidx.compose.ui.graphics.Color

/** The squares and marks of a board. */
data class BoardColors(
    val light: Color,
    val dark: Color,
    val lastMove: Color = Color(0x9EFFD666),
    val arrow: Color = Color(0xEBFFAD33),
    val selected: Color = Color(0x9956B4E9),
)

/** The boards on offer. */
enum class BoardTheme(val title: String) {
    GRAPHITE("Graphite"), BROWN("Brown"), GREEN("Green");

    fun colors(): BoardColors = when (this) {
        GRAPHITE -> BoardColors(Color(0xFFD9DCE1), Color(0xFF8E96A1))
        BROWN -> BoardColors(Color(0xFFF0D9B5), Color(0xFFB58863))
        GREEN -> BoardColors(Color(0xFFEBECD0), Color(0xFF739552))
    }
}
