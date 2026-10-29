package wrklabs.chess.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wrklabs.chess.Graph
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.classifier.MoveReview
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.MoveNode
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.ROOT_ID
import wrklabs.chess.model.Square
import wrklabs.chess.review.ReviewService
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Display
import wrklabs.chess.ui.theme.Mono
import wrklabs.chess.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

/** A game's review: first its summary, then the board. Each is a screen of its own, with no bottom bar. */
@Composable
fun ReviewScreen(vm: ReviewViewModel, settings: AppSettings, onBack: () -> Unit, onAnalyseFromHere: () -> Unit) {
    val s by vm.state.collectAsState()
    val c = Palette.colors
    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        if (s.showBoard) BoardScreen(s, vm, settings, onBack = vm::showSummary, onAnalyseFromHere = onAnalyseFromHere)
        else SummaryScreen(s, vm, onBack)
    }
}

// ═══════════════ The summary ═══════════════

@Composable
private fun SummaryScreen(s: ReviewUiState, vm: ReviewViewModel, onBack: () -> Unit) {
    val c = Palette.colors
    val info = s.info
    val report = s.report
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back to games", onBack, size = 44)
            Text("Game review", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 4.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            s.message?.let { msg -> RCard(color = c.danger.copy(alpha = 0.16f)) { Text(msg, color = c.danger, style = MaterialTheme.typography.bodyMedium) } }

            if (s.points.size >= 2) EvalGraphView(s.points, s.gamePly, { ply -> vm.seekToPly(ply); vm.showBoard() })
            else Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(c.field))

            if (info != null) {
                val sides = listOf(PieceColor.WHITE, PieceColor.BLACK).sortedBy { if (it == (info.myColor ?: PieceColor.WHITE)) 0 else 1 }
                RCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        for (color in sides) {
                            val name = if (color == PieceColor.WHITE) info.white else info.black
                            Text(name, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                        }
                    }
                    // The score, under each name: 1, 0 or ½ in the player's colour.
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Score", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                        for (color in sides) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ScoreCircle(color, scoreOf(color, info.result)) }
                        }
                    }
                    // How it ended, in the site's own words when it gave them.
                    val ending = s.termination ?: info.result.takeIf { it != "*" }?.let { r ->
                        when (r) { "1-0" -> "${info.white} won"; "0-1" -> "${info.black} won"; else -> "Draw" }
                    }
                    if (ending != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Result", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                            Text(fromYourSide(ending, info), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Accuracy", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                        for (color in sides) {
                            val acc = report?.accuracy?.get(color)
                            val white = color == PieceColor.WHITE
                            Box(Modifier.weight(1f).padding(horizontal = 4.dp).height(52.dp).clip(RoundedCornerShape(12.dp)).background(if (white) Color(0xFFECECEE) else Color(0xFF3B434B)), contentAlignment = Alignment.Center) {
                                Text(acc?.let { String.format("%.1f", it) } ?: "—", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = if (white) Color(0xFF1B1B1D) else Color(0xFFF2F2F3))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Hairline()
                    // All ten rows, always: nothing jumps while the numbers come in or when the review ends.
                    ClassCounts(report, sides, showAll = true)
                }
            }

            if (report != null) {
                val moments = s.keyMoments
                if (moments.isNotEmpty()) RCard {
                    Text("Key moments", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(bottom = 4.dp))
                    for (node in moments) {
                        val r = report.moves[node.id] ?: continue
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.select(node.id); vm.showBoard() }.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ClassBadge(r.cls, 20.dp)
                            Text("${(node.ply + 1) / 2}${if (node.ply % 2 == 1) "." else "…"} ${node.san}", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text(r.cls.label, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                            Text(
                                if (r.color == PieceColor.WHITE) (info?.white ?: "White") else (info?.black ?: "Black"),
                                style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            when {
                s.reviewing -> RCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reviewing the game", style = MaterialTheme.typography.titleMedium, color = c.text)
                            val done = (s.progress * (s.mainline.size + 1)).toInt()
                            val left = if (done >= 4) ((System.currentTimeMillis() - s.reviewStart) * (1 - s.progress) / s.progress / 1000).toInt() else null
                            Text(
                                "Position $done of ${s.mainline.size + 1}" + (left?.let { " · about ${if (it >= 60) "${it / 60} min ${it % 60} s" else "$it s"} left" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = c.muted,
                            )
                        }
                        RButton("Stop", vm::stopReview, style = ButtonStyle.Tonal, compact = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = c.brand, trackColor = c.field)
                }
                s.limitUntil != null -> RCard {
                    Text("Free reviews used", style = MaterialTheme.typography.titleMedium, color = c.text)
                    val back = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(s.limitUntil))
                    Text(
                        "Free accounts review ${wrklabs.chess.billing.ReviewQuota.FREE_PER_WEEK} games a week. The next one comes back $back. Games you have reviewed stay open, and Pro removes the limit.",
                        style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    ProUnlock()
                }
                report == null -> RButton("Review game", vm::review, Modifier.fillMaxWidth(), icon = Icons.Default.AutoAwesome)
                else -> RButton("Start Review", vm::showBoard, Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow)
            }
        }
    }
}

// ═══════════════ The board ═══════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardScreen(s: ReviewUiState, vm: ReviewViewModel, settings: AppSettings, onBack: () -> Unit, onAnalyseFromHere: () -> Unit) {
    val c = Palette.colors
    val info = s.info
    val board = remember(s.currentNode.fen) { s.board }
    val bottomColor = s.orientation
    val topColor = bottomColor.opponent()
    // A mate in a variation of yours says nothing about the game's result.
    val result = (if (s.onBranch) null else s.matedColor?.let { if (it == PieceColor.BLACK) "1-0" else "0-1" }) ?: info?.result
    fun name(color: PieceColor) = info?.let { if (color == PieceColor.WHITE) it.white else it.black } ?: ""
    fun rating(color: PieceColor) = info?.let { if (color == PieceColor.WHITE) it.whiteRating else it.blackRating }
    fun accuracy(color: PieceColor) = s.report?.accuracy?.get(color)
    val review = s.playedReview
    // The arrow shows the move the button offers once that deeper search is in; until then, the review's own.
    val arrowMove = if (!s.candidatesPending && s.playedNode.parentId != null) s.suggestion?.candidate?.uci else review?.bestMove
    var sheet by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boardSize = maxWidth
        Column(Modifier.fillMaxSize()) {
            // The game, and the way back. Tapping the title opens its details.
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back to the summary", onBack, size = 44)
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Game details") { sheet = true }.padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(info?.let { "${it.white} vs ${it.black}" } ?: "", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            info?.timeClass?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() },
                            info?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.date)) },
                            s.gameOpening?.let { "${it.eco} ${it.name}" },
                        ).joinToString(" · ")
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = c.muted, modifier = Modifier.size(22.dp))
                }
            }

            PlayerBar(name(topColor), rating(topColor), topColor, accuracy(topColor), score = scoreOf(topColor, result))
            ChessboardView(
                modifier = Modifier.size(boardSize),
                board = board,
                colors = settings.boardTheme.colors(),
                orientation = s.orientation,
                lastMove = s.lastMove,
                selectedSquare = s.selectedSquare,
                legalMoves = s.legalMoves,
                arrows = if (s.whyFrom != null) emptyList() else bestArrow(s.showingBest, review, arrowMove, settings.showArrows),
                badge = (when {
                    s.whyFrom != null -> s.report?.moves?.get(s.currentNodeId) ?: s.grades[s.currentNodeId]
                    !s.showingBest && !s.onBranch -> review
                    else -> s.grades[s.currentNodeId]
                })?.let { r -> s.currentNode.to?.let { Square.fromName(it) to r.cls } },
                mated = remember(board, s.matedColor) { s.matedColor?.let { mc -> (0 until 64).map { Square.fromIndex(it) }.firstOrNull { sq -> board.getPiece(sq)?.let { it.type == PieceType.KING && it.color == mc } == true } } },
                coordinates = settings.coordinates,
                highlightLastMove = settings.highlightLastMove,
                showLegalMoves = settings.showLegalMoves,
                interactive = !s.reviewing && !s.thinking,
                onSquareClick = vm::onSquareClick,
                onMove = vm::onDrag,
            )
            EvalStrip(s.score, s.orientation)
            PlayerBar(name(bottomColor), rating(bottomColor), bottomColor, accuracy(bottomColor), score = scoreOf(bottomColor, result))

            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(horizontal = 14.dp).padding(top = 4.dp)) { MoveCard(s, vm) }
                MoveStrip(
                    s.strip, s.currentNodeId, s.report, vm::select, offGame = { it !in s.gameIds }, extraGrades = s.grades.mapValues { it.value.cls },
                    alternatives = { n -> n.parentId?.let { s.tree.nodes[it] }?.children.orEmpty().filter { it != n.id }.mapNotNull { s.tree.nodes[it] } },
                )
                // Always this tall, so Resume appearing never moves anything.
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    if (s.canResume) {
                        Box(
                            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(c.brand).clickable(onClick = vm::resume),
                            contentAlignment = Alignment.Center,
                        ) { Text("Resume", style = MaterialTheme.typography.labelLarge, color = c.brandInk) }
                    }
                }
            }

            Hairline()
            Toolbar(s, vm, settings, onAnalyseFromHere)
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = c.cardHigh) { InfoSheet(s) }
    }
}

