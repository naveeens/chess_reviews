package wrklabs.chess.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a store needs of a key-value table; the database implements it, and a test can too. */
interface KeyValue {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
}

class KvRepository(private val dbHelper: AppDatabaseHelper) : KeyValue {

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.query("kv", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use { cursor ->
            if (cursor.moveToNext()) cursor.getString(0) else null
        }
    }

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("kv", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("kv", "key = ?", arrayOf(key))
        Unit
    }
}
