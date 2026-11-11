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
    /** What is searched, filtered and sorted. Kept while the app runs, not between launches. */
    val query: GameQuery = GameQuery(),
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
        // A game finished against Maia joins the list.
        viewModelScope.launch { Graph.play.gamesChanged.collect { publish() } }
        viewModelScope.launch {
            settings.state.collect { s ->
                val now = s.chesscomUser.trim() to s.lichessUser.trim()
                if (now != users) {
                    val before = users
                    users = now
                    // Only the account that changed loses its games: taking one away leaves the other's alone.
                    val keep = keepAfterAccountChange(known.values, before, now).mapTo(HashSet()) { it.key }
                    known.keys.filter { it !in keep }.forEach { known.remove(it) }
                    // What was remembered of a Chess.com archive goes with the games, or a name added back later would skip its months.
                    if (before.first != now.first) listOf(before.first, now.first).filter { it.isNotEmpty() }.forEach { kv.remove("$MEMORY_PREFIX${it.lowercase()}") }
                    fetchedOnce = false
                    publish()
                    saveCache()
                    if (s.hasAccounts) refresh()
                }
            }
        }
    }

    fun setQuery(change: GameQuery.() -> GameQuery) { _state.value = _state.value.let { it.copy(query = it.query.change()) } }

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
                val saved = kv.get(memoryKey)?.let { runCatching { json.decodeFromString(ArchiveMemory.serializer(), it) }.getOrNull() }
                // A memory with no games to go with it is stale (the list was emptied since): start the history again.
                val memory = if (chesscomSince > 0 && saved != null) saved else ArchiveMemory()
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
        val online = known.values
            .filter { (it.source == "chesscom" && chesscom.isNotEmpty()) || (it.source == "lichess" && lichess.isNotEmpty()) }
            .map { g -> GameItem(g, if (g.source == "chesscom") chesscom else lichess, reviewed[g.key]?.accuracy, reviewed.containsKey(g.key)) }
        // Games played here, against Maia, need no account.
        val played = Graph.play.games().map { g -> GameItem(g, PlayViewModel.ME, reviewed[g.key]?.accuracy, reviewed.containsKey(g.key)) }
        val items = (online + played).sortedByDescending { it.game.date }
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
        val (chesscom, lichess) = users
        // Only games of the accounts now set up are kept.
        val all = known.values.filter { (it.source == "chesscom" && chesscom.isNotEmpty()) || (it.source == "lichess" && lichess.isNotEmpty()) }.sortedByDescending { it.date }.chunked(CHUNK)
        all.forEachIndexed { i, chunk -> kv.set("$CHUNK_PREFIX$i", json.encodeToString(serializer, chunk)) }
        kv.set(CHUNKS_KEY, all.size.toString())
    }

    private companion object {
        const val CHUNK = 100
        const val CHUNKS_KEY = "games.imported.v2.chunks"
        const val CHUNK_PREFIX = "games.imported.v2."
        // v2: the v1 memories could be out of step with the games held (see keepAfterAccountChange), so they are not trusted.
        const val MEMORY_PREFIX = "games.chesscom.archives.v2."
    }
}

/**
 * The games to keep when the accounts change from [before] to [now] (Chess.com name, Lichess name): those of an account whose
 * name is the same. An account that was removed or renamed takes only its own games with it.
 */
internal fun keepAfterAccountChange(games: Collection<ImportedGame>, before: Pair<String, String>, now: Pair<String, String>): List<ImportedGame> =
    games.filter { g ->
        when (g.source) {
            "chesscom" -> before.first == now.first
            "lichess" -> before.second == now.second
            else -> true
        }
    }
