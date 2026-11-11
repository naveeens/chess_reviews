package wrklabs.chess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Palette

enum class AppTab(val title: String, val icon: ImageVector) {
    GAMES("Games", Icons.Default.History),
    TRAIN("Train", Icons.Default.School),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun MainScreen(settings: AppSettings) {
    val analysis: AnalysisViewModel = viewModel()
    val games: GamesViewModel = viewModel()
    val train: TrainViewModel = viewModel()
    val puzzles: PuzzleViewModel = viewModel()
    val review: ReviewViewModel = viewModel()
    var reviewOpen by remember { mutableStateOf(false) }
    // The board editor, over the tabs, with the position it started from.
    var editorFen by rememberSaveable { mutableStateOf<String?>(null) }
    // The analysis board, a screen of its own over the tabs.
    var analysisOpen by rememberSaveable { mutableStateOf(false) }
    var tab by remember { mutableStateOf(if (settings.hasAccounts) AppTab.GAMES else AppTab.GAMES) }
    val c = Palette.colors

    // Only one screen drives the engine at a time: the analysis board steps aside while a review is open.
    LaunchedEffect(analysisOpen, reviewOpen) { analysis.setActive(analysisOpen && !reviewOpen) }
    val closeReview = {
        review.close()
        reviewOpen = false
        games.reload()
    }
    // A game against Maia asked for from a review or a lesson: leave the review if it is open and go to Play.
    LaunchedEffect(Unit) {
        wrklabs.chess.play.PlayLaunch.request.collect { if (it != null) { if (reviewOpen) closeReview(); editorFen = null; analysisOpen = false; tab = AppTab.TRAIN } }
    }
    // On the board, back goes to the summary; on the summary, back leaves the review.
    BackHandler(enabled = reviewOpen) { if (review.state.value.showBoard) review.showSummary() else closeReview() }

    // Inside a drill or lesson the tabs are out of the way, as they are in a review.
    var trainOpen by remember { mutableStateOf(false) }
    val barShown = !(tab == AppTab.TRAIN && trainOpen)
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = c.bg,
        bottomBar = {
            if (barShown) NavigationBar(containerColor = c.card, tonalElevation = 0.dp, modifier = Modifier.height(62.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                for (t in AppTab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            if (t == AppTab.TRAIN) { train.loadSession(); puzzles.loadSession() }
                        },
                        icon = { Icon(t.icon, t.title) },
                        label = { Text(t.title, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.brandCardText, selectedTextColor = c.text, indicatorColor = c.brandCard,
                            unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).then(if (barShown) Modifier else Modifier.navigationBarsPadding())) {
            when (tab) {
                AppTab.GAMES -> GamesScreen(
                    games, settings,
                    onOpen = { item ->
                        review.open(item.game, item.me)
                        reviewOpen = true
                    },
                    onOpenSettings = { tab = AppTab.SETTINGS },
                    onOpenAnalysis = { analysisOpen = true },
                )
                AppTab.TRAIN -> TrainScreen(train, puzzles, settings, onOpenGames = { tab = AppTab.GAMES }, onDrillOpen = { trainOpen = it }, onOpenAnalysis = { analysisOpen = true }, onOpenEditor = { editorFen = wrklabs.chess.model.ChessBoard.DEFAULT_FEN }, onReviewGame = { game, me -> review.open(game, me); reviewOpen = true })
                AppTab.SETTINGS -> SettingsScreen(settings)
            }
        }
    }
    // A review is a screen of its own, over the tabs and their bar.
    if (reviewOpen) {
        // It takes every touch itself: nothing reaches the list under it.
        Box(Modifier.fillMaxSize().background(c.bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            ReviewScreen(
                review, settings,
                onBack = closeReview,
                onAnalyseFromHere = {
                    val state = review.state.value
                    analysis.openLine(state.info, review.lineToHere(), state.orientation)
                    review.close()
                    reviewOpen = false
                    analysisOpen = true
                },
            )
        }
    }
    // The analysis board is a screen of its own, over the tabs and their bar.
    if (analysisOpen && !reviewOpen) {
        BackHandler(enabled = editorFen == null) { analysisOpen = false }
        Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            AnalysisScreen(analysis, settings, onBack = { analysisOpen = false }, onEdit = { editorFen = it })
        }
    }
    // The editor is a screen of its own too.
    editorFen?.let { start ->
        Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            BoardEditor(
                start, settings,
                onBack = { editorFen = null },
                onAnalyse = { fen -> if (analysis.openFen(fen)) { editorFen = null; analysisOpen = true } },
                onPlay = { fen -> wrklabs.chess.play.PlayLaunch.request.value = wrklabs.chess.play.PlayRequest(fen, null, null, "A position you set up") },
            )
        }
    }
    }
}
