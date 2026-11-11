package wrklabs.chess

import org.junit.Assert.*
import org.junit.Test
import wrklabs.chess.ui.WhyLine

class WhyLineTest {
    private val start = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"
    private val quiet = listOf("f1c4", "g8f6", "d2d3", "f8c5", "e1g1", "d7d6", "c2c3", "a7a6")

    @Test fun itPlaysExactlyTheLengthAsked() {
        assertEquals(4, WhyLine.plan(start, quiet, plies = 4).size)
        assertEquals(6, WhyLine.plan(start, quiet, plies = 6).size)
        assertEquals(8, WhyLine.plan(start, quiet, plies = 12).size) // the line itself is only eight moves
    }

    @Test fun aQuietMoveAfterACaptureDoesNotCutItShort() {
        // Nxe5 Nxe5 d4 (quiet) Ng6 Qh5: the old rule stopped after the two captures.
        val pv = listOf("f3e5", "c6e5", "d2d4", "e5g6", "d1h5")
        assertEquals(5, WhyLine.plan(start, pv, plies = 10).size)
        assertEquals(3, WhyLine.plan(start, pv, plies = 3).size)
    }

    private val chain = "3r2k1/3r4/8/3p4/4P3/8/8/3RR1K1 w - - 0 1"
    private val captures = listOf("e4d5", "d7d5", "d1d5", "d8d5")

    @Test fun aLineEndingOnACaptureGoesOnThroughTheRecaptures() {
        // Asked for one move, it plays the capture and the next two captures, not more.
        assertEquals(3, WhyLine.plan(chain, captures, plies = 1).size)
        assertEquals(4, WhyLine.plan(chain, captures, plies = 2).size)
    }

    @Test fun aLineThatEndsQuietIsNotExtended() {
        assertEquals(2, WhyLine.plan(start, listOf("f1c4", "g8f6", "f3g5"), plies = 2).size)
    }

    @Test fun aMatingLineIsPlayedToTheEnd() {
        val fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"
        val pv = listOf("d1d8")
        val plan = WhyLine.plan(fen, pv, mating = true, plies = 1)
        assertEquals(1, plan.size)
        assertTrue(plan.last().san.endsWith("#"))
    }

    @Test fun anIllegalMoveEndsTheLine() {
        assertEquals(1, WhyLine.plan(start, listOf("f1c4", "e2e5"), plies = 8).size)
    }

    @Test fun aLineIsNumberedFromThePosition() {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        assertEquals("1. e4 e5 2. Nf3", wrklabs.chess.ui.WhyLine.sanLine(start, listOf("e2e4", "e7e5", "g1f3")))
    }

    @Test fun aLineThatStartsWithBlackSaysSo() {
        val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        assertEquals("1… e5 2. Nf3 Nc6", wrklabs.chess.ui.WhyLine.sanLine(afterE4, listOf("e7e5", "g1f3", "b8c6")))
    }

    @Test fun aLineStopsAtAMoveThatIsNotLegal() {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        assertEquals("1. e4", wrklabs.chess.ui.WhyLine.sanLine(start, listOf("e2e4", "e2e4")))
    }
}
