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

/** The engine's better move, as the card's button offers it. */
data class BestChoice(val label: String, val san: String, val eval: String, val on: Boolean = false)

/**
 * What happened on the move being looked at, the same card on the review and the analysis screens: the move and its grade,
 * the loss, a line of news if there is one, the engine's better move and, for a move that went wrong, "Show why".
 */
@Composable
fun MoveCardView(
    atStart: Boolean,
    san: String?,
    cls: MoveClass?,
    lostPercent: Int?,
    note: String?,
    best: BestChoice?,
    bestPending: Boolean,
    /** The button's words ("Show why", "What you missed"), or null when the move has nothing to explain. */
    whyLabel: String?,
    whyActive: Boolean,
    onBest: () -> Unit,
    onWhy: () -> Unit,
) {
    val c = Palette.colors
    val title = if (atStart) "Start of the game" else if (cls != null) "$san · ${cls.label}" else san.orEmpty()
    RCard(Modifier.fillMaxWidth().height(112.dp), padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
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
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(12.dp)
            if (best != null) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .background(if (best.on) c.brandCard else c.button)
                        .border(1.dp, if (best.on) c.brand else Color.Transparent, shape)
                        .clickable(onClick = onBest)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${best.label} · ${best.san}", style = MaterialTheme.typography.labelLarge, color = if (best.on) c.brandCardText else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(best.eval, fontFamily = Mono, fontSize = 11.sp, color = if (best.on) c.brandCardText else c.muted, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            } else if (bestPending) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(shape).background(c.button), contentAlignment = Alignment.Center) { Text("…", color = c.muted) }
            }
            if (whyLabel != null) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(shape).background(if (whyActive) c.brandCard else c.button).clickable(onClick = onWhy),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = if (whyActive) c.brandCardText else c.text, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(whyLabel, style = MaterialTheme.typography.labelLarge, color = if (whyActive) c.brandCardText else c.text, maxLines = 1)
                }
            }
        }
    }
}
