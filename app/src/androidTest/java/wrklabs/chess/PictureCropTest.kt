package wrklabs.chess

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.scan.LoadedPicture
import wrklabs.chess.scan.Quad
import wrklabs.chess.scan.Tiles
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.PictureCrop
import wrklabs.chess.ui.theme.ChessTheme
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class PictureCropTest {
    @get:Rule val rule = createComposeRule()

    private fun picture(): LoadedPicture {
        val w = 400; val h = 300
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) bmp.setPixel(x, y, if (((x - 100) / 25 + (y - 50) / 25) % 2 == 0) Color.WHITE else Color.DKGRAY)
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        return LoadedPicture(bmp, Tiles.gray(px, w, h), Quad(100.0, 50.0, 300.0, 250.0))
    }

    @Test fun theMagnifierTakesThePreviewsPlaceWhileACornerIsHeldAndGivesItBack() {
        var read: Quad? = null
        rule.setContent { ChessTheme(AppSettings().accent) { PictureCrop(picture(), onBack = {}, onRead = { read = it }) } }
        rule.onNodeWithContentDescription("The board as it will be read").assertIsDisplayed()
        rule.onNodeWithContentDescription("Zoomed view of the corner").assertDoesNotExist()

        val size = rule.onNodeWithTag("cropCanvas").fetchSemanticsNode().size
        val scale = min(size.width / 400f, size.height / 300f)
        val left = (size.width - 400 * scale) / 2; val top = (size.height - 300 * scale) / 2
        val corner = Offset(left + 100 * scale, top + 50 * scale)
        rule.onNodeWithTag("cropCanvas").performTouchInput { down(corner); moveBy(Offset(24f, 24f)) }
        rule.onNodeWithContentDescription("Zoomed view of the corner").assertIsDisplayed()
        rule.onNodeWithContentDescription("The board as it will be read").assertDoesNotExist()
        rule.onNodeWithText("Put the cross on the board's corner", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("cropCanvas").performTouchInput { up() }
        rule.onNodeWithContentDescription("The board as it will be read").assertIsDisplayed()
        rule.onNodeWithContentDescription("Zoomed view of the corner").assertDoesNotExist()

        rule.onNodeWithText("Read the board").performClick()
        val q = read!!
        assertEquals(true, q.topLeft.x > 100.0 && q.topLeft.y > 50.0)   // the held corner moved
        assertEquals(300.0, q.bottomRight.x, 1e-6)                      // the others did not
    }
}
