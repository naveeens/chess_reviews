package wrklabs.chess.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import wrklabs.chess.scan.LoadedPicture
import wrklabs.chess.scan.Point
import wrklabs.chess.scan.Quad
import wrklabs.chess.scan.Straighten
import wrklabs.chess.ui.theme.Palette
import kotlin.math.hypot
import kotlin.math.min

/** Where the picture sits in the space it is drawn in: its size on screen and the gap round it. */
private class Placement(val scale: Float, val left: Float, val top: Float)

/**
 * The picture with the board's four corners on it, to drag onto the board's corners. The corners are first put where the board
 * was found. A small picture beside the buttons shows the board as the reader will be given it, so a corner out of place, or
 * a board on its side, can be seen before reading. While a corner is being dragged the same space shows that corner magnified,
 * to put it exactly on the board's corner.
 */
@Composable
fun PictureCrop(picture: LoadedPicture, onBack: () -> Unit, onRead: (Quad) -> Unit) {
    val c = Palette.colors
    var quad by remember(picture) { mutableStateOf(picture.suggested) }
    val image = remember(picture) { picture.bitmap.asImageBitmap() }
    val w = picture.bitmap.width
    val h = picture.bitmap.height
    BackHandler(onBack = onBack)
    // Which corner is being dragged (none: -1), and how big the picture is drawn, for the magnifier.
    var dragging by remember { mutableIntStateOf(-1) }
    var shownScale by remember { mutableFloatStateOf(1f) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, size = 44)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Place the corners", style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1)
                Text("Drag each corner onto a corner of the board", style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val scale = min(boxW / w, boxH / h)
            val place = Placement(scale, (boxW - w * scale) / 2, (boxH - h * scale) / 2)
            // The gesture handlers outlive recompositions, so they read the newest corners and placement through these.
            val current by rememberUpdatedState(quad)
            val placed by rememberUpdatedState(place)
            val handle = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
            val grab = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
            SideEffect { shownScale = scale }
            fun toScreen(p: Point) = Offset(placed.left + (p.x * placed.scale).toFloat(), placed.top + (p.y * placed.scale).toFloat())
            fun toPicture(o: Offset) = Point(((o.x - placed.left) / placed.scale).toDouble().coerceIn(0.0, w.toDouble()), ((o.y - placed.top) / placed.scale).toDouble().coerceIn(0.0, h.toDouble()))

            Canvas(
                Modifier.fillMaxSize().testTag("cropCanvas").pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val corners = current.points.map { toScreen(it) }
                            val nearest = corners.indices.minByOrNull { hypot(corners[it].x - start.x, corners[it].y - start.y) } ?: -1
                            dragging = if (nearest >= 0 && hypot(corners[nearest].x - start.x, corners[nearest].y - start.y) <= grab) nearest else -1
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (dragging >= 0) quad = current.moved(dragging, toPicture(change.position))
                        },
                        onDragEnd = { dragging = -1 },
                        onDragCancel = { dragging = -1 },
                    )
                },
            ) {
                drawImage(image, dstOffset = IntOffset(place.left.toInt(), place.top.toInt()), dstSize = IntSize((w * place.scale).toInt(), (h * place.scale).toInt()))
                val outline = Path().apply {
                    val pts = quad.points.map { toScreen(it) }
                    moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
                }
                drawPath(outline, Color.Black.copy(alpha = 0.55f), style = Stroke(width = 5f))
                drawPath(outline, c.brand, style = Stroke(width = 2.5f))
                // The squares, as the reader will cut them: lines that follow the board's tilt.
                val m = quad.homography()
                for (i in 1..7) {
                    val t = i / 8.0
                    val v0 = toScreen(m.map(t, 0.0)); val v1 = toScreen(m.map(t, 1.0))
                    val h0 = toScreen(m.map(0.0, t)); val h1 = toScreen(m.map(1.0, t))
                    drawLine(Color.White.copy(alpha = 0.55f), v0, v1, strokeWidth = 1.5f)
                    drawLine(Color.White.copy(alpha = 0.55f), h0, h1, strokeWidth = 1.5f)
                }
                quad.points.forEachIndexed { i, p ->
                    val o = toScreen(p)
                    drawCircle(Color.Black.copy(alpha = 0.6f), handle + 2f, o)
                    // The first corner is the board's top left, as it is to be read.
                    if (i == 0) drawCircle(c.brand, handle, o) else drawCircle(Color.White.copy(alpha = 0.9f), handle, o, style = Stroke(width = 4f))
                    if (dragging == i) drawCircle(c.brand.copy(alpha = 0.35f), grab / 2, o)
                }
            }
        }
        // What the reader will see, and what to do about it.
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val panel = Modifier.size(PANEL).clip(RoundedCornerShape(8.dp)).border(1.dp, c.border, RoundedCornerShape(8.dp))
            if (dragging in 0..3) Magnifier(image, quad, dragging, shownScale, panel)
            else {
                val preview = remember(quad) { straightened(picture, quad) }
                Image(preview.asImageBitmap(), "The board as it will be read", panel)
            }
            Text(
                if (dragging in 0..3) "Put the cross on the board's corner, where the two blue lines should run along its edges."
                else if (quad.isUsable()) "This is the board as it will be read. The filled corner should be its top left, with the pieces upright. Turn it if they are on their side."
                else "The corners cross over. Put them back in order, round the board.",
                style = MaterialTheme.typography.bodySmall, color = if (quad.isUsable()) c.muted else c.danger, modifier = Modifier.weight(1f),
            )
        }
        ActionBar {
            RButton("Turn", { quad = quad.turned() }, Modifier.weight(1f), style = ButtonStyle.Tonal, icon = Icons.Default.RotateRight)
            RButton("Read the board", { onRead(quad) }, Modifier.weight(2f), enabled = quad.isUsable(), icon = Icons.AutoMirrored.Filled.ArrowForward)
        }
    }
}

