package wrklabs.chess.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
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
    fun read(gray: GrayImage, box: BoardBox): Reading = readTiles(Tiles.extract(gray, box))

    /** What the network makes of the board inside [quad], straightened first. */
    fun read(gray: GrayImage, quad: Quad): Reading = readTiles(Straighten.tiles(gray, quad))

    private fun readTiles(tiles: FloatArray): Reading {
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

/** A picture, decoded: to show, and as grey to read, with where its board probably is as a first guess for the four corners. */
class LoadedPicture(val bitmap: Bitmap, val gray: GrayImage, val suggested: Quad)

/** Reads a board from a picture: decodes it, finds the board (or takes the corners it is given) and reads the squares. */
class BoardScanner(private val context: Context, private val model: TileModel = TileModel(context)) {
    /** The picture at [uri], decoded, with a guess at where the board is. */
    suspend fun load(uri: Uri): LoadedPicture = withContext(Dispatchers.Default) {
        val bitmap = decode(uri)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val gray = Tiles.gray(pixels, bitmap.width, bitmap.height)
        val box = runCatching { BoardFinder.find(gray) }.getOrNull()
        // A box that is not roughly square, or is tiny, is not a board: start from the middle of the picture instead.
        val plausible = box != null && (box.x1 - box.x0) > 0.1 * minOf(bitmap.width, bitmap.height) && Math.abs((box.x1 - box.x0) / (box.y1 - box.y0) - 1.0) < 0.12
        val suggested = if (plausible && box != null) Quad(box.x0, box.y0, box.x1, box.y1) else centred(bitmap.width, bitmap.height)
        LoadedPicture(bitmap, gray, suggested)
    }

    /** The board inside the four corners [quad], which the user has placed: straightened and read. */
    suspend fun scan(picture: LoadedPicture, quad: Quad): ScannedPosition = withContext(Dispatchers.Default) {
        val reading = model.read(picture.gray, quad)
        position(reading, reliable = reading.minConfidence >= BoardReader.CONFIDENCE_FLOOR, plausible = reading.placement.count { it == 'K' } == 1 && reading.placement.count { it == 'k' } == 1)
    }

    /** The position in the picture at [uri], found without help, or null when no board is found in it. */
    suspend fun scan(uri: Uri): ScannedPosition? = withContext(Dispatchers.Default) {
        val gray = load(uri).gray
        val scan = BoardReader.read(gray) { box -> model.read(gray, box) } ?: return@withContext null
        position(scan.reading, scan.reliable, scan.plausible)
    }

    private fun position(reading: Reading, reliable: Boolean, plausible: Boolean): ScannedPosition {
        val oriented = Placement.orient(reading.placement)
        // The confidences are in the order the board was read; turning the read round turns them round too.
        val unsure = reading.confidences.withIndex()
            .filter { it.value < BoardReader.CONFIDENCE_FLOOR }
            .map { Square.fromIndex(if (oriented.blackAtBottom) 63 - it.index else it.index) }
            .toSet()
        return ScannedPosition(oriented.placement, unsure, reliable, plausible, oriented.blackAtBottom)
    }

    /** A square covering most of the picture's middle, for when no board was found. */
    private fun centred(w: Int, h: Int): Quad {
        val side = minOf(w, h) * 0.8
        val x0 = (w - side) / 2
        val y0 = (h - side) / 2
        return Quad(x0, y0, x0 + side, y0 + side)
    }

    /** The picture, no larger than the board finder needs (a big screenshot is scaled down). */
    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1.0, MAX_DIM / max(info.size.width, info.size.height).toDouble())
            if (scale < 1.0) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        }
    }

    fun close() = model.close()

    companion object { private const val MAX_DIM = 1600.0 }
}
