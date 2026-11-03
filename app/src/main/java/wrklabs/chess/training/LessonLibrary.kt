/*
 * Copyright (C) 2026 naveen s
 * SPDX-License-Identifier: GPL-3.0-or-later. See NOTICE.
 */
package wrklabs.chess.training

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.MoveNode

/** One chapter on the shelf. `key` is stable across launches, so a screen can remember where you were. */
data class LessonEntry(val file: String, val index: Int, val chapter: LessonChapter) {
    val key: String get() = "$file#$index"
    val title: String get() = chapter.title
    val side: PieceColor get() = if (chapter.headers["Learn"]?.startsWith("b", ignoreCase = true) == true) PieceColor.BLACK else PieceColor.WHITE
    val eco: String? get() = chapter.headers["ECO"]
    val moves: Int get() = chapter.tree.nodes.size - 1
    val lines: Int get() = chapter.tree.nodes.values.count { it.children.isEmpty() && it.id != "root" }
}

/** The lessons that ship in assets/lessons, read once. A file that cannot be read is skipped and logged, not fatal. */
object LessonLibrary {
    @Volatile private var cache: List<LessonEntry>? = null

    suspend fun load(context: Context): List<LessonEntry> = cache ?: withContext(Dispatchers.IO) {
        val assets = context.applicationContext.assets
        assets.list("lessons").orEmpty().filter { it.endsWith(".pgn") }.sorted().flatMap { name ->
            runCatching {
                val text = assets.open("lessons/$name").bufferedReader().use { it.readText() }
                LessonPgn.parseAll(text).mapIndexed { i, c -> LessonEntry(name, i, c) }
            }.getOrElse { android.util.Log.e("LessonLibrary", "Could not read $name", it); emptyList() }
        }.also { cache = it }
    }
}

/** How a move is written in a lesson: "3. Bc4" for white, "3... Bc5" for black. */
fun MoveNode.label(): String {
    val san = san ?: return ""
    return if (ply % 2 == 1) "${(ply + 1) / 2}. $san" else "${ply / 2}... $san"
}

/** The signs a lesson uses, from PGN's numeric annotation glyphs. Ones with no sign are left out. */
fun nagSymbol(nag: Int): String? = when (nag) {
    1 -> "!"; 2 -> "?"; 3 -> "!!"; 4 -> "??"; 5 -> "!?"; 6 -> "?!"
    10 -> "="; 13 -> "∞"; 14 -> "+="; 15 -> "=+"; 16 -> "±"; 17 -> "∓"; 18 -> "+−"; 19 -> "−+"
    else -> null
}

/** What a node's glyphs say, written after its move: "?!  ⩱". */
fun MoveNode.signs(): String = nags.mapNotNull(::nagSymbol).joinToString(" ")

/** The moves from the start to this node, as a line of text with numbers. */
fun GameTree.lineTo(nodeId: String): String {
    val path = generateSequence(nodes[nodeId]) { n -> n.parentId?.let { nodes[it] } }.toList().reversed().filter { it.san != null }
    val out = StringBuilder()
    path.forEachIndexed { i, n -> if (i > 0) out.append("  "); out.append(if (n.ply % 2 == 1 || i == 0) n.label() else n.san) }
    return out.toString()
}

/** The id of the card for one position of one lesson chapter: "line:italian.pgn#0:e2e4.e7e5". */
fun lineCardId(entryKey: String, nodeId: String): String = "line:$entryKey:$nodeId"

/** The chapter key and node id a line card was made for, or null if the id is not one. */
fun parseLineCardId(id: String): Pair<String, String>? {
    val parts = id.split(':')
    return if (parts.size == 3 && parts[0] == "line" && parts[1].isNotEmpty() && parts[2].isNotEmpty()) parts[1] to parts[2] else null
}
