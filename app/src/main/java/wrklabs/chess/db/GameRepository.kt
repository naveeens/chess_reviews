package wrklabs.chess.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(private val dbHelper: AppDatabaseHelper) {

    suspend fun insertOrReplaceGame(game: GameRecord, moves: List<MoveRecord>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val gameValues = ContentValues().apply {
                put("key", game.key)
                put("v", game.v)
                put("review_at", game.reviewAt)
                put("rules", game.rules)
                put("source", game.source)
                put("color", game.color)
                put("me", game.me)
                put("opponent", game.opponent)
                put("result", game.result)
                put("ended_at", game.endedAt)
                put("exact_time", game.exactTime)
                put("rating", game.rating)
                put("opp_rating", game.oppRating)
                put("time_class", game.timeClass)
                put("base", game.base)
                put("inc", game.inc)
                put("timeout", game.timeout)
                put("accuracy", game.accuracy)
                put("opening_eco", game.openingEco)
                put("opening_name", game.openingName)
                put("book_plies", game.bookPlies)
                put("plies", game.plies)
                put("tags_v", game.tagsV)
                put("pgn", game.pgn)
            }
            db.insertWithOnConflict("game", null, gameValues, SQLiteDatabase.CONFLICT_REPLACE)

            // Remove existing moves for this game
            db.delete("move", "game_key = ?", arrayOf(game.key))

            for (m in moves) {
                val moveValues = ContentValues().apply {
                    put("game_key", m.gameKey)
                    put("i", m.i)
                    put("cls", m.cls)
                    put("win_before", m.winBefore)
                    put("win_after", m.winAfter)
                    put("phase", m.phase)
                    put("piece", m.piece)
                    put("clock", m.clock)
                    put("spent", m.spent)
                    put("missed_mate", m.missedMate)
                    put("uci", m.uci)
                    put("san", m.san)
                    put("fen", m.fen)
                    put("best", m.best)
                    put("best_san", m.bestSan)
                    put("reply", m.reply)
                    put("reply_san", m.replySan)
                    put("missed", m.missed)
                    put("allowed", m.allowed)
                }
                db.insertWithOnConflict("move", null, moveValues, SQLiteDatabase.CONFLICT_REPLACE)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getAllGames(): List<GameRecord> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<GameRecord>()
        db.query("game", null, null, null, null, null, "ended_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                list.add(readGameRecord(cursor))
            }
        }
        list
    }

    suspend fun getGameWithMoves(key: String): Pair<GameRecord, List<MoveRecord>>? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        var game: GameRecord? = null
        db.query("game", null, "key = ?", arrayOf(key), null, null, null).use { cursor ->
            if (cursor.moveToNext()) {
                game = readGameRecord(cursor)
            }
        }
        val foundGame = game ?: return@withContext null

        val moves = mutableListOf<MoveRecord>()
        db.query("move", null, "game_key = ?", arrayOf(key), null, null, "i ASC").use { cursor ->
            while (cursor.moveToNext()) {
                moves.add(readMoveRecord(cursor))
            }
        }
        Pair(foundGame, moves)
    }

    suspend fun deleteGame(key: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("game", "key = ?", arrayOf(key))
    }

    /** When each review since [since] was made, for the free daily limit. */
    suspend fun reviewTimesSince(since: Long): List<Long> = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery("SELECT review_at FROM game WHERE review_at >= ? ORDER BY review_at", arrayOf(since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
    }

    suspend fun hasGame(key: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery("SELECT 1 FROM game WHERE key = ?", arrayOf(key)).use { it.moveToFirst() }
    }

    suspend fun countGames(): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT count(*) FROM game", null).use { cursor ->
            if (cursor.moveToNext()) cursor.getInt(0) else 0
        }
    }

    private fun readGameRecord(cursor: Cursor): GameRecord {
        return GameRecord(
            key = cursor.getString(cursor.getColumnIndexOrThrow("key")),
            v = cursor.getInt(cursor.getColumnIndexOrThrow("v")),
            reviewAt = cursor.getLong(cursor.getColumnIndexOrThrow("review_at")),
            rules = if (cursor.isNull(cursor.getColumnIndexOrThrow("rules"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("rules")),
            source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
            color = cursor.getString(cursor.getColumnIndexOrThrow("color")),
            me = cursor.getString(cursor.getColumnIndexOrThrow("me")),
            opponent = cursor.getString(cursor.getColumnIndexOrThrow("opponent")),
            result = cursor.getString(cursor.getColumnIndexOrThrow("result")),
            endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at")),
            exactTime = cursor.getLong(cursor.getColumnIndexOrThrow("exact_time")),
            rating = if (cursor.isNull(cursor.getColumnIndexOrThrow("rating"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("rating")),
            oppRating = if (cursor.isNull(cursor.getColumnIndexOrThrow("opp_rating"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("opp_rating")),
            timeClass = cursor.getString(cursor.getColumnIndexOrThrow("time_class")),
            base = if (cursor.isNull(cursor.getColumnIndexOrThrow("base"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("base")),
            inc = if (cursor.isNull(cursor.getColumnIndexOrThrow("inc"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("inc")),
            timeout = cursor.getInt(cursor.getColumnIndexOrThrow("timeout")),
            accuracy = if (cursor.isNull(cursor.getColumnIndexOrThrow("accuracy"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("accuracy")),
            openingEco = cursor.getString(cursor.getColumnIndexOrThrow("opening_eco")),
            openingName = cursor.getString(cursor.getColumnIndexOrThrow("opening_name")),
            bookPlies = cursor.getInt(cursor.getColumnIndexOrThrow("book_plies")),
            plies = cursor.getInt(cursor.getColumnIndexOrThrow("plies")),
            tagsV = cursor.getInt(cursor.getColumnIndexOrThrow("tags_v")),
            pgn = cursor.getString(cursor.getColumnIndexOrThrow("pgn"))
        )
    }

    private fun readMoveRecord(cursor: Cursor): MoveRecord {
        return MoveRecord(
            gameKey = cursor.getString(cursor.getColumnIndexOrThrow("game_key")),
            i = cursor.getInt(cursor.getColumnIndexOrThrow("i")),
            cls = cursor.getString(cursor.getColumnIndexOrThrow("cls")),
            winBefore = cursor.getDouble(cursor.getColumnIndexOrThrow("win_before")),
            winAfter = cursor.getDouble(cursor.getColumnIndexOrThrow("win_after")),
            phase = cursor.getString(cursor.getColumnIndexOrThrow("phase")),
            piece = cursor.getString(cursor.getColumnIndexOrThrow("piece")),
            clock = if (cursor.isNull(cursor.getColumnIndexOrThrow("clock"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("clock")),
            spent = if (cursor.isNull(cursor.getColumnIndexOrThrow("spent"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("spent")),
            missedMate = cursor.getInt(cursor.getColumnIndexOrThrow("missed_mate")),
            uci = cursor.getString(cursor.getColumnIndexOrThrow("uci")),
            san = cursor.getString(cursor.getColumnIndexOrThrow("san")),
            fen = cursor.getString(cursor.getColumnIndexOrThrow("fen")),
            best = cursor.getString(cursor.getColumnIndexOrThrow("best")),
            bestSan = cursor.getString(cursor.getColumnIndexOrThrow("best_san")),
            reply = cursor.getString(cursor.getColumnIndexOrThrow("reply")),
            replySan = cursor.getString(cursor.getColumnIndexOrThrow("reply_san")),
            missed = cursor.getString(cursor.getColumnIndexOrThrow("missed")),
            allowed = cursor.getString(cursor.getColumnIndexOrThrow("allowed"))
        )
    }
}
