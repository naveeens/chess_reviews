package wrklabs.chess.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wrklabs.chess.db.Puzzle
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Square
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Palette

/** One line to replay: where it starts, and each move in order with its notation. */
private class Line(val start: ChessBoard, val moves: List<Pair<String, String>>) {
    /** The line in the notation it is read in, e.g. "12... Nxe5  13. Qd5". */
    fun text(from: Int = 0): String {
        val first = start.fullmoveNumber
        var white = start.turn == PieceColor.WHITE
        var number = first
        val out = StringBuilder()
        moves.forEachIndexed { i, (_, san) ->
            if (i >= from) {
                if (white) out.append("$number. ") else if (i == from || i == 0) out.append("$number... ")
                out.append(san).append("  ")
            }
            if (!white) number++
            white = !white
        }
        return out.toString().trim()
    }
}

private fun lineOf(p: Puzzle): Line? {
    var board = ChessBoard.fromFen(p.fen)
    val start = board
    val moves = p.moves.map { uci ->
        val m = board.legalMoves().firstOrNull { it.uci == uci } ?: return null
        board = board.makeMove(m)
        uci to m.san
    }
    return Line(start, moves)
}

/** Read a line in notation and play it on the board, move by move, both sides. */
@Composable
fun ColumnScope.NotationSection(settings: AppSettings) {
    val puzzles = rememberPuzzleSet()
    val c = Palette.colors
    if (puzzles == null) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.brand) }
        return
    }
    val random = remember { kotlin.random.Random(System.nanoTime()) }
    fun nextLine(): Line {
        repeat(200) { puzzles.random(random).takeIf { it.moves.size in 3..6 }?.let(::lineOf)?.let { return it } }
        error("No line to play")
    }
    var line by remember { mutableStateOf(nextLine()) }
    var ply by remember { mutableIntStateOf(0) }
    var board by remember { mutableStateOf(line.start) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var slips by remember { mutableIntStateOf(0) }
    var wasRight by remember { mutableStateOf<Boolean?>(null) }
    var clean by remember { mutableIntStateOf(0) }
    var played by remember { mutableIntStateOf(0) }
    val finished = ply >= line.moves.size

    fun reset(next: Line) { line = next; board = next.start; ply = 0; selected = null; slips = 0; wasRight = null }
    fun attempt(from: Square, to: Square) {
        selected = null
        // Read the live state: the board can hand back an older copy of this function.
        if (ply >= line.moves.size) return
        val expected = line.moves[ply].first
        if (expected.startsWith("${from.name}${to.name}")) {
            board = board.makeMove(board.legalMoves().first { it.uci == expected })
            ply++
            wasRight = true
            if (ply == line.moves.size) { played++; if (slips == 0) clean++ }
        } else {
            slips++
            wasRight = false
        }
    }
    fun click(sq: Square) {
        if (ply >= line.moves.size) return
        val from = selected
        val mine = board.getPiece(sq)?.color == board.turn
        when {
            from == null -> if (mine) selected = sq
            sq == from -> selected = null
            mine -> selected = sq
            else -> attempt(from, sq)
        }
    }

    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Play the line", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        finished -> if (slips == 0) "Clean. No slips." else "Done, with $slips slip${if (slips == 1) "" else "s"}."
                        wasRight == false -> "Not that one."
                        else -> "Move ${ply + 1} of ${line.moves.size}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when { finished && slips == 0 -> c.win; wasRight == false -> c.danger; else -> c.muted },
                )
            }
            Text("$clean clean of $played", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        ChessboardView(
            modifier = Modifier.fillMaxWidth(),
            board = board,
            colors = settings.boardTheme.colors(),
            orientation = line.start.turn,
            selectedSquare = selected,
            legalMoves = if (selected != null) board.legalMoves().filter { it.from == selected } else emptyList(),
            coordinates = settings.coordinates,
            showLegalMoves = settings.showLegalMoves,
            highlightLastMove = false,
            interactive = !finished,
            onSquareClick = ::click,
            onMove = { from, to -> attempt(from, to) },
        )
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RCard(Modifier.fillMaxWidth()) {
                Text(line.text(), style = MaterialTheme.typography.bodyLarge, color = c.text)
                if (!finished) Text("Next: ${line.moves[ply].second}", style = MaterialTheme.typography.titleMedium, color = c.brand, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    // The way on, pinned at the bottom: Next line once the line is played, Skip while it is.
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)) {
        if (finished) RButton("Next line", { reset(nextLine()) }, Modifier.fillMaxWidth())
        else RButton("Skip", { reset(nextLine()) }, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal)
    }
}
