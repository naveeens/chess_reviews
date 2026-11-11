package wrklabs.chess.scan

/** What the tile reader made of a board: the pieces as FEN's first field, and how sure it was of each square (a1 to h8). */
class Reading(val placement: String, val confidences: List<Double>) {
    val minConfidence: Double get() = confidences.min()
    val meanConfidence: Double get() = confidences.average()
}

/** The pieces of a read as FEN text, and the two facts about a picture that a position needs and the pixels only hint at. */
object Placement {
    /** The tile reader's classes, in the order it gives them: empty, then white KQRBNP, then black kqrbnp. */
    private const val LABELS = "1KQRBNPkqrbnp"

    /** The reader's 64 by 13 probabilities, as a placement (ranks 8 to 1) with the confidence of each square. */
    fun fromProbabilities(probs: FloatArray): Reading {
        val names = StringBuilder()
        val confidences = ArrayList<Double>(64)
        for (i in 0 until 64) {
            var best = -1f
            var bestIdx = 0
            for (c in 0 until 13) {
                val p = probs[i * 13 + c]
                if (p > best) { best = p; bestIdx = c }
            }
            names.append(LABELS[bestIdx])
            confidences.add(best.toDouble())
        }
        val ranks = (7 downTo 0).map { r -> names.substring(r * 8, r * 8 + 8) }
        return Reading(ranks.joinToString("/").replace(Regex("1{2,}")) { it.value.length.toString() }, confidences)
    }

    /** The same pieces seen from the other side of the board: ranks and files both reversed. */
    fun flip(placement: String): String = placement.split("/").reversed().joinToString("/") { it.reversed() }

    private fun meanPawnRanks(placement: String): Pair<Double?, Double?> {
        val white = mutableListOf<Int>()
        val black = mutableListOf<Int>()
        placement.split("/").forEachIndexed { i, row ->
            val rank = 8 - i
            for (ch in row) { if (ch == 'P') white.add(rank) else if (ch == 'p') black.add(rank) }
        }
        return (if (white.isEmpty()) null else white.average()) to (if (black.isEmpty()) null else black.average())
    }

    /** A placement as read (always with White at the bottom), and whether it was turned round to put it right. */
    class Oriented(val placement: String, val blackAtBottom: Boolean)

    /**
     * Which way up the picture was, from the one fact about a position that has a direction: White's pawns are, on average,
     * on lower ranks than Black's. When a read has them the other way round it is turned. Without pawns on both sides there
     * is nothing to go by, and the read is kept as it is.
     */
    fun orient(placement: String): Oriented {
        fun naturalness(p: String): Double? {
            val (w, b) = meanPawnRanks(p)
            return if (w == null || b == null) null else b - w
        }
        val asRead = naturalness(placement)
        val rotated = flip(placement)
        val asRotated = naturalness(rotated)
        return if (asRead != null && asRotated != null && asRotated > asRead) Oriented(rotated, true) else Oriented(placement, false)
    }
}
