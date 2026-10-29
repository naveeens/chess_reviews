package wrklabs.chess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Palette

enum class AppTab(val title: String, val icon: ImageVector) {
    GAMES("Games", Icons.Default.History),
    ANALYZE("Analyze", Icons.Default.Analytics),
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
    var tab by remember { mutableStateOf(if (settings.hasAccounts) AppTab.GAMES else AppTab.GAMES) }
    val c = Palette.colors

    // Only one screen drives the engine at a time: the analysis board steps aside while a review is open.
    LaunchedEffect(tab, reviewOpen) { analysis.setActive(tab == AppTab.ANALYZE && !reviewOpen) }
    val closeReview = {
        review.close()
        reviewOpen = false
        games.reload()
    }
    // On the board, back goes to the summary; on the summary, back leaves the review.
    BackHandler(enabled = reviewOpen) { if (review.state.value.showBoard) review.showSummary() else closeReview() }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = c.bg,
        bottomBar = {
            NavigationBar(containerColor = c.card, tonalElevation = 0.dp, modifier = Modifier) {
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.GAMES -> GamesScreen(
                    games, settings,
                    onOpen = { item ->
                        review.open(item.game, item.me)
                        reviewOpen = true
                    },
                    onOpenSettings = { tab = AppTab.SETTINGS },
                )
                AppTab.ANALYZE -> AnalysisScreen(analysis, settings)
                AppTab.TRAIN -> TrainScreen(train, puzzles, settings, onOpenGames = { tab = AppTab.GAMES })
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
                    tab = AppTab.ANALYZE
                },
            )
        }
    }
    }
}
