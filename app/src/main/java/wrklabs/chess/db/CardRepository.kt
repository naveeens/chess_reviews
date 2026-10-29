package wrklabs.chess.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MistakeCardPuzzle(
    val id: String,
    val gameKey: String,
    val moveIndex: Int,
    val fen: String,
    val playedUci: String,
    val playedSan: String,
    val bestUci: String,
    val bestSan: String,
    val cls: String,
    val winBefore: Double,
    val winAfter: Double,
    val loss: Double,
    val opponent: String,
    val opening: String?,
    val box: Int,
    val due: Long,
    val reps: Int,
    val lapses: Int,
    /** The opponent's move that led to this position, when the game has it. */
    val prevUci: String? = null
)

class CardRepository(private val dbHelper: AppDatabaseHelper) {

    companion object {
        val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 16, 35, 90)
        val MAX_BOX = INTERVAL_DAYS.size - 1
        const val DAY_MS = 86400000L
    }

    suspend fun getDueCards(now: Long = System.currentTimeMillis(), limit: Int = 20): List<MistakeCardPuzzle> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MistakeCardPuzzle>()

        val sql = """
            SELECT 
                c.id, c.box, c.due, c.reps, c.lapses,
                m.game_key, m.i, m.cls, m.win_before, m.win_after, m.fen, m.uci, m.san, m.best, m.best_san,
                g.opponent, g.opening_name, pm.uci AS prev_uci
            FROM card c
            JOIN move m ON m.game_key || '#' || m.i = c.id
            JOIN game g ON g.key = m.game_key
            LEFT JOIN move pm ON pm.game_key = m.game_key AND pm.i = m.i - 1
            WHERE c.due <= ?
              AND ((g.color = 'white' AND m.i % 2 = 1) OR (g.color = 'black' AND m.i % 2 = 0))
            ORDER BY c.due ASC, (m.win_before - m.win_after) DESC
            LIMIT ?
        """.trimIndent()

        db.rawQuery(sql, arrayOf(now.toString(), limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(readCardPuzzle(cursor))
            }
        }

        // If there are fewer than limit due cards, fetch uncarded mistakes to introduce as new cards
        if (list.size < limit) {
            val needed = limit - list.size
            val freshSql = """
                SELECT 
                    m.game_key || '#' || m.i AS id, 0 AS box, ? AS due, 0 AS reps, 0 AS lapses,
                    m.game_key, m.i, m.cls, m.win_before, m.win_after, m.fen, m.uci, m.san, m.best, m.best_san,
                    g.opponent, g.opening_name, pm.uci AS prev_uci
                FROM move m
                JOIN game g ON g.key = m.game_key
                LEFT JOIN move pm ON pm.game_key = m.game_key AND pm.i = m.i - 1
                LEFT JOIN card c ON c.id = (m.game_key || '#' || m.i)
                WHERE c.id IS NULL
                  AND m.cls IN ('BLUNDER', 'MISTAKE', 'MISS')
                  AND ((g.color = 'white' AND m.i % 2 = 1) OR (g.color = 'black' AND m.i % 2 = 0))
                  AND m.fen IS NOT NULL
                  AND m.best IS NOT NULL
                ORDER BY (m.win_before - m.win_after) DESC
                LIMIT ?
            """.trimIndent()

            db.rawQuery(freshSql, arrayOf(now.toString(), needed.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(readCardPuzzle(cursor))
                }
            }
        }

        list
    }

    suspend fun recordResult(id: String, solved: Boolean, now: Long = System.currentTimeMillis(), kind: String = "mistake") = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        var currentBox = 0
        var currentReps = 0
        var currentLapses = 0
        var added = now

        db.query("card", arrayOf("box", "reps", "lapses", "added"), "id = ?", arrayOf(id), null, null, null).use { cursor ->
            if (cursor.moveToNext()) {
                currentBox = cursor.getInt(0)
                currentReps = cursor.getInt(1)
                currentLapses = cursor.getInt(2)
                added = cursor.getLong(3)
            }
        }

        val newBox = if (solved) {
            minOf(MAX_BOX, currentBox + 1)
        } else {
            maxOf(0, currentBox - 2)
        }

        val newDue = if (solved) {
            now + INTERVAL_DAYS[newBox] * DAY_MS
        } else {
            now
        }

        val newReps = currentReps + 1
        val newLapses = if (solved) currentLapses else currentLapses + 1

        val values = ContentValues().apply {
            put("id", id)
            put("kind", kind)
            put("box", newBox)
            put("due", newDue)
            put("reps", newReps)
            put("lapses", newLapses)
            put("last", now)
            put("added", added)
        }

        db.insertWithOnConflict("card", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getSummary(now: Long = System.currentTimeMillis()): CardSummary = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        // Every mistake of the user's own that can be practised, the ones due now (new ones count), and the ones learned.
        db.rawQuery(
            """
            SELECT
                count(*) AS total,
                coalesce(sum(case when c.id IS NULL OR c.due <= ? then 1 else 0 end), 0) AS due,
                coalesce(sum(case when c.box >= 4 then 1 else 0 end), 0) AS mastered
            FROM move m
            JOIN game g ON g.key = m.game_key
            LEFT JOIN card c ON c.id = (m.game_key || '#' || m.i)
            WHERE m.cls IN ('BLUNDER', 'MISTAKE', 'MISS')
              AND m.fen IS NOT NULL AND m.best IS NOT NULL
              AND ((g.color = 'white' AND m.i % 2 = 1) OR (g.color = 'black' AND m.i % 2 = 0))
            """.trimIndent(),
            arrayOf(now.toString()),
        ).use { cursor ->
            if (cursor.moveToNext()) CardSummary(total = cursor.getInt(0), due = cursor.getInt(1), mastered = cursor.getInt(2))
            else CardSummary(0, 0, 0)
        }
    }

    private fun readCardPuzzle(cursor: Cursor): MistakeCardPuzzle {
        val winBefore = cursor.getDouble(cursor.getColumnIndexOrThrow("win_before"))
        val winAfter = cursor.getDouble(cursor.getColumnIndexOrThrow("win_after"))
        return MistakeCardPuzzle(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            gameKey = cursor.getString(cursor.getColumnIndexOrThrow("game_key")),
            moveIndex = cursor.getInt(cursor.getColumnIndexOrThrow("i")),
            fen = cursor.getString(cursor.getColumnIndexOrThrow("fen")),
            playedUci = cursor.getString(cursor.getColumnIndexOrThrow("uci")) ?: "",
            playedSan = cursor.getString(cursor.getColumnIndexOrThrow("san")) ?: "",
            bestUci = cursor.getString(cursor.getColumnIndexOrThrow("best")) ?: "",
            bestSan = cursor.getString(cursor.getColumnIndexOrThrow("best_san")) ?: cursor.getString(cursor.getColumnIndexOrThrow("best")) ?: "",
            cls = cursor.getString(cursor.getColumnIndexOrThrow("cls")),
            winBefore = winBefore,
            winAfter = winAfter,
            loss = maxOf(0.0, winBefore - winAfter),
            opponent = cursor.getString(cursor.getColumnIndexOrThrow("opponent")),
            opening = cursor.getString(cursor.getColumnIndexOrThrow("opening_name")),
            box = cursor.getInt(cursor.getColumnIndexOrThrow("box")),
            due = cursor.getLong(cursor.getColumnIndexOrThrow("due")),
            reps = cursor.getInt(cursor.getColumnIndexOrThrow("reps")),
            lapses = cursor.getInt(cursor.getColumnIndexOrThrow("lapses")),
            prevUci = cursor.getString(cursor.getColumnIndexOrThrow("prev_uci"))
        )
    }
}

data class CardSummary(
    val total: Int,
    val due: Int,
    val mastered: Int
)
