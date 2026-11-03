package wrklabs.chess.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (running && q != null) session.Status(mode.question(q))
        else Text(mode.title, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.Bold)
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
            highlightLastMove = revealing && mode == SquareMode.Diagonal,
            showLegalMoves = false,
            onSquareClick = { if (q != null && session.phase == SetPhase.Asking && mode == SquareMode.Find) session.answer(it == q.target) },
        )
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (revealing && q != null) {
                RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
                    Text(session.missLine(), style = MaterialTheme.typography.labelLarge, color = c.brandCardText)
                    Text(mode.reveal(q), style = MaterialTheme.typography.titleMedium, color = c.brandCardText, fontWeight = FontWeight.Bold)
                    RButton("Next", session::next, Modifier.fillMaxWidth().padding(top = 8.dp), style = ButtonStyle.OnCard)
                }
            } else if (session.phase == SetPhase.Asking && q != null) {
                when (mode) {
                    SquareMode.Find -> Text("Tap the square on the board.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
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
            } else {
                if (session.phase == SetPhase.Done) session.ResultCard()
                // Two rows, so five labels fit; the chosen one is lit in whichever row it is in.
                Segmented(SquareMode.entries.take(3).map { it to it.short }, mode, { mode = it }, Modifier.fillMaxWidth())
                Segmented(SquareMode.entries.drop(3).map { it to it.short }, mode, { mode = it }, Modifier.fillMaxWidth())
                Segmented(listOf(true to "As White", false to "As Black"), asWhite, { asWhite = it }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show coordinates", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Switch(coordinates, { coordinates = it })
                }
                session.Goal()
                RButton(if (session.phase == SetPhase.Done) "Another set" else "Start", session::start, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
