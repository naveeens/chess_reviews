/*
 * Copyright (C) bijoyv9 and contributors (Rookahead, https://github.com/rookahead/rookahead)
 * Copyright (C) 2026 naveen s
 *
 * Train screen for the mistake puzzles derived from Rookahead's src/lib/mistakes.ts.
 * SPDX-License-Identifier: GPL-3.0-or-later. See NOTICE.
 */
package wrklabs.chess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import wrklabs.chess.db.CardRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
fun TrainScreen(vm: TrainViewModel, puzzles: PuzzleViewModel, settings: AppSettings, onOpenGames: () -> Unit, onDrillOpen: (Boolean) -> Unit = {}, onOpenAnalysis: () -> Unit = {}, onOpenEditor: () -> Unit = {}, onReviewGame: (wrklabs.chess.importer.ImportedGame, String) -> Unit = { _, _ -> }) {
    val state by vm.trainState.collectAsState()
    val summary by vm.summary.collectAsState()
    val c = Palette.colors
    val puzzleSummary by puzzles.summary.collectAsState()
    var drill by rememberSaveable { mutableStateOf<Drill?>(null) }
    // Opening positions that are due again, read each time the hub is shown.
    val lineDue by produceState(0, drill) { value = wrklabs.chess.Graph.cards.getLineSummary().due }
    BackHandler(enabled = drill != null) { drill = null }
    // A position sent from a review or a lesson opens Play.
    val playRequest by wrklabs.chess.play.PlayLaunch.request.collectAsState()
    LaunchedEffect(playRequest) { if (playRequest != null) drill = Drill.Play }
    // A drill or lesson takes the whole screen; the tab bar comes back with the hub.
    LaunchedEffect(drill != null) { onDrillOpen(drill != null) }
    DisposableEffect(Unit) { onDispose { onDrillOpen(false) } }

    val lessons = remember { LessonChrome() }
    Column(Modifier.fillMaxSize()) {
        val open = drill
        if (open == null) {
            TrainHub(summary.due + puzzleSummary.due + lineDue, summary, puzzleSummary, lineDue, onOpen = { drill = it }, onAnalysis = onOpenAnalysis, onEditor = onOpenEditor, onReload = { vm.loadSession(); puzzles.loadSession() })
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back to Train", { drill = null }, size = 44)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(open.title, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1)
                Text(
                    if (open == Drill.Puzzles) "${puzzleSummary.due} due · ${puzzleSummary.total} puzzles · ${puzzleSummary.learned} learned"
                    else if (open == Drill.Openings) "Lessons with variations"
                    else if (open == Drill.Play) "Against Maia, a bot that moves like a person"
                    else if (open == Drill.Squares) "Timed sets of ten"
                    else if (open == Drill.Vision) "Attacked, hanging, recall"
                    else if (open == Drill.Notation) "Lines from real puzzles"
                    else "${summary.due} due · ${summary.total} mistakes · ${summary.mastered} learned",
                    style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (open == Drill.Mistakes || open == Drill.Puzzles) IconTap(Icons.Default.Refresh, "Reload", { vm.loadSession(); puzzles.loadSession() })
            if (open == Drill.Openings && lessons.lessonOpen) {
                Segmented(listOf(false to "Learn", true to "Practise"), lessons.practise, { lessons.practise = it }, Modifier.width(176.dp))
            }
        }
        if (open == Drill.Puzzles) { PuzzleSection(puzzles, settings); return@Column }
        if (open == Drill.Openings) { OpeningsSection(settings, lessons); return@Column }
        if (open == Drill.Play) { PlaySection(settings, onReview = onReviewGame); return@Column }
        if (open == Drill.Squares) { SquareSection(settings); return@Column }
        if (open == Drill.Vision) { VisionSection(settings); return@Column }
        if (open == Drill.Notation) { NotationSection(settings); return@Column }
        when (val q = state) {
            is TrainState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.brand) }
            is TrainState.AllDone -> Box(Modifier.padding(14.dp)) {
                if (summary.total == 0) {
                    EmptyState("No mistakes yet", "Review a few of your games and the moves you got wrong turn up here as puzzles.", action = { RButton("Your games", onOpenGames, style = ButtonStyle.OnCard) })
                } else {
                    EmptyState("All caught up", "You've been through everything that's due. Come back tomorrow.", action = { RButton("Check again", vm::loadSession, style = ButtonStyle.OnCardSoft) })
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
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
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
                    onMove = vm::onMove,
                )
                q.promotion?.let { PromotionPicker(q.board.turn, vm::choosePromotion, vm::cancelPromotion) }
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (q.status) {
                        CardAnswerStatus.Unanswered -> Text("Play your move on the board.", style = MaterialTheme.typography.titleMedium, color = c.text)
                        CardAnswerStatus.Checking -> Text("Checking your move…", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        CardAnswerStatus.Better -> RCard(color = c.brandCard) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Good move, but there's a better one.", style = MaterialTheme.typography.titleMedium, color = c.brandCardText)
                                    Text(if (q.bestShown) "Best was ${card.bestSan}." else "Try again, or see what it was. This card isn't counted yet.", style = MaterialTheme.typography.bodySmall, color = c.brandCardText)
                                }
                            }
                        }
                        CardAnswerStatus.Correct -> RCard(color = c.win.copy(alpha = 0.16f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("That's the move.", style = MaterialTheme.typography.titleMedium, color = c.win)
                                    Text("You'll see it again ${comesBack(CardRepository.INTERVAL_DAYS[minOf(CardRepository.MAX_BOX, card.box + 1)])}.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                                }
                            }
                        }
                        CardAnswerStatus.Wrong -> MoveCardView(
                            atStart = false,
                            san = q.userPlayedMove?.san?.ifEmpty { null } ?: card.playedSan,
                            cls = q.grade,
                            lostPercent = (q.loss * 100).toInt().takeIf { it >= 1 },
                            note = q.whyText ?: "You'll see it again today.",
                            best = q.bestLine?.let { LineChoice("Best", evalLabel(q.bestScore), it, on = q.lineOn == LineOn.Best) },
                            bestPending = q.bestLine == null,
                            played = LineChoice("Played", "", q.playedLine, on = q.lineOn == LineOn.Played),
                            onBest = vm::showBestLine,
                            onPlayed = vm::showWhy,
                        )
                    }
                }
                }
                // What to do next, pinned at the bottom: Next is always the big one.
                when (q.status) {
                    CardAnswerStatus.Better -> ActionBar {
                        RButton("Retry", vm::retryCurrentCard, Modifier.weight(1f), style = ButtonStyle.Tonal)
                        if (!q.bestShown) RButton("Show best", vm::showBest, Modifier.weight(1f), style = ButtonStyle.Tonal)
                        RButton("Next", vm::nextCard, Modifier.weight(1f))
                    }
                    CardAnswerStatus.Correct -> ActionBar { RButton("Next", vm::nextCard, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.ArrowForward) }
                    CardAnswerStatus.Wrong -> ActionBar {
                        RButton("Retry", vm::retryCurrentCard, Modifier.weight(1f), style = ButtonStyle.Tonal)
                        RButton("Next", vm::nextCard, Modifier.weight(1f))
                    }
                    else -> Unit
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
            EmptyState("Done for today", "${wrklabs.chess.db.PuzzleRepository.NEW_PER_DAY} new puzzles a day, and any that come back. See you tomorrow.", action = { RButton("Check again", vm::loadSession, style = ButtonStyle.OnCardSoft) })
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
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
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
            q.promotion?.let { PromotionPicker(q.board.turn, vm::choosePromotion, vm::cancelPromotion) }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (q.status) {
                    PuzzleStatus.Playing -> Text(if (q.replying) "…" else "Play your move on the board.", style = MaterialTheme.typography.titleMedium, color = c.text)
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
                        }
                    }
                    PuzzleStatus.Wrong -> MoveCardView(
                        atStart = false,
                        san = q.wrongMove?.san?.ifEmpty { null },
                        cls = q.grade,
                        lostPercent = (q.loss * 100).toInt().takeIf { it >= 1 },
                        note = q.whyText ?: "You'll see it again today.",
                        best = LineChoice("Best", "", q.bestLine, on = q.lineOn == LineOn.Best),
                        bestPending = false,
                        played = LineChoice("Played", "", q.playedLine, on = q.lineOn == LineOn.Played),
                        onBest = vm::showBestLine,
                        onPlayed = vm::showWhy,
                    )
                }
            }
            }
            when (q.status) {
                PuzzleStatus.Solved -> ActionBar { RButton("Next", vm::next, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.ArrowForward) }
                PuzzleStatus.Wrong -> ActionBar {
                    RButton("Retry", vm::retry, Modifier.weight(1f), style = ButtonStyle.Tonal)
                    RButton("Next", vm::next, Modifier.weight(1f))
                }
                else -> Unit
            }
        }
    }
}

