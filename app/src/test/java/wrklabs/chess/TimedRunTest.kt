package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.training.Pace
import wrklabs.chess.training.TimedRun

class TimedRunTest {
    @Test fun aCleanSetEndsAfterExactlyTheSetSize() {
        var n = 0
        val run = TimedRun(10) { n++ }
        repeat(10) { run.answer(true) }
        assertTrue(run.done)
        assertEquals(10, run.firstTry)
        assertEquals(0, run.missed)
    }

    @Test fun aMissedProblemIsNotAskedAgain() {
        var n = 0
        val run = TimedRun(3) { n++ }
        run.answer(false)
        run.answer(true); run.answer(true)
        assertTrue(run.done)
        assertEquals(2, run.firstTry)
        assertEquals(1, run.missed)
        assertEquals(3, run.seen)
    }

    @Test fun aSetOfAllMissesStillEnds() {
        val run = TimedRun(10) { 0 }
        repeat(10) { run.answer(false) }
        assertTrue(run.done)
        assertEquals(10, run.missed)
    }

    @Test fun paceRises_stays_andFallsWithTheMisses() {
        assertEquals(5, Pace.after(4, 0))
        assertEquals(4, Pace.after(4, 1))
        assertEquals(3, Pace.after(4, 2))
        assertEquals(3, Pace.after(4, 9))
    }

    @Test fun paceStaysInsideTheLadder() {
        assertEquals(Pace.top, Pace.after(Pace.top, 0))
        assertEquals(0, Pace.after(0, 5))
        assertEquals(1500, Pace.limit(999))
        assertEquals(20000, Pace.limit(-3))
    }

    @Test fun theLadderOnlyGetsFaster() {
        assertEquals(Pace.LIMITS_MS.sortedDescending(), Pace.LIMITS_MS)
                assertEquals("2.5 s", Pace.label(9))
        assertEquals("5 s", Pace.label(6))
        assertEquals(10000, Pace.limit(Pace.START_LEVEL))
    }

    @Test fun everyAnswerIsRecordedInOrderWithItsTiming() {
        var n = 0
        val run = TimedRun(3) { n++ }
        run.answer(true, ms = 1200)
        run.answer(false, late = true, ms = 10000)
        run.answer(false, ms = 800)
        assertEquals(listOf(0, 1, 2), run.attempts.map { it.problem })
        assertEquals(listOf(true, false, false), run.attempts.map { it.right })
        assertEquals(listOf(false, true, false), run.attempts.map { it.late })
        assertEquals(listOf(1200L, 10000L, 800L), run.attempts.map { it.ms })
    }

    @Test fun historyKeepsTheLastFiveScoresOldestFirst() {
        var h = emptyList<Int>()
        (1..7).forEach { h = Pace.pushed(h, it) }
        assertEquals(listOf(3, 4, 5, 6, 7), h)
    }
}
