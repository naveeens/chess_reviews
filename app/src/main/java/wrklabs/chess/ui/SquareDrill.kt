package wrklabs.chess.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Piece
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.Square
import wrklabs.chess.training.Pace
import wrklabs.chess.training.Vision
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Palette

/** The three questions of the square drill. */
enum class SquareMode(val title: String, val short: String) {
    Find("Find the square", "Find"),
    Name("Name the square", "Name"),
    Colour("Square colour", "Colour"),
    Knight("Knight moves", "Knight"),
    Diagonal("Same diagonal?", "Diagonal"),
}

private val EMPTY_BOARD = ChessBoard.fromFen("8/8/8/8/8/8/8/8 w - - 0 1")

/** a1 is dark, so a square is light when its file and rank add up to an odd number. */
internal fun isLightSquare(s: Square) = (s.file + s.rank) % 2 == 1

/** The name of the right answer and three wrong ones from the same file or rank, which are the easy ones to mix up. */
internal fun nameChoices(target: Square, random: kotlin.random.Random): List<String> {
    val near = (0..7).flatMap { i -> listOf(Square(target.file, i), Square(i, target.rank)) }.filter { it != target }.distinct()
    return (near.shuffled(random).take(3).map { it.name } + target.name).shuffled(random)
}

/** One question: [target] always, [other] for the two-square modes, [choices] for naming. */
private class SquareQ(val target: Square, val other: Square, val choices: List<String>)

private fun makeQuestion(mode: SquareMode, previous: Square?, random: kotlin.random.Random): SquareQ {
    var target = Square(random.nextInt(8), random.nextInt(8))
    while (target == previous) target = Square(random.nextInt(8), random.nextInt(8))
    val choices = if (mode == SquareMode.Name) nameChoices(target, random) else emptyList()
    val other = when (mode) {
        SquareMode.Knight -> { var o: Square; do { o = Square(random.nextInt(8), random.nextInt(8)) } while (Vision.knightDistance(o, target) !in 1..4); o }
        SquareMode.Diagonal -> Vision.diagonalOther(target, random)
        else -> Square(0, 0)
    }
    return SquareQ(target, other, choices)
}

private fun SquareMode.question(q: SquareQ) = when (this) {
    SquareMode.Find -> "Find ${q.target.name}"
    SquareMode.Name -> "Which square is lit?"
    SquareMode.Knight -> "Knight moves to the lit square?"
    SquareMode.Diagonal -> "Are ${q.target.name} and ${q.other.name} on the same diagonal?"
    SquareMode.Colour -> "Light or dark?"
}

/** The right answer in words, shown after a miss. */
private fun SquareMode.reveal(q: SquareQ) = when (this) {
    SquareMode.Find -> "That is ${q.target.name}"
    SquareMode.Name -> "It was ${q.target.name}"
    SquareMode.Colour -> "${q.target.name} is ${if (isLightSquare(q.target)) "light" else "dark"}"
    SquareMode.Knight -> "${Vision.knightDistance(q.other, q.target)} knight move${if (Vision.knightDistance(q.other, q.target) == 1) "" else "s"}"
    SquareMode.Diagonal -> Vision.diagonalWhy(q.target, q.other)
}

