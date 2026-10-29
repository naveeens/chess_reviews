package wrklabs.chess.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wrklabs.chess.classifier.GameReport
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.engine.formatShort
import wrklabs.chess.engine.winPercent
import wrklabs.chess.model.MoveNode
import wrklabs.chess.ui.theme.Display
import wrklabs.chess.ui.theme.Mono
import wrklabs.chess.ui.theme.Palette

/**
 * A player's name and rating over or under the board, and their accuracy at the far end once the game is reviewed.
 * The circle is the player's colour, white or black, and holds their score: 1, 0 or ½ (nothing while the result is unknown).
 */
@Composable
fun PlayerBar(name: String, rating: Int?, color: PieceColor, accuracy: Double?, modifier: Modifier = Modifier, score: String? = null) {
    val c = Palette.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScoreCircle(color, score)
        // The name and rating take what the chip leaves, so a long name is cut only when it has to be.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (rating != null) Text(rating.toString(), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
        }
        if (accuracy != null) Chip(accuracyChip(accuracy), color = c.brandCard, textColor = c.brandCardText)
    }
}

/** The evaluation as a slim vertical bar beside the board: the player at the bottom has the bottom, the number sits on the leader's side. */
@Composable
fun EvalBar(score: Score?, orientation: PieceColor, modifier: Modifier = Modifier) {
    val c = Palette.colors
    val whiteWin = (score?.winPercent() ?: 50.0) / 100.0
    val bottomIsWhite = orientation == PieceColor.WHITE
    val share by animateFloatAsState(
        ((if (bottomIsWhite) whiteWin else 1 - whiteWin).toFloat()).coerceIn(0.02f, 0.98f),
        tween(350), label = "eval",
    )
    val bottomColor = if (bottomIsWhite) c.barLight else c.barDark
    val topColor = if (bottomIsWhite) c.barDark else c.barLight
    val leaderIsBottom = (whiteWin >= 0.5) == bottomIsWhite
    val leaderColor = if (leaderIsBottom) bottomColor else topColor
    Box(modifier.width(20.dp).clip(RoundedCornerShape(6.dp)).background(topColor)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(share).background(bottomColor))
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(c.muted.copy(alpha = 0.35f)))
        Text(
            score?.formatShort() ?: "0.0",
            modifier = Modifier.align(if (leaderIsBottom) Alignment.BottomCenter else Alignment.TopCenter).fillMaxWidth().padding(vertical = 6.dp),
            color = if (leaderColor == c.barLight) Color(0xFF1B1B1D) else Color(0xFFF2F2F3),
            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            // The font's own spacing above the digits would make the gap above look bigger than the one below.
            style = DigitsInBox,
        )
    }
}

/** Digits centred in their box: without the font's own spacing above them they sit too low. */
private val DigitsInBox = androidx.compose.ui.text.TextStyle(
    lineHeight = 10.sp,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
)

