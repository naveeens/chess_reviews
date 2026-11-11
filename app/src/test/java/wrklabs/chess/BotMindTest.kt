package wrklabs.chess

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.db.KeyValue
import wrklabs.chess.importer.ImportedGame
import wrklabs.chess.play.*

class BotMindTest {
    private val lost = Outlook(0.01f, 0.02f, 0.97f)
    private val fine = Outlook(0.5f, 0.1f, 0.4f)

    @Test fun outlookIsTheSoftmaxOfLossDrawWin() {
        val o = Outlook.fromLogits(loss = 0f, draw = 0f, win = 0f)
        assertEquals(1f / 3, o.win, 1e-6f); assertEquals(1.0, (o.win + o.draw + o.loss).toDouble(), 1e-5)
        val strong = Outlook.fromLogits(loss = -5f, draw = -2f, win = 4f)
        assertTrue(strong.win > 0.99f && strong.expected > 0.99f)
    }

    @Test fun theBotResignsAfterSeveralHopelessTurnsNotOne() {
        assertFalse(BotMind.resigns(listOf(fine, fine, lost), plies = 40, elo = 1600))
        assertFalse(BotMind.resigns(listOf(lost, lost), plies = 40, elo = 1600))
        assertTrue(BotMind.resigns(listOf(fine, lost, lost, lost), plies = 40, elo = 1600))
    }

    @Test fun weakerBotsHoldOnLonger() {
        val three = listOf(lost, lost, lost)
        assertTrue(BotMind.resigns(three, 40, 1600))
        assertFalse(BotMind.resigns(three, 40, 900))
        assertTrue(BotMind.resigns(three + lost, 40, 900))
    }

    @Test fun nobodyResignsOrDrawsInTheOpening() {
        assertFalse(BotMind.resigns(List(5) { lost }, plies = 10, elo = 1600))
        assertFalse(BotMind.acceptsDraw(lost, plies = 10))
    }

    @Test fun aDrawIsTakenOnlyWhenTheBotIsNotBetter() {
        assertTrue(BotMind.acceptsDraw(lost, plies = 40))                       // the bot is losing: take it
        assertTrue(BotMind.acceptsDraw(Outlook(0.4f, 0.2f, 0.4f), plies = 40))   // even
        assertFalse(BotMind.acceptsDraw(Outlook(0.7f, 0.1f, 0.2f), plies = 40))  // the bot is winning
    }

    @Test fun theLevelMovesOneStepWithAResultAndStaysInRange() {
        assertEquals(1600, AutoLevel.next(1500, "win"))
        assertEquals(1400, AutoLevel.next(1500, "loss"))
        assertEquals(1500, AutoLevel.next(1500, "draw"))
        assertEquals(2600, AutoLevel.next(2600, "win"))
        assertEquals(600, AutoLevel.next(600, "loss"))
        assertEquals(1500, AutoLevel.snap(1464)); assertEquals(600, AutoLevel.snap(212)); assertEquals(2600, AutoLevel.snap(3100))
    }

    private class MemKv : KeyValue {
        val m = mutableMapOf<String, String>()
        override suspend fun get(key: String) = m[key]
        override suspend fun set(key: String, value: String) { m[key] = value }
        override suspend fun remove(key: String) { m.remove(key) }
    }

    @Test fun theStoreKeepsTheActiveGameResultsLevelAndGames() = runBlocking {
        val store = PlayStore(MemKv())
        assertNull(store.active()); assertNull(store.level()); assertTrue(store.results().isEmpty())
        val rec = PlayRecord("fen", listOf("e2e4", "e7e5"), true, 1500, 1480, 123L, takebacks = 1, hints = 2, auto = true)
        store.saveActive(rec); assertEquals(rec, store.active())
        store.clearActive(); assertNull(store.active())
        repeat(205) { store.addResult(PlayResult(it.toLong(), 1500, 1500, if (it % 3 == 0) "win" else "loss", true)) }
        assertEquals(PlayStore.MAX_RESULTS, store.results().size)
        assertEquals("W4 L6 D0", PlayStore.record(store.results()))
        store.setLevel(1700); assertEquals(1700, store.level())
        val g = ImportedGame("play:1", "play", "You", "Maia", 1500, 1500, "1-0", "casual", "1. e4 *", 1L)
        store.addGame(g); store.addGame(g.copy(result = "0-1"))
        assertEquals(1, store.games().size); assertEquals("0-1", store.games().single().result)
        assertEquals(2, store.gamesChanged.value)
    }

    @Test fun aGameFromAPositionKeepsItsFenAndNumbersFromThere() {
        // Black to move at move 12.
        val fen = "r2qkb1r/ppp1pppp/2n1bn2/3p4/3P4/2P1PN2/PP3PPP/RNBQKB1R b KQkq - 0 12"
        val g = PlayGame.replay(listOf("g7g6", "f1d3", "f8g7"), fen = fen)
        val pgn = g.pgn("Maia", "You", 1500, 1500, "2026.11.05")
        assertTrue(pgn, pgn.contains("[SetUp \"1\"]") && pgn.contains("[FEN \"$fen\"]"))
        assertTrue(pgn, pgn.contains("12... g6 13. Bd3 Bg7"))
        assertEquals(fen, g.startFen)
    }

    @Test fun takingBackRemovesMovesAndANewGameEndsByAgreement() {
        val g = PlayGame.replay(listOf("e2e4", "e7e5", "g1f3"))
        assertEquals(listOf("e2e4"), g.takeback(2).ucis)
        assertEquals(PlayGame.start().fens, g.takeback(10).fens)
        val d = g.agreeDraw()
        assertEquals(EndReason.Agreement, d.end!!.reason); assertEquals("1/2-1/2", d.end!!.result)
        assertEquals(EndReason.Agreement, PlayGame.replay(listOf("e2e4"), agreed = true).end!!.reason)
    }

    @Test fun aSetUpGameReadsBackFromItsOwnStart() {
        val fen = "r2qkb1r/ppp1pppp/2n1bn2/3p4/3P4/2P1PN2/PP3PPP/RNBQKB1R b KQkq - 0 12"
        val pgn = PlayGame.replay(listOf("g7g6", "f1d3", "f8g7"), fen = fen).pgn("Maia", "You", 1500, 1500, "2026.11.05")
        val parsed = wrklabs.chess.importer.PgnParser.parse(pgn)
        assertEquals(listOf("g6", "Bd3", "Bg7"), parsed.moves)
        assertEquals(fen, parsed.headers["FEN"]); assertEquals("1", parsed.headers["SetUp"])
        var tree = wrklabs.chess.model.GameTree.create(fen)
        var id = wrklabs.chess.model.ROOT_ID
        for (san in parsed.moves) { val (t, n) = tree.addMoveBySan(id, san)!!; tree = t; id = n }
        assertEquals(3, tree.getMainLine().size)
    }
}
