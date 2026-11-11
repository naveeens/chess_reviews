package wrklabs.chess

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.scan.BoardReader
import wrklabs.chess.scan.BoardScanner
import wrklabs.chess.scan.GrayImage
import wrklabs.chess.scan.Placement
import wrklabs.chess.scan.TileModel
import wrklabs.chess.scan.Tiles

/** Boards read from real pictures, with the real tile reader. */
@RunWith(AndroidJUnit4::class)
class BoardScannerTest {
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val model = TileModel(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun picture(name: String): GrayImage {
        val bmp = testAssets.open("scan/$name").use { BitmapFactory.decodeStream(it) }
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return Tiles.gray(pixels, bmp.width, bmp.height)
    }

    private fun read(img: GrayImage) = BoardReader.read(img) { box -> model.read(img, box) }

    @Test fun theReferencePictureIsReadExactly() {
        val meta = JSONObject(testAssets.open("scan/example-meta.json").use { String(it.readBytes()) })
        val bytes = testAssets.open("scan/example-gray.u8.bin").use { it.readBytes() }
        val w = meta.getInt("width"); val h = meta.getInt("height")
        val img = GrayImage(FloatArray(w * h) { (bytes[it].toInt() and 0xFF).toFloat() }, w, h)
        val scan = read(img)
        assertNotNull(scan)
        assertEquals(meta.getString("expectedFen"), scan!!.reading.placement)
        assertTrue(scan.plausible)
        assertTrue("min ${scan.reading.minConfidence}", scan.reading.minConfidence > 0.7)
        assertTrue("mean ${scan.reading.meanConfidence}", scan.reading.meanConfidence > 0.9)
    }

    @Test fun everyScreenshotIsReadAsItsKnownPositionOrFlaggedAsUnsure() {
        val manifest = JSONObject(testAssets.open("scan/manifest.json").use { String(it.readBytes()) })
        val problems = mutableListOf<String>()
        for (name in manifest.keys()) {
            val spec = manifest.getJSONObject(name)
            val scan = read(picture(name))
            if (scan == null) { problems += "$name: no board found"; continue }
            if (spec.getString("expect") == "match") {
                val oriented = Placement.orient(scan.reading.placement)
                if (oriented.placement != spec.getString("fen")) problems += "$name: read ${oriented.placement}, wanted ${spec.getString("fen")}"
                if (oriented.blackAtBottom != (spec.getString("orientation") == "black")) problems += "$name: orientation ${oriented.blackAtBottom}"
                if (scan.reading.minConfidence <= 0.7) problems += "$name: unsure (${scan.reading.minConfidence})"
            } else if (scan.reading.minConfidence >= 0.7) problems += "$name: a foreign piece set should read as unsure, got ${scan.reading.minConfidence}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun aPageWithNoBoardGivesNothing() {
        assertEquals(null, read(picture("reddit-chrome-no-board.png")))
    }

    /** The whole path a picture takes from the gallery: a file, decoded, read, turned the right way up. */
    private fun fileFor(name: String): android.net.Uri {
        val f = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        testAssets.open("scan/$name").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return android.net.Uri.fromFile(f)
    }

    @Test fun aPictureFromABlackSidedScreenshotComesBackWhiteAtTheBottom() {
        val scanner = BoardScanner(InstrumentationRegistry.getInstrumentation().targetContext, model)
        val p = runBlocking { scanner.scan(fileFor("lichess-italian-black.png")) }
        assertNotNull(p)
        assertEquals("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", p!!.placement)
        assertTrue(p.fromBlackSide)
        assertTrue(p.reliable && p.plausible)
        assertTrue(p.unsure.isEmpty())
    }

    @Test fun aPieceSetItHasNotSeenMarksSquaresAsUnsure() {
        val scanner = BoardScanner(InstrumentationRegistry.getInstrumentation().targetContext, model)
        val p = runBlocking { scanner.scan(fileFor("lichess-italian-horsey.png")) }
        assertNotNull(p)
        assertTrue(!p!!.reliable)
        assertTrue(p.unsure.isNotEmpty())
    }

    @Test fun aPictureWithNoBoardInItGivesNull() {
        val scanner = BoardScanner(InstrumentationRegistry.getInstrumentation().targetContext, model)
        assertEquals(null, runBlocking { scanner.scan(fileFor("reddit-chrome-no-board.png")) })
    }
}
