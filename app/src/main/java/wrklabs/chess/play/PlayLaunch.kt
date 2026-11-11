package wrklabs.chess.play

import kotlinx.coroutines.flow.MutableStateFlow

/** A game to start from a given position, asked for from another screen (a review, an opening lesson). */
data class PlayRequest(
    val fen: String,
    /** The side the user plays; the side to move when null. */
    val userWhite: Boolean? = null,
    /** The bot's rating; the user's level when null. */
    val botElo: Int? = null,
    /** Where it came from, shown on the setup screen: "Your game, move 14". */
    val label: String = "A position",
)

/** The hand-over from the screen that asks to the Play screen. The Play screen takes the request and clears it. */
object PlayLaunch {
    val request = MutableStateFlow<PlayRequest?>(null)
}
