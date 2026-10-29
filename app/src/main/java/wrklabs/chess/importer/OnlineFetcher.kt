package wrklabs.chess.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ImportedGame(
    val key: String,
    val source: String,
    val white: String,
    val black: String,
    val whiteRating: Int?,
    val blackRating: Int?,
    /** "1-0", "0-1", "1/2-1/2" or "*". */
    val result: String,
    val timeClass: String,
    val pgn: String,
    /** When it ended (ms). */
    val date: Long,
) {
    /** The side `user` played; white when the name matches neither. */
    fun colorOf(user: String): String = if (black.equals(user.trim(), ignoreCase = true)) "black" else "white"
}

class FetchException(message: String) : Exception(message)

/**
 * What the app remembers of a Chess.com account's archive: the ETag of each month it has read, so an unchanged month costs
 * nothing to ask about, and whether the whole history has been read (a first import can be interrupted).
 */
@Serializable
class ArchiveMemory(val etags: MutableMap<String, String> = mutableMapOf(), var complete: Boolean = false)

/** A page and the ETag it came with. */
private class Fetched(val body: String, val etag: String?)

/** The public Chess.com and Lichess APIs: no sign-in, just a username. */
object OnlineFetcher {
    /** Names the app and where to reach its maker, as Chess.com and Lichess ask of API clients. */
    private const val USER_AGENT = "WrkChess-Android/1.2 (+https://www.wrklabs.in/chess.html)"


    /**
     * Your standard games, newest first, month by month. Chess.com can only be asked for a whole month, so with `sinceMs`
     * it reads just the month of the newest game it holds and anything after, and each month is asked about with the ETag
     * from last time: an unchanged one comes back empty. Until the whole history has been read (a first import, or one that
     * was cut short) it goes on back, skipping the months it already has. `onBatch` gets each month as it arrives.
     */
    suspend fun fetchChesscomGames(
        username: String, sinceMs: Long, memory: ArchiveMemory,
        onBatch: (List<ImportedGame>) -> Unit = {}, onMemory: suspend () -> Unit = {},
    ): List<ImportedGame> = withContext(Dispatchers.IO) {
        val user = username.trim().lowercase()
        val archives = JSONObject(get("https://api.chess.com/pub/player/$user/games/archives", "Chess.com")).optJSONArray("archives")
        val out = mutableListOf<ImportedGame>()
        if (archives != null) {
            val sinceMonth = if (sinceMs > 0) monthKey(sinceMs) else null
            var reachedStart = true
            for (month in archives.length() - 1 downTo 0) {
                val url = archives.getString(month)
                // Everything older than the newest game held is already in, once the whole history is.
                if (memory.complete && sinceMonth != null && url.substringAfter("/games/") < sinceMonth) { reachedStart = false; break }
                val page = getIfChanged(url, "Chess.com", memory.etags[url]) ?: continue
                val games = JSONObject(page.body).optJSONArray("games")
                val batch = mutableListOf<ImportedGame>()
                if (games != null) for (i in games.length() - 1 downTo 0) {
                    val g = games.getJSONObject(i)
                    val pgn = g.optString("pgn", "")
                    if (pgn.isBlank() || g.optString("rules") != "chess") continue
                    val date = g.optLong("end_time") * 1000
                    if (date <= sinceMs) continue
                    val white = g.optJSONObject("white")
                    val black = g.optJSONObject("black")
                    batch += ImportedGame(
                        key = "chesscom:" + g.optString("url").substringAfterLast('/'),
                        source = "chesscom",
                        white = white?.optString("username") ?: "White",
                        black = black?.optString("username") ?: "Black",
                        whiteRating = white?.optInt("rating"),
                        blackRating = black?.optInt("rating"),
                        result = chesscomResult(white?.optString("result").orEmpty(), black?.optString("result").orEmpty()),
                        timeClass = g.optString("time_class", "rapid"),
                        pgn = pgn,
                        date = date,
                    )
                }
                if (batch.isNotEmpty()) {
                    out += batch
                    onBatch(batch)
                }
                // Only a month that has been read in full is remembered, so a cut-off import picks up where it stopped.
                page.etag?.let { memory.etags[url] = it }
                onMemory()
            }
            if (reachedStart && !memory.complete) { memory.complete = true; onMemory() }
        }
        out
    }