/** The kinds of training on offer; each is a card on the Train page and opens its own session. */
enum class Drill(val title: String, val blurb: String) {
    Mistakes("Your mistakes", "Positions from your own games where you went wrong. Find the move you missed."),
    Puzzles("Puzzles", "A bundled set of tactics, a few new ones a day and the ones that come back."),
    Openings("Openings", "Step through opening lessons, with the ideas behind the moves and the lines you will meet."),
    Play("Play", "A game against Maia, a bot that plays the way people of a rating play, mistakes included. Review it afterwards."),
    Squares("Squares", "Find, name and colour squares, knight moves and diagonals, in timed sets of ten. The clock gets shorter as you get them right."),
    Vision("Vision", "Spot attacked and hanging pieces in real positions, and rebuild a position from memory."),
    Notation("Notation", "Read a line in algebraic notation and play it on the board."),
}

/** Train's front page: one card per kind of training, with what is due in each. */
@Composable
private fun TrainHub(due: Int, mistakes: wrklabs.chess.db.CardSummary, puzzles: wrklabs.chess.db.PuzzleSummary, openingsDue: Int, onOpen: (Drill) -> Unit, onAnalysis: () -> Unit, onEditor: () -> Unit, onReload: () -> Unit) {
    val c = Palette.colors
    PageHeader(
        "Train",
        subtitle = if (due > 0) "$due due today" else "Nothing due today",
        actions = { IconTap(Icons.Default.Refresh, "Reload", onReload) },
    )
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Drill.entries.forEach { d ->
            val (n, status) = when (d) {
                Drill.Mistakes -> mistakes.due to "${mistakes.total} mistakes · ${mistakes.mastered} learned"
                Drill.Puzzles -> puzzles.due to "${puzzles.total} puzzles · ${puzzles.learned} learned"
                Drill.Openings -> openingsDue to "Lessons with variations"
                Drill.Play -> 0 to "600 to 2600, no clock"
                Drill.Squares -> 0 to "Timed sets of ten"
                Drill.Vision -> 0 to "Attacked, hanging, recall"
                Drill.Notation -> 0 to "Lines from real puzzles"
            }
            RCard(Modifier.fillMaxWidth(), onClick = { onOpen(d) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                        Spacer(Modifier.height(2.dp))
                        Text(d.blurb, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        Spacer(Modifier.height(6.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    if (n > 0) {
                        Text("$n due", style = MaterialTheme.typography.titleSmall, color = c.brand, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        SectionLabel("Tools", Modifier.padding(top = 6.dp))
        for ((title, blurb, open) in listOf(
            Triple("Analysis board", "Move the pieces freely and see what the engine thinks, or load a game or a position.", onAnalysis),
            Triple("Board editor", "Set up any position piece by piece, then analyse it or play it against Maia.", onEditor),
        )) {
            RCard(Modifier.fillMaxWidth(), onClick = open) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(blurb, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}
