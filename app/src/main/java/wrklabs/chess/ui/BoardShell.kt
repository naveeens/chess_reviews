package wrklabs.chess.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.ui.theme.Palette

/*
 * The parts the screens that show a board have in common: review, analysis, a game against the bot and a lesson.
 * Each screen fills in its own middle (a card, a hint, a lesson's notes); the frame around it is the same.
 */

/**
 * The part of a board screen that scrolls, with the footer pinned beneath it: a toolbar ([BoardToolbar]) or a bar of buttons
 * ([ActionBar]). On a phone too short for the board and what goes with it, the body scrolls and the footer stays.
 */
@Composable
fun ColumnScope.BoardBody(spacing: Dp = 0.dp, footer: @Composable () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing), content = body)
    footer()
}

/** The row of icon buttons at the foot of a board screen, under a hairline. */
@Composable
fun BoardToolbar(content: @Composable RowScope.() -> Unit) {
    Hairline()
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically, content = content)
}

/** Previous and next move; a long press goes to the start or the end. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StepButtons(onPrevious: () -> Unit, onNext: () -> Unit, onFirst: () -> Unit, onLast: () -> Unit) {
    val c = Palette.colors
    Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = onPrevious, onLongClick = onFirst), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous move", tint = c.text, modifier = Modifier.size(28.dp))
    }
    Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = onNext, onLongClick = onLast), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next move", tint = c.text, modifier = Modifier.size(28.dp))
    }
}

/** One side of the board: who is playing it, and what to show beside the name. */
class Seat(
    val name: String, val rating: Int?, val color: PieceColor,
    val accuracy: Double? = null, val score: String? = null, val material: Int? = null,
)

/** The evaluation bar under the board: the engine's score, seen from the side at the bottom. */
class EvalBar(val score: Score?, val orientation: PieceColor)

/** The board with a player's bar above and below it, and the evaluation bar between the board and the player at the foot. */
@Composable
fun BoardWithSeats(top: Seat, bottom: Seat, eval: EvalBar? = null, board: @Composable () -> Unit) {
    PlayerBar(top.name, top.rating, top.color, top.accuracy, score = top.score, material = top.material)
    board()
    if (eval != null) EvalStrip(eval.score, eval.orientation)
    PlayerBar(bottom.name, bottom.rating, bottom.color, bottom.accuracy, score = bottom.score, material = bottom.material)
}
