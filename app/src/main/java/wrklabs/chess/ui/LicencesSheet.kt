package wrklabs.chess.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wrklabs.chess.ui.theme.Palette

private class Credit(val name: String, val licence: String, val note: String)

private val CREDITS = listOf(
    Credit(
        "Chess Reviews", "GNU GPL v3 or later",
        "This app is free software: you can use, study, change and share it under the GPL v3 or later. Source code: https://github.com/naveeens/chess_reviews",
    ),
    Credit(
        "Rookahead", "GNU GPL v3 or later",
        "This app's move grading and mistake puzzles are ported from Rookahead by bijoyv9 and contributors, with changes for Android: https://github.com/rookahead/rookahead",
    ),
    Credit(
        "Stockfish", "GNU GPL v3",
        "The chess engine that reviews and analyses your games, run unmodified on your phone. Source code and licence: https://github.com/official-stockfish/Stockfish",
    ),
    Credit(
        "Move sounds", "GNU GPL / AGPL",
        "The Standard sound set from the Lichess mobile app: https://github.com/lichess-org/mobile/tree/main/assets/sounds/standard",
    ),
    Credit(
        "Chess pieces", "GPL v2+ / BSD / GFDL",
        "The cburnett piece set by Colin M. L. Burnett, as used by Lichess.",
    ),
    Credit(
        "Puzzles", "CC0 (public domain)",
        "Positions from the Lichess puzzle database: https://database.lichess.org/#puzzles",
    ),
    Credit(
        "Opening names", "CC0 (public domain)",
        "From the lichess-org/chess-openings dataset: https://github.com/lichess-org/chess-openings",
    ),
    Credit(
        "DM Sans and JetBrains Mono", "SIL Open Font License 1.1",
        "Typefaces bundled in the app.",
    ),
    Credit(
        "Roboto Slab", "Apache License 2.0",
        "Typeface for headings, bundled in the app.",
    ),
    Credit(
        "Android libraries", "Apache License 2.0",
        "AndroidX, Jetpack Compose, Kotlin and kotlinx.coroutines / serialization.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesSheet(onDismiss: () -> Unit) {
    val c = Palette.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.cardHigh) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Open-source licences", style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(
                "Chess Reviews is built on the work below. Chess.com and Lichess games are fetched from their public APIs; the game data belongs to those sites and their players.",
                style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            for (credit in CREDITS) {
                Text(credit.name, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = 14.dp))
                Text(credit.licence, style = MaterialTheme.typography.labelMedium, color = c.brand)
                Text(credit.note, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
