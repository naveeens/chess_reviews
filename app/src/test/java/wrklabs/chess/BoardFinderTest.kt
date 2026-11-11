package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.scan.BoardBox
import wrklabs.chess.scan.BoardFinder
import wrklabs.chess.scan.GrayImage
import wrklabs.chess.scan.Placement
import wrklabs.chess.scan.Tiles
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/** The board finder and the tile cutter on real pictures. The tile reader needs the phone; see BoardScannerTest. */
class BoardFinderTest {
    private fun resource(name: String): ByteArray = javaClass.getResourceAsStream("/scan/$name")!!.use { it.readBytes() }

    private fun picture(name: String): GrayImage {
        val img = ImageIO.read(resource(name).inputStream())
        val pixels = IntArray(img.width * img.height) { img.getRGB(it % img.width, it / img.width) }
        return Tiles.gray(pixels, img.width, img.height)
    }

    /** A picture stored as raw grey bytes, with its size and the box a reference implementation found in it. */
    private class Golden(val image: GrayImage, val box: List<Int>, val fen: String)

    private fun golden(): Golden {
        val meta = String(resource("example-meta.json"))
        fun int(key: String) = Regex("\"$key\":\\s*(\\d+)").find(meta)!!.groupValues[1].toInt()
        val corners = Regex("\"corners\":\\s*\\[([^\\]]*)]").find(meta)!!.groupValues[1].split(",").map { it.trim().toInt() }
        val bytes = resource("example-gray.u8.bin")
        val w = int("width")
        val h = int("height")
        return Golden(GrayImage(FloatArray(w * h) { (bytes[it].toInt() and 0xFF).toFloat() }, w, h), corners, Regex("\"expectedFen\":\\s*\"([^\"]*)\"").find(meta)!!.groupValues[1])
    }

    @Test fun theBoxMatchesTheReferenceOnThePictureItWasMadeFor() {
        val g = golden()
        val box = BoardFinder.find(g.image)
        assertNotNull(box)
        assertTrue(abs(box!!.x0 - g.box[0]) <= 2 && abs(box.y0 - g.box[1]) <= 2 && abs(box.x1 - g.box[2]) <= 2 && abs(box.y1 - g.box[3]) <= 2)
    }

    @Test fun aBoardIsCutIntoSixtyFourSquaresOfTheReadersSize() {
        val g = golden()
        val tiles = Tiles.extract(g.image, BoardBox(g.box[0].toDouble(), g.box[1].toDouble(), g.box[2].toDouble(), g.box[3].toDouble()))
        assertEquals(64 * Tiles.TILE_INPUT_SIZE, tiles.size)
        assertTrue(tiles.all { it in 0f..1f })
    }

    @Test fun aPageWithNoBoardHasNoBoxFound() {
        assertNull(BoardFinder.find(picture("reddit-chrome-no-board.png")))
    }

    @Test fun aBoxIsFoundInEveryPictureThatHasABoard() {
        for (name in listOf("chesscom-italian-white.png", "lichess-italian-black.png", "fen2image-italian.png", "reddit-page-board.png", "pale-blue-tds-board.png", "whole-screen-two-boards.png", "lichess-queens-only-board-marble.png")) {
            assertNotNull(name, BoardFinder.find(picture(name)))
        }
    }

    @Test fun aBoardWholeSquareAcrossHasEqualSides() {
        val box = BoardFinder.find(picture("chesscom-italian-white.png"))!!
        assertTrue(abs((box.x1 - box.x0) - (box.y1 - box.y0)) <= 4)
    }

    @Test fun flippingTwiceGivesTheSameBoardAndOnceGivesTheOtherSide() {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        assertEquals(start, Placement.flip(Placement.flip(start)))
        assertEquals("RNBKQBNR/PPPPPPPP/8/8/8/8/pppppppp/rnbkqbnr", Placement.flip(start))
    }

    @Test fun aReadWithWhitePawnsLowKeepsItsSide() {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        assertTrue(!Placement.orient(start).blackAtBottom)
        assertEquals(start, Placement.orient(start).placement)
        assertTrue(!Placement.orient("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R").blackAtBottom)
    }

    @Test fun aReadWithWhitePawnsHighIsTurnedRound() {
        val read = "2K2R2/1P5P/1bPr2P1/4p3/8/7N/pp6/1k6"
        val r = Placement.orient(read)
        assertTrue(r.blackAtBottom)
        assertEquals(Placement.flip(read), r.placement)
    }

    @Test fun withoutPawnsOnBothSidesTheSideIsLeftAlone() {
        assertTrue(!Placement.orient("4k3/8/8/8/8/8/8/4K3").blackAtBottom)
    }

    @Test fun probabilitiesBecomeAPlacementAndTheirConfidence() {
        // 64 squares, empty everywhere (class 0) except a1 = white king (class 1) and h8 = black king (class 7).
        val probs = FloatArray(64 * 13)
        for (i in 0 until 64) probs[i * 13] = 0.9f
        probs[0 * 13] = 0f; probs[0 * 13 + 1] = 0.8f
        probs[63 * 13] = 0f; probs[63 * 13 + 7] = 0.95f
        val r = Placement.fromProbabilities(probs)
        assertEquals("7k/8/8/8/8/8/8/K7", r.placement)
        assertEquals(0.8, r.minConfidence, 1e-6)
        assertEquals(64, r.confidences.size)
    }
}
