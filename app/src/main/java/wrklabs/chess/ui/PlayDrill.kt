package wrklabs.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.play.PlayLaunch
import wrklabs.chess.play.PlayStore
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.PlayViewModel.Companion.BOT
import wrklabs.chess.ui.PlayViewModel.Companion.ME
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapVert
import wrklabs.chess.ui.theme.Palette
import kotlin.math.roundToInt

/** A game against Maia, which plays the way people of the chosen rating play. A finished game is saved to Games and can be reviewed. */
@Composable
fun ColumnScope.PlaySection(settings: AppSettings, vm: PlayViewModel = viewModel(), onReview: (ImportedGame, String) -> Unit) {
    val s by vm.state.collectAsState()
    val request by PlayLaunch.request.collectAsState()
    // A position sent here from a review or a lesson: set it up, and clear the request.
    LaunchedEffect(request) { request?.let { vm.prepare(it); PlayLaunch.request.value = null } }
    if (s.inGame) GamePane(s, vm, settings, onReview) else SetupPane(s, vm)
}

// ── Setup ──

@Composable
private fun ColumnScope.SetupPane(s: PlayUi, vm: PlayViewModel) {
    val c = Palette.colors
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Play Maia, a bot that moves like a person. It makes the mistakes players of its rating make, not random ones.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        s.resume?.let { r ->
            RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
                Text("A game in progress", style = MaterialTheme.typography.titleMedium, color = c.brandCardText, fontWeight = FontWeight.Bold)
                Text("Against $BOT ${r.botElo}, ${r.ucis.size} moves played.", style = MaterialTheme.typography.bodyMedium, color = c.brandCardText)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RButton("Continue", vm::continueGame, Modifier.weight(1f), style = ButtonStyle.OnCard)
                    RButton("Discard", vm::discardSaved, Modifier.weight(1f), style = ButtonStyle.OnCardSoft)
                }
            }
        }
        s.custom?.let { req ->
            RCard(Modifier.fillMaxWidth()) {
                Text(req.label, style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
                Text("You start from this position. It does not count towards your level.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(8.dp))
                RButton("Use the usual start", vm::clearCustom, style = ButtonStyle.Tonal, compact = true)
            }
        }
        RCard(Modifier.fillMaxWidth()) {
            Text("Plays like a", style = MaterialTheme.typography.labelMedium, color = c.muted)
            Text("${s.botElo} player", style = MaterialTheme.typography.headlineSmall, color = c.text, fontWeight = FontWeight.Bold)
            if (s.autoOn) {
                Text("Moves up a step after a win and down after a loss.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            } else {
                Slider(
                    value = s.botElo.toFloat(), onValueChange = { vm.setElo((it / 100f).roundToInt() * 100) },
                    valueRange = 600f..2600f, steps = 19,
                    colors = SliderDefaults.colors(thumbColor = c.brand, activeTrackColor = c.brand, inactiveTrackColor = c.muted.copy(alpha = 0.25f)),
                )
            }
            if (s.autoOn) {
                Text("Your rating: ${s.userElo}", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(top = 6.dp))
            } else Row(Modifier.fillMaxWidth()) {
                Text("600", style = MaterialTheme.typography.labelSmall, color = c.muted)
                Spacer(Modifier.weight(1f))
                Text("Your rating: ${s.userElo}", style = MaterialTheme.typography.labelSmall, color = c.muted)
                Spacer(Modifier.weight(1f))
                Text("2600", style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
            if (s.custom == null) Toggle("Auto level", s.auto) { vm.setAuto(it) }
        }
        RCard(Modifier.fillMaxWidth()) {
            Text("You play", style = MaterialTheme.typography.labelMedium, color = c.muted)
            Spacer(Modifier.height(8.dp))
            Segmented(SideChoice.entries.map { it to it.label }, s.side, vm::setSide, Modifier.fillMaxWidth())
        }
        if (s.results.isNotEmpty()) RCard(Modifier.fillMaxWidth()) {
            Text("Your last games against $BOT", style = MaterialTheme.typography.labelMedium, color = c.muted)
            Text(PlayStore.record(s.results), style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.results.takeLast(10).forEach { r ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(when (r.result) { "win" -> c.win; "loss" -> c.danger; else -> c.warn }))
                }
            }
        }
        Text("There is no clock. When the game ends it is saved to Games and can be reviewed.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
    RButton("Start", vm::start, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp))
}

// ── The game ──

@Composable
private fun ColumnScope.GamePane(s: PlayUi, vm: PlayViewModel, settings: AppSettings, onReview: (ImportedGame, String) -> Unit) {
    val c = Palette.colors
    val g = s.game
    val end = g.end
    val shownPly = s.viewPly ?: g.moves.size
    val board = remember(g.fenAt(shownPly)) { ChessBoard.fromFen(g.fenAt(shownPly)) }
    val last = g.moves.getOrNull(shownPly - 1)
    val orientation = if (s.userWhite != s.flipped) PieceColor.WHITE else PieceColor.BLACK
    fun name(color: PieceColor) = if (color == s.userColor) ME else BOT
    fun rating(color: PieceColor) = if (color == s.userColor) s.userElo else s.botElo
    var askResign by remember { mutableStateOf(false) }

    BoardBody(spacing = 6.dp, footer = {
        if (end != null) ActionBar {
            RButton("Change bot", vm::leave, Modifier.weight(1f), style = ButtonStyle.Tonal)
            RButton("Play again", vm::start, Modifier.weight(1f), style = ButtonStyle.Tonal)
            RButton("Review game", { s.finished?.let { onReview(it.game, ME) } }, Modifier.weight(1.3f), enabled = s.finished != null)
        } else BoardToolbar {
            IconTap(Icons.AutoMirrored.Filled.Undo, "Undo", vm::takeback, enabled = g.moves.isNotEmpty(), size = 48)
            IconTap(Icons.Default.Lightbulb, "Hint", vm::hint, enabled = s.canMove, size = 48)
            IconTap(Icons.Default.SwapVert, "Flip board", vm::flip, size = 48)
            IconTap(Icons.Default.Handshake, "Offer a draw", vm::offerDraw, enabled = s.canMove, size = 48)
            IconTap(Icons.Default.Flag, "Resign", { askResign = true }, size = 48)
        }
    }) {
        BoardWithSeats(Seat(name(orientation.opponent()), rating(orientation.opponent()), orientation.opponent()), Seat(name(orientation), rating(orientation), orientation)) {
        ChessboardView(
            modifier = Modifier.fillMaxWidth().testTag("board"),
            board = board,
            colors = settings.boardTheme.colors(),
            orientation = orientation,
            lastMove = last?.let { it.from to it.to },
            selectedSquare = s.selected,
            legalMoves = s.selected?.let { sq -> board.legalMoves().filter { it.from == sq } }.orEmpty(),
            arrows = when {
                !s.live -> emptyList()
                s.hintKind == HintKind.Best -> listOfNotNull(s.best?.let { EngineArrow(it.from, it.to, c.win) })
                else -> s.hints.orEmpty().mapIndexed { i, h -> EngineArrow(h.from, h.to, if (i == 0) c.win else c.brand.copy(alpha = 0.7f)) }
            },
            mated = if (s.live && end?.reason == wrklabs.chess.play.EndReason.Checkmate) wrklabs.chess.play.kingSquare(board, board.turn) else null,
            coordinates = settings.coordinates,
            showLegalMoves = settings.showLegalMoves,
            interactive = s.canMove,
            onSquareClick = { vm.click(it) },
            onMove = { from, to -> vm.attempt(from, to) },
        )
        }

        PlayMoveStrip(s, vm)

        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!s.live) {
                RCard(Modifier.fillMaxWidth()) {
                    Text(if (shownPly == 0) "The start of the game." else "After move $shownPly.", style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RButton("Back", vm::viewPrevious, Modifier.weight(1f), style = ButtonStyle.Tonal, compact = true)
                        RButton("Forward", vm::viewNext, Modifier.weight(1f), style = ButtonStyle.Tonal, compact = true)
                        RButton("To the game", { vm.view(null) }, Modifier.weight(1.4f), compact = true)
                    }
                }
            }
            if (end == null) {
                Text(
                    when {
                        s.thinking -> "$BOT is thinking…"
                        s.checking -> "Checking your move…"
                        s.myTurn -> buildString {
                            append("Your move.")
                            if (g.board.isCheck()) append(" You are in check.")
                            // The bot's last move, when it is the last move of the game.
                            if (g.moves.isNotEmpty() && g.turn == s.userColor) append(" $BOT played ${g.moves.last().san}.")
                        }
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                s.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.Bold) }
                s.hints?.let { hints ->
                    RCard(Modifier.fillMaxWidth()) {
                        Segmented(listOf(HintKind.People to "What people play", HintKind.Best to "Best move"), s.hintKind, vm::setHintKind, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        if (s.hintKind == HintKind.People) {
                            Text("At ${s.userElo}, players choose", style = MaterialTheme.typography.labelMedium, color = c.muted)
                            hints.forEach { h ->
                                Row(Modifier.padding(top = 4.dp)) {
                                    Text(h.san, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
                                    Text(wrklabs.chess.maia.Human.share(h.share), style = MaterialTheme.typography.titleMedium, color = c.muted)
                                }
                            }
                        } else {
                            val b = s.best
                            when {
                                s.bestLoading -> Text("The engine is looking…", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                                b == null -> Text("The engine has nothing to add here.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                                else -> {
                                    Text("The engine plays", style = MaterialTheme.typography.labelMedium, color = c.muted)
                                    Row(Modifier.padding(top = 4.dp)) {
                                        Text(b.san, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
                                        Text(b.eval, style = MaterialTheme.typography.titleMedium, color = c.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val f = s.finished
                RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
                    Text(f?.title ?: resultTitle(end, s.userColor), style = MaterialTheme.typography.titleLarge, color = c.brandCardText, fontWeight = FontWeight.Bold)
                    val full = g.moves.size / 2 + g.moves.size % 2
                    Text("$full move${if (full == 1) "" else "s"} against $BOT (${s.botElo}).", style = MaterialTheme.typography.bodyMedium, color = c.brandCardText)
                    f?.levelNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.brandCardText) }
                    if (f != null && f.result == "loss") Text("The review shows where it turned.", style = MaterialTheme.typography.bodySmall, color = c.brandCardText)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    s.promotion?.let { PromotionPicker(s.userColor, vm::choosePromotion, vm::cancelPromotion) }
    s.warning?.let { w ->
        AlertDialog(
            onDismissRequest = vm::chooseAnother,
            title = { Text("This looks like a blunder") },
            text = { Text("${w.move.san} costs you about ${w.lost}% of your chances." + (w.bestSan?.let { " The engine prefers $it." } ?: "")) },
            confirmButton = { TextButton(vm::playAnyway) { Text("Play it anyway") } },
            dismissButton = { TextButton(vm::chooseAnother) { Text("Choose another") } },
        )
    }
    if (askResign) AlertDialog(
        onDismissRequest = { askResign = false },
        title = { Text("Resign this game?") },
        confirmButton = { TextButton({ askResign = false; vm.resign() }) { Text("Resign") } },
        dismissButton = { TextButton({ askResign = false }) { Text("Keep playing") } },
    )
}

/** The moves so far in a row, the one on show lit. A tap goes to that position; the game waits. */
@Composable
private fun PlayMoveStrip(s: PlayUi, vm: PlayViewModel) {
    val moves = s.game.moves
    if (moves.isEmpty()) return
    val first = ChessBoard.fromFen(s.game.startFen)
    // The ply of the first move, so that the numbers are right for a game that starts from a position.
    val start = 2 * (first.fullmoveNumber - 1) + (if (first.turn == PieceColor.WHITE) 1 else 2)
    val strip = remember(moves.size, s.game.startFen) { moves.mapIndexed { i, m -> StripMove(i.toString(), start + i, m.san) } }
    val shown = (s.viewPly ?: moves.size) - 1
    MoveStripRow(strip, shown.toString(), { id -> val i = id.toInt(); vm.view(if (i + 1 == moves.size) null else i + 1) }, Modifier.testTag("moves"))
}
