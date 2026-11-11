package wrklabs.chess

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.maia.MaiaEngine

/** The model on a device, through ONNX Runtime, against what Maia's own Python gives for the same positions. */
@RunWith(AndroidJUnit4::class)
class MaiaEngineTest {
    @Test fun probabilitiesMatchTheReferenceAndAreFastEnough() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val fixtures = Json.parseToJsonElement(inst.context.assets.open("maia_fixtures.json").bufferedReader().readText()).jsonArray.map { it.jsonObject }
        val engine = MaiaEngine(inst.targetContext)
        var worst = 0f
        var worstValue = 0f
        var top = 0
        engine.predict(listOf(wrklabs.chess.model.ChessBoard.DEFAULT_FEN), 1500) // loads the model
        val t0 = System.nanoTime()
        for (f in fixtures) {
            val fens = f["fens"]!!.jsonArray.map { it.jsonPrimitive.content }
            val full = engine.predictFull(fens, f["elo"]!!.jsonPrimitive.int)
            val got = full.moves
            val ldw = f["loss_draw_win"]!!.jsonArray.map { it.jsonPrimitive.float }
            worstValue = maxOf(worstValue, kotlin.math.abs(ldw[0] - full.outlook.loss), kotlin.math.abs(ldw[1] - full.outlook.draw), kotlin.math.abs(ldw[2] - full.outlook.win))
            val want = f["moves"]!!.jsonObject.mapValues { it.value.jsonArray[1].jsonPrimitive.float }
            assertEquals(want.keys, got.keys)
            for ((u, p) in want) worst = maxOf(worst, kotlin.math.abs(p - got.getValue(u)))
            if (want.maxByOrNull { it.value }!!.key == got.maxByOrNull { it.value }!!.key) top++
        }
        val ms = (System.nanoTime() - t0) / 1e6 / fixtures.size
        Log.i("MaiaTest", "positions=${fixtures.size} worst|dp|=$worst sameTopMove=$top ms/position=$ms")
        assertTrue("worst difference $worst", worst < 1e-3f)
        assertTrue("worst outlook difference $worstValue", worstValue < 1e-3f)
        assertEquals(fixtures.size, top)
        engine.close()
    }
}
