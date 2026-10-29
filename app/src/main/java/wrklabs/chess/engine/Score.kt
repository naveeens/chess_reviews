package wrklabs.chess.engine

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

enum class PieceColor {
    WHITE, BLACK;

    fun opponent(): PieceColor = if (this == WHITE) BLACK else WHITE
}

@Serializable
sealed class Score {
    @Serializable
    data class Centipawns(val value: Int) : Score()

    @Serializable
    data class Mate(val value: Int, val matedColor: PieceColor? = null) : Score()
}

/**
 * Flips a side-to-move score so it's always from White's point of view.
 */
fun Score.toWhitePov(turn: PieceColor): Score {
    return when (this) {
        is Score.Mate -> {
            if (value == 0) Score.Mate(0, turn)
            else if (turn == PieceColor.WHITE) this
            else Score.Mate(-value)
        }
        is Score.Centipawns -> {
            if (turn == PieceColor.WHITE) this
            else Score.Centipawns(-value)
        }
    }
}

/**
 * The White-POV score for a position where `turn` has been checkmated.
 */
fun checkmatedScore(turn: PieceColor): Score = Score.Mate(0, turn)

/**
 * White's winning chances in [0, 100], from a White-POV score.
 * Uses the logistic model Lichess fitted to real games.
 */
fun Score.winPercent(): Double {
    return when (this) {
        is Score.Mate -> {
            if (value == 0) {
                when (matedColor) {
                    PieceColor.WHITE -> 0.0
                    PieceColor.BLACK -> 100.0
                    null -> 50.0
                }
            } else if (value > 0) 100.0 else 0.0
        }
        is Score.Centipawns -> {
            val cp = max(-1000.0, min(1000.0, value.toDouble()))
            50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * cp)) - 1.0)
        }
    }
}

/**
 * Formats score for display: "+1.24", "−0.50", "M3", "−M2", "0.00", "#".
 * Expects a White-POV score.
 */
fun Score.format(): String {
    return when (this) {
        is Score.Mate -> {
            if (value == 0) "#"
            else {
                val sign = if (value < 0) "−" else ""
                "${sign}M${abs(value)}"
            }
        }
        is Score.Centipawns -> {
            val pawns = value / 100.0
            if (abs(pawns) < 0.005) "0.00"
            else {
                val sign = if (pawns > 0) "+" else "−"
                String.format("%s%.2f", sign, abs(pawns))
            }
        }
    }
}

/**
 * Short form for the eval bar: "1.2", "M3", "0-1", "1-0".
 */
fun Score.formatShort(): String {
    return when (this) {
        is Score.Mate -> {
            if (value == 0) {
                if (matedColor == PieceColor.WHITE) "0-1" else "1-0"
            } else {
                "M${abs(value)}"
            }
        }
        is Score.Centipawns -> {
            val pawns = abs(value / 100.0)
            if (pawns >= 10.0) String.format("%.0f", pawns)
            else String.format("%.1f", pawns)
        }
    }
}

/**
 * Which side the score favours (for badge colouring). Returns null if drawish.
 */
fun Score.favoured(): PieceColor? {
    return when (this) {
        is Score.Mate -> {
            if (value == 0) {
                when (matedColor) {
                    PieceColor.WHITE -> PieceColor.BLACK
                    PieceColor.BLACK -> PieceColor.WHITE
                    null -> null
                }
            } else if (value > 0) PieceColor.WHITE else PieceColor.BLACK
        }
        is Score.Centipawns -> {
            if (abs(value) < 5) null
            else if (value > 0) PieceColor.WHITE else PieceColor.BLACK
        }
    }
}
