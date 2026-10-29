package wrklabs.chess.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

private const val TAG = "StockfishEngine"

data class EngineLine(
    val multipv: Int,
    val depth: Int,
    /** Always from White's point of view. */
    val score: Score,
    val wdl: List<Int>? = null,
    val pv: List<String>
)

data class AnalysisResult(
    val fen: String,
    val depth: Int,
    val lines: List<EngineLine>,
    val bestMove: String?,
    val nodes: Long? = null,
    val nps: Long? = null
)

data class AnalyzeRequest(
    val fen: String,
    val multiPv: Int = 1,
    val depth: Int? = null,
    val movetime: Long? = null,
    val nodes: Long? = null,
    val fresh: Boolean = false,
    val threads: Int? = null,
    val hashMb: Int? = null,
    val strength: Strength? = null,
    val onUpdate: ((AnalysisResult) -> Unit)? = null
)

data class Strength(
    val skill: Int? = null,
    val elo: Int? = null
)

sealed class EngineStatus {
    object Uninitialized : EngineStatus()
    object Loading : EngineStatus()
    data class Ready(val name: String, val threads: Int) : EngineStatus()
    data class Error(val message: String) : EngineStatus()
}

class StockfishEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /** One search at a time. */
    private val searchLock = Mutex()
    private val startLock = Mutex()

    private var process: Process? = null
    private var writer: BufferedWriter? = null

    /** Every line the engine prints, in order, read by one thread so no search can eat another's output. */
    private val output = Channel<String>(Channel.UNLIMITED)
    private var readerThread: Thread? = null

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Uninitialized)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var activeThreads: Int = 1
    private var activeHash: Int = 0

    private fun locateBinary(): File {
        val libDir = context.applicationInfo.nativeLibraryDir
        val primary = File(libDir, "libstockfish.so")
        if (primary.exists() && primary.canExecute()) return primary

        // Fallback: extract from APK zip if not extracted by system
        val fallback = File(context.filesDir, "libstockfish.so")
        if (!fallback.exists() || fallback.length() == 0L) {
            try {
                java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { zip ->
                    val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { ab -> zip.getEntry("lib/$ab/libstockfish.so") != null } ?: "arm64-v8a"
                    val entry = zip.getEntry("lib/$abi/libstockfish.so")
                    if (entry != null) {
                        zip.getInputStream(entry).use { input -> fallback.outputStream().use { input.copyTo(it) } }
                        fallback.setExecutable(true, false)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback extraction failed: ${e.message}")
            }
        }
        return if (fallback.exists() && fallback.canExecute()) fallback else primary
    }

    suspend fun start() = startLock.withLock {
        withContext(Dispatchers.IO) {
            if (_status.value is EngineStatus.Ready) return@withContext
            _status.value = EngineStatus.Loading
            try {
                val binary = locateBinary()
                if (!binary.exists() || !binary.canExecute()) {
                    throw IllegalStateException("Stockfish binary not found or not executable at ${binary.absolutePath}")
                }
                val proc = ProcessBuilder(binary.absolutePath).redirectErrorStream(true).start()
                process = proc
                writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
                val reader = BufferedReader(InputStreamReader(proc.inputStream))

                // The handshake is read directly; after it, one thread owns the output.
                send("uci")
                var engineName = "Stockfish"
                while (true) {
                    val line = reader.readLine() ?: throw IllegalStateException("Stockfish closed before answering")
                    if (line.startsWith("id name ")) engineName = line.removePrefix("id name ").trim()
                    if (line == "uciok") break
                }
                activeThreads = maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), 4))
                send("setoption name Threads value $activeThreads")
                send("setoption name Hash value 64")
                activeHash = 64
                send("isready")
                while (reader.readLine()?.let { it != "readyok" } != false) { /* wait */ }

                readerThread = Thread({
                    try {
                        while (true) output.trySend(reader.readLine() ?: break)
                    } catch (_: Exception) {
                    }
                    output.trySend(CLOSED)
                }, "stockfish-reader").apply { isDaemon = true; start() }

                _status.value = EngineStatus.Ready(engineName, activeThreads)
                Log.i(TAG, "Stockfish ready: $engineName with $activeThreads threads")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Stockfish", e)
                _status.value = EngineStatus.Error(e.message ?: "Failed to start Stockfish")
            }
        }
    }

    private fun send(cmd: String) {
        val w = writer ?: return
        try {
            w.write(cmd)
            w.newLine()
            w.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Could not send '$cmd': ${e.message}")
        }
    }

    /** Reads engine output until a line starts with `prefix`; false if it takes longer than `ms` or the engine is gone. */
    private suspend fun waitFor(prefix: String, ms: Long): Boolean =
        withTimeoutOrNull(ms) {
            while (true) {
                val line = output.receive()
                if (line == CLOSED) return@withTimeoutOrNull false
                if (line.startsWith(prefix)) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") false
        } ?: false

    /**
     * Searches a position. Cancel the calling coroutine to stop it; the engine is brought to rest before the
     * next search starts, so results never mix.
     */
    suspend fun analyze(req: AnalyzeRequest): AnalysisResult? {
        if (_status.value !is EngineStatus.Ready) {
            start()
            if (_status.value !is EngineStatus.Ready) return null
        }
        return searchLock.withLock { withContext(Dispatchers.IO) { search(req) } }
    }

    private suspend fun search(req: AnalyzeRequest): AnalysisResult? {
        val turn = if (req.fen.split(" ").getOrNull(1) == "b") PieceColor.BLACK else PieceColor.WHITE

        if (req.fresh) send("ucinewgame")
        if (req.threads != null && req.threads != activeThreads) {
            activeThreads = req.threads
            send("setoption name Threads value $activeThreads")
        }
        if (req.hashMb != null && req.hashMb != activeHash) {
            activeHash = req.hashMb
            send("setoption name Hash value $activeHash")
        }
        if (req.strength?.elo != null) {
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value ${req.strength.elo}")
        } else if (req.strength?.skill != null) {
            send("setoption name UCI_LimitStrength value false")
            send("setoption name Skill Level value ${req.strength.skill}")
        } else {
            send("setoption name UCI_LimitStrength value false")
        }
        send("setoption name MultiPV value ${req.multiPv}")
        // Anything the engine still has to say is read up to here, so what follows belongs to this search.
        send("isready")
        if (!waitFor("readyok", 5000)) return null
        send("position fen ${req.fen}")
        val go = StringBuilder("go")
        req.depth?.let { go.append(" depth $it") }
        req.movetime?.let { go.append(" movetime $it") }
        req.nodes?.let { go.append(" nodes $it") }
        if (req.depth == null && req.movetime == null && req.nodes == null) go.append(" infinite")
        send(go.toString())

        val lineMap = mutableMapOf<Int, EngineLine>()
        var depth = 0
        var nodes: Long? = null
        var nps: Long? = null
        fun result(best: String?) = AnalysisResult(req.fen, depth, (1..req.multiPv).mapNotNull { lineMap[it] }, best, nodes, nps)

        var finished = false
        try {
            while (true) {
                val line = output.receive()
                if (line == CLOSED) {
                    finished = true
                    _status.value = EngineStatus.Error("Stockfish stopped unexpectedly")
                    return result(null)
                }
                if (line.startsWith("bestmove")) {
                    finished = true
                    return result(UciParser.parseBestMove(line))
                }
                val info = UciParser.parseInfo(line) ?: continue
                // A bound is a guess that the search is still narrowing down; only settled scores are shown.
                if (info.bound != null) continue
                depth = maxOf(depth, info.depth)
                info.nodes?.let { nodes = it }
                info.nps?.let { nps = it }
                lineMap[info.multipv] = EngineLine(info.multipv, info.depth, info.score.toWhitePov(turn), info.wdl, info.pv)
                req.onUpdate?.invoke(result(null))
            }
        } finally {
            if (!finished) {
                // Cancelled mid-search: stop it and read its last word before anything else is sent.
                withContext(NonCancellable) {
                    send("stop")
                    waitFor("bestmove", 3000)
                }
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) { send("stop") }

    fun destroy() {
        scope.cancel()
        try {
            send("quit")
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        writer = null
        _status.value = EngineStatus.Uninitialized
    }

    private companion object {
        const val CLOSED = "\u0000closed"
    }
}
