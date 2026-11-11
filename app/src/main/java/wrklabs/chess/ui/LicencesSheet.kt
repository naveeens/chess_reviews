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
        "fenshot and tensorflow_chessbot", "MIT licence",
        "The code that finds and cuts up a board in a picture is adapted from fenshot, https://github.com/scoriiu/fenshot, Copyright (c) 2026 SORTINO LABS S.R.L. (kibitzo.com), which builds on tensorflow_chessbot, https://github.com/Elucidation/tensorflow_chessbot, Copyright (c) 2016 Sameer Ansari. Both are used under the MIT licence:\n\n" +
            "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n" +
            "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.",
    ),
    Credit(
        "Maia-3", "GNU AGPL v3",
        "The model that says how often players of a given rating choose each move (assets/maia/maia3-5m.onnx) is Maia-3 (5M) by the Computational Social Science Lab at the University of Toronto, https://github.com/CSSLab/maia3, under the GNU AGPL v3. The code that prepares positions for it is a Kotlin port of its Python. Please cite: Monroe, Eilender, Chalmers, Tang and Anderson, \"Chessformer: A Unified Architecture for Chess Modeling\", ICLR 2026. This app's source, which includes that code, is public at https://github.com/naveeens/chess_reviews",
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
