package wrklabs.chess.play

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import wrklabs.chess.db.KeyValue
import wrklabs.chess.importer.ImportedGame

/** A game in progress, as kept between launches so it can be continued. */
@Serializable
data class PlayRecord(
    val startFen: String,
    val ucis: List<String>,
    val userWhite: Boolean,
    val botElo: Int,
    val userElo: Int,
    val startedAt: Long,
    val takebacks: Int = 0,
    val hints: Int = 0,
    /** Whether the level moves with the result of this game. */
    val auto: Boolean = false,
)

/** How one finished game went for the user. [result] is "win", "loss" or "draw". */
@Serializable
data class PlayResult(val at: Long, val botElo: Int, val userElo: Int, val result: String, val userWhite: Boolean, val hints: Int = 0, val takebacks: Int = 0)

/** What Play remembers: the game in progress, how past games went, the level to play at next, and the finished games for the list. */
class PlayStore(private val kv: KeyValue) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _gamesChanged = MutableStateFlow(0)
    /** Counts every finished game added, so the Games list knows to look again. */
    val gamesChanged: StateFlow<Int> = _gamesChanged.asStateFlow()

    suspend fun active(): PlayRecord? = kv.get(ACTIVE)?.let { runCatching { json.decodeFromString(PlayRecord.serializer(), it) }.getOrNull() }
    suspend fun saveActive(r: PlayRecord) = kv.set(ACTIVE, json.encodeToString(PlayRecord.serializer(), r))
    suspend fun clearActive() = kv.remove(ACTIVE)

    suspend fun results(): List<PlayResult> = kv.get(RESULTS)?.let { runCatching { json.decodeFromString(ListSerializer(PlayResult.serializer()), it) }.getOrNull() }.orEmpty()
    suspend fun addResult(r: PlayResult) = kv.set(RESULTS, json.encodeToString(ListSerializer(PlayResult.serializer()), (results() + r).takeLast(MAX_RESULTS)))

    /** Forgets everything (for tests, and a future "reset my record"). */
    suspend fun clearAll() { listOf(ACTIVE, RESULTS, LEVEL, GAMES).forEach { kv.remove(it) } }

    suspend fun level(): Int? = kv.get(LEVEL)?.toIntOrNull()
    suspend fun setLevel(level: Int) = kv.set(LEVEL, level.toString())

    suspend fun games(): List<ImportedGame> = kv.get(GAMES)?.let { runCatching { json.decodeFromString(ListSerializer(ImportedGame.serializer()), it) }.getOrNull() }.orEmpty()
    suspend fun addGame(g: ImportedGame) {
        kv.set(GAMES, json.encodeToString(ListSerializer(ImportedGame.serializer()), (games().filter { it.key != g.key } + g).takeLast(MAX_GAMES)))
        _gamesChanged.value++
    }

    companion object {
        private const val ACTIVE = "play.active.v1"
        private const val RESULTS = "play.results.v1"
        private const val LEVEL = "play.level.v1"
        private const val GAMES = "play.games.v1"
        const val MAX_RESULTS = 200
        const val MAX_GAMES = 500

        /** "W3 L2 D1" for the latest [n] results. */
        fun record(results: List<PlayResult>, n: Int = 10): String {
            val last = results.takeLast(n)
            val w = last.count { it.result == "win" }; val l = last.count { it.result == "loss" }; val d = last.count { it.result == "draw" }
            return "W$w L$l D$d"
        }
    }
}
