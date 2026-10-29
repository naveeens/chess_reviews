package wrklabs.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wrklabs.chess.engine.EngineLine
import wrklabs.chess.engine.EngineStatus
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.engine.Score
import wrklabs.chess.engine.format
import wrklabs.chess.ui.theme.Mono
import wrklabs.chess.ui.theme.Palette

/** A result in words. */
fun resultText(result: String) = when (result) {
    "1-0" -> "White won"
    "0-1" -> "Black won"
    "1/2-1/2" -> "Draw"
    else -> "Result unknown"
}

/** A player's score in a finished game: "1", "0" or "½"; null while the result is unknown. */
fun scoreOf(color: PieceColor, result: String?): String? = when (result) {
    "1-0" -> if (color == PieceColor.WHITE) "1" else "0"
    "0-1" -> if (color == PieceColor.WHITE) "0" else "1"
    "1/2-1/2" -> "½"
    else -> null
}

/** An evaluation for a table cell: "+0.3", "−1.2", "M3". */
fun evalLabel(score: Score?): String = score?.format() ?: ""

/** A game's accuracy as a chip reads it: rounded to the nearest whole number, as the review's table does to one decimal. */
fun accuracyChip(accuracy: Double): String = "${Math.round(accuracy)}%"
