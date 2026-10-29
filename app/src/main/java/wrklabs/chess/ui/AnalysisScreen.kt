package wrklabs.chess.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalContext
import wrklabs.chess.Graph
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.ui.theme.Mono
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import wrklabs.chess.engine.format
import androidx.compose.ui.unit.sp
import wrklabs.chess.engine.EngineLine
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.ROOT_ID
import wrklabs.chess.model.Square
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Palette

/** The analysis board: move the pieces, see what the engine thinks. Reviewing a game is the review screen. */
@Composable
fun AnalysisScreen(vm: AnalysisViewModel, settings: AppSettings) {
    val s by vm.state.collectAsState()
    val engineStatus by vm.engineStatus.collectAsState()
    var loadPgn by remember { mutableStateOf(false) }
    var loadFen by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(settings.analysisEngine, settings.threatArrows, settings.moveFeedback) { vm.restartEngine() }
    val c = Palette.colors
    val info = s.info
    val board = remember(s.currentNode.fen) { s.board }
    val legalMoves = remember(board, s.selectedSquare) { s.legalMoves }
    val grade = if (settings.moveFeedback && settings.analysisEngine) s.grades[s.currentNodeId] else null
    val bottomColor = s.orientation
    val topColor = bottomColor.opponent()
    fun name(color: PieceColor) = info?.let { if (color == PieceColor.WHITE) it.white else it.black } ?: if (color == PieceColor.WHITE) "White" else "Black"
    fun rating(color: PieceColor) = info?.let { if (color == PieceColor.WHITE) it.whiteRating else it.blackRating }
    fun score(color: PieceColor) = scoreOf(color, s.result)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showBar = settings.analysisEngine && settings.showEvalBar
        val boardSize = maxWidth
        Column(Modifier.fillMaxSize()) {
            PageHeader("Analyze", subtitle = info?.let { "${it.white} vs ${it.black}" })
            PlayerBar(name(topColor), rating(topColor), topColor, null, score = score(topColor), material = if (settings.showMaterial) materialDiff(board, topColor) else null)
            Box(Modifier.fillMaxWidth().height(boardSize)) {
                ChessboardView(
                    modifier = Modifier.size(boardSize),
                    board = board,
                    colors = settings.boardTheme.colors(),
                    orientation = s.orientation,
                    lastMove = s.lastMove,
                    selectedSquare = s.selectedSquare,
                    legalMoves = legalMoves,
                    arrows = (if (settings.showArrows) arrowsFor(s.lines) else emptyList()) + (if (settings.threatArrows && settings.analysisEngine) threatArrow(s.threat) else emptyList()),
                    badge = grade?.let { cls -> s.lastMove?.let { it.second to cls } },
                    mated = remember(board, s.matedColor) { s.matedColor?.let { mc -> (0 until 64).map { Square.fromIndex(it) }.firstOrNull { sq -> board.getPiece(sq)?.let { it.type == PieceType.KING && it.color == mc } == true } } },
                    coordinates = settings.coordinates,
                    highlightLastMove = settings.highlightLastMove,
                    showLegalMoves = settings.showLegalMoves,
                    onSquareClick = vm::onSquareClick,
                    onMove = vm::onDrag,
                )
            }
            if (showBar) EvalStrip(s.score, s.orientation)
            PlayerBar(name(bottomColor), rating(bottomColor), bottomColor, null, score = score(bottomColor), material = if (settings.showMaterial) materialDiff(board, bottomColor) else null)

            MoveStrip(s.line, s.currentNodeId, null, vm::select, extraGrades = if (settings.moveFeedback && settings.analysisEngine) s.grades else emptyMap(), alternatives = { n -> n.parentId?.let { s.tree.nodes[it] }?.children.orEmpty().filter { it != n.id }.mapNotNull { s.tree.nodes[it] } })
            Spacer(Modifier.weight(1f))
            // The engine's two best lines: a tap anywhere on a row plays its first move.
            if (settings.analysisEngine) EngineRows(s.lines, s.currentNode.fen, engineStatus, why = if (s.whyFrom != null) (s.whyText ?: "Following the opponent's best reply…") else if (s.thinking) "The engine is replying…" else null) { uci -> vm.playLine(listOf(uci)) }
            else Text("The engine is off. Turn it on in Analysis options.", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

            Hairline()
            AnalysisToolbar(vm, onOptions = { options = true }, onLoadPgn = { loadPgn = true }, onLoadFen = { loadFen = true }, onCopyFen = { copyText(context, "FEN", vm.fen()) }, onCopyPgn = { copyText(context, "PGN", vm.pgn()) })
        }
    }

    if (options) BoardOptions(settings, onDismiss = { options = false })
    if (loadFen) LoadFenDialog(onDismiss = { loadFen = false }, onLoad = { text -> vm.openFen(text).also { if (it) loadFen = false } })
    if (loadPgn) LoadPgnDialog(onDismiss = { loadPgn = false }, onLoad = { text -> vm.openPgn(text).also { if (it) loadPgn = false } })
}

private fun threatArrow(uci: String?): List<EngineArrow> =
    if (uci == null || uci.length < 4) emptyList() else listOf(EngineArrow(Square.fromName(uci.substring(0, 2)), Square.fromName(uci.substring(2, 4)), color = Color(0xFFE5484D)))

