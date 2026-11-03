/*
 * Copyright (C) 2026 naveen s
 * SPDX-License-Identifier: GPL-3.0-or-later. See NOTICE.
 */
package wrklabs.chess.training

import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID

/** One chapter of a lesson: its headers (Event is the title, FEN the start if not the usual one) and the tree of lines. */
data class LessonChapter(val headers: Map<String, String>, val tree: GameTree) {
    val title: String get() = headers["Event"] ?: headers["Opening"] ?: "Chapter"
}

class LessonPgnException(message: String) : IllegalArgumentException(message)

/**
 * Reads PGN with everything a lesson needs: variations to any depth, {comments}, $NAGs and the
 * lichess [%cal] arrows and [%csl] squares. A file may hold several chapters, one game each.
 * A move that is not legal is an error, not a skipped move, since a lesson with a hole in it teaches the wrong thing.
 */
object LessonPgn {
    fun parseAll(text: String): List<LessonChapter> = splitGames(text).map(::parseChapter)

    fun parseChapter(game: String): LessonChapter {
        val headers = linkedMapOf<String, String>()
        val body = StringBuilder()
        for (line in game.lines()) {
            val t = line.trim()
            val m = HEADER.matchEntire(t)
            if (m != null && body.isEmpty()) headers[m.groupValues[1]] = m.groupValues[2] else body.append(line).append('\n')
        }
        val start = headers["FEN"]?.takeIf { it.isNotBlank() } ?: ChessBoard.DEFAULT_FEN
        return LessonChapter(headers, parseMoves(body.toString(), start))
    }

    private val HEADER = Regex("""\[(\w+)\s+"(.*)"\]""")
    private val TOKEN = Regex("""\{[^}]*\}|;[^\n]*|\(|\)|\$\d+|[^\s(){}$;]+""")
    private val NUMBER = Regex("""^\d+\.+""")
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
    private val CAL = Regex("""\[%cal\s+([^\]]*)\]""")
    private val CSL = Regex("""\[%csl\s+([^\]]*)\]""")
    private val OTHER_TAG = Regex("""\[%\w+\s+[^\]]*\]""")
    private val GLYPHS = mapOf("!!" to 3, "??" to 4, "!?" to 5, "?!" to 6, "!" to 1, "?" to 2)

    private fun splitGames(text: String): List<String> {
        val games = mutableListOf<StringBuilder>()
        var inMoves = false
        for (line in text.lines()) {
            val isHeader = HEADER.matches(line.trim())
            if (isHeader && (inMoves || games.isEmpty())) { games.add(StringBuilder()); inMoves = false }
            else if (!isHeader && line.isNotBlank()) { if (games.isEmpty()) games.add(StringBuilder()); inMoves = true }
            games.lastOrNull()?.append(line)?.append('\n')
        }
        return games.map { it.toString() }.filter { it.isNotBlank() }
    }

    private fun parseMoves(text: String, startFen: String): GameTree {
        var tree = GameTree.create(startFen)
        // `cur` is where the next move goes; `before` is where the last move came from, which a "(" branches off.
        var cur = ROOT_ID
        var before: String? = null
        val stack = ArrayDeque<Pair<String, String?>>()
        var count = 0
        for (m in TOKEN.findAll(text)) {
            val tok = m.value
            when {
                tok.startsWith("{") -> {
                    val note = readNote(tok.substring(1, tok.length - 1))
                    val node = tree.nodes.getValue(cur)
                    tree = tree.annotate(
                        cur,
                        comment = joinNotes(node.comment, note.text),
                        nags = node.nags,
                        arrows = node.arrows + note.arrows,
                        marks = node.marks + note.marks,
                    )
                }
                tok.startsWith(";") -> Unit
                tok == "(" -> {
                    val back = before ?: throw LessonPgnException("A variation before any move, after $count moves")
                    stack.addLast(cur to before)
                    cur = back
                    before = tree.nodes[back]?.parentId
                }
                tok == ")" -> {
                    val (c, b) = stack.removeLastOrNull() ?: throw LessonPgnException("Unmatched ')' after $count moves")
                    cur = c; before = b
                }
                tok.startsWith("$") -> {
                    val node = tree.nodes.getValue(cur)
                    tree = tree.annotate(cur, node.comment, node.nags + tok.substring(1).toInt(), node.arrows, node.marks)
                }
                tok in RESULTS -> Unit
                else -> {
                    var san = tok.replace(NUMBER, "")
                    if (san.isEmpty()) continue
                    san = san.replace(Regex("^0-0-0"), "O-O-O").replace(Regex("^0-0"), "O-O")
                    val glyph = san.takeLastWhile { it == '!' || it == '?' }
                    san = san.dropLast(glyph.length).trimEnd('+', '#')
                    if (san == "--" || san == "Z0") throw LessonPgnException("Null move in a lesson, after $count moves")
                    val parent = tree.nodes.getValue(cur)
                    val board = ChessBoard.fromFen(parent.fen)
                    val legal = board.legalMoves().filter { it.san.trimEnd('+', '#') == san }
                    if (legal.size != 1) throw LessonPgnException("'$tok' is ${if (legal.isEmpty()) "not legal" else "ambiguous"} after ${tree.pathSan(cur)}")
                    val (t, id) = tree.addMove(cur, legal.single().uci) ?: throw LessonPgnException("Could not play '$tok'")
                    tree = t
                    before = cur; cur = id
                    count++
                    GLYPHS[glyph]?.let { tree = tree.annotate(id, nags = listOf(it)) }
                }
            }
        }
        if (stack.isNotEmpty()) throw LessonPgnException("Unclosed '(' at the end")
        return tree
    }

    private class Note(val text: String, val arrows: List<String>, val marks: List<String>)

    private fun readNote(raw: String): Note {
        fun list(re: Regex) = re.findAll(raw).flatMap { it.groupValues[1].split(',').map(String::trim).filter(String::isNotEmpty) }.toList()
        val text = raw.replace(CAL, "").replace(CSL, "").replace(OTHER_TAG, "").replace(Regex("\\s+"), " ").trim()
        return Note(text, list(CAL), list(CSL))
    }

    private fun joinNotes(a: String?, b: String): String? = listOfNotNull(a?.takeIf { it.isNotBlank() }, b.takeIf { it.isNotBlank() }).joinToString(" ").ifEmpty { null }

    private fun GameTree.pathSan(id: String): String {
        val path = generateSequence(nodes[id]) { n -> n.parentId?.let { nodes[it] } }.mapNotNull { it.san }.toList().reversed()
        return if (path.isEmpty()) "the start" else path.joinToString(" ")
    }
}
