package wrklabs.chess.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "chess_reviews.db"
        const val DATABASE_VERSION = 3

        @Volatile
        private var instance: AppDatabaseHelper? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE kv (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE game (
                    key TEXT PRIMARY KEY,
                    v INTEGER NOT NULL,
                    review_at INTEGER NOT NULL,
                    rules INTEGER,
                    source TEXT NOT NULL,
                    color TEXT NOT NULL,
                    me TEXT NOT NULL,
                    opponent TEXT NOT NULL,
                    result TEXT,
                    ended_at INTEGER NOT NULL,
                    exact_time INTEGER NOT NULL,
                    rating INTEGER,
                    opp_rating INTEGER,
                    time_class TEXT NOT NULL,
                    base INTEGER,
                    inc INTEGER,
                    timeout INTEGER NOT NULL,
                    accuracy REAL,
                    opening_eco TEXT,
                    opening_name TEXT,
                    book_plies INTEGER NOT NULL,
                    plies INTEGER NOT NULL,
                    tags_v INTEGER NOT NULL DEFAULT 0,
                    pgn TEXT
                )
                """.trimIndent()
            )

            db.execSQL("CREATE INDEX idx_game_ended ON game (ended_at)")

            db.execSQL(
                """
                CREATE TABLE move (
                    game_key TEXT NOT NULL REFERENCES game (key) ON DELETE CASCADE,
                    i INTEGER NOT NULL,
                    cls TEXT NOT NULL,
                    win_before REAL NOT NULL,
                    win_after REAL NOT NULL,
                    phase TEXT NOT NULL,
                    piece TEXT NOT NULL,
                    clock REAL,
                    spent REAL,
                    missed_mate INTEGER NOT NULL DEFAULT 0,
                    uci TEXT,
                    san TEXT,
                    fen TEXT,
                    best TEXT,
                    best_san TEXT,
                    reply TEXT,
                    reply_san TEXT,
                    missed TEXT,
                    allowed TEXT,
                    PRIMARY KEY (game_key, i)
                ) WITHOUT ROWID
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE card (
                    id TEXT PRIMARY KEY,
                    kind TEXT NOT NULL,
                    box INTEGER NOT NULL DEFAULT 0,
                    due INTEGER NOT NULL,
                    reps INTEGER NOT NULL DEFAULT 0,
                    lapses INTEGER NOT NULL DEFAULT 0,
                    last INTEGER,
                    added INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL("CREATE INDEX idx_card_due ON card (due)")
            createPuzzleTable(db)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Version 2 added pgn column to game table if missing
            try {
                db.execSQL("ALTER TABLE game ADD COLUMN pgn TEXT")
            } catch (_: Exception) {}
        }
        if (oldVersion < 3) createPuzzleTable(db)
    }

    /** Version 3: the puzzle set, loaded from the bundled file on first use. */
    private fun createPuzzleTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS puzzle (
                id TEXT PRIMARY KEY,
                seq INTEGER NOT NULL,
                level TEXT NOT NULL,
                fen TEXT NOT NULL,
                moves TEXT NOT NULL,
                rating INTEGER NOT NULL,
                themes TEXT NOT NULL,
                game_url TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_puzzle_seq ON puzzle (seq)")
    }
}
