package wrklabs.chess.engine

data class InfoLine(
    val depth: Int,
    val seldepth: Int? = null,
    val multipv: Int = 1,
    val score: Score,
    val bound: Bound? = null,
    val wdl: List<Int>? = null,
    val nodes: Long? = null,
    val nps: Long? = null,
    val time: Long? = null,
    val pv: List<String>
)

enum class Bound {
    LOWER, UPPER
}

object UciParser {

    /**
     * Parses an `info ... pv ...` line. Returns null for lines without a scored PV.
     */
    fun parseInfo(line: String): InfoLine? {
        if (!line.startsWith("info ")) return null
        val tokens = line.split("\\s+".toRegex())

        var depth: Int? = null
        var seldepth: Int? = null
        var multipv = 1
        var nodes: Long? = null
        var nps: Long? = null
        var time: Long? = null
        var wdl: List<Int>? = null
        var score: Score? = null
        var bound: Bound? = null
        var pv: List<String>? = null

        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> {
                    if (i + 1 < tokens.size) depth = tokens[++i].toIntOrNull()
                }
                "seldepth" -> {
                    if (i + 1 < tokens.size) seldepth = tokens[++i].toIntOrNull()
                }
                "multipv" -> {
                    if (i + 1 < tokens.size) multipv = tokens[++i].toIntOrNull() ?: 1
                }
                "nodes" -> {
                    if (i + 1 < tokens.size) nodes = tokens[++i].toLongOrNull()
                }
                "nps" -> {
                    if (i + 1 < tokens.size) nps = tokens[++i].toLongOrNull()
                }
                "time" -> {
                    if (i + 1 < tokens.size) time = tokens[++i].toLongOrNull()
                }
                "wdl" -> {
                    if (i + 3 < tokens.size) {
                        val w = tokens[++i].toIntOrNull()
                        val d = tokens[++i].toIntOrNull()
                        val l = tokens[++i].toIntOrNull()
                        if (w != null && d != null && l != null) {
                            wdl = listOf(w, d, l)
                        }
                    }
                }
                "score" -> {
                    if (i + 2 < tokens.size) {
                        val kind = tokens[++i]
                        val value = tokens[++i].toIntOrNull() ?: 0
                        score = when (kind) {
                            "cp" -> Score.Centipawns(value)
                            "mate" -> Score.Mate(value)
                            else -> null
                        }
                    }
                    if (i + 1 < tokens.size) {
                        when (tokens[i + 1]) {
                            "lowerbound" -> {
                                bound = Bound.LOWER
                                i++
                            }
                            "upperbound" -> {
                                bound = Bound.UPPER
                                i++
                            }
                        }
                    }
                }
                "pv" -> {
                    pv = tokens.subList(i + 1, tokens.size).filter { it.isNotBlank() }
                    break
                }
                "string" -> return null
            }
            i++
        }

        if (depth == null || score == null || pv.isNullOrEmpty()) return null
        return InfoLine(
            depth = depth,
            seldepth = seldepth,
            multipv = multipv,
            score = score,
            bound = bound,
            wdl = wdl,
            nodes = nodes,
            nps = nps,
            time = time,
            pv = pv
        )
    }

    /**
     * Parses `bestmove e2e4 [ponder e7e5]`. Returns null for `(none)`.
     */
    fun parseBestMove(line: String): String? {
        if (!line.startsWith("bestmove")) return null
        val tokens = line.split("\\s+".toRegex())
        if (tokens.size < 2) return null
        val move = tokens[1]
        return if (move == "(none)") null else move
    }
}
