package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.importer.OnlineFetcher
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.ui.keepAfterAccountChange

class AccountChangeTest {
    private fun game(source: String, n: Int) = ImportedGame("$source:$n", source, "a", "b", 1500, 1500, "1-0", "rapid", "1. e4 *", n.toLong())
    private val games = listOf(game("chesscom", 1), game("chesscom", 2), game("lichess", 3), game("lichess", 4))

    @Test fun removingTheLichessAccountKeepsTheChesscomGames() {
        val kept = keepAfterAccountChange(games, "me" to "li", "me" to "")
        assertEquals(listOf("chesscom:1", "chesscom:2"), kept.map { it.key })
    }

    @Test fun removingTheChesscomAccountKeepsTheLichessGames() {
        val kept = keepAfterAccountChange(games, "me" to "li", "" to "li")
        assertEquals(listOf("lichess:3", "lichess:4"), kept.map { it.key })
    }

    @Test fun renamingOneAccountDropsOnlyItsGames() {
        assertEquals(listOf("lichess:3", "lichess:4"), keepAfterAccountChange(games, "me" to "li", "other" to "li").map { it.key })
    }

    @Test fun anUnchangedAccountLosesNothing() {
        assertEquals(games.size, keepAfterAccountChange(games, "me" to "li", "me" to "li").size)
    }

    @Test fun addingAnAccountKeepsWhatWasThere() {
        assertEquals(2, keepAfterAccountChange(games.filter { it.source == "chesscom" }, "me" to "", "me" to "li").size)
    }

    @Test fun aHistoryNotReadInFullIsReadFromTheStartWhateverTheNewestGameHeld() {
        assertEquals(0L, OnlineFetcher.effectiveSince(1_700_000_000_000L, complete = false))
        assertEquals(1_700_000_000_000L, OnlineFetcher.effectiveSince(1_700_000_000_000L, complete = true))
    }
}
