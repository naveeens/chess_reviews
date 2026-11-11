package wrklabs.chess.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import wrklabs.chess.db.Puzzle
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Piece
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.Square
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.training.Vision
import wrklabs.chess.ui.theme.Palette

enum class VisionMode(val title: String, val short: String) {
    Attacked("Attacked pieces", "Attacked"),
    Hanging("Hanging piece", "Hanging"),
    Recall("Recall", "Recall"),
}

private const val LOOK_SECONDS = 5

private fun best(context: Context, key: String) = context.getSharedPreferences("squares", Context.MODE_PRIVATE).getInt("best_$key", 0)
private fun saveBest(context: Context, key: String, score: Int) = context.getSharedPreferences("squares", Context.MODE_PRIVATE).edit().putInt("best_$key", score).apply()

private fun side(color: PieceColor) = if (color == PieceColor.WHITE) "White" else "Black"

private const val DOT = "side"

/** [before], then a dot filled with [color] and its name in bold, then [after]. */
private fun sideQuestion(before: String, color: PieceColor, after: String): AnnotatedString = buildAnnotatedString {
    append(before)
    appendInlineContent(DOT, "●")
    append(" ")
    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(side(color)) }
    append(after)
}

/** The dot: the piece colour itself, with a thin rim so it shows on light and dark themes. */
private fun sideDot(color: PieceColor, rim: Color) = mapOf(
    DOT to InlineTextContent(Placeholder(0.9.em, 0.9.em, PlaceholderVerticalAlign.TextCenter)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(if (color == PieceColor.WHITE) Color.White else Color.Black)
                .border(1.dp, rim, CircleShape),
        )
    },
)

/** Vision training: spot what is attacked or loose in a real position, and hold a position in your head. */
@Composable
fun ColumnScope.VisionSection(settings: AppSettings) {
    val puzzles = rememberPuzzleSet()
    var mode by rememberSaveable { mutableStateOf(VisionMode.Attacked) }
    var busy by remember { mutableStateOf(false) }
    if (!busy) Segmented(VisionMode.entries.map { it to it.short }, mode, { mode = it }, Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth())
    when {
        puzzles == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.colors.brand) }
        mode == VisionMode.Recall -> RecallDrill(puzzles, settings) { busy = it }
        else -> SpotDrill(mode, puzzles, settings) { busy = it }
    }
}

/** A position with the question being asked of it. */
private class Spot(val board: ChessBoard, val color: PieceColor, val targets: Set<Square>)