/** The board inside [quad], straightened, as a small picture to look at. */
private fun straightened(picture: LoadedPicture, quad: Quad): Bitmap {
    val size = 128
    val grey = Straighten.boardImage(picture.gray, quad, size)
    val pixels = IntArray(size * size) { val v = (grey[it] * 255f).toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** The preview and the magnifier are this big, so the screen does not change shape when one takes the other's place. */
private val PANEL = 112.dp

/** How many times bigger than on the screen the magnifier shows the picture. */
private const val MAGNIFY = 5f

/** The picture round corner [index] of [quad], [MAGNIFY] times bigger than it is drawn, with the board's edges and a cross on the corner. */
@Composable
internal fun Magnifier(image: androidx.compose.ui.graphics.ImageBitmap, quad: Quad, index: Int, shownScale: Float, modifier: Modifier = Modifier) {
    val c = Palette.colors
    Canvas(modifier.semantics { contentDescription = "Zoomed view of the corner" }) {
        val side = size.minDimension
        // The window, in picture pixels, centred on the corner; it may reach past the picture's edge.
        val window = side / (shownScale * MAGNIFY)
        val k = side / window
        val centre = quad.points[index]
        val x0 = centre.x.toFloat() - window / 2
        val y0 = centre.y.toFloat() - window / 2
        drawRect(Color.Black)
        val ix0 = maxOf(x0, 0f); val iy0 = maxOf(y0, 0f)
        val ix1 = minOf(x0 + window, image.width.toFloat()); val iy1 = minOf(y0 + window, image.height.toFloat())
        if (ix1 > ix0 && iy1 > iy0) {
            val sx = kotlin.math.floor(ix0).toInt(); val sy = kotlin.math.floor(iy0).toInt()
            val sw = (kotlin.math.ceil(ix1).toInt() - sx).coerceAtLeast(1); val sh = (kotlin.math.ceil(iy1).toInt() - sy).coerceAtLeast(1)
            drawImage(
                image, srcOffset = IntOffset(sx, sy), srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(((sx - x0) * k).toInt(), ((sy - y0) * k).toInt()), dstSize = IntSize((sw * k).toInt().coerceAtLeast(1), (sh * k).toInt().coerceAtLeast(1)),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
            )
        }
        fun at(p: Point) = Offset((p.x.toFloat() - x0) * k, (p.y.toFloat() - y0) * k)
        // The board's two edges that meet at this corner, so the line can be laid along the board's own edge.
        val here = at(centre)
        for (neighbour in listOf(quad.points[(index + 1) % 4], quad.points[(index + 3) % 4])) {
            val to = at(neighbour)
            val dx = to.x - here.x; val dy = to.y - here.y; val len = hypot(dx, dy).coerceAtLeast(1f)
            val far = Offset(here.x + dx / len * side * 2, here.y + dy / len * side * 2)
            drawLine(Color.Black.copy(alpha = 0.6f), here, far, strokeWidth = 4f)
            drawLine(c.brand, here, far, strokeWidth = 2f)
        }
        val arm = side * 0.09f
        drawLine(Color.White, Offset(here.x - arm, here.y), Offset(here.x + arm, here.y), strokeWidth = 1.5f)
        drawLine(Color.White, Offset(here.x, here.y - arm), Offset(here.x, here.y + arm), strokeWidth = 1.5f)
        drawCircle(Color.Black.copy(alpha = 0.7f), 4f, here)
        drawCircle(c.brand, 2.5f, here)
    }
}
