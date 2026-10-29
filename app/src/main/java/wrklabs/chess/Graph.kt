package wrklabs.chess

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import wrklabs.chess.billing.BillingManager
import wrklabs.chess.billing.ProAccess
import wrklabs.chess.db.AppDatabaseHelper
import wrklabs.chess.db.CardRepository
import wrklabs.chess.db.GameRepository
import wrklabs.chess.db.KvRepository
import wrklabs.chess.db.PuzzleRepository
import wrklabs.chess.engine.StockfishEngine
import wrklabs.chess.review.ReviewService
import wrklabs.chess.settings.SettingsStore

/** The app's long-lived parts, made once: the settings, the database and the engine. */
object Graph {
    lateinit var settings: SettingsStore
        private set
    lateinit var games: GameRepository
        private set
    lateinit var cards: CardRepository
        private set
    lateinit var puzzles: PuzzleRepository
        private set
    lateinit var kv: KvRepository
        private set
    lateinit var engine: StockfishEngine
        private set
    lateinit var reviews: ReviewService
        private set
    lateinit var pro: ProAccess
        private set
    lateinit var billing: BillingManager
        private set
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        val db = AppDatabaseHelper.getInstance(app)
        settings = SettingsStore(app)
        games = GameRepository(db)
        cards = CardRepository(db)
        puzzles = PuzzleRepository(db, app)
        kv = KvRepository(db)
        engine = StockfishEngine(app)
        reviews = ReviewService(app, engine, games)
        pro = ProAccess(app)
        billing = BillingManager(app, CoroutineScope(SupervisorJob() + Dispatchers.Main)) { pro.set(it) }.also { it.startConnection() }
        wrklabs.chess.sound.SoundPlayer.init(app)
        ready = true
    }

    fun shutdown() {
        if (!ready) return
        billing.endConnection()
        engine.destroy()
        ready = false
    }
}
