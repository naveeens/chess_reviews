package wrklabs.chess.ui

import androidx.compose.foundation.layout.*
import wrklabs.chess.db.CardRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wrklabs.chess.classifier.MoveClass
import wrklabs.chess.model.Square
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Display
import wrklabs.chess.ui.theme.Palette

@Composable
fun TrainScreen(vm: TrainViewModel, puzzles: PuzzleViewModel, settings: AppSettings, onOpenGames: () -> Unit) {
    val state by vm.trainState.collectAsState()
    val summary by vm.summary.collectAsState()
    val c = Palette.colors
    val puzzleSummary by puzzles.summary.collectAsState()
    var showPuzzles by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Train",
            subtitle = if (showPuzzles) "${puzzleSummary.due} due · ${puzzleSummary.total} puzzles · ${puzzleSummary.learned} learned"
            else "${summary.due} due · ${summary.total} mistakes · ${summary.mastered} learned",
            actions = { IconTap(Icons.Default.Refresh, "Reload", { vm.loadSession(); puzzles.loadSession() }) },
        )
        Segmented(listOf(false to "Mistakes", true to "Puzzles"), showPuzzles, { showPuzzles = it }, Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth())
        if (showPuzzles) { PuzzleSection(puzzles, settings); return@Column }
        when (val q = state) {
            is TrainState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.brand) }
            is TrainState.AllDone -> Box(Modifier.padding(14.dp)) {
                if (summary.total == 0) {
                    EmptyState("No mistakes yet", "Review a few of your games and the moves you got wrong turn up here as puzzles.", action = { RButton("Your games", onOpenGames) })
                } else {
                    EmptyState("All caught up", "You've been through everything that's due. Come back tomorrow.", action = { RButton("Check again", vm::loadSession, style = ButtonStyle.Tonal) })
                }
            }
            is TrainState.Question -> {
                val card = q.card
                val cls = runCatching { MoveClass.valueOf(card.cls) }.getOrNull()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Find the best move", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text("You played ${card.playedSan}${cls?.let { " (${it.label})" } ?: ""} vs ${card.opponent}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                val arrows = remember(q.status, q.bestShown, q.whyStarted, card.bestUci) {
                    if (((q.status == CardAnswerStatus.Wrong && !q.whyStarted) || (q.status == CardAnswerStatus.Better && q.bestShown)) && card.bestUci.length >= 4) {
                        listOf(EngineArrow(Square.fromName(card.bestUci.substring(0, 2)), Square.fromName(card.bestUci.substring(2, 4)), c.win))
                    } else emptyList()
                }
                ChessboardView(
                    modifier = Modifier.fillMaxWidth(),
                    board = q.board,
                    colors = settings.boardTheme.colors(),
                    orientation = ChessBoardTurn.of(card.fen),
                    selectedSquare = q.selectedSquare,
                    legalMoves = if (q.selectedSquare != null) q.board.legalMoves().filter { it.from == q.selectedSquare } else emptyList(),
                    arrows = arrows,
                    coordinates = settings.coordinates,
                    showLegalMoves = settings.showLegalMoves,
                    lastMove = q.lastMove,
                    // What the answer was: its class on the square it went to, until the line of "Show why" moves the pieces.
                    badge = q.grade?.takeIf { q.status in setOf(CardAnswerStatus.Correct, CardAnswerStatus.Better, CardAnswerStatus.Wrong) && !q.whyStarted }?.let { cls -> q.userPlayedMove?.let { it.to to cls } },
                    highlightLastMove = q.lastMove != null,
                    onSquareClick = vm::onSquareClick,
                    onMove = { from, to -> q.board.legalMoves().find { it.from == from && it.to == to }?.let(vm::onUserMove) },
                )
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (q.status) {
                        CardAnswerStatus.Unanswered -> Text("Play your move on the board.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        CardAnswerStatus.Checking -> Text("Checking your move…", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        CardAnswerStatus.Better -> RCard(color = c.brandCard) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Good move, but there's a better one.", style = MaterialTheme.typography.titleMedium, color = c.brandCardText)
                                    Text(if (q.bestShown) "Best was ${card.bestSan}." else "Try again, or see what it was. This card isn't counted yet.", style = MaterialTheme.typography.bodySmall, color = c.brandCardText)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RButton("Retry", vm::retryCurrentCard, compact = true)
                                if (!q.bestShown) RButton("Show best", vm::showBest, style = ButtonStyle.Tonal, compact = true)
                                RButton("Next", vm::nextCard, style = ButtonStyle.Tonal, compact = true)
                            }
                        }
                        CardAnswerStatus.Correct -> RCard(color = c.win.copy(alpha = 0.16f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("That's the move.", style = MaterialTheme.typography.titleMedium, color = c.win)
                                    Text("You'll see it again ${comesBack(CardRepository.INTERVAL_DAYS[minOf(CardRepository.MAX_BOX, card.box + 1)])}.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                                }
                                RButton("Next", vm::nextCard, icon = Icons.AutoMirrored.Filled.ArrowForward)
                            }
                        }
                        CardAnswerStatus.Wrong -> RCard(color = c.danger.copy(alpha = 0.16f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Best was ${card.bestSan}", style = MaterialTheme.typography.titleMedium, color = c.danger)
                                    Text(if (q.whyStarted) (q.whyText ?: "Following the opponent's best reply…") else "You'll see it again today.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RButton("Retry", vm::retryCurrentCard, style = ButtonStyle.Tonal, compact = true)
                                if (!q.whyStarted) RButton("Show why", vm::showWhy, style = ButtonStyle.Tonal, compact = true, icon = Icons.Default.Lightbulb)
                                RButton("Next", vm::nextCard, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The side to move in a FEN, as the way to face the board. */
private object ChessBoardTurn {
    fun of(fen: String) = if (fen.split(" ").getOrNull(1) == "b") wrklabs.chess.engine.PieceColor.BLACK else wrklabs.chess.engine.PieceColor.WHITE
}

/** When a card comes back, from the days it waits. */
private fun comesBack(days: Int) = when (days) {
    0 -> "today"
    1 -> "tomorrow"
    else -> "in $days days"
}

/** Puzzles: the lines of the bundled set, one move of yours at a time, the opponent answering in between. */
@Composable
private fun ColumnScope.PuzzleSection(vm: PuzzleViewModel, settings: AppSettings) {
    val state by vm.state.collectAsState()
    val c = Palette.colors
    when (val q = state) {
        is PuzzleState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.brand) }
        is PuzzleState.AllDone -> Box(Modifier.padding(14.dp)) {
            EmptyState("Done for today", "${wrklabs.chess.db.PuzzleRepository.NEW_PER_DAY} new puzzles a day, and any that come back. See you tomorrow.", action = { RButton("Check again", vm::loadSession, style = ButtonStyle.Tonal) })
        }
        is PuzzleState.Question -> {
            val puzzle = q.card.puzzle
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Find the best move", style = MaterialTheme.typography.titleMedium, color = c.text)
                    val place = if (q.status == PuzzleStatus.Solved) "Solved" else "Move ${minOf(q.yourMove, puzzle.yourMoves)} of ${puzzle.yourMoves}"
                    Text("#${puzzle.seq} · ${puzzle.level} · ${puzzle.rating} · $place", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            val arrows = remember(q.status, q.step, q.whyStarted) {
                if (q.status == PuzzleStatus.Wrong && !q.whyStarted && q.expected.length >= 4) {
                    listOf(EngineArrow(Square.fromName(q.expected.substring(0, 2)), Square.fromName(q.expected.substring(2, 4)), c.win))
                } else emptyList()
            }
            ChessboardView(
                modifier = Modifier.fillMaxWidth(),
                board = q.board,
                colors = settings.boardTheme.colors(),
                // Yours is the side the opponent's first move left to move.
                orientation = if (puzzle.fen.split(" ").getOrNull(1) == "b") wrklabs.chess.engine.PieceColor.WHITE else wrklabs.chess.engine.PieceColor.BLACK,
                selectedSquare = q.selectedSquare,
                legalMoves = if (q.selectedSquare != null) q.board.legalMoves().filter { it.from == q.selectedSquare } else emptyList(),
                arrows = arrows,
                coordinates = settings.coordinates,
                showLegalMoves = settings.showLegalMoves,
                lastMove = q.lastMove,
                highlightLastMove = q.lastMove != null,
                onSquareClick = vm::onSquareClick,
                onMove = vm::onDrag,
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (q.status) {
                    PuzzleStatus.Playing -> Text(if (q.replying) "…" else "Play your move on the board.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    PuzzleStatus.Solved -> RCard(color = c.win.copy(alpha = 0.16f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Solved.", style = MaterialTheme.typography.titleMedium, color = c.win)
                                Text(
                                    if (q.failed) "You missed it on the way, so it comes back today."
                                    else "You'll see it again ${comesBack(CardRepository.INTERVAL_DAYS[minOf(CardRepository.MAX_BOX, q.card.box + 1)])}.",
                                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                                )
                                puzzleTags(puzzle.themes).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.muted) }
                            }
                            RButton("Next", vm::next, icon = Icons.AutoMirrored.Filled.ArrowForward)
                        }
                    }
                    PuzzleStatus.Wrong -> RCard(color = c.danger.copy(alpha = 0.16f)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("Best was ${q.bestSan ?: q.expected}", style = MaterialTheme.typography.titleMedium, color = c.danger)
                            Text(if (q.whyStarted) (q.whyText ?: "Following the opponent's best reply…") else "You'll see it again today.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RButton("Retry", vm::retry, style = ButtonStyle.Tonal, compact = true)
                            if (!q.whyStarted) RButton("Show why", vm::showWhy, style = ButtonStyle.Tonal, compact = true, icon = Icons.Default.Lightbulb)
                            RButton("Next", vm::next, compact = true)
                        }
                    }
                }
            }
        }
    }
}