/** "Bob won on time" as "Opponent won on time" and "Alice won by checkmate" as "You won by checkmate", when it is known who you are. */
private fun fromYourSide(text: String, info: wrklabs.chess.review.GameInfo): String {
    val mine = when (info.myColor) {
        PieceColor.WHITE -> info.white
        PieceColor.BLACK -> info.black
        null -> return text
    }
    val theirs = if (info.myColor == PieceColor.WHITE) info.black else info.white
    // The longer name first, in case one contains the other.
    return listOf(mine to "You", theirs to "Opponent").sortedByDescending { it.first.length }
        .fold(text) { t, (name, word) -> if (name.isBlank()) t else t.replace(name, word, ignoreCase = true) }
}

private fun bestArrow(showingBest: Boolean, r: MoveReview?, best: String?, enabled: Boolean): List<EngineArrow> {
    if (!enabled || showingBest || r == null || r.cls !in setOf(MoveClass.INACCURACY, MoveClass.MISTAKE, MoveClass.MISS, MoveClass.BLUNDER)) return emptyList()
    val uci = best?.takeIf { it.length >= 4 } ?: return emptyList()
    return listOf(EngineArrow(Square.fromName(uci.substring(0, 2)), Square.fromName(uci.substring(2, 4)), Color(0xFF4F9A3C)))
}

