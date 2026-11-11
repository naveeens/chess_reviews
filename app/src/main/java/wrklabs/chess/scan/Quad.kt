package wrklabs.chess.scan

import kotlin.math.floor

/** A point in a picture, in pixels (a pixel's edges are whole numbers). */
data class Point(val x: Double, val y: Double)

/**
 * Where a board's four corners are in a picture, in reading order as the board is meant to be seen: top left, top right,
 * bottom right, bottom left. Any four points will do, so a board photographed at an angle can be straightened.
 */
class Quad(val topLeft: Point, val topRight: Point, val bottomRight: Point, val bottomLeft: Point) {
    val points: List<Point> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** The same four corners with the reading order turned a quarter: for a picture where the board is on its side. */
    fun turned(): Quad = Quad(bottomLeft, topLeft, topRight, bottomRight)

    fun moved(index: Int, to: Point): Quad {
        val p = points.toMutableList()
        p[index] = to
        return Quad(p[0], p[1], p[2], p[3])
    }

    /** A straight rectangle. */
    constructor(x0: Double, y0: Double, x1: Double, y1: Double) : this(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1))

    /** Maps a place on the board (u across, v down, each 0 to 1) to the picture. */
    fun toPicture(u: Double, v: Double): Point = homography().map(u, v)

    fun homography(): Homography {
        val (p0, p1, p2, p3) = points
        val sx = p0.x - p1.x + p2.x - p3.x
        val sy = p0.y - p1.y + p2.y - p3.y
        if (Math.abs(sx) < 1e-9 && Math.abs(sy) < 1e-9) {
            return Homography(p1.x - p0.x, p3.x - p0.x, p0.x, p1.y - p0.y, p3.y - p0.y, p0.y, 0.0, 0.0)
        }
        val dx1 = p1.x - p2.x
        val dx2 = p3.x - p2.x
        val dy1 = p1.y - p2.y
        val dy2 = p3.y - p2.y
        val den = dx1 * dy2 - dx2 * dy1
        val g = (sx * dy2 - dx2 * sy) / den
        val h = (dx1 * sy - sx * dy1) / den
        return Homography(p1.x - p0.x + g * p1.x, p3.x - p0.x + h * p3.x, p0.x, p1.y - p0.y + g * p1.y, p3.y - p0.y + h * p3.y, p0.y, g, h)
    }

    /** Whether the four corners make a plain four-sided shape in order: no twists, and not squashed flat. */
    fun isUsable(): Boolean {
        val pts = points
        var sign = 0
        for (i in 0 until 4) {
            val a = pts[i]; val b = pts[(i + 1) % 4]; val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 1e-6) 1 else if (cross < -1e-6) -1 else 0
            if (s == 0) return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }
}

/** A projective map from the unit square to a picture. */
class Homography(private val a: Double, private val b: Double, private val c: Double, private val d: Double, private val e: Double, private val f: Double, private val g: Double, private val h: Double) {
    fun map(u: Double, v: Double): Point {
        val w = g * u + h * v + 1
        return Point((a * u + b * v + c) / w, (d * u + e * v + f) / w)
    }
}

/** Straightens the board inside a [Quad] into the picture the tile reader takes. */
object Straighten {
    /** The quad, straightened to [Tiles.BOARD_PX] across with bilinear sampling, values 0..1. A rectangle gives what [Tiles.boardImage] gives. */
    fun boardImage(img: GrayImage, quad: Quad, size: Int = Tiles.BOARD_PX): FloatArray {
        val m = quad.homography()
        val out = FloatArray(size * size)
        for (ty in 0 until size) {
            val v = (ty + 0.5) / size
            for (tx in 0 until size) {
                val p = m.map((tx + 0.5) / size, v)
                // Pixel centres sit on whole numbers for sampling, half a pixel in from the edges the corners are given in.
                val sx = p.x - 0.5
                val sy = p.y - 0.5
                val fx = floor(sx)
                val fy = floor(sy)
                val wx = sx - fx
                val wy = sy - fy
                val ix = fx.toInt()
                val iy = fy.toInt()
                val value = sample(img, ix, iy) * (1 - wx) * (1 - wy) + sample(img, ix + 1, iy) * wx * (1 - wy) +
                    sample(img, ix, iy + 1) * (1 - wx) * wy + sample(img, ix + 1, iy + 1) * wx * wy
                out[ty * size + tx] = (value / 255).toFloat()
            }
        }
        return out
    }

    private fun sample(img: GrayImage, x: Int, y: Int): Float = img.data[y.coerceIn(0, img.height - 1) * img.width + x.coerceIn(0, img.width - 1)]

    /** The 64 squares of the straightened board, as the tile reader takes them. */
    fun tiles(img: GrayImage, quad: Quad): FloatArray = Tiles.slice(boardImage(img, quad))
}
