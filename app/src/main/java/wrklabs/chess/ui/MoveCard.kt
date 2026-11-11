package wrklabs.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.ui.theme.Mono
import wrklabs.chess.ui.theme.Palette

/** One of the card's two lines to look at: what it is called, a word on the right, and the line itself as text. */
data class LineChoice(val title: String, val trailing: String, val line: String?, val on: Boolean = false)

/**
 * What happened on the move being looked at, on the review screen: the move and its grade, the loss, a line of news if there
 * is one, then the engine's better line ("Best") and, for a move that went wrong, the line that followed the move that was
 * played ("Played"). Tapping a line plays it on the board.
 */
@Composable
fun MoveCardView(
    atStart: Boolean,
    san: String?,
    cls: MoveClass?,
    lostPercent: Int?,
    note: String?,
    best: LineChoice?,
    bestPending: Boolean,
    /** For a move that went wrong; null when the move has nothing to explain. */
    played: LineChoice?,
    onBest: () -> Unit,
    onPlayed: () -> Unit,
) {
    val c = Palette.colors
    val title = if (atStart) "Start of the game" else if (cls != null) "$san · ${cls.label}" else san.orEmpty()
    RCard(Modifier.fillMaxWidth().height(150.dp), padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (cls != null) ClassBadge(cls, 36.dp)
            else Box(Modifier.size(36.dp).clip(CircleShape).background(c.field), contentAlignment = Alignment.Center) {
                Icon(if (atStart) Icons.Default.PlayArrow else Icons.Default.Edit, null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (lostPercent != null) Text("−$lostPercent%", fontFamily = Mono, fontSize = 12.sp, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (best != null) LineRow(best, onBest, Modifier.weight(1f))
            else if (bestPending) Box(Modifier.weight(1f).height(LINE_ROW).clip(RoundedCornerShape(12.dp)).background(c.button), contentAlignment = Alignment.Center) { Text("…", color = c.muted) }
            if (played != null) LineRow(played, onPlayed, Modifier.weight(1f))
        }
    }
}

private val LINE_ROW = 72.dp

@Composable
private fun LineRow(choice: LineChoice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Palette.colors
    val shape = RoundedCornerShape(12.dp)
    val ink = if (choice.on) c.brandCardText else c.text
    val soft = if (choice.on) c.brandCardText else c.muted
    Row(
        modifier.height(LINE_ROW).clip(shape)
            .background(if (choice.on) c.brandCard else c.button)
            .border(1.dp, if (choice.on) c.brand else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(choice.title, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, modifier = Modifier.weight(1f))
                if (choice.trailing.isNotEmpty()) Text(choice.trailing, fontFamily = Mono, fontSize = 11.sp, color = soft, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
            Text(choice.line ?: "Tap to see how it goes", style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