/** A player's colour, white or black, as a circle holding their score: 1, 0 or ½ (empty while the result is unknown). */
@Composable
fun ScoreCircle(color: PieceColor, score: String?, modifier: Modifier = Modifier) {
    val c = Palette.colors
    val label = when (score) { "1" -> "won"; "0" -> "lost"; "½" -> "drew"; else -> null }
    Box(
        modifier.size(22.dp).clip(CircleShape).background(if (color == PieceColor.WHITE) Color(0xFFF2F2F3) else Color(0xFF111113)).border(1.dp, c.muted.copy(alpha = 0.45f), CircleShape)
            .semantics { contentDescription = "${if (color == PieceColor.WHITE) "White" else "Black"}${label?.let { ", $it" } ?: ""}" },
        contentAlignment = Alignment.Center,
    ) {
        if (score != null) Text(score, color = if (color == PieceColor.WHITE) Color(0xFF111113) else Color(0xFFF2F2F3), fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontSize = if (score == "½") 15.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The evaluation as a slim strip under the board: the player at the bottom has the left, the number sits on the leader's side. */
@Composable
fun EvalStrip(score: Score?, orientation: PieceColor, modifier: Modifier = Modifier) {
    val c = Palette.colors
    val whiteWin = (score?.winPercent() ?: 50.0) / 100.0
    val bottomIsWhite = orientation == PieceColor.WHITE
    val share by animateFloatAsState(
        ((if (bottomIsWhite) whiteWin else 1 - whiteWin).toFloat()).coerceIn(0.02f, 0.98f),
        tween(350), label = "eval",
    )
    val ours = if (bottomIsWhite) c.barLight else c.barDark
    val theirs = if (bottomIsWhite) c.barDark else c.barLight
    val leaderIsBottom = (whiteWin >= 0.5) == bottomIsWhite
    val leaderColor = if (leaderIsBottom) ours else theirs
    Box(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(16.dp).clip(RoundedCornerShape(5.dp)).background(theirs)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(ours))
        Box(Modifier.align(Alignment.Center).fillMaxHeight().width(1.dp).background(c.muted.copy(alpha = 0.35f)))
        Text(
            score?.formatShort() ?: "0.0",
            modifier = Modifier.align(if (leaderIsBottom) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 6.dp),
            color = if (leaderColor == c.barLight) Color(0xFF1B1B1D) else Color(0xFFF2F2F3),
            fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
            style = DigitsInBox,
        )
    }
}

/** The moves of the line being looked at, side by side, with the review's mark on each. */
@Composable
fun MoveStrip(
    line: List<MoveNode>, currentId: String, report: GameReport?, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    /** Moves that are not part of the game (a variation being explored): drawn in the accent colour, with no grade. */
    offGame: (String) -> Boolean = { false },
    /** Grades of moves the review doesn't know: a variation, an engine move on show. */
    extraGrades: Map<String, wrklabs.chess.classifier.MoveClass> = emptyMap(),
    /** The other moves played from the same position as this one, shown in brackets after it. */
    alternatives: (MoveNode) -> List<MoveNode> = { emptyList() },
) {
    val c = Palette.colors
    val state = rememberLazyListState()
    val index = line.indexOfFirst { it.id == currentId }
    LaunchedEffect(index) { if (index >= 0) state.animateScrollToItem((index - 2).coerceAtLeast(0)) }
    if (line.isEmpty()) {
        Spacer(modifier.fillMaxWidth().height(34.dp))
        return
    }
    LazyRow(state = state, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        items(line, key = { it.id }) { node ->
            val white = node.ply % 2 == 1
            val on = node.id == currentId
            val off = offGame(node.id)
            val cls = report?.moves?.get(node.id)?.cls ?: extraGrades[node.id]
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (white) Text("${(node.ply + 1) / 2}.", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) c.brandCard else Color.Transparent).clickable { onSelect(node.id) }.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(node.san.orEmpty(), style = MaterialTheme.typography.titleSmall, color = if (on) c.brandCardText else if (off) c.brand else c.text)
                    if (cls != null) Box(Modifier.size(7.dp).clip(CircleShape).background(cls.composeColor))
                }
                for (alt in alternatives(node)) {
                    val altOn = alt.id == currentId
                    val number = if (alt.ply % 2 == 1) "${(alt.ply + 1) / 2}." else "${alt.ply / 2}…"
                    Text(
                        "($number${alt.san.orEmpty()})", style = MaterialTheme.typography.bodyMedium,
                        color = if (altOn) c.brandCardText else c.brand,
                        modifier = Modifier.padding(start = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (altOn) c.brandCard else Color.Transparent).clickable { onSelect(alt.id) }.padding(horizontal = 5.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** How many moves of each kind each player made: `sides` is the left column, then the right one. */
@Composable
fun ClassCounts(report: GameReport?, sides: List<PieceColor> = listOf(PieceColor.WHITE, PieceColor.BLACK), modifier: Modifier = Modifier, showAll: Boolean = false) {
    val c = Palette.colors
    val shown = listOf(MoveClass.BRILLIANT, MoveClass.GREAT, MoveClass.BEST, MoveClass.EXCELLENT, MoveClass.GOOD, MoveClass.BOOK, MoveClass.INACCURACY, MoveClass.MISTAKE, MoveClass.MISS, MoveClass.BLUNDER)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for (cls in shown) {
            val left = report?.counts?.get(sides[0])?.get(cls) ?: 0
            val right = report?.counts?.get(sides[1])?.get(cls) ?: 0
            if (!showAll && left == 0 && right == 0 && cls !in setOf(MoveClass.BEST, MoveClass.BLUNDER, MoveClass.MISTAKE)) continue
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClassBadge(cls, 20.dp)
                    Text(cls.label, style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
                Text(left.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (left == 0) c.muted else cls.composeColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(right.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (right == 0) c.muted else cls.composeColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
