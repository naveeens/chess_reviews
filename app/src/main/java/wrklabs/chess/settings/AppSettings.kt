package wrklabs.chess.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import wrklabs.chess.ui.BoardTheme

/** How hard the engine looks: a depth for each position of a review and the most time it may take over it, and the depth of the live lines in Analyze. */
enum class ReviewSpeed(val label: String, val depth: Int, val moveMs: Long, val liveDepth: Int, val hint: String) {
    FAST("Fast", 10, 120, 16, "Reviews are about 10 seconds a game. Finds the blunders. Live analysis depth 16."),
    BALANCED("Balanced", 12, 300, 20, "Reviews are about 25 seconds a game. Live analysis depth 20."),
    DEEP("Deep", 16, 800, 26, "Reviews are about a minute a game. More accurate. Live analysis depth 26."),
}

data class AppSettings(
    val boardTheme: BoardTheme = BoardTheme.GREEN,
    val coordinates: Boolean = true,
    val highlightLastMove: Boolean = true,
    val showLegalMoves: Boolean = true,
    val showArrows: Boolean = true,
    /** The Analyze tab's engine: off, it neither searches nor shows the bar and lines. */
    val analysisEngine: Boolean = true,
    val showEvalBar: Boolean = true,
    /** On the Analyze tab: an arrow for what the opponent would play if it were their move. */
    val threatArrows: Boolean = false,
    /** On the Analyze tab: each move gets a mark (Best, Mistake…) once the engine has looked at it. */
    val moveFeedback: Boolean = false,
    val sound: Boolean = true,
    val chesscomUser: String = "",
    val lichessUser: String = "",
    val reviewSpeed: ReviewSpeed = ReviewSpeed.FAST,
    /** Start a review as soon as a game opens, unless it has one already. */
    val autoReview: Boolean = true,
    /** In a review, the engine answers the moves you make off the game with the opponent's reply. */
    val opponentReply: Boolean = false,
) {
    val hasAccounts get() = chesscomUser.isNotBlank() || lichessUser.isNotBlank()
}

/** The settings, kept in the app's own preferences file. Change one with [update]; collect [state] to follow them. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    fun update(change: AppSettings.() -> AppSettings) {
        val next = _state.value.change()
        _state.value = next
        write(next)
    }

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
        getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            boardTheme = prefs.enum("boardTheme", d.boardTheme),
            coordinates = prefs.getBoolean("coordinates", d.coordinates),
            highlightLastMove = prefs.getBoolean("highlightLastMove", d.highlightLastMove),
            showLegalMoves = prefs.getBoolean("showLegalMoves", d.showLegalMoves),
            showArrows = prefs.getBoolean("showArrows", d.showArrows),
            analysisEngine = prefs.getBoolean("analysisEngine", d.analysisEngine),
            showEvalBar = prefs.getBoolean("showEvalBar", d.showEvalBar),
            threatArrows = prefs.getBoolean("threatArrows", d.threatArrows),
            moveFeedback = prefs.getBoolean("moveFeedback", d.moveFeedback),
            sound = prefs.getBoolean("sound", d.sound),
            chesscomUser = prefs.getString("chesscomUser", "") ?: "",
            lichessUser = prefs.getString("lichessUser", "") ?: "",
            reviewSpeed = prefs.enum("reviewSpeed", d.reviewSpeed),
            autoReview = prefs.getBoolean("autoReview", d.autoReview),
            opponentReply = prefs.getBoolean("opponentReply", d.opponentReply),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString("boardTheme", s.boardTheme.name)
            .putBoolean("coordinates", s.coordinates)
            .putBoolean("highlightLastMove", s.highlightLastMove)
            .putBoolean("showLegalMoves", s.showLegalMoves)
            .putBoolean("showArrows", s.showArrows)
            .putBoolean("analysisEngine", s.analysisEngine)
            .putBoolean("showEvalBar", s.showEvalBar)
            .putBoolean("threatArrows", s.threatArrows)
            .putBoolean("moveFeedback", s.moveFeedback)
            .putBoolean("sound", s.sound)
            .putString("chesscomUser", s.chesscomUser)
            .putString("lichessUser", s.lichessUser)
            .putString("reviewSpeed", s.reviewSpeed.name)
            .putBoolean("autoReview", s.autoReview)
            .putBoolean("opponentReply", s.opponentReply)
            .apply()
    }
}
