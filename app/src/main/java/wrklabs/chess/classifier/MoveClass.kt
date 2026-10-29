package wrklabs.chess.classifier

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
enum class MoveClass(
    val label: String,
    val colorHex: String,
    /** Text in the badge when there is no [icon]. */
    val glyph: String,
    val isNotable: Boolean = false
) {
    BRILLIANT("Brilliant", "#26c2a3", "!!", isNotable = true),
    GREAT("Great", "#749bbf", "!", isNotable = true),
    BEST("Best", "#81b64c", "★"),
    EXCELLENT("Excellent", "#81b64c", "✓"),
    GOOD("Good", "#95b776", "·"),
    BOOK("Book", "#a88865", "📖"),
    INACCURACY("Inaccuracy", "#f7c631", "?!", isNotable = true),
    MISTAKE("Mistake", "#ffa459", "?", isNotable = true),
    MISS("Miss", "#ff7769", "✕", isNotable = true),
    BLUNDER("Blunder", "#fa412d", "??", isNotable = true),
    FORCED("Forced", "#5b5f68", "□");

    val composeColor: Color
        get() = Color(android.graphics.Color.parseColor(colorHex))

    /** The badge's picture, for the classes drawn as one rather than as text. */
    val icon: ImageVector?
        get() = when (this) {
            BEST -> Icons.Default.Star
            EXCELLENT -> Icons.Default.ThumbUp
            GOOD -> Icons.Default.Check
            BOOK -> Icons.AutoMirrored.Filled.MenuBook
            MISS -> Icons.Default.Close
            else -> null
        }
}