/** Square training: sets of ten questions, each against its own clock, getting faster as you get them right. */
@Composable
fun ColumnScope.SquareSection(settings: AppSettings) {
    val context = LocalContext.current
    val c = Palette.colors
    var mode by rememberSaveable { mutableStateOf(SquareMode.Find) }
    var asWhite by rememberSaveable { mutableStateOf(true) }
    var coordinates by rememberSaveable { mutableStateOf(false) }
    val random = remember { kotlin.random.Random(System.nanoTime()) }
    // One session per question type, each with its own pace.
    val sessions = remember { SquareMode.entries.associateWith { m -> var last: Square? = null; TimedSession(context, "Squares.${m.name}") { makeQuestion(m, last, random).also { last = it.target } } } }
    val session = sessions.getValue(mode)
    session.Drive()

    val q = session.current
    val running = session.phase == SetPhase.Asking || session.phase == SetPhase.Revealing
    val revealing = session.phase == SetPhase.Revealing
    // The colour question must not be answerable from the board: one flat tone with the square lit,
    // and the real checkerboard comes back with the answer.
    val themed = settings.boardTheme.colors()
    val boardColors = if (mode == SquareMode.Colour && session.phase == SetPhase.Asking) {
        val flat = androidx.compose.ui.graphics.lerp(themed.light, themed.dark, 0.5f)
        themed.copy(light = flat, dark = flat, selected = androidx.compose.ui.graphics.Color(0xFF56B4E9))
    } else themed
    // A lone knight on the board, for the question of how far it is from the lit square.
    val knightBoard = remember(q?.other) { Vision.boardOf(mapOf((q?.other ?: Square(0, 0)) to Piece(PieceType.KNIGHT, PieceColor.WHITE))) }

    when (session.phase) {
        SetPhase.Idle -> SetupScreen(
            session, null,
            "Know the board without looking for it. Ten questions a set, against a clock that shortens as you get them right.",
            "Each set is ten questions. A wrong answer or a clock that runs out shows the right one, then the set moves on. " +
                "Ten right with no misses makes the clock shorter; two or more misses make it longer.",
        ) {
            SquareMode.entries.forEach { m -> ModeRow(m, sessions.getValue(m).level, m == mode) { mode = m } }
            Segmented(listOf(true to "As White", false to "As Black"), asWhite, { asWhite = it }, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show coordinates", style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text("Letters and numbers on the board edge", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(coordinates, { coordinates = it })
            }
        }
        SetPhase.Done -> SummaryScreen(session, { mode.summary(it) })
        else -> {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            session.Progress()
        // Who is at which end, as on the review and analysis boards, while a set runs.
        val bottomColor = if (asWhite) PieceColor.WHITE else PieceColor.BLACK
        val topColor = if (asWhite) PieceColor.BLACK else PieceColor.WHITE
        fun sideName(color: PieceColor) = if (color == PieceColor.WHITE) "White" else "Black"
        val sides = true
        if (sides) PlayerBar(sideName(topColor), null, topColor, null)
        ChessboardView(
            modifier = Modifier.fillMaxWidth(),
            board = if (running && mode == SquareMode.Knight) knightBoard else EMPTY_BOARD,
            colors = boardColors,
            orientation = if (asWhite) PieceColor.WHITE else PieceColor.BLACK,
            selectedSquare = if (q != null && running && mode != SquareMode.Diagonal && (revealing || mode in setOf(SquareMode.Name, SquareMode.Colour, SquareMode.Knight))) q.target else null,
            // The two squares, and the diagonal when there is one, only once the answer is shown.
            lastMove = if (q != null && revealing && mode == SquareMode.Diagonal) q.target to q.other else null,
            arrows = if (q != null && revealing && mode == SquareMode.Diagonal && Vision.sameDiagonal(q.target, q.other)) listOf(EngineArrow(q.target, q.other, c.win)) else emptyList(),
            coordinates = coordinates,
            grid = boardColors.light == boardColors.dark,
            highlightLastMove = revealing && mode == SquareMode.Diagonal,
            showLegalMoves = false,
            onSquareClick = { if (q != null && session.phase == SetPhase.Asking && mode == SquareMode.Find) session.answer(it == q.target) },
        )
        if (sides) PlayerBar(sideName(bottomColor), null, bottomColor, null)
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (revealing && q != null) {
                RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
                    Text(session.missLine(), style = MaterialTheme.typography.labelLarge, color = c.brandCardText)
                    Text(mode.reveal(q), style = MaterialTheme.typography.titleMedium, color = c.brandCardText, fontWeight = FontWeight.Bold)
                }
            } else if (session.phase == SetPhase.Asking && q != null) {
                // The question under the board, where the eyes and the thumb are.
                Text(mode.question(q), style = MaterialTheme.typography.titleLarge, color = c.text)
            }
            Spacer(Modifier.height(12.dp))
        }
        }
        // The answers, or Next after a miss, pinned at the bottom of the screen.
        if (revealing && q != null) {
            RButton("Next", session::next, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp))
        } else if (session.phase == SetPhase.Asking && q != null) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)) {
                when (mode) {
                    SquareMode.Find -> Unit
                    SquareMode.Name -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        q.choices.forEach { name -> RButton(name, { session.answer(name == q.target.name) }, Modifier.weight(1f), style = ButtonStyle.Tonal) }
                    }
                    SquareMode.Knight -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { n -> RButton("$n", { session.answer(n == Vision.knightDistance(q.other, q.target)) }, Modifier.weight(1f), style = ButtonStyle.Tonal) }
                    }
                    SquareMode.Diagonal -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RButton("Yes", { session.answer(Vision.sameDiagonal(q.target, q.other)) }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                        RButton("No", { session.answer(!Vision.sameDiagonal(q.target, q.other)) }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                    }
                    SquareMode.Colour -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RButton("Light", { session.answer(isLightSquare(q.target)) }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                        RButton("Dark", { session.answer(!isLightSquare(q.target)) }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                    }
                }
            }
        }
        }
    }
}

/** One line of the mode list on the setup screen: the question, what it asks, and the pace you are at. */
@Composable
private fun ModeRow(mode: SquareMode, level: Int, on: Boolean, onClick: () -> Unit) {
    val c = Palette.colors
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(if (on) c.cardHigh else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(mode.title, style = MaterialTheme.typography.bodyLarge, color = c.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            Text(mode.about, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Text(Pace.label(level), style = MaterialTheme.typography.labelLarge, color = if (on) c.brand else c.muted)
    }
}

private val SquareMode.about get() = when (this) {
    SquareMode.Find -> "Tap the named square"
    SquareMode.Name -> "Name the lit square"
    SquareMode.Colour -> "Light or dark?"
    SquareMode.Knight -> "Count knight moves to the lit square"
    SquareMode.Diagonal -> "Do two squares share a diagonal?"
}

/** A missed question and its answer, in one line for the summary. */
private fun SquareMode.summary(q: SquareQ) = when (this) {
    SquareMode.Find -> "Find ${q.target.name}"
    SquareMode.Name -> "The lit square was ${q.target.name}"
    SquareMode.Colour -> reveal(q)
    SquareMode.Knight -> "${q.other.name} to ${q.target.name}: ${reveal(q)}"
    SquareMode.Diagonal -> "${q.target.name} and ${q.other.name}: ${reveal(q)}"
}
