package wrklabs.chess.classifier

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wrklabs.chess.model.MoveNode

@Serializable
data class BookData(
    val names: List<String>,
    val positions: Map<String, Int>
)

data class Opening(
    val eco: String,
    val name: String
)

class OpeningsBook private constructor(private val data: BookData) {

    companion object {
        private var instance: OpeningsBook? = null

        private fun key(fen: String): String {
            val parts = fen.split("\\s+".toRegex())
            return parts.take(3).joinToString(" ")
        }

        suspend fun load(context: Context): OpeningsBook = withContext(Dispatchers.IO) {
            instance?.let { return@withContext it }
            try {
                val jsonString = context.assets.open("openings.json").bufferedReader().use { it.readText() }
                val json = Json { ignoreUnknownKeys = true }
                val bookData = json.decodeFromString<BookData>(jsonString)
                val book = OpeningsBook(bookData)
                instance = book
                book
            } catch (e: Exception) {
                android.util.Log.e("OpeningsBook", "Failed to load openings", e)
                val fallback = OpeningsBook(BookData(emptyList(), emptyMap()))
                instance = fallback
                fallback
            }
        }
    }

    fun isBook(fen: String): Boolean {
        return data.positions.containsKey(key(fen))
    }

    fun nameAt(fen: String): String? {
        val idx = data.positions[key(fen)] ?: return null
        return if (idx >= 0 && idx < data.names.size) data.names[idx] else null
    }

    fun openingOf(path: List<MoveNode>): Opening? {
        for (i in path.indices.reversed()) {
            val label = nameAt(path[i].fen)
            if (label != null) {
                val space = label.indexOf(' ')
                return if (space != -1) {
                    Opening(eco = label.substring(0, space), name = label.substring(space + 1))
                } else {
                    Opening(eco = "", name = label)
                }
            }
        }
        return null
    }
}