private fun nextSpot(mode: VisionMode, puzzles: List<Puzzle>, random: kotlin.random.Random): Spot {
    repeat(500) {
        val board = ChessBoard.fromFen(puzzles.random(random).fen)
        val color = if (random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
        val targets = if (mode == VisionMode.Hanging) Vision.hanging(board, color) else Vision.attacked(board, color)
        if (if (mode == VisionMode.Hanging) targets.size == 1 else targets.size in 1..4) return Spot(board, color, targets)
    }
    error("No position to ask about")
}

/** Attacked pieces and the hanging piece: sets of ten real positions, each against its own clock. */
@Composable
private fun ColumnScope.SpotDrill(mode: VisionMode, puzzles: List<Puzzle>, settings: AppSettings, setBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val c = Palette.colors
    val random = remember { kotlin.random.Random(System.nanoTime()) }
    val session = remember(mode) { TimedSession(context, "Vision.${mode.name}") { nextSpot(mode, puzzles, random) } }
    session.Drive()
    var picked by remember { mutableStateOf(emptySet<Square>()) }
    // Index into the misses of the set just played, while they are being looked at.
    var review by remember(session) { mutableStateOf<Int?>(null) }
    val on = session.phase == SetPhase.Asking || session.phase == SetPhase.Revealing
    // Tell the screen a set is on or just over, so the mode switch is hidden.
    val away = on || session.phase == SetPhase.Done
    LaunchedEffect(away) { setBusy(away) }
    // A new problem starts with nothing picked.
    LaunchedEffect(session.token) { picked = emptySet() }

    fun question(s: Spot) = when (mode) {
        VisionMode.Hanging -> sideQuestion("Tap the hanging ", s.color, " piece")
        else -> if (s.targets.size == 1) sideQuestion("Tap the ", s.color, " piece under attack")
        else sideQuestion("Tap the ${s.targets.size} ", s.color, " pieces under attack")
    }
    fun answerText(s: Spot) = "${if (mode == VisionMode.Hanging) "Hanging" else "Attacked"}: ${s.targets.joinToString(", ") { it.name }}"
    val misses = session.attempts.filter { !it.right }
    val shown = review?.let { misses.getOrNull(it) }?.problem

    when {
        shown != null -> {
            val i = review ?: 0
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(question(shown), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text, inlineContent = sideDot(shown.color, c.muted))
                    Text("Miss ${i + 1} of ${misses.size}", style = MaterialTheme.typography.titleSmall, color = c.muted)
                }
                ChessboardView(
                    modifier = Modifier.fillMaxWidth(),
                    board = shown.board, colors = settings.boardTheme.colors(), orientation = shown.color,
                    marked = shown.targets, coordinates = settings.coordinates,
                    highlightLastMove = false, showLegalMoves = false, interactive = false,
                )
                RCard(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), color = c.brandCard) {
                    Text(answerText(shown), style = MaterialTheme.typography.titleMedium, color = c.brandCardText, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (i > 0) RButton("Previous", { review = i - 1 }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                if (i < misses.lastIndex) RButton("Next", { review = i + 1 }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                RButton("Done", { review = null }, Modifier.weight(1f))
            }
        }
        session.phase == SetPhase.Idle -> SetupScreen(
            session, mode.title,
            if (mode == VisionMode.Hanging) "Find the piece that is attacked and not defended, in a real position."
            else "Find every piece of one colour that the other side attacks, in a real position.",
            if (mode == VisionMode.Hanging) "A piece hangs when it is attacked and nothing guards it. Each question gives one position; tap the piece that hangs. A wrong answer or an empty clock shows it, then the set moves on."
            else "Tap every piece of the colour named that the other side attacks, whether or not it is guarded. The question says how many; your last tap is your answer.",
        )
        session.phase == SetPhase.Done -> SummaryScreen(
            session,
            { s -> "${side(s.color)} ${if (mode == VisionMode.Hanging) "hanging" else "attacked"}: ${s.targets.joinToString(", ") { it.name }}" },
            onReview = { review = 0 },
        )
        else -> {
            val s = session.current
            val revealing = session.phase == SetPhase.Revealing
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                session.Progress()
                if (s != null) ChessboardView(
                    modifier = Modifier.fillMaxWidth(),
                    board = s.board,
                    colors = settings.boardTheme.colors(),
                    orientation = s.color,
                    // After a miss the board shows the answer.
                    marked = if (revealing) s.targets else picked,
                    coordinates = settings.coordinates,
                    highlightLastMove = false,
                    showLegalMoves = false,
                    interactive = session.phase == SetPhase.Asking,
                    onSquareClick = { sq ->
                        if (session.phase != SetPhase.Asking) return@ChessboardView
                        when (mode) {
                            // The one that hangs: a tap is the answer.
                            VisionMode.Hanging -> session.answer(sq in s.targets)
                            // Several: tap to pick or drop. The prompt says how many, so the last pick is the answer.
                            else -> {
                                val now = if (sq in picked) picked - sq else picked + sq
                                picked = now
                                if (now.size == s.targets.size) session.answer(now == s.targets)
                            }
                        }
                    },
                )
                if (s != null && !revealing) {
                    // The question sits under the board, where the eyes and the thumb already are.
                    Row(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(question(s), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text, inlineContent = sideDot(s.color, c.muted))
                        if (mode != VisionMode.Hanging && s.targets.size > 1) Text("${picked.size} of ${s.targets.size}", style = MaterialTheme.typography.titleMedium, color = c.muted)
                    }
                }
                if (revealing && s != null) {
                    RCard(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), color = c.brandCard) {
                        Text(session.missLine(), style = MaterialTheme.typography.labelLarge, color = c.brandCardText)
                        Text(answerText(s), style = MaterialTheme.typography.titleMedium, color = c.brandCardText, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // After a miss, the next question is one tap away, in the same place as the other drills' buttons.
            if (revealing) RButton("Next", session::next, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp))
        }
    }
}

private enum class Look { Idle, Showing, Placing, Result }

private val PALETTE = listOf(PieceColor.WHITE, PieceColor.BLACK).map { color ->
    listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN).map { Piece(it, color) }
}

/** Look at a position for five seconds, then put the pieces back from memory. */
@Composable
private fun ColumnScope.RecallDrill(puzzles: List<Puzzle>, settings: AppSettings, setBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val c = Palette.colors
    val random = remember { kotlin.random.Random(System.nanoTime()) }
    var count by rememberSaveable { mutableIntStateOf(6) }
    var phase by remember { mutableStateOf(Look.Idle) }
    var shown by remember { mutableStateOf<Map<Square, Piece>>(emptyMap()) }
    var placed by remember { mutableStateOf<Map<Square, Piece>>(emptyMap()) }
    // Null holds the eraser.
    var held by remember { mutableStateOf<Piece?>(Piece(PieceType.PAWN, PieceColor.WHITE)) }
    var left by remember { mutableIntStateOf(LOOK_SECONDS) }
    var percent by remember { mutableIntStateOf(0) }
    var record by remember(count) { mutableIntStateOf(best(context, "Recall$count")) }
    var newRecord by remember { mutableStateOf(false) }

    LaunchedEffect(phase) {
        if (phase != Look.Showing) return@LaunchedEffect
        while (left > 0) { delay(1000); left-- }
        placed = emptyMap(); phase = Look.Placing
    }

    fun start() {
        val board = ChessBoard.fromFen(puzzles.random(random).fen)
        shown = Vision.pick(board, count, random)
        left = LOOK_SECONDS; newRecord = false; phase = Look.Showing; setBusy(true)
    }

    fun check() {
        val right = shown.count { (sq, p) -> placed[sq] == p }
        val extra = placed.count { (sq, p) -> shown[sq] != p }
        percent = (maxOf(0, right - extra) * 100) / count
        if (percent > record) { record = percent; newRecord = true; saveBest(context, "Recall$count", percent) }
        phase = Look.Result; setBusy(false)
    }

    // Before a look there is nothing to see: the options, and Start at the foot.
    if (phase == Look.Idle) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Recall", style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.Bold)
            Text("Look at a real position for $LOOK_SECONDS seconds, then put the pieces back from memory.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            RCard(Modifier.fillMaxWidth()) {
                Text("How many pieces", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Spacer(Modifier.height(8.dp))
                Segmented(listOf(4, 6, 8, 10).map { it to "$it" }, count, { count = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(if (record > 0) "Best for $count pieces: $record%" else "No score yet for $count pieces.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        RButton("Start", ::start, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp))
        return
    }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                when (phase) { Look.Showing -> "Remember this"; Look.Placing -> "Put the pieces back"; else -> "Recall" },
                style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold,
            )
            if (phase == Look.Placing) Text("Pick a piece below, then tap squares. The eraser takes one off.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (phase == Look.Showing) Text("${left}s", style = MaterialTheme.typography.titleMedium, color = c.text)
    }
    ChessboardView(
        modifier = Modifier.fillMaxWidth(),
        board = when (phase) {
            Look.Showing, Look.Result -> Vision.boardOf(shown)
            Look.Placing -> Vision.boardOf(placed)
            Look.Idle -> Vision.boardOf(emptyMap())
        },
        colors = settings.boardTheme.colors(),
        coordinates = settings.coordinates,
        highlightLastMove = false,
        showLegalMoves = false,
        interactive = phase == Look.Placing,
        onSquareClick = { sq ->
            if (phase == Look.Placing) placed = held?.let { placed + (sq to it) } ?: (placed - sq)
        },
    )
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (phase) {
            Look.Placing -> {
                PALETTE.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { p ->
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEEED2))
                                    .then(if (held == p) Modifier.border(3.dp, c.brand, RoundedCornerShape(10.dp)) else Modifier)
                                    .clickable { held = p },
                                contentAlignment = Alignment.Center,
                            ) { Image(painterResource(pieceDrawableRes(p)), p.type.name.lowercase(), Modifier.fillMaxSize(0.85f)) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RButton("Eraser", { held = null }, Modifier.weight(1f), style = if (held == null) ButtonStyle.Primary else ButtonStyle.Tonal)
                    RButton("Check", ::check, Modifier.weight(1f))
                }
            }
            Look.Result -> {
                RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
                    Text("$percent% of the position", style = MaterialTheme.typography.titleMedium, color = c.brandCardText)
                    Text(if (newRecord) "A new best for $count pieces." else "Best for $count pieces: $record%", style = MaterialTheme.typography.bodySmall, color = c.brandCardText)
                }
                Text("The board shows what was there.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                RButton("Again", ::start, Modifier.fillMaxWidth())
                RButton("Change pieces", { phase = Look.Idle }, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal)
            }
            Look.Idle -> Unit
            Look.Showing -> Unit
        }
        Spacer(Modifier.height(12.dp))
    }
    }
}
