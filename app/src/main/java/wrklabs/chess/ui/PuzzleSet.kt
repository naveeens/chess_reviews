package wrklabs.chess.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wrklabs.chess.db.Puzzle

/** The bundled puzzles as a plain list, for the drills that need real positions to look at. Read once. */
object PuzzleSet {
    @Volatile private var cache: List<Puzzle>? = null

    fun load(context: Context): List<Puzzle> = cache ?: context.applicationContext.assets.open("puzzles.csv").bufferedReader()
        .useLines { lines -> lines.drop(1).mapNotNull(Puzzle::parse).toList() }.also { cache = it }
}

/** The puzzles once they have been read, null until then. */
@Composable
fun rememberPuzzleSet(): List<Puzzle>? {
    val context = LocalContext.current
    return produceState<List<Puzzle>?>(null) { value = withContext(Dispatchers.IO) { PuzzleSet.load(context) } }.value
}
