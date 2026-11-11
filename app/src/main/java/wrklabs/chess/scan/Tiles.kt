/*
 * Adapted from fenshot (https://github.com/scoriiu/fenshot), MIT licence: Copyright (c) 2026 SORTINO LABS S.R.L. (kibitzo.com),
 * which builds on tensorflow_chessbot (https://github.com/Elucidation/tensorflow_chessbot), MIT licence: Copyright (c) 2016 Sameer Ansari.
 * Ported from TypeScript to Kotlin and changed for this app. Copyright (C) 2026 naveen s.
 * The MIT licence text is in NOTICE.
 */

package wrklabs.chess.scan

import kotlin.math.floor

/** Cuts the board out of the picture and into the 64 squares the tile reader takes. */
object Tiles {
    /** The board is straightened to this many pixels across, and each square is a block of [TILE_PX] by [TILE_PX]: what the square reader was trained on. */
    const val BOARD_PX = 384
    private const val TILE_PX = BOARD_PX / 8
    const val TILE_INPUT_SIZE = TILE_PX * TILE_PX

    /** A pixel, with the picture's edge repeated outside it. */
    private fun sample(img: GrayImage, x: Int, y: Int): Float {
        val cx = x.coerceIn(0, img.width - 1)
        val cy = y.coerceIn(0, img.height - 1)
        return img.data[cy * img.width + cx]
    }

    /** The box, scaled to the board size with bilinear sampling, values 0..1. */
    fun boardImage(img: GrayImage, box: BoardBox): FloatArray {
        val cw = box.x1 - box.x0
        val ch = box.y1 - box.y0
        val out = FloatArray(BOARD_PX * BOARD_PX)
        for (ty in 0 until BOARD_PX) {
            val sy = box.y0 + (ty + 0.5) * ch / BOARD_PX - 0.5
            val fy = floor(sy)
            val wy = sy - fy
            for (tx in 0 until BOARD_PX) {
                val sx = box.x0 + (tx + 0.5) * cw / BOARD_PX - 0.5
                val fx = floor(sx)
                val wx = sx - fx
                val ix = fx.toInt()
                val iy = fy.toInt()
                val v = sample(img, ix, iy) * (1 - wx) * (1 - wy) +
                    sample(img, ix + 1, iy) * wx * (1 - wy) +
                    sample(img, ix, iy + 1) * (1 - wx) * wy +
                    sample(img, ix + 1, iy + 1) * wx * wy
                out[ty * BOARD_PX + tx] = (v / 255).toFloat()
            }
        }
        return out
    }

    /** The straightened board as 64 rows of [TILE_INPUT_SIZE]: tile k is rank*8+file with a1 at the bottom left, each a square block. */
    fun slice(board: FloatArray): FloatArray {
        val out = FloatArray(64 * TILE_INPUT_SIZE)
        for (rank in 0 until 8) for (file in 0 until 8) {
            val tile = rank * 8 + file
            val srcY0 = (7 - rank) * TILE_PX
            val srcX0 = file * TILE_PX
            for (y in 0 until TILE_PX) {
                val srcRow = (srcY0 + y) * BOARD_PX + srcX0
                val dstRow = tile * TILE_INPUT_SIZE + y * TILE_PX
                for (x in 0 until TILE_PX) out[dstRow + x] = board[srcRow + x]
            }
        }
        return out
    }

    fun extract(img: GrayImage, box: BoardBox): FloatArray = slice(boardImage(img, box))

    /** Grey from packed ARGB pixels (the usual luma weights). */
    fun gray(argb: IntArray, width: Int, height: Int): GrayImage {
        val data = FloatArray(width * height)
        for (i in 0 until width * height) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            data[i] = (0.299 * r + 0.587 * g + 0.114 * b).toFloat()
        }
        return GrayImage(data, width, height)
    }
}
