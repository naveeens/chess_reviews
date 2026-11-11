package wrklabs.chess.maia

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import wrklabs.chess.play.Outlook
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Maia-3 (the 5M model), which says how often players of a given rating play each legal move.
 * The model loads on first use and stays loaded; a prediction takes a few milliseconds to a few tens on a phone.
 */
class MaiaEngine(private val context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val loading = Mutex()

    private suspend fun session(): OrtSession = session ?: loading.withLock {
        session ?: withContext(Dispatchers.IO) {
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            env.createSession(bytes, options)
        }.also { session = it }
    }

    /** Maia's view of a position: the odds of each legal move, and the chances for the side to move. */
    class Prediction(val moves: Map<String, Float>, val outlook: Outlook)

    /**
     * How likely each legal move is for a player rated [selfElo] against [oppoElo] (the same by default).
     * [fens] are the positions leading to this one, oldest first, the last being the one to move in; Maia reads up to eight.
     */
    suspend fun predict(fens: List<String>, selfElo: Int, oppoElo: Int = selfElo): Map<String, Float> = predictFull(fens, selfElo, oppoElo).moves

    /** [predict], with the win, draw and loss chances for the side to move as well. */
    suspend fun predictFull(fens: List<String>, selfElo: Int, oppoElo: Int = selfElo): Prediction {
        val ses = session()
        return withContext(Dispatchers.Default) {
            val (logits, value) = run(ses, MaiaEncoder.tokens(fens), selfElo, oppoElo)
            Prediction(MaiaEncoder.probabilities(fens.last(), logits), Outlook.fromLogits(loss = value[0], draw = value[1], win = value[2]))
        }
    }

    /** The raw outputs, for the tests: 4352 move scores and three value scores (loss, draw, win). */
    internal fun run(ses: OrtSession, tokens: FloatArray, selfElo: Int, oppoElo: Int): Pair<FloatArray, FloatArray> {
        val inputs = mapOf(
            "tokens" to OnnxTensor.createTensor(env, FloatBuffer.wrap(tokens), longArrayOf(1, 64, MaiaEncoder.WIDTH.toLong())),
            "self_elo" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(selfElo.toLong())), longArrayOf(1)),
            "oppo_elo" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(oppoElo.toLong())), longArrayOf(1)),
        )
        try {
            ses.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                return (result[0].value as Array<FloatArray>)[0] to (result[1].value as Array<FloatArray>)[0]
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    fun close() { session?.close(); session = null }

    companion object {
        const val MODEL = "maia/maia3-5m.onnx"
    }
}
