package wrklabs.chess.db

data class GameRecord(
    val key: String,
    val v: Int = 1,
    val reviewAt: Long = System.currentTimeMillis(),
    val rules: Int? = 5,
    val source: String,
    val color: String,
    val me: String,
    val opponent: String,
    val result: String?,
    val endedAt: Long = System.currentTimeMillis(),
    val exactTime: Long = System.currentTimeMillis(),
    val rating: Int? = null,
    val oppRating: Int? = null,
    val timeClass: String = "rapid",
    val base: Int? = null,
    val inc: Int? = null,
    val timeout: Int = 0,
    val accuracy: Double? = null,
    val openingEco: String? = null,
    val openingName: String? = null,
    val bookPlies: Int = 0,
    val plies: Int = 0,
    val tagsV: Int = 0,
    val pgn: String? = null
)

data class MoveRecord(
    val gameKey: String,
    val i: Int,
    val cls: String,
    val winBefore: Double,
    val winAfter: Double,
    val phase: String,
    val piece: String,
    val clock: Double? = null,
    val spent: Double? = null,
    val missedMate: Int = 0,
    val uci: String? = null,
    val san: String? = null,
    val fen: String? = null,
    val best: String? = null,
    val bestSan: String? = null,
    val reply: String? = null,
    val replySan: String? = null,
    val missed: String? = null,
    val allowed: String? = null
)

data class CardRecord(
    val id: String,
    val kind: String = "mistake",
    val box: Int = 0,
    val due: Long = System.currentTimeMillis(),
    val reps: Int = 0,
    val lapses: Int = 0,
    val last: Long? = null,
    val added: Long = System.currentTimeMillis()
)
