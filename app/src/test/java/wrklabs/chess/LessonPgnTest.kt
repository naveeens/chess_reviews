package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import wrklabs.chess.model.ROOT_ID
import wrklabs.chess.training.LessonPgn
import wrklabs.chess.training.LessonPgnException

class LessonPgnTest {
    private val italian = """
        [Event "Italian Game: Giuoco Piano"]
        [ECO "C50"]

        {The Italian: develop, aim at f7, and fight for the centre.} 1. e4 e5 2. Nf3 Nc6 3. Bc4 {[%cal Gc4f7,Rc4f7] [%csl Rf7] Bishop eyes f7.} 3... Bc5 (3... Nf6 $5 {The Two Knights.} 4. Ng5 d5 5. exd5 Nxd5?!) 4. c3 {Prepares d4.} Nf6 5. d3 $14 *
    """.trimIndent()

    @Test
    fun variationsHangOffTheMoveTheyReplace() {
        val tree = LessonPgn.parseAll(italian).single().tree
        val bishopMove = tree.nodes.getValue("e2e4.e7e5.g1f3.b8c6.f1c4")
        assertEquals(listOf("f8c5", "g8f6").map { "e2e4.e7e5.g1f3.b8c6.f1c4.$it" }, bishopMove.children)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "c3", "Nf6", "d3"), tree.getMainLine().map { it.san })
        val twoKnights = tree.nodes.getValue("e2e4.e7e5.g1f3.b8c6.f1c4.g8f6")
        assertEquals(listOf(5), twoKnights.nags)
        assertEquals("The Two Knights.", twoKnights.comment)
    }

    @Test
    fun commentsArrowsAndGlyphsLandOnTheirMove() {
        val tree = LessonPgn.parseAll(italian).single().tree
        assertEquals("The Italian: develop, aim at f7, and fight for the centre.", tree.nodes.getValue(ROOT_ID).comment)
        val bc4 = tree.nodes.getValue("e2e4.e7e5.g1f3.b8c6.f1c4")
        assertEquals("Bishop eyes f7.", bc4.comment)
        assertEquals(listOf("Gc4f7", "Rc4f7"), bc4.arrows)
        assertEquals(listOf("Rf7"), bc4.marks)
        assertEquals(listOf(14), tree.getMainLine().last().nags)
        assertEquals(listOf(6), tree.nodes.values.first { it.san == "Nxd5" }.nags)
    }

    @Test
    fun theNextBlackMoveAfterAWhiteCommentKeepsItsNumber() {
        val pgn = LessonPgn.parseAll(italian).single().tree.toPgn()
        assertTrue(pgn, "3. Bc4 {" in pgn && "3... Bc5" in pgn)
    }

    @Test
    fun writingThenReadingGivesTheSameTree() {
        val a = LessonPgn.parseAll(italian).single().tree
        val b = LessonPgn.parseAll(a.toPgn()).single().tree
        assertEquals(a, b)
    }

    @Test
    fun severalChaptersAndAStartPosition() {
        val file = """
            [Event "One"]

            1. d4 d5 *

            [Event "Two"]
            [FEN "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"]

            1. e4 Kd7 *
        """.trimIndent()
        val chapters = LessonPgn.parseAll(file)
        assertEquals(listOf("One", "Two"), chapters.map { it.title })
        assertEquals(listOf("e4", "Kd7"), chapters[1].tree.getMainLine().map { it.san })
    }

    @Test
    fun anIllegalMoveIsAnErrorThatSaysWhere() {
        try {
            LessonPgn.parseAll("[Event \"x\"]\n\n1. e4 e5 2. Nf6 *")
            fail("expected an error")
        } catch (e: LessonPgnException) {
            assertTrue(e.message, "Nf6" in e.message!! && "e4 e5" in e.message!!)
        }
    }

    @Test
    fun unbalancedBracketsAreErrors() {
        for (bad in listOf("1. e4 (1. d4 e5 *", "1. e4 e5) *")) {
            try { LessonPgn.parseAll("[Event \"x\"]\n\n$bad"); fail("expected an error for $bad") } catch (_: LessonPgnException) {}
        }
    }
}

/** Every lesson that ships has to be legal from the first move to the last, and have something to say. */
class ShippedLessonsTest {
    private val files = java.io.File("src/main/assets/lessons").listFiles { f -> f.extension == "pgn" }.orEmpty().sortedBy { it.name }

    @Test
    fun thereAreLessons() = assertTrue(files.isNotEmpty())