// ── Toolbar ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Toolbar(s: ReviewUiState, vm: ReviewViewModel, settings: AppSettings, onAnalyseFromHere: () -> Unit) {
    val c = Palette.colors
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconTap(Icons.Default.Menu, "Menu", { menu = true }, size = 48)
            DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                DropdownMenuItem(text = { Text("Analyse from here") }, leadingIcon = { Icon(Icons.Default.Search, null) }, onClick = { menu = false; onAnalyseFromHere() })
                DropdownMenuItem(text = { Text("Copy PGN") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; copyPgn(context, s) })
                DropdownMenuItem(text = { Text("Clear variations") }, leadingIcon = { Icon(Icons.Default.Close, null) }, enabled = s.hasVariations, onClick = { menu = false; vm.clearVariations() })
                DropdownMenuItem(text = { Text("Review again") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, enabled = !s.reviewing, onClick = { menu = false; vm.showSummary(); vm.review() })
                DropdownMenuItem(
                    text = { Text("Opponent replies") },
                    leadingIcon = { Icon(Icons.Default.SmartToy, null) },
                    trailingIcon = { if (settings.opponentReply) Icon(Icons.Default.Check, "On", tint = c.brand) },
                    onClick = { Graph.settings.update { copy(opponentReply = !opponentReply) } },
                )
            }
        }
        IconTap(Icons.Default.SwapVert, "Flip board", vm::flip, size = 48)
        // A long press goes to the start or the end.
        Box(
            Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = vm::previous, onLongClick = vm::first),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous move", tint = c.text, modifier = Modifier.size(28.dp)) }
        Box(
            Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = vm::next, onLongClick = vm::last),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next move", tint = c.text, modifier = Modifier.size(28.dp)) }
    }
}

