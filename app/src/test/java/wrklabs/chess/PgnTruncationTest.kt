package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.importer.PgnParser
import wrklabs.chess.model.GameTree
import wrklabs.chess.model.ROOT_ID

class PgnTruncationTest {
    /** How many moves of the PGN end up on the board: what the review opens with. */
    private fun played(pgn: String): Int {
        var tree = GameTree.create()
        var id = ROOT_ID
        for (san in PgnParser.parse(pgn).moves) {
            val (t, n) = tree.addMoveBySan(id, san) ?: break
            tree = t; id = n
        }
        return tree.getMainLine().size
    }

    private val chesscom = """
        [Event "Live Chess"]
        [White "a"]
        [Black "b"]
        [Result "1-0"]

        1. e4 {[%clk 0:10:00]} 1... e5 {[%clk 0:10:00]} 2. Nf3 {[%clk 0:09:58.9]} 2... Nc6 {[%clk 0:09:58]} 3. Bb5 a6 4. Bxc6 dxc6 5. O-O Bg4 1-0
    """.trimIndent()

    @Test
    fun aChessComGameWithClocksIsReadInFull() {
        assertEquals(10, played(chesscom))
    }

    @Test
    fun annotationMarksOnMovesDoNotCutTheGameShort() {
        val lichess = "[Event \"x\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6?! dxc6 5. O-O Bg4 6. h3? h5 7. Qe1!! Bxf3 1-0"
        assertEquals(14, played(lichess))
    }

    @Test
    fun zerosForCastlingAndAnEnPassantNoteAreRead() {
        assertEquals(7, played("[Event \"x\"]\n\n1. e4 e6 2. Nf3 d5 3. Be2 Nf6 4. 0-0 *"))
        assertEquals(6, played("[Event \"x\"]\n\n1. e4 a6 2. e5 d5 3. exd6 e.p. Nf6 *"))
        assertEquals(4, played("[Event \"x\"]\n\n1. e4 e5 2. Nf3 +- Nc6 =  *"))
    }

    @Test
    fun aPinnedKnightDoesNotCallForAFileLetter() {
        // After 7.Bb5 the knight on c6 is pinned, so only the one on g8 can go to e7 and the game says "Ne7", not "Nge7".
        val game = "[Event \"x\"]\n\n1. Nf3 c6 2. d4 d5 3. c4 e6 4. cxd5 cxd5 5. Nc3 Nc6 6. e3 Bd6 7. Bb5 Ne7 8. O-O O-O *"
        assertEquals(16, played(game))
    }

    @Test
    fun aWholeGameIsReadToTheCheckmate() {
        val game = "[Event \"x\"]\n\n1. Nf3 c6 2. d4 d5 3. c4 e6 4. cxd5 cxd5 5. Nc3 Nc6 6. e3 Bd6 7. Bb5 Ne7 8. O-O O-O 9. a3 a6 10. Bd3 b5 " +
            "11. b4 Nf5 12. Bb2 Nh4 13. Ne5 Nxe5 14. dxe5 Bxe5 15. Qh5 Nf5 16. g4 g6 17. Qh3 Qg5 18. f4 Qh4 19. Qxh4 Nxh4 20. fxe5 Bb7 " +
            "21. Ne2 Rac8 22. Nd4 Rc7 23. Kf2 Bc8 24. Kg3 g5 25. Rac1 Re7 26. Rc5 Ng6 27. Bxg6 hxg6 28. Rfc1 Ree8 29. Nf3 Bb7 30. Nxg5 d4 " +
            "31. Bxd4 Rd8 32. Rc7 Bd5 33. Bc5 Kg7 34. Bxf8+ Rxf8 35. Rf1 Kh6 36. h4 f5 37. Rh7# 1-0"
        assertEquals(73, played(game))
    }
}
