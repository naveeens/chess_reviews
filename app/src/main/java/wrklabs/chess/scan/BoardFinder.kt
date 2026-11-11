package wrklabs.chess.scan

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A picture in grey: values 0..255, row by row. */
class GrayImage(val data: FloatArray, val width: Int, val height: Int)

/** The board's outer rectangle in the picture, in pixels. */
data class BoardBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

/**
 * Finds a chessboard in a picture.
 *
 * The picture's brightness changes are summed along every row and every column. The edges of a board's squares show up as
 * evenly spaced peaks; a run of seven or more peaks the same distance apart is the grid. Of the sub-grids that run allows,
 * the one that looks most like a checkerboard is taken. Boards whose grid lines are faint on one side are rebuilt from the
 * other side, since a board is square, and a box that is a whole number of squares out is moved back.
 */
object BoardFinder {
    private const val PEAK_KEEP_RATIO = 0.2
    private const val MIN_SEQ_LEN = 7
    private const val ERR_PX = 5.0
    /** How many candidate runs of lines are kept for the checkerboard test to choose between. */
    private const val MAX_CANDIDATE_SEQS = 5

    /** JavaScript's rounding: halves go up. */
    private fun round(x: Double): Double = floor(x + 0.5)

    private fun maxOf(arr: DoubleArray, from: Int, to: Int): Double {
        var m = Double.NEGATIVE_INFINITY
        for (i in from until to) if (arr[i] > m) m = arr[i]
        return m
    }

    /** The change from row to row (centre differences inside, one-sided at the edges). */
    private fun gradientRows(img: GrayImage): FloatArray {
        val (data, width, height) = Triple(img.data, img.width, img.height)
        val out = FloatArray(width * height)
        val last = (height - 1) * width
        for (x in 0 until width) {
            out[x] = (data[width + x].toDouble() - data[x]).toFloat()
            out[last + x] = (data[last + x].toDouble() - data[last - width + x]).toFloat()
        }
        for (y in 1 until height - 1) for (x in 0 until width) {
            out[y * width + x] = ((data[(y + 1) * width + x].toDouble() - data[(y - 1) * width + x]) / 2).toFloat()
        }
        return out
    }

