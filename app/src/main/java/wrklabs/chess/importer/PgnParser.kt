package wrklabs.chess.importer

data class ParsedPgn(
    val headers: Map<String, String>,
    val moves: List<String>,
    val rawPgn: String
) {
    val white: String get() = headers["White"] ?: "White"
    val black: String get() = headers["Black"] ?: "Black"
    val result: String get() = headers["Result"] ?: "*"
    val event: String get() = headers["Event"] ?: "Casual Game"
    val eco: String? get() = headers["ECO"]
    val opening: String? get() = headers["Opening"]
    val whiteElo: Int? get() = headers["WhiteElo"]?.toIntOrNull()
    val blackElo: Int? get() = headers["BlackElo"]?.toIntOrNull()
    val timeControl: String? get() = headers["TimeControl"]
}

object PgnParser {

    fun parse(pgnText: String): ParsedPgn {
        val headers = mutableMapOf<String, String>()
        val moveSanList = mutableListOf<String>()

        val lines = pgnText.lines()
        val moveTextBuilder = StringBuilder()

        var readingHeaders = true
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (headers.isNotEmpty()) {
                    readingHeaders = false
                }
                continue
            }

            if (readingHeaders && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val content = trimmed.substring(1, trimmed.length - 1)
                val firstSpace = content.indexOf(' ')
                if (firstSpace > 0) {
                    val key = content.substring(0, firstSpace).trim()
                    var value = content.substring(firstSpace + 1).trim()
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length - 1)
                    }
                    headers[key] = value
                }
            } else {
                readingHeaders = false
                moveTextBuilder.append(" ").append(trimmed)
            }
        }

        // Clean move text: strip comments {...}, strip variations (...), strip annotations like $1, $2
        var rawMoves = moveTextBuilder.toString()
        // Strip comments {...}
        rawMoves = rawMoves.replace(Regex("\\{[^}]*\\}"), "")
        // Strip variations (...)
        rawMoves = rawMoves.replace(Regex("\\([^)]*\\)"), "")
        // Strip NAGs like $1, $2
        rawMoves = rawMoves.replace(Regex("\\$\\d+"), "")

        // Split tokens
        val tokens = rawMoves.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (token in tokens) {
            // Skip move numbers like "1.", "12...", "1..."
            if (token.matches(Regex("\\d+\\.+"))) continue
            if (token == "1-0" || token == "0-1" || token == "1/2-1/2" || token == "*") {
                // Game result token reached
                continue
            }
            // If token starts with a move number like "1.e4" or "12...Nf6", strip it (a "0-0" has no dot, so it stays)
            var clean = token.replace(Regex("^\\d+\\.+"), "")
            // Annotation marks ("Bxc6?!", "Qe1!!") and castling written with zeros are the same moves; "e.p." is a note, not a move.
            clean = clean.trimEnd('!', '?').replace(Regex("^0-0-0"), "O-O-O").replace(Regex("^0-0"), "O-O")
            if (clean == "e.p." || clean == "ep") continue
            // Anything that cannot start a move (an evaluation like "+-" or "=") is left out too.
            if (!clean.matches(Regex("^[a-hNBRQKO].*"))) continue
            moveSanList.add(clean)
        }

        return ParsedPgn(headers = headers, moves = moveSanList, rawPgn = pgnText.trim())
    }
}
