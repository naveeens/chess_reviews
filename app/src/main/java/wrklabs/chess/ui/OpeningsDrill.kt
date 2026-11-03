package wrklabs.chess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.MoveNode
import wrklabs.chess.model.ROOT_ID
import wrklabs.chess.model.Square
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.training.LessonEntry
import kotlinx.coroutines.launch
import wrklabs.chess.Graph
import wrklabs.chess.db.DueLine
import wrklabs.chess.training.LessonLibrary
import wrklabs.chess.training.lineCardId
import wrklabs.chess.training.parseLineCardId
import wrklabs.chess.training.LessonPractice
import wrklabs.chess.training.PracticeResult
import wrklabs.chess.training.label
import wrklabs.chess.training.lineTo
import wrklabs.chess.training.signs
import wrklabs.chess.ui.theme.Palette

/** Opening lessons: pick a chapter, then walk its tree of moves with the notes beside the board. */
@Composable
fun ColumnScope.OpeningsSection(settings: AppSettings) {
    val context = LocalContext.current
    val lessons by produceState<List<LessonEntry>?>(null) { value = LessonLibrary.load(context) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    val entry = lessons?.firstOrNull { it.key == open }
    // The positions you got wrong that are due again; read again whenever you come back to the list.
    val due by produceState<List<DueLine>?>(null, open, reviewing) { value = if (open == null && !reviewing) Graph.cards.getDueLines(limit = 50) else value }
    BackHandler(enabled = entry != null || reviewing) { open = null; reviewing = false }
    when {
        lessons == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.colors.brand) }
        reviewing -> LineReview(lessons.orEmpty(), due.orEmpty(), settings) { reviewing = false }
        entry != null -> LessonViewer(entry, settings)
        else -> LessonList(lessons.orEmpty(), due.orEmpty().count { parseLineCardId(it.id) != null }, { open = it.key }, { reviewing = true })
    }
}

@Composable
private fun LessonList(lessons: List<LessonEntry>, dueCount: Int, onOpen: (LessonEntry) -> Unit, onReview: () -> Unit) {
    val c = Palette.colors
    if (lessons.isEmpty()) {
        Box(Modifier.padding(14.dp)) { EmptyState("No lessons yet", "Opening lessons will appear here.") }
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dueCount > 0) RCard(Modifier.fillMaxWidth(), color = c.brandCard, onClick = onReview) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Review", style = MaterialTheme.typography.titleMedium, color = c.brandCardText)
                    Text("Positions you missed in practice.", style = MaterialTheme.typography.bodySmall, color = c.brandCardText)
                }
                Text("$dueCount due", style = MaterialTheme.typography.titleSmall, color = c.brandCardText, fontWeight = FontWeight.Bold)
            }
        }
        lessons.forEach { l ->
            RCard(Modifier.fillMaxWidth(), onClick = { onOpen(l) }) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(if (l.side == PieceColor.WHITE) "You play White" else "You play Black", l.eco, "${l.moves} moves", "${l.lines} lines").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
        }
    }
}

/** A lichess-style arrow or square, "Gc4f7" or "Rf7": the letter is the colour. */
private fun colourOf(letter: Char, c: wrklabs.chess.ui.theme.AppColors): Color = when (letter) {
    'G' -> c.win
    'R' -> c.danger
    'Y' -> Color(0xFFE0B13C)
    'B' -> c.brand
    else -> c.win
}

private fun square(name: String): Square? = runCatching { Square.fromName(name) }.getOrNull()

private fun arrowOf(spec: String, c: wrklabs.chess.ui.theme.AppColors): EngineArrow? {
    if (spec.length != 5) return null
    val from = square(spec.substring(1, 3)) ?: return null
    val to = square(spec.substring(3, 5)) ?: return null
    return if (from == to) null else EngineArrow(from, to, colourOf(spec[0], c))
}

private enum class LessonMode { Learn, Practise }

@Composable
private fun LessonViewer(entry: LessonEntry, settings: AppSettings) {
    var mode by rememberSaveable(entry.key) { mutableStateOf(LessonMode.Learn) }
    Column(Modifier.fillMaxWidth()) {
        Segmented(LessonMode.entries.map { it to it.name }, mode, { mode = it }, Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth())
        if (mode == LessonMode.Learn) LearnBody(entry, settings) else PractiseBody(entry, settings)
    }
}