    /** The change from column to column. */
    private fun gradientCols(img: GrayImage): FloatArray {
        val (data, width, height) = Triple(img.data, img.width, img.height)
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            out[row] = (data[row + 1].toDouble() - data[row]).toFloat()
            out[row + width - 1] = (data[row + width - 1].toDouble() - data[row + width - 2]).toFloat()
            for (x in 1 until width - 1) out[row + x] = ((data[row + x + 1].toDouble() - data[row + x - 1]) / 2).toFloat()
        }
        return out
    }

    /** For each row (or column): the upward changes summed, times the downward ones summed. */
    private fun houghResponse(grad: FloatArray, width: Int, height: Int, rows: Boolean): DoubleArray {
        val n = if (rows) height else width
        val pos = DoubleArray(n)
        val neg = DoubleArray(n)
        for (y in 0 until height) for (x in 0 until width) {
            val g = grad[y * width + x].toDouble()
            val i = if (rows) y else x
            if (g > 0) pos[i] += g else neg[i] -= g
        }
        return DoubleArray(n) { pos[it] * neg[it] }
    }

    /** Keeps a value only where it is the largest in a small window. */
    private fun nonmaxSuppress(arr: DoubleArray, winsize: Int = 5): DoubleArray {
        val out = arr.copyOf()
        val n = arr.size
        for (i in 0 until n) {
            val left = if (i == 0) 0.0 else maxOf(arr, max(0, i - winsize), i)
            val right = if (i >= n - 2) 0.0 else maxOf(arr, i + 1, min(n - 1, i + winsize))
            if (arr[i] < left || arr[i] <= right) out[i] = 0.0
        }
        return out
    }

    /** Every run of at least seven peaks that are the same distance apart (to within a few pixels). */
    private fun allSequences(seq: List<Int>): List<List<Int>> {
        if (seq.size < MIN_SEQ_LEN) return emptyList()
        val seqs = mutableListOf<List<Int>>()
        for (i in 0 until seq.size - 1) for (j in i + 1 until seq.size) {
            var duplicate = false
            for (prev in seqs) for (k in 0 until prev.size - 1) if (seq[i] == prev[k] && seq[j] == prev[k + 1]) duplicate = true
            if (duplicate) continue
            val d = (seq[j] - seq[i]).toDouble()
            if (d < ERR_PX) continue
            val s = mutableListOf(seq[i], seq[j])
            var n = s.last() + d
            while (true) {
                var bestIdx = 0
                var bestDist = Double.POSITIVE_INFINITY
                for (k in seq.indices) {
                    val dist = abs(seq[k] - n)
                    if (dist < bestDist) { bestDist = dist; bestIdx = k }
                }
                if (bestDist >= ERR_PX) break
                s.add(seq[bestIdx])
                n = s.last() + d
            }
            if (s.size >= MIN_SEQ_LEN) seqs.add(s)
        }
        return seqs
    }

    /** A run of more than nine lines is cut down to seven by dropping whichever end is the weaker. */
    private fun trimSequence(seq: List<Int>, vals: List<Double>): Pair<List<Int>, List<Double>> {
        var s = seq
        var v = vals
        if (s.size > 9) {
            while (s.size > 7) {
                if (v.first() > v.last()) { s = s.dropLast(1); v = v.dropLast(1) } else { s = s.drop(1); v = v.drop(1) }
            }
        }
        return s to v
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    /**
     * How much a crop looks like a checkerboard with a light square in the corner, on a 64 by 64 sample. Signed: a crop that
     * is an odd number of squares out has the colours the wrong way round and scores below zero.
     */
    private fun checkerboardScore(img: GrayImage, x0: Double, y0: Double, x1: Double, y1: Double): Double {
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0 || h <= 0) return 0.0
        var score = 0.0
        for (ty in 0 until 64) {
            val sy = (y0 + floor(ty * h / 64)).toInt()
            for (tx in 0 until 64) {
                val sx = (x0 + floor(tx * w / 64)).toInt()
                var px = 0.0
                if (sx >= 0 && sx < img.width && sy >= 0 && sy < img.height) px = img.data[sy * img.width + sx].toDouble()
                val parity = if ((tx / 8 + ty / 8) % 2 == 0) 1 else -1
                score += parity * px / 64
            }
        }
        return score
    }

    private class Candidate(val trimmed: List<Int>, val full: List<Int>)

    /** The best few runs of evenly spaced peaks along one axis, strongest first. */
    private fun rankedPeakSequences(hough: DoubleArray): List<Candidate> {
        val suppressed = nonmaxSuppress(hough)
        val peak = maxOf(suppressed, 0, suppressed.size)
        if (peak <= 0) return emptyList()
        val positions = mutableListOf<Int>()
        val values = mutableListOf<Double>()
        for (i in suppressed.indices) {
            val v = suppressed[i] / peak
            if (v >= PEAK_KEEP_RATIO) { positions.add(i); values.add(v) }
        }
        val seqs = allSequences(positions)
        if (seqs.isEmpty()) return emptyList()
        val valueAt = positions.indices.associate { positions[it] to values[it] }
        val scored = seqs.map { seq ->
            val vals = seq.map { valueAt[it] ?: 0.0 }
            val (ts, tv) = trimSequence(seq, vals)
            Candidate(ts, seq) to tv.sum() / tv.size
        }.sortedByDescending { it.second }
        val unique = mutableListOf<Candidate>()
        for ((candidate, _) in scored) {
            if (unique.none { it.full == candidate.full }) unique.add(candidate)
            if (unique.size >= MAX_CANDIDATE_SEQS) break
        }
        return unique
    }

    /**
     * A second guess at where the board is, a few pixels off the first: the checkerboard that fits best around it. A board
     * texture full of edges (a hatched diagram) can pull the grid a quarter of a square out; the caller reads both boxes and
     * keeps the one the tile reader is surer of.
     */
    fun snap(img: GrayImage, box: BoardBox): BoardBox {
        val tile = (box.x1 - box.x0) / 8
        val radius = max(2, (tile / 3).roundToIntJs())
        var bestDx = 0
        var bestDy = 0
        var bestScore = Double.NEGATIVE_INFINITY
        fun evalAt(dx: Int, dy: Int) {
            val score = checkerboardScore(img, box.x0 + dx, box.y0 + dy, box.x1 + dx, box.y1 + dy)
            if (score > bestScore) { bestScore = score; bestDx = dx; bestDy = dy }
        }
        var dy = -radius
        while (dy <= radius) { var dx = -radius; while (dx <= radius) { evalAt(dx, dy); dx += 2 }; dy += 2 }
        val cx = bestDx
        val cy = bestDy
        for (y in cy - 2..cy + 2) for (x in cx - 2..cx + 2) evalAt(x, y)
        return BoardBox(box.x0 + bestDx, box.y0 + bestDy, box.x1 + bestDx, box.y1 + bestDy)
    }

    private fun Double.roundToIntJs(): Int = round(this).toInt()

    /**
     * When the grid lines are clear along one axis but lost along the other, the board is rebuilt from the clear one: it is
     * square, so it is eight squares of that spacing along the other axis as well. The square window is slid along the weak
     * axis and the position that looks most like a checkerboard is kept.
     */
    private fun reconstructSquare(img: GrayImage, good: List<Int>, xAxis: Boolean): Pair<BoardBox, Double>? {
        val tile = median(good.drop(1).mapIndexed { i, v -> (v - good[i]).toDouble() })
        if (!(tile > 0)) return null
        val gA = good.first().toDouble()
        val gB = good.last().toDouble()
        if (gB - gA <= 0) return null
        val pad = round(tile)
        val extents = mutableListOf(gA to gB)
        var k = 0
        while (k + 7 <= good.size) {
            val e = (round(good[k].toDouble()) - pad) to (round(good[k + 6].toDouble()) + pad)
            if (extents.none { (a, b) -> abs(a - e.first) <= 2 && abs(b - e.second) <= 2 }) extents.add(e)
            k++
        }
        val limit = if (xAxis) img.height else img.width
        var best: BoardBox? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for ((eA, eB) in extents) {
            val span = eB - eA
            val step = max(2.0, round(tile / 8))
            var start = -span
            while (start <= limit) {
                val box = if (xAxis) BoardBox(eA, start, eB, start + span) else BoardBox(start, eA, start + span, eB)
                val score = checkerboardScore(img, box.x0, box.y0, box.x1, box.y1)
                if (score > bestScore) { bestScore = score; best = box }
                start += step
            }
        }
        return best?.let { it to bestScore }
    }

    private fun reconstructFromCandidates(img: GrayImage, candidates: List<Candidate>, xAxis: Boolean): BoardBox? {
        var best: BoardBox? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (c in candidates) {
            val r = reconstructSquare(img, c.full, xAxis)
            if (r != null && r.second > bestScore) { bestScore = r.second; best = r.first }
        }
        return best
    }

    /** A box that scores below zero has the colours inverted: the grid was found a square out. Tries one square each way. */
    private fun repairParity(img: GrayImage, box: BoardBox): BoardBox {
        val score = checkerboardScore(img, box.x0, box.y0, box.x1, box.y1)
        if (score >= 0) return box
        val tile = round((box.x1 - box.x0) / 8)
        var best = box
        var bestScore = score
        for ((sx, sy) in listOf(tile to 0.0, -tile to 0.0, 0.0 to tile, 0.0 to -tile)) {
            val c = BoardBox(box.x0 + sx, box.y0 + sy, box.x1 + sx, box.y1 + sy)
            val s = checkerboardScore(img, c.x0, c.y0, c.x1, c.y1)
            if (s > bestScore) { bestScore = s; best = c }
        }
        return best
    }

    /** Where the board is in the picture, or null when nothing in it is laid out like one. */
    fun find(img: GrayImage): BoardBox? {
        val gradY = gradientRows(img)
        val gradX = gradientCols(img)
        val houghRows = houghResponse(gradY, img.width, img.height, rows = true)
        val houghCols = houghResponse(gradX, img.width, img.height, rows = false)
        val candidatesY = rankedPeakSequences(houghRows)
        val candidatesX = rankedPeakSequences(houghCols)
        val linesY = candidatesY.firstOrNull()?.trimmed
        val linesX = candidatesX.firstOrNull()?.trimmed

        if (linesX != null && linesY == null) return reconstructFromCandidates(img, candidatesX, xAxis = true)?.let { repairParity(img, it) }
        if (linesY != null && linesX == null) return reconstructFromCandidates(img, candidatesY, xAxis = false)?.let { repairParity(img, it) }
        if (linesX == null || linesY == null) return null

        val dx = median(linesX.drop(1).mapIndexed { i, v -> (v - linesX[i]).toDouble() })
        val dy = median(linesY.drop(1).mapIndexed { i, v -> (v - linesY[i]).toDouble() })
        val subX = (0..linesX.size - 7).map { linesX.subList(it, it + 7) }
        val subY = (0..linesY.size - 7).map { linesY.subList(it, it + 7) }

        var best: BoardBox? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (sx in subX) for (sy in subY) {
            val x0 = round(sx[0] - dx)
            val x1 = round(sx[6] + dx)
            val y0 = round(sy[0] - dy)
            val y1 = round(sy[6] + dy)
            val score = checkerboardScore(img, x0, y0, x1, y1)
            if (score > bestScore) { bestScore = score; best = BoardBox(x0, y0, x1, y1) }
        }
        return best?.let { repairParity(img, it) }
    }
}
