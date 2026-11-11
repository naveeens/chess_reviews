package wrklabs.chess.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wrklabs.chess.model.Square
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** The tile reader: a small network that says what is on each of a board's 64 squares. It loads when first used and stays loaded. */
class TileModel(private val context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    @Synchronized
    private fun session(): OrtSession = session ?: run {
        val bytes = context.assets.open(MODEL).use { it.readBytes() }
        env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
    }.also { session = it }

    /** What the network makes of the board at [box]. */
    fun read(gray: GrayImage, box: BoardBox): Reading {
        val tiles = Tiles.extract(gray, box)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(tiles), longArrayOf(64, Tiles.TILE_INPUT_SIZE.toLong())).use { input ->
            session().run(mapOf("tiles" to input)).use { out ->
                val buffer = (out[0] as OnnxTensor).floatBuffer
                val probs = FloatArray(64 * 13)
                buffer.get(probs)
                return Placement.fromProbabilities(probs)
            }
        }
    }

    fun close() { session?.close(); session = null }

    companion object { const val MODEL = "scan/tiles.onnx" }
}

/** A position read from a picture, with White at the bottom of the placement, and the squares the reader was unsure of. */
class ScannedPosition(
    val placement: String,
    /** Squares whose contents are worth checking. */
    val unsure: Set<Square>,
    /** Every square was read with confidence; false when some were not. */
    val reliable: Boolean,
    /** One king each side. */
    val plausible: Boolean,
    /** The picture showed the board from Black's side, and the read was turned round to put White at the bottom. */
    val fromBlackSide: Boolean,
)

/** Reads a board from a picture: decodes it, finds the board and reads the squares. */
class BoardScanner(private val context: Context, private val model: TileModel = TileModel(context)) {
    /** The position in the picture at [uri], or null when no board is found in it. */
    suspend fun scan(uri: Uri): ScannedPosition? = withContext(Dispatchers.Default) {
        val gray = decode(uri)
        val scan = BoardReader.read(gray) { box -> model.read(gray, box) } ?: return@withContext null
        val oriented = Placement.orient(scan.reading.placement)
        // The confidences are in the order the board was read; turning the read round turns them round too.
        val unsure = scan.reading.confidences.withIndex()
            .filter { it.value < BoardReader.CONFIDENCE_FLOOR }
            .map { Square.fromIndex(if (oriented.blackAtBottom) 63 - it.index else it.index) }
            .toSet()
        ScannedPosition(oriented.placement, unsure, scan.reliable, scan.plausible, oriented.blackAtBottom)
    }

    /** The picture as grey, no larger than the board finder needs (a big screenshot is scaled down). */
    private fun decode(uri: Uri): GrayImage {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1.0, MAX_DIM / max(info.size.width, info.size.height).toDouble())
            if (scale < 1.0) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val gray = Tiles.gray(pixels, bitmap.width, bitmap.height)
        bitmap.recycle()
        return gray
    }

    fun close() = model.close()

    companion object { private const val MAX_DIM = 1600.0 }
}
