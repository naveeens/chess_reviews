package wrklabs.chess.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import wrklabs.chess.Graph
import wrklabs.chess.importer.FetchException
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.importer.ArchiveMemory
import wrklabs.chess.importer.OnlineFetcher

/** A game in the list: what the site said about it, and how it fared in review if it has one. */
data class GameItem(
    val game: ImportedGame,
    /** The user's name on that site. */
    val me: String,
    val accuracy: Double?,
    val reviewed: Boolean,
) {
    val myColorIsWhite get() = !game.black.equals(me, ignoreCase = true)
    val opponent get() = if (myColorIsWhite) game.black else game.white
    val opponentRating get() = if (myColorIsWhite) game.blackRating else game.whiteRating
    val myRating get() = if (myColorIsWhite) game.whiteRating else game.blackRating

    /** "win", "loss" or "draw" for the user. */
    val outcome: String
        get() = when (game.result) {
            "1-0" -> if (myColorIsWhite) "win" else "loss"
            "0-1" -> if (myColorIsWhite) "loss" else "win"
            "1/2-1/2" -> "draw"
            else -> "draw"
        }
}

data class GamesUiState(
    val items: List<GameItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** False until we know what the list is: the saved copy has been read, or a fetch has finished. Until then, an empty list means nothing. */
    val ready: Boolean = false,
)

class GamesViewModel : ViewModel() {
    private val settings = Graph.settings
    private val kv = Graph.kv
    private val repo = Graph.games
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ImportedGame.serializer())

    private val _state = MutableStateFlow(GamesUiState())
    val state: StateFlow<GamesUiState> = _state.asStateFlow()

    private var users = settings.value.chesscomUser.trim() to settings.value.lichessUser.trim()
    /** Every game known, by key. */
    private val known = java.util.concurrent.ConcurrentHashMap<String, ImportedGame>()
    private var reviewed: Map<String, wrklabs.chess.db.GameRecord> = emptyMap()
    private var fetchedOnce = false

    init {
        viewModelScope.launch {
            loadCache()
            publish()
            if (settings.value.hasAccounts) refresh()
        }
        viewModelScope.launch {
            settings.state.collect { s ->
                val now = s.chesscomUser.trim() to s.lichessUser.trim()
                if (now != users) {
                    users = now
                    // A new name means a different set of games.
                    known.clear()
                    fetchedOnce = false
                    publish()
                    if (s.hasAccounts) refresh()
                }
            }
        }
    }

    /** Reads the review database again, so accuracies appear after a game is reviewed. */
    fun reload() {
        viewModelScope.launch { publish() }
    }

    /** Gets what's new from the sites: everything the first time, then only games newer than the newest one held. */
    fun refresh() {
        val (chesscom, lichess) = users
        if (chesscom.isEmpty() && lichess.isEmpty() || _state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            fun newest(source: String) = known.values.filter { it.source == source }.maxOfOrNull { it.date } ?: 0L
            // Each month or hundred games shows up in the list as it arrives.
            val take = { batch: List<ImportedGame> ->
                batch.forEach { known[it.key] = it }
                viewModelScope.launch { publish() }
                Unit
            }
            val chesscomSince = newest("chesscom")
            val lichessSince = newest("lichess")
            val jobs = mutableListOf<suspend () -> Unit>()
            if (chesscom.isNotEmpty()) jobs += {
                // A name that is new to this phone has no memory yet; one with games already held was read in full by an older version.
                val memoryKey = "$MEMORY_PREFIX${chesscom.lowercase()}"
                val memory = kv.get(memoryKey)?.let { runCatching { json.decodeFromString(ArchiveMemory.serializer(), it) }.getOrNull() } ?: ArchiveMemory(complete = chesscomSince > 0)
                runCatching { OnlineFetcher.fetchChesscomGames(chesscom, chesscomSince, memory, take) { kv.set(memoryKey, json.encodeToString(ArchiveMemory.serializer(), memory)) } }.onFailure { errors += message(it) }
            }
            if (lichess.isNotEmpty()) jobs += {
                runCatching { OnlineFetcher.fetchLichessGames(lichess, lichessSince, take) }.onFailure { errors += message(it) }
            }
            for (job in jobs) job()
            saveCache()
            fetchedOnce = true
            publish()
            _state.value = _state.value.copy(loading = false, error = errors.firstOrNull())
        }
    }

    private fun message(e: Throwable) = (e as? FetchException)?.message ?: "Couldn't load your games."

    private suspend fun publish() {
        reviewed = repo.getAllGames().associateBy { it.key }
        val (chesscom, lichess) = users
        val items = known.values
            .filter { (it.source == "chesscom" && chesscom.isNotEmpty()) || (it.source == "lichess" && lichess.isNotEmpty()) }
            .sortedByDescending { it.date }
            .map { g ->
                val review = reviewed[g.key]
                GameItem(g, if (g.source == "chesscom") chesscom else lichess, review?.accuracy, review != null)
            }
        _state.value = _state.value.copy(items = items, ready = items.isNotEmpty() || fetchedOnce || !(chesscom.isNotEmpty() || lichess.isNotEmpty()))
    }

    // The list is kept in chunks: one database row can't be read back if it grows past a couple of megabytes.
    private suspend fun loadCache() {
        val chunks = kv.get(CHUNKS_KEY)?.toIntOrNull() ?: return
        for (i in 0 until chunks) {
            val games = kv.get("$CHUNK_PREFIX$i")?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: continue
            games.forEach { known[it.key] = it }
        }
    }

    private suspend fun saveCache() {
        val all = known.values.sortedByDescending { it.date }.chunked(CHUNK)
        all.forEachIndexed { i, chunk -> kv.set("$CHUNK_PREFIX$i", json.encodeToString(serializer, chunk)) }
        kv.set(CHUNKS_KEY, all.size.toString())
    }

    private companion object {
        const val CHUNK = 100
        const val CHUNKS_KEY = "games.imported.v2.chunks"
        const val CHUNK_PREFIX = "games.imported.v2."
        const val MEMORY_PREFIX = "games.chesscom.archives.v1."
    }
}
