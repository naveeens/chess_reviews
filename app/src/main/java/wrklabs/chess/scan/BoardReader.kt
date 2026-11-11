package wrklabs.chess.scan

/** A picture read for a board: what the tile reader saw, where the board was, and how far it can be trusted. */
class BoardScan(
    val reading: Reading,
    val box: BoardBox,
    /** Every square was read with at least [BoardReader.CONFIDENCE_FLOOR]; otherwise the reader was unsure of some. */
    val reliable: Boolean,
    /** One king each: what every real position has. A read without is likely not the board that was meant. */
    val plausible: Boolean,
)

/**
 * Finds the board in a picture and reads its squares. The reader of squares is passed in, so this part is plain Kotlin that
 * the tests run on pictures without a phone.
 */
object BoardReader {
    /** A square read with less certainty than this is one to check. */
    const val CONFIDENCE_FLOOR = 0.7
    /** A page can hold more than one board-like region; an empty or kingless one is masked and the picture scanned again. */
    private const val MAX_SCAN_PASSES = 3
    private const val EMPTY = "8/8/8/8/8/8/8/8"

    private fun plausible(placement: String) = placement.count { it == 'K' } == 1 && placement.count { it == 'k' } == 1

    /**
     * The board at [box] and the same box nudged to the checkerboard that fits best are both read, and the read the reader is
     * surer of wins: a textured board can fool the grid search by a fraction of a square.
     */
    private fun scanOnce(gray: GrayImage, classify: (BoardBox) -> Reading): BoardScan? {
        val box = BoardFinder.find(gray) ?: return null
        var bestBox = box
        var best = classify(box)
        val snapped = BoardFinder.snap(gray, box)
        if (snapped != box) {
            val read = classify(snapped)
            if (read.meanConfidence > best.meanConfidence) { best = read; bestBox = snapped }
        }
        return BoardScan(best, bestBox, best.minConfidence >= CONFIDENCE_FLOOR, plausible(best.placement))
    }

    /** The region flattened to grey, so the finder cannot settle on it again. */
    private fun mask(gray: GrayImage, box: BoardBox): GrayImage {
        val data = gray.data.copyOf()
        val x0 = maxOf(0, Math.floor(box.x0).toInt())
        val y0 = maxOf(0, Math.floor(box.y0).toInt())
        val x1 = minOf(gray.width, Math.ceil(box.x1).toInt())
        val y1 = minOf(gray.height, Math.ceil(box.y1).toInt())
        for (y in y0 until y1) if (x1 > x0) java.util.Arrays.fill(data, y * gray.width + x0, y * gray.width + x1, 128f)
        return GrayImage(data, gray.width, gray.height)
    }

    /** The board in [gray], or null when there isn't one. A read that is not plausible is returned only when nothing better turns up. */
    fun read(gray: GrayImage, classify: (BoardBox) -> Reading): BoardScan? {
        var working = gray
        var fallback: BoardScan? = null
        repeat(MAX_SCAN_PASSES) {
            val result = scanOnce(working, classify) ?: return fallback
            if (result.plausible) return result
            val fallbackIsEmpty = fallback == null || fallback!!.reading.placement == EMPTY
            if (fallbackIsEmpty && (fallback == null || result.reading.placement != EMPTY)) fallback = result
            working = mask(working, result.box)
        }
        return fallback
    }
}