private fun arrowsFor(lines: List<EngineLine>): List<EngineArrow> {
    val first = lines.firstOrNull()?.pv?.firstOrNull()?.takeIf { it.length >= 4 } ?: return emptyList()
    return listOf(EngineArrow(Square.fromName(first.substring(0, 2)), Square.fromName(first.substring(2, 4))))
}

@Composable
private fun LoadPgnDialog(onDismiss: () -> Unit, onLoad: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val c = Palette.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.cardHigh,
        title = { Text("Load a game", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Paste the game's PGN.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it; failed = false }, Modifier.fillMaxWidth().height(180.dp), isError = failed, placeholder = { Text("1. e4 e5 2. Nf3 …") })
                if (failed) Text("No moves found in that.", color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton({ failed = !onLoad(text) }, enabled = text.isNotBlank()) { Text("Load") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private fun copyText(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardOptions(settings: AppSettings, onDismiss: () -> Unit) {
    val c = Palette.colors
    val store = Graph.settings
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.cardHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Analysis options", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(bottom = 6.dp))
            Toggle("Engine", settings.analysisEngine) { v -> store.update { copy(analysisEngine = v) } }
            Toggle("Best-move arrow", settings.showArrows) { v -> store.update { copy(showArrows = v) } }
            Toggle("Threat arrows", settings.threatArrows) { v -> store.update { copy(threatArrows = v) } }
            Toggle("Move feedback", settings.moveFeedback) { v -> store.update { copy(moveFeedback = v) } }
            Toggle("Evaluation bar", settings.showEvalBar) { v -> store.update { copy(showEvalBar = v) } }
            Toggle("Opponent replies", settings.opponentReply) { v -> store.update { copy(opponentReply = v) } }
        }
    }
}

@Composable
private fun LoadFenDialog(onDismiss: () -> Unit, onLoad: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val c = Palette.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.cardHigh,
        title = { Text("Set up a position", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Paste the position's FEN.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it; failed = false }, Modifier.fillMaxWidth(), isError = failed, placeholder = { Text("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") })
                if (failed) Text("That isn't a position the board can hold.", color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton({ failed = !onLoad(text) }, enabled = text.isNotBlank()) { Text("Set up") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** The engine's best two lines, each a row that plays its first move when tapped. */
@Composable
private fun EngineRows(lines: List<EngineLine>, fen: String, status: wrklabs.chess.engine.EngineStatus, why: String?, onPlay: (String) -> Unit) {
    val c = Palette.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (status is wrklabs.chess.engine.EngineStatus.Error) {
            Box(Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(12.dp)).background(c.danger.copy(alpha = 0.16f)).padding(12.dp)) {
                Text(status.message, color = c.danger, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }
        for (i in 0 until AnalysisViewModel.LINES) {
            val line = lines.getOrNull(i)
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.card)
                    .then(if (why == null && line?.pv?.firstOrNull() != null) Modifier.clickable { onPlay(line.pv.first()) } else Modifier)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (why != null) {
                    // While "Show why" plays its line the engine is paused: its words take the first row.
                    if (i == 0) Text(why, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else if (line == null) {
                    Text(if (i == 0) (if (status is wrklabs.chess.engine.EngineStatus.Ready) "Thinking…" else "Starting the engine…") else "", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                } else {
                    Text(
                        line.score.format(), fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        modifier = Modifier.width(58.dp).clip(RoundedCornerShape(7.dp)).background(c.field).padding(vertical = 4.dp),
                        color = c.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    val moves = remember(fen, line.pv) { pvToMoves(fen, line.pv, 6) }
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            moves.forEachIndexed { j, m ->
                                if (j > 0) append("  ")
                                if (m.number != null) withStyle(androidx.compose.ui.text.SpanStyle(color = c.muted)) { append(m.number + " ") }
                                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = if (j == 0) FontWeight.Bold else FontWeight.Normal)) { append(m.san) }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The same bar as on the review screen: the menu, flip, back and forward (a long press on either goes to the start or the end). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AnalysisToolbar(vm: AnalysisViewModel, onOptions: () -> Unit, onLoadPgn: () -> Unit, onLoadFen: () -> Unit, onCopyFen: () -> Unit, onCopyPgn: () -> Unit) {
    val c = Palette.colors
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconTap(Icons.Default.Menu, "Menu", { menu = true }, size = 48)
            DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                DropdownMenuItem(text = { Text("Analysis options…") }, leadingIcon = { Icon(Icons.Default.Tune, null) }, onClick = { menu = false; onOptions() })
                DropdownMenuItem(text = { Text("Load PGN…") }, leadingIcon = { Icon(Icons.Default.UploadFile, null) }, onClick = { menu = false; onLoadPgn() })
                DropdownMenuItem(text = { Text("Set up from FEN…") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onLoadFen() })
                DropdownMenuItem(text = { Text("Copy FEN") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onCopyFen() })
                DropdownMenuItem(text = { Text("Copy PGN") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onCopyPgn() })
                DropdownMenuItem(text = { Text("New board") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; vm.reset() })
            }
        }
        IconTap(Icons.Default.SwapVert, "Flip board", vm::flip, size = 48)
        Box(
            Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).combinedClickable(onClick = vm::previous, onLongClick = vm::first),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous move", tint = c.text, modifier = Modifier.size(28.dp)) }
        Box(
            Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).combinedClickable(onClick = vm::next, onLongClick = vm::last),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next move", tint = c.text, modifier = Modifier.size(28.dp)) }
    }
}