    @Test
    fun everyLessonParsesAndIsCommented() {
        for (file in files) {
            val chapters = LessonPgn.parseAll(file.readText())
            assertTrue("${file.name} has no chapters", chapters.isNotEmpty())
            for (c in chapters) {
                val nodes = c.tree.nodes.values
                assertTrue("${file.name}: ${c.title} has no moves", nodes.size > 10)
                assertTrue("${file.name}: ${c.title} has no comments", nodes.count { !it.comment.isNullOrBlank() } >= 5)
                assertTrue("${file.name}: ${c.title} has no variations", nodes.any { it.children.size > 1 })
            }
        }
    }
}

class LessonPracticeTest {
    private val tree = LessonPgn.parseAll(
        "[Event \"x\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 (3... Nf6 4. Ng5) 4. c3 (4. b4 Bxb4 5. c3) 4... Nf6 *"
    ).single().tree

    private fun white(seed: Int = 1) = wrklabs.chess.training.LessonPractice(tree, wrklabs.chess.engine.PieceColor.WHITE, kotlin.random.Random(seed))

    @Test
    fun yourRightMoveBringsTheOpponentsReply() {
        val p = white()
        var s = p.start()
        assertEquals(ROOT_ID, s.nodeId)
        val r = p.play(s, "e2", "e4") as wrklabs.chess.training.PracticeResult.Correct
        s = r.state
        assertEquals(listOf("e4", "e5"), s.played.map { it.san })
        assertEquals("e2e4.e7e5", s.nodeId)
    }

    @Test
    fun aMoveNotInTheLessonIsWrongAndSaysWhatWasWanted() {
        val p = white()
        val s = (p.play(p.start(), "e2", "e4") as wrklabs.chess.training.PracticeResult.Correct).state
        val w = p.play(s, "d2", "d4") as wrklabs.chess.training.PracticeResult.Wrong
        assertEquals("Nf3", w.expected.san)
        assertEquals(1, w.state.mistakes)
        assertEquals(1, w.state.triesHere)
        assertEquals(s.nodeId, w.state.nodeId)
    }

    @Test
    fun anyOfTheLessonsMovesIsRightWhereThereIsAChoice() {
        val p = white()
        val atMove4 = wrklabs.chess.training.PracticeState(nodeId = "e2e4.e7e5.g1f3.b8c6.f1c4.f8c5")
        for ((f, t) in listOf("c2" to "c3", "b2" to "b4")) assertTrue(p.play(atMove4, f, t) is wrklabs.chess.training.PracticeResult.Correct)
        assertTrue(p.play(atMove4, "d2", "d3") is wrklabs.chess.training.PracticeResult.Wrong)
    }

    @Test
    fun successiveRunsTakeDifferentBranches() {
        val p = white(7)
        fun run(prev: wrklabs.chess.training.PracticeState?): wrklabs.chess.training.PracticeState {
            var s = p.start(prev)
            while (!s.finished) {
                val m = p.expected(s)!!
                s = (p.play(s, m.from!!, m.to!!) as wrklabs.chess.training.PracticeResult.Correct).state
            }
            return s
        }
        val first = run(null)
        val second = run(first)
        // The two runs reach different ends, since the opponent had two choices at move 3 and picks the unseen one.
        assertTrue(first.nodeId != second.nodeId)
    }

    @Test
    fun playingBlackLetsTheOpponentOpen() {
        val p = wrklabs.chess.training.LessonPractice(tree, wrklabs.chess.engine.PieceColor.BLACK, kotlin.random.Random(1))
        val s = p.start()
        assertEquals(listOf("e4"), s.played.map { it.san })
    }

    @Test
    fun theRunEndsWhenTheLessonDoes() {
        val p = white()
        var s = p.start()
        var guard = 0
        while (!s.finished && guard++ < 20) { val m = p.expected(s)!!; s = (p.play(s, m.from!!, m.to!!) as wrklabs.chess.training.PracticeResult.Correct).state }
        assertTrue(s.finished)
    }
}

class LineCardIdTest {
    @Test
    fun anIdRoundTrips() {
        val id = wrklabs.chess.training.lineCardId("italian.pgn#0", "e2e4.e7e5")
        assertEquals("italian.pgn#0" to "e2e4.e7e5", wrklabs.chess.training.parseLineCardId(id))
        assertEquals("root", wrklabs.chess.training.parseLineCardId(wrklabs.chess.training.lineCardId("x.pgn#1", "root"))?.second)
    }

    @Test
    fun otherIdsAreNotLineCards() {
        assertEquals(null, wrklabs.chess.training.parseLineCardId("abc123#7"))
        assertEquals(null, wrklabs.chess.training.parseLineCardId("line:onlytwo"))
        assertEquals(null, wrklabs.chess.training.parseLineCardId("line::x"))
    }
}
