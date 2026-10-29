package wrklabs.chess.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** A puzzle from the bundled set: the position before the opponent's move, and the line that follows it. */
data class Puzzle(
    val id: String,
    val seq: Int,
    val level: String,
    val fen: String,
    /** The opponent's move first, then yours, then theirs, and so on, ending on yours. */
    val moves: List<String>,
    val rating: Int,
    val themes: String,
    val gameUrl: String,
) {
    /** How many moves you make. */
    val yourMoves: Int get() = moves.size / 2

    companion object {
        /** One row of the bundled file: Seq,Level,PuzzleId,FEN,Moves,Rating,Themes,GameUrl. Null when it isn't one. */
        fun parse(line: String): Puzzle? {
            val f = line.trim().split(',')
            if (f.size < 8) return null
            val moves = f[4].split(' ').filter { it.isNotEmpty() }
            val seq = f[0].toIntOrNull() ?: return null
            val rating = f[5].toIntOrNull() ?: return null
            if (moves.size < 2) return null
            return Puzzle(id = f[2], seq = seq, level = f[1], fen = f[3], moves = moves, rating = rating, themes = f[6], gameUrl = f[7])
        }
    }
}

/** A puzzle as it comes up: new (box 0) or one of the ones due again. */
data class PuzzleCard(val puzzle: Puzzle, val box: Int, val isNew: Boolean)

data class PuzzleSummary(val total: Int, val due: Int, val learned: Int)

class PuzzleRepository(private val dbHelper: AppDatabaseHelper, private val context: Context) {

    companion object {
        /** New puzzles brought in each day, on top of the ones coming back. */
        const val NEW_PER_DAY = 30
        private const val ASSET = "puzzles.csv"

        fun cardId(puzzleId: String) = "puzzle#$puzzleId"

        private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Loads the bundled set the first time. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val has = db.rawQuery("SELECT count(*) FROM puzzle", null).use { it.moveToNext() && it.getInt(0) > 0 }
        if (has) return@withContext
        val puzzles = context.assets.open(ASSET).bufferedReader().useLines { lines -> lines.drop(1).mapNotNull(Puzzle::parse).toList() }
        db.beginTransaction()
        try {
            for (p in puzzles) {
                db.insertWithOnConflict("puzzle", null, ContentValues().apply {
                    put("id", p.id); put("seq", p.seq); put("level", p.level); put("fen", p.fen); put("moves", p.moves.joinToString(" "))
                    put("rating", p.rating); put("themes", p.themes); put("game_url", p.gameUrl)
                }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** How many of today's new puzzles are still to come. */
    private fun newLeft(now: Long): Int {
        val db = dbHelper.readableDatabase
        val today = db.rawQuery("SELECT count(*) FROM card WHERE kind = 'puzzle' AND added >= ?", arrayOf(startOfDay(now).toString())).use { if (it.moveToNext()) it.getInt(0) else 0 }
        return maxOf(0, NEW_PER_DAY - today)
    }

    /** The ones due again, soonest first, then today's new ones in the set's order. */
    suspend fun session(now: Long = System.currentTimeMillis()): List<PuzzleCard> = withContext(Dispatchers.IO) {
        ensureSeeded()
        val db = dbHelper.readableDatabase
        val list = mutableListOf<PuzzleCard>()
        db.rawQuery(
            """
            SELECT p.id, p.seq, p.level, p.fen, p.moves, p.rating, p.themes, p.game_url, c.box
            FROM card c JOIN puzzle p ON c.id = 'puzzle#' || p.id
            WHERE c.kind = 'puzzle' AND c.due <= ?
            ORDER BY c.due ASC, p.seq ASC
            """.trimIndent(),
            arrayOf(now.toString()),
        ).use { while (it.moveToNext()) list += read(it, isNew = false) }
        val left = newLeft(now)
        if (left > 0) {
            db.rawQuery(
                """
                SELECT p.id, p.seq, p.level, p.fen, p.moves, p.rating, p.themes, p.game_url, 0 AS box
                FROM puzzle p LEFT JOIN card c ON c.id = 'puzzle#' || p.id
                WHERE c.id IS NULL
                ORDER BY p.seq ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(left.toString()),
            ).use { while (it.moveToNext()) list += read(it, isNew = true) }
        }
        list
    }

    suspend fun summary(now: Long = System.currentTimeMillis()): PuzzleSummary = withContext(Dispatchers.IO) {
        ensureSeeded()
        val db = dbHelper.readableDatabase
        val total = db.rawQuery("SELECT count(*) FROM puzzle", null).use { if (it.moveToNext()) it.getInt(0) else 0 }
        val back = db.rawQuery("SELECT count(*) FROM card WHERE kind = 'puzzle' AND due <= ?", arrayOf(now.toString())).use { if (it.moveToNext()) it.getInt(0) else 0 }
        val started = db.rawQuery("SELECT count(*) FROM card WHERE kind = 'puzzle'", null).use { if (it.moveToNext()) it.getInt(0) else 0 }
        val learned = db.rawQuery("SELECT count(*) FROM card WHERE kind = 'puzzle' AND box >= 4", null).use { if (it.moveToNext()) it.getInt(0) else 0 }
        PuzzleSummary(total = total, due = back + minOf(newLeft(now), total - started), learned = learned)
    }

    private fun read(c: Cursor, isNew: Boolean) = PuzzleCard(
        Puzzle(
            id = c.getString(0), seq = c.getInt(1), level = c.getString(2), fen = c.getString(3),
            moves = c.getString(4).split(' ').filter { it.isNotEmpty() }, rating = c.getInt(5), themes = c.getString(6), gameUrl = c.getString(7),
        ),
        box = c.getInt(8), isNew = isNew,
    )
}