@Composable
private fun LearnBody(entry: LessonEntry, settings: AppSettings) {
    val c = Palette.colors
    val tree = entry.chapter.tree
    var nodeId by rememberSaveable(entry.key) { mutableStateOf(ROOT_ID) }
    val node = tree.nodes[nodeId] ?: tree.nodes.getValue(ROOT_ID)
    val parent = node.parentId?.let { tree.nodes[it] }
    val board = remember(node.fen) { ChessBoard.fromFen(node.fen) }
    val children = node.children.mapNotNull { tree.nodes[it] }
    val go = { id: String -> nodeId = id }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChessboardView(
            modifier = Modifier.fillMaxWidth(),
            board = board,
            colors = settings.boardTheme.colors(),
            orientation = entry.side,
            lastMove = node.from?.let { f -> node.to?.let { t -> square(f)?.let { a -> square(t)?.let { b -> a to b } } } },
            arrows = node.arrows.mapNotNull { arrowOf(it, c) },
            marked = node.marks.mapNotNull { square(it.drop(1)) }.toSet(),
            coordinates = settings.coordinates,
            interactive = false,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconTap(Icons.Default.Refresh, "Start of the lesson", { go(ROOT_ID) }, enabled = node.id != ROOT_ID)
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back one move", { parent?.let { go(it.id) } }, enabled = parent != null)
            IconTap(Icons.AutoMirrored.Filled.ArrowForward, "Next move", { children.firstOrNull()?.let { go(it.id) } }, enabled = children.isNotEmpty())
            Spacer(Modifier.weight(1f))
            Text(if (node.id == ROOT_ID) "Start" else node.label() + node.signs().let { if (it.isEmpty()) "" else " $it" }, style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            node.comment?.takeIf { it.isNotBlank() }?.let { RCard(Modifier.fillMaxWidth(), color = c.brandCard) { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.brandCardText) } }
            if (children.isEmpty()) {
                Text("End of this line.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                RButton("Back to the start", { go(ROOT_ID) }, style = ButtonStyle.Tonal, compact = true)
            } else {
                Text(if (children.size > 1) "Choose a move" else "Next", style = MaterialTheme.typography.labelLarge, color = c.muted)
                children.forEachIndexed { i, child -> ChoiceRow(child, main = i == 0) { go(child.id) } }
            }
            if (node.id != ROOT_ID) Text(tree.lineTo(node.id), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChoiceRow(child: MoveNode, main: Boolean, onClick: () -> Unit) {
    val c = Palette.colors
    RCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(child.label() + child.signs().let { if (it.isEmpty()) "" else " $it" }, style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (main) "Main line" else "Alternative", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

/**
 * The state of one practice sitting. The board's tap handlers outlive recompositions, so they call into this object,
 * which always reads the current position, rather than into a function that closed over an old one.
 */
private class PracticeSession(private val tree: wrklabs.chess.model.GameTree, side: PieceColor, private val onResult: (nodeId: String, solved: Boolean) -> Unit) {
    val practice = LessonPractice(tree, side)
    var state by mutableStateOf(practice.start()); private set
    var selected by mutableStateOf<Square?>(null); private set
    var wrong by mutableStateOf<MoveNode?>(null); private set
    var hint by mutableStateOf(false)

    val node: MoveNode get() = tree.nodes.getValue(state.nodeId)
    val board: ChessBoard get() = ChessBoard.fromFen(node.fen)
    val legal get() = board.legalMoves()
    val expected: MoveNode? get() = if (state.finished) null else practice.expected(state)
    val showAnswer: Boolean get() = hint || state.triesHere >= 2

    fun attempt(from: Square, to: Square) {
        if (state.finished || legal.none { it.from == from && it.to == to }) return
        selected = null
        val here = state.nodeId
        val firstTry = state.triesHere == 0
        when (val r = practice.play(state, from.name, to.name)) {
            is PracticeResult.Correct -> { state = r.state; wrong = null; hint = false; if (firstTry) onResult(here, true) }
            is PracticeResult.Wrong -> { state = r.state; wrong = r.expected; if (firstTry) onResult(here, false) }
        }
    }

    fun click(sq: Square) {
        val from = selected
        val b = board
        if (from != null && legal.any { it.from == from && it.to == sq }) attempt(from, sq)
        else selected = if (!state.finished && b.getPiece(sq)?.color == b.turn) sq else null
    }

    fun another() { state = practice.start(state); wrong = null; hint = false; selected = null }
}

/** You play your side of the lesson; the app plays the other, steering you down branches you have not tried. */
@Composable
private fun PractiseBody(entry: LessonEntry, settings: AppSettings) {
    val c = Palette.colors
    val scope = rememberCoroutineScope()
    val session = remember(entry.key) { PracticeSession(entry.chapter.tree, entry.side) { nodeId, ok -> scope.launch { Graph.cards.recordLine(lineCardId(entry.key, nodeId), ok) } } }
    val state = session.state
    val node = session.node
    val board = session.board
    val wrong = session.wrong
    val expected = session.expected
    val showAnswer = session.showAnswer
    val selected = session.selected
    val legalFromSelected = remember(node.fen, selected) { selected?.let { s -> session.legal.filter { it.from == s } }.orEmpty() }
    val arrows = buildList {
        node.arrows.mapNotNull { arrowOf(it, c) }.let(::addAll)
        if (showAnswer && expected?.from != null && expected.to != null) {
            val a = square(expected.from!!); val b = square(expected.to!!)
            if (a != null && b != null) add(EngineArrow(a, b, c.win))
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChessboardView(
            modifier = Modifier.fillMaxWidth(),
            board = board,
            colors = settings.boardTheme.colors(),
            orientation = entry.side,
            lastMove = node.from?.let { f -> node.to?.let { t -> square(f)?.let { a -> square(t)?.let { b -> a to b } } } },
            selectedSquare = selected,
            legalMoves = legalFromSelected,
            arrows = arrows,
            coordinates = settings.coordinates,
            showLegalMoves = settings.showLegalMoves,
            onSquareClick = session::click,
            onMove = session::attempt,
        )
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                state.finished -> RCard(Modifier.fillMaxWidth(), color = c.win.copy(alpha = 0.16f)) {
                    Text("End of the line.", style = MaterialTheme.typography.titleMedium, color = c.win)
                    Text(if (state.mistakes == 0) "No mistakes." else "${state.mistakes} wrong ${if (state.mistakes == 1) "move" else "moves"}.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Spacer(Modifier.height(10.dp))
                    RButton("Another line", session::another, icon = Icons.AutoMirrored.Filled.ArrowForward)
                }
                wrong != null -> RCard(Modifier.fillMaxWidth(), color = c.danger.copy(alpha = 0.16f)) {
                    Text("Not the lesson move.", style = MaterialTheme.typography.titleMedium, color = c.danger)
                    Text(if (showAnswer) "The lesson plays ${expected?.label()}." else "Try again, or ask for a hint.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                else -> Text(if (state.played.isEmpty()) "Play your move." else "Your move.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            // What the lesson says about the move you just played and the reply to it.
            if (wrong == null) state.played.filter { !it.comment.isNullOrBlank() }.forEach { m ->
                RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
                    Text(m.label() + m.signs().let { if (it.isEmpty()) "" else " $it" }, style = MaterialTheme.typography.labelLarge, color = c.brandCardText, fontWeight = FontWeight.Bold)
                    Text(m.comment.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = c.brandCardText)
                }
            }
            if (!state.finished && !showAnswer) RButton("Hint", { session.hint = true }, style = ButtonStyle.Tonal, compact = true)
            Text("${state.mistakes} wrong so far", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.height(16.dp))
        }
    }
}

private class ReviewItem(val cardId: String, val entry: LessonEntry, val nodeId: String)

private enum class ReviewVerdict { Playing, Right, Wrong }

/** Goes through the positions that are due, one at a time. Reads its position afresh on every call, like [PracticeSession]. */
private class ReviewSession(val items: List<ReviewItem>, private val record: (cardId: String, solved: Boolean) -> Unit) {
    var index by mutableStateOf(0); private set
    var selected by mutableStateOf<Square?>(null); private set
    var verdict by mutableStateOf(ReviewVerdict.Playing); private set
    var answered by mutableStateOf<MoveNode?>(null); private set
    var firstTryRight by mutableStateOf(0); private set
    private var tries = 0

    val item: ReviewItem? get() = items.getOrNull(index)
    val node: MoveNode? get() = item?.let { it.entry.chapter.tree.nodes.getValue(it.nodeId) }
    val board: ChessBoard? get() = node?.let { ChessBoard.fromFen(it.fen) }
    val legal get() = board?.legalMoves().orEmpty()
    val expected: MoveNode? get() = item?.let { i -> node?.children?.firstOrNull()?.let { i.entry.chapter.tree.nodes[it] } }

    fun attempt(from: Square, to: Square) {
        val i = item ?: return
        if (verdict == ReviewVerdict.Right || legal.none { it.from == from && it.to == to }) return
        selected = null
        val tree = i.entry.chapter.tree
        val match = node?.children?.mapNotNull { tree.nodes[it] }?.firstOrNull { it.from == from.name && it.to == to.name }
        if (match != null) {
            if (tries == 0) { record(i.cardId, true); firstTryRight++ }
            answered = match; verdict = ReviewVerdict.Right
        } else {
            if (tries == 0) record(i.cardId, false)
            tries++; verdict = ReviewVerdict.Wrong
        }
    }

    fun click(sq: Square) {
        val from = selected
        val b = board ?: return
        if (from != null && legal.any { it.from == from && it.to == sq }) attempt(from, sq)
        else selected = if (verdict != ReviewVerdict.Right && b.getPiece(sq)?.color == b.turn) sq else null
    }

    fun retry() { verdict = ReviewVerdict.Playing }
    fun next() { index++; verdict = ReviewVerdict.Playing; answered = null; selected = null; tries = 0 }
}

/** The positions you missed in practice, as one-move questions, until they stick. */
@Composable
private fun LineReview(lessons: List<LessonEntry>, due: List<DueLine>, settings: AppSettings, onDone: () -> Unit) {
    val c = Palette.colors
    val scope = rememberCoroutineScope()
    val session = remember {
        val items = due.mapNotNull { d ->
            val (key, nodeId) = parseLineCardId(d.id) ?: return@mapNotNull null
            val entry = lessons.firstOrNull { it.key == key } ?: return@mapNotNull null
            val node = entry.chapter.tree.nodes[nodeId] ?: return@mapNotNull null
            if (node.children.isEmpty()) null else ReviewItem(d.id, entry, nodeId)
        }
        ReviewSession(items) { id, ok -> scope.launch { Graph.cards.recordLine(id, ok) } }
    }
    val item = session.item
    if (item == null) {
        Box(Modifier.padding(14.dp)) {
            EmptyState(
                if (session.items.isEmpty()) "Nothing due" else "Done",
                if (session.items.isEmpty()) "Positions you miss in practice will come back here." else "${session.firstTryRight} of ${session.items.size} right first time.",
                action = { RButton("Back to lessons", onDone, style = ButtonStyle.OnCard) },
            )
        }
        return
    }
    val node = session.node!!
    val tree = item.entry.chapter.tree
    // Once answered right, the board shows the move played.
    val shown = session.answered ?: node
    val board = if (session.answered != null) ChessBoard.fromFen(shown.fen) else session.board!!
    val selected = session.selected
    val legalFromSelected = remember(node.fen, selected) { selected?.let { s -> session.legal.filter { it.from == s } }.orEmpty() }
    val expected = session.expected
    val arrows = if (session.verdict == ReviewVerdict.Wrong && expected?.from != null && expected.to != null) {
        listOfNotNull(square(expected.from!!)?.let { a -> square(expected.to!!)?.let { b -> EngineArrow(a, b, c.win) } })
    } else emptyList()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text("${session.index + 1} of ${session.items.size} · ${item.entry.title}", style = MaterialTheme.typography.labelLarge, color = c.muted)
            if (shown.id != ROOT_ID) Text(tree.lineTo(shown.id), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        ChessboardView(
            modifier = Modifier.fillMaxWidth(),
            board = board,
            colors = settings.boardTheme.colors(),
            orientation = item.entry.side,
            lastMove = shown.from?.let { f -> shown.to?.let { t -> square(f)?.let { a -> square(t)?.let { b -> a to b } } } },
            selectedSquare = selected,
            legalMoves = legalFromSelected,
            arrows = arrows,
            coordinates = settings.coordinates,
            showLegalMoves = settings.showLegalMoves,
            interactive = session.answered == null,
            onSquareClick = session::click,
            onMove = session::attempt,
        )
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (session.verdict) {
                ReviewVerdict.Playing -> Text("Find the lesson move.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                ReviewVerdict.Right -> {
                    RCard(Modifier.fillMaxWidth(), color = c.win.copy(alpha = 0.16f)) {
                        Text("Right: ${session.answered?.label().orEmpty()}", style = MaterialTheme.typography.titleMedium, color = c.win)
                        session.answered?.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
                    }
                    RButton("Next", session::next, icon = Icons.AutoMirrored.Filled.ArrowForward)
                }
                ReviewVerdict.Wrong -> {
                    RCard(Modifier.fillMaxWidth(), color = c.danger.copy(alpha = 0.16f)) {
                        Text("The lesson plays ${expected?.label().orEmpty()}.", style = MaterialTheme.typography.titleMedium, color = c.danger)
                        expected?.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
                        Text("It comes back today.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RButton("Retry", session::retry, style = ButtonStyle.Tonal, compact = true)
                        RButton("Next", session::next, compact = true)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