    /** The month a time falls in, as Chess.com's archives name it ("2026/10"). */
    fun monthKey(ms: Long): String {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC)
        return "%04d/%02d".format(t.year, t.monthValue)
    }

    private fun chesscomResult(white: String, black: String): String = when {
        white == "win" -> "1-0"
        black == "win" -> "0-1"
        white.isEmpty() && black.isEmpty() -> "*"
        else -> "1/2-1/2"
    }

    /** Your standard games, newest first, read as they stream in; `sinceMs` limits it to newer ones. */
    suspend fun fetchLichessGames(username: String, sinceMs: Long = 0, onBatch: (List<ImportedGame>) -> Unit = {}): List<ImportedGame> = withContext(Dispatchers.IO) {
        val user = username.trim()
        val url = "https://lichess.org/api/games/user/$user?pgnInJson=true&clocks=false&opening=false" + if (sinceMs > 0) "&since=${sinceMs + 1}" else ""
        val out = mutableListOf<ImportedGame>()
        val pending = mutableListOf<ImportedGame>()
        stream(url, "Lichess", mapOf("Accept" to "application/x-ndjson")) { line ->
            if (line.isBlank()) return@stream
            val g = runCatching { JSONObject(line) }.getOrNull() ?: return@stream
            val pgn = g.optString("pgn", "")
            if (pgn.isBlank() || g.optString("variant", "standard") != "standard") return@stream
            val players = g.optJSONObject("players")
            fun side(color: String) = players?.optJSONObject(color)
            fun name(color: String) = side(color)?.optJSONObject("user")?.optString("name") ?: "Anonymous"
            val game = ImportedGame(
                key = "lichess:" + g.optString("id"),
                source = "lichess",
                white = name("white"),
                black = name("black"),
                whiteRating = side("white")?.optInt("rating"),
                blackRating = side("black")?.optInt("rating"),
                result = when (g.optString("winner")) {
                    "white" -> "1-0"
                    "black" -> "0-1"
                    else -> if (g.optString("status") in setOf("draw", "stalemate")) "1/2-1/2" else "*"
                },
                timeClass = g.optString("speed", "rapid"),
                pgn = pgn,
                date = g.optLong("lastMoveAt", g.optLong("createdAt")),
            )
            out += game
            pending += game
            if (pending.size >= 100) {
                onBatch(pending.toList())
                pending.clear()
            }
        }
        if (pending.isNotEmpty()) onBatch(pending.toList())
        out
    }

    /** Reads a response a line at a time, so a long history shows up as it arrives. */
    private fun stream(url: String, site: String, headers: Map<String, String>, onLine: (String) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            when (val code = conn.responseCode) {
                in 200..299 -> conn.inputStream.bufferedReader().use { it.forEachLine(onLine) }
                404 -> throw FetchException("$site has no player with that username.")
                429 -> throw FetchException("$site says to slow down. Try again in a minute.")
                else -> throw FetchException("$site answered with an error ($code).")
            }
        } catch (e: FetchException) {
            throw e
        } catch (e: java.io.IOException) {
            throw FetchException("Couldn't reach $site. Check your connection.")
        } finally {
            conn.disconnect()
        }
    }

    /** The page, or null when `etag` says it hasn't changed. */
    private fun getIfChanged(url: String, site: String, etag: String?): Fetched? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (etag != null) conn.setRequestProperty("If-None-Match", etag)
            when (val code = conn.responseCode) {
                in 200..299 -> return Fetched(conn.inputStream.bufferedReader().use { it.readText() }, conn.getHeaderField("ETag"))
                304 -> return null
                404 -> throw FetchException("$site has no player with that username.")
                429 -> throw FetchException("$site says to slow down. Try again in a minute.")
                else -> throw FetchException("$site answered with an error ($code).")
            }
        } catch (e: FetchException) {
            throw e
        } catch (e: java.io.IOException) {
            throw FetchException("Couldn't reach $site. Check your connection.")
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String, site: String, headers: Map<String, String> = emptyMap()): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            when (val code = conn.responseCode) {
                in 200..299 -> return conn.inputStream.bufferedReader().use { it.readText() }
                404 -> throw FetchException("$site has no player with that username.")
                429 -> throw FetchException("$site says to slow down. Try again in a minute.")
                else -> throw FetchException("$site answered with an error ($code).")
            }
        } catch (e: FetchException) {
            throw e
        } catch (e: java.io.IOException) {
            throw FetchException("Couldn't reach $site. Check your connection.")
        } finally {
            conn.disconnect()
        }
    }
}
