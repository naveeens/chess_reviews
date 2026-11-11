package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.scan.BoardBox
import wrklabs.chess.scan.GrayImage
import wrklabs.chess.scan.Point
import wrklabs.chess.scan.Quad
import wrklabs.chess.scan.Straighten
import wrklabs.chess.scan.Tiles
import kotlin.math.abs

/** Four corners placed by hand, and the board straightened from them. */
class QuadTest {
    private val tilted = Quad(Point(120.0, 60.0), Point(520.0, 110.0), Point(480.0, 470.0), Point(90.0, 400.0))

    private fun golden(): GrayImage {
        val meta = String(javaClass.getResourceAsStream("/scan/example-meta.json")!!.readBytes())
        val w = Regex("\"width\":\\s*(\\d+)").find(meta)!!.groupValues[1].toInt()
        val h = Regex("\"height\":\\s*(\\d+)").find(meta)!!.groupValues[1].toInt()
        val bytes = javaClass.getResourceAsStream("/scan/example-gray.u8.bin")!!.readBytes()
        return GrayImage(FloatArray(w * h) { (bytes[it].toInt() and 0xFF).toFloat() }, w, h)
    }

    @Test fun theCornersMapToThemselves() {
        for ((u, v, p) in listOf(Triple(0.0, 0.0, tilted.topLeft), Triple(1.0, 0.0, tilted.topRight), Triple(1.0, 1.0, tilted.bottomRight), Triple(0.0, 1.0, tilted.bottomLeft))) {
            val m = tilted.toPicture(u, v)
            assertEquals(p.x, m.x, 1e-6); assertEquals(p.y, m.y, 1e-6)
        }
    }

    private val N = Tiles.BOARD_PX

    @Test fun aRectangleStraightensLikeTheBoxCrop() {
        val g = golden()
        val box = BoardBox(30.0, 14.0, 542.0, 526.0)
        val a = Tiles.boardImage(g, box)
        val b = Straighten.boardImage(g, Quad(30.0, 14.0, 542.0, 526.0))
        var sum = 0.0
        for (i in a.indices) sum += abs(a[i] - b[i])
        assertTrue("mean ${sum / a.size}", sum / a.size < 1e-3)
    }

    @Test fun aTiltedPictureOfABoardStraightensBackToTheBoard() {
        val g = golden()
        val flat = Tiles.boardImage(g, BoardBox(30.0, 14.0, 542.0, 526.0))
        // Paint the flat board into a larger picture inside the tilted quad, by sampling the board finely.
        val w = 640; val h = 560
        val data = FloatArray(w * h) { 128f }
        val n = 1536
        for (ty in 0 until n) for (tx in 0 until n) {
            val p = tilted.toPicture((tx + 0.5) / n, (ty + 0.5) / n)
            val x = (p.x - 0.5).toInt().coerceIn(0, w - 1)
            val y = (p.y - 0.5).toInt().coerceIn(0, h - 1)
            data[y * w + x] = flat[(ty * N / n) * N + tx * N / n] * 255f
        }
        val back = Straighten.boardImage(GrayImage(data, w, h), tilted)
        var sum = 0.0
        for (i in flat.indices) sum += abs(flat[i] - back[i])
        assertTrue("mean ${sum / flat.size}", sum / flat.size < 0.04)
    }

    @Test fun turningTheCornersTurnsTheBoardAQuarter() {
        val g = golden()
        val q = Quad(30.0, 14.0, 542.0, 526.0)
        val a = Straighten.boardImage(g, q)
        val b = Straighten.boardImage(g, q.turned())
        // The new board's (x, y) is the old one's (y, N - 1 - x).
        var sum = 0.0
        for (y in 8 until N - 8) for (x in 8 until N - 8) sum += abs(b[y * N + x] - a[(N - 1 - x) * N + y])
        assertTrue("mean ${sum / ((N - 16) * (N - 16))}", sum / ((N - 16) * (N - 16)) < 0.02)
    }

    @Test fun twistedOrFlatCornersAreNotUsable() {
        assertTrue(Quad(0.0, 0.0, 100.0, 100.0).isUsable())
        assertTrue(tilted.isUsable())
        assertFalse(Quad(tilted.topLeft, tilted.bottomRight, tilted.topRight, tilted.bottomLeft).isUsable())
        assertFalse(Quad(Point(0.0, 0.0), Point(50.0, 0.0), Point(100.0, 0.0), Point(30.0, 0.0)).isUsable())
    }
}