private fun copyPgn(context: Context, s: ReviewUiState) {
    val info = s.info ?: return
    val pgn = info.pgn ?: s.tree.toPgn(mapOf("White" to info.white, "Black" to info.black, "Result" to info.result))
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PGN", pgn))
}

// ── The move card ──

/**
 * What happened on the move being looked at, always the same card: the move and its grade, a line about it, and the
 * engine's two moves for that position. A variation of yours gets the same card, graded once the engine has looked.
 */
@Composable
private fun MoveCard(s: ReviewUiState, vm: ReviewViewModel) {
    val node = s.playedNode
    val r = s.playedReview
    val atStart = s.currentNodeId == ROOT_ID
    // Theory and forced moves have nothing to be improved on.
    val noAlternative = r?.cls == MoveClass.BOOK || r?.cls == MoveClass.FORCED
    val sug = s.suggestion.takeIf { !noAlternative }
    // The only words under the title are the ones that are news: what "Show why" found, or that the engine is still at work.
    val note: String? = when {
        s.whyFrom != null -> s.whyText ?: "Following the opponent's best reply…"
        r == null && s.onBranch -> if (s.thinking) "The engine is replying…" else "The engine is looking at it…"
        else -> null
    }
    MoveCardView(
        atStart = atStart,
        san = node.san,
        cls = r?.takeIf { !atStart }?.cls,
        lostPercent = r?.takeIf { it.cls != MoveClass.BOOK && it.cls != MoveClass.FORCED && it.loss >= 0.01 }?.let { (it.loss * 100).toInt() },
        note = note,
        best = sug?.let { BestChoice(it.label, it.candidate.san, evalLabel(it.candidate.score), on = s.showingBest && s.currentNode.uci == it.candidate.uci) },
        bestPending = sug == null && s.candidatesPending && !atStart && !noAlternative && r?.bestMove != null,
        whyLabel = if (r != null && r.cls in setOf(MoveClass.MISTAKE, MoveClass.MISS, MoveClass.BLUNDER)) (if (r.cls == MoveClass.MISS) "What you missed" else "Show why") else null,
        whyActive = s.whyFrom != null,
        onBest = { sug?.let { vm.showCandidate(it.candidate.uci) } },
        onWhy = vm::showWhy,
    )
}

// ── The game's details, in a sheet ──

@Composable
private fun InfoSheet(s: ReviewUiState) {
    val c = Palette.colors
    val info = s.info
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Game details", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(bottom = 8.dp))
        val rows = listOf(
            "White" to (info?.let { "${it.white}${it.whiteRating?.let { r -> " ($r)" } ?: ""}" } ?: "—"),
            "Black" to (info?.let { "${it.black}${it.blackRating?.let { r -> " ($r)" } ?: ""}" } ?: "—"),
            "Result" to (s.termination ?: info?.let { resultText(it.result) } ?: "—"),
            "Time" to (info?.timeClass?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() } ?: "—"),
            "Played" to (info?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.date)) } ?: "—"),
            "Opening" to (s.gameOpening?.let { "${it.eco} ${it.name}" } ?: "—"),
            "Moves" to ((s.mainline.size + 1) / 2).toString(),
        )
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.width(84.dp))
                Text(v, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            }
        }
    }
}
