package wrklabs.chess.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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

/** Vision training: spot what is attacked or loose in a real position, and hold a position in your head. */
@Composable
fun ColumnScope.VisionSection(settings: AppSettings) {
    val puzzles = rememberPuzzleSet()
    var mode by rememberSaveable { mutableStateOf(VisionMode.Attacked) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!busy) Segmented(VisionMode.entries.map { it to it.short }, mode, { mode = it }, Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth())
        when {
            puzzles == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.colors.brand) }
            mode == VisionMode.Recall -> RecallDrill(puzzles, settings) { busy = it }
            else -> SpotDrill(mode, puzzles, settings) { busy = it }
        }
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
private fun SpotDrill(mode: VisionMode, puzzles: List<Puzzle>, settings: AppSettings, setBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val c = Palette.colors
    val random = remember { kotlin.random.Random(System.nanoTime()) }
    val session = remember(mode) { TimedSession(context, "Vision.${mode.name}") { nextSpot(mode, puzzles, random) } }
    session.Drive()
    var picked by remember { mutableStateOf(emptySet<Square>()) }
    // Tell the screen a set is on, so the mode switch is hidden.
    val on = session.phase == SetPhase.Asking || session.phase == SetPhase.Revealing
    LaunchedEffect(on) { setBusy(on) }
    // A new problem starts with nothing picked.
    LaunchedEffect(session.token) { picked = emptySet() }

    val s = session.current
    val revealing = session.phase == SetPhase.Revealing
    fun question(s: Spot) = when (mode) {
        VisionMode.Hanging -> "Tap the hanging ${side(s.color)} piece"
        else -> "Tap all ${s.targets.size} attacked ${side(s.color)} piece${if (s.targets.size == 1) "" else "s"}"
    }
    if (on && s != null) session.Status(question(s))
    else Text(mode.title, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    ChessboardView(
        modifier = Modifier.fillMaxWidth(),
        board = if (on && s != null) s.board else ChessBoard.fromFen(ChessBoard.DEFAULT_FEN),
        colors = settings.boardTheme.colors(),
        orientation = if (on && s != null) s.color else PieceColor.WHITE,
        // After a miss the board shows the answer.
        marked = if (revealing && s != null) s.targets else picked,
        coordinates = settings.coordinates,
        highlightLastMove = false,
        showLegalMoves = false,
        interactive = session.phase == SetPhase.Asking,
        onSquareClick = { sq ->
            if (session.phase != SetPhase.Asking || s == null) return@ChessboardView
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
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (revealing && s != null) {
            RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
                Text(session.missLine(), style = MaterialTheme.typography.labelLarge, color = c.brandCardText)
                Text(
                    "${if (mode == VisionMode.Hanging) "Hanging" else "Attacked"}: ${s.targets.joinToString(", ") { it.name }}",
                    style = MaterialTheme.typography.titleMedium, color = c.brandCardText, fontWeight = FontWeight.Bold,
                )
                RButton("Next", session::next, Modifier.fillMaxWidth().padding(top = 8.dp), style = ButtonStyle.OnCard)
            }
        }
        if (!on) {
            if (session.phase == SetPhase.Done) session.ResultCard()
            Text(
                if (mode == VisionMode.Hanging) "A piece hangs when it is attacked and nothing guards it. Find the one in each position."
                else "Tap every piece of the colour named that the other side attacks, whether or not it is guarded. Your last tap is your answer.",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            session.Goal()
            RButton(if (session.phase == SetPhase.Done) "Another set" else "Start", session::start, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
    }
}

private enum class Look { Idle, Showing, Placing, Result }

private val PALETTE = listOf(PieceColor.WHITE, PieceColor.BLACK).map { color ->
    listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN).map { Piece(it, color) }
}

/** Look at a position for five seconds, then put the pieces back from memory. */
@Composable
private fun RecallDrill(puzzles: List<Puzzle>, settings: AppSettings, setBusy: (Boolean) -> Unit) {
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
            Look.Idle -> ChessBoard.fromFen("8/8/8/8/8/8/8/8 w - - 0 1")
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
            Look.Idle -> {
                Segmented(listOf(4, 6, 8, 10).map { it to "$it pieces" }, count, { count = it }, Modifier.fillMaxWidth())
                Text("Best for $count pieces: $record% · $LOOK_SECONDS seconds to look", style = MaterialTheme.typography.bodySmall, color = c.muted)
                RButton("Start", ::start, Modifier.fillMaxWidth())
            }
            Look.Showing -> Unit
        }
        Spacer(Modifier.height(12.dp))
    }
}
