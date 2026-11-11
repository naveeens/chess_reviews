package wrklabs.chess.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import java.io.File
import wrklabs.chess.Graph
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Piece
import wrklabs.chess.model.PieceType
import wrklabs.chess.model.PositionDraft
import wrklabs.chess.model.Square
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Palette

/** What a tap on the board does: move a piece, clear a square, or put a piece down. */
sealed interface EditTool {
    data object Move : EditTool
    data object Erase : EditTool
    data class Put(val piece: Piece) : EditTool
}

/**
 * Sets up a position by hand: pick a piece below the board and tap squares to put it down, or move the pieces that are there
 * (tap one, then an empty square; or drag it anywhere). Side to move and castling are under the pieces. The position goes on
 * to the analysis board or to a game against Maia once it is one the board can hold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardEditor(startFen: String, settings: AppSettings, onBack: () -> Unit, onAnalyse: (String) -> Unit, onPlay: (String) -> Unit) {
    val c = Palette.colors
    val context = LocalContext.current
    val draft = remember { PositionDraft.fromFen(startFen) ?: PositionDraft().also { it.startingPosition() } }
    // The draft is changed in place; this number is what tells the screen to look at it again.
    var rev by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf<EditTool>(EditTool.Move) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var orientation by remember { mutableStateOf(PieceColor.WHITE) }
    var menu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    // Reading a position from a picture: the squares the reader was unsure of are marked until they are touched.
    var unsure by remember { mutableStateOf<Set<Square>>(emptySet()) }
    var info by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var pictureSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun changed() { rev++; note = null }

    fun readPicture(uri: Uri?) {
        if (uri == null) return
        scanning = true; note = null; info = null
        scope.launch {
            val result = runCatching { Graph.scanner.scan(uri) }
            scanning = false
            val scanned = result.getOrNull()
            when {
                result.isFailure -> note = "That picture could not be opened."
                scanned == null -> note = "No chessboard found in that picture. Try one cropped closer to the board."
                else -> {
                    draft.load("${scanned.placement} ${if (draft.turn == PieceColor.WHITE) "w" else "b"} KQkq - 0 1")
                    selected = null; unsure = scanned.unsure; changed()
                    info = when {
                        !scanned.plausible -> "That does not look like a whole position. Check the pieces."
                        scanned.unsure.isNotEmpty() -> "The squares marked on the board are ones the reader was unsure of."
                        else -> "Read from the picture. Check it against the original before you go on."
                    }
                }
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { readPicture(it) }
    var photo by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken -> if (taken) readPicture(photo) }
    fun takePhoto() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", File.createTempFile("board", ".jpg", dir))
        photo = uri
        try { camera.launch(uri) } catch (e: ActivityNotFoundException) { note = "There is no camera app to take the picture with." }
    }

    val fen = remember(rev) { draft.toFen() }
    val board = remember(fen) { ChessBoard.fromFen(fen) }
    val problems = remember(fen) { draft.problems() }
    BackHandler(onBack = onBack)
    if (pictureSheet) PictureSheet(onDismiss = { pictureSheet = false }, onGallery = { pictureSheet = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onCamera = { pictureSheet = false; takePhoto() })

    fun tap(sq: Square) {
        when (val t = tool) {
            is EditTool.Put -> { draft.put(sq, t.piece); unsure -= sq; changed() }
            EditTool.Erase -> { draft.put(sq, null); unsure -= sq; changed() }
            EditTool.Move -> {
                val from = selected
                when {
                    draft.pieceAt(sq) != null -> selected = if (from == sq) null else sq
                    from != null -> { draft.move(from, sq); unsure = unsure - from - sq; selected = null; changed() }
                }
            }
        }
    }
    fun drag(from: Square, to: Square) {
        if (tool == EditTool.Move) { draft.move(from, to); unsure = unsure - from - to; selected = null; changed() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, size = 44)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Set up a position", style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1)
                Text("Pick a piece, then tap the squares", style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
            }
            IconTap(Icons.Default.SwapVert, "Flip board", { orientation = orientation.opponent() })
            Box {
                IconTap(Icons.Default.MoreVert, "More", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                    DropdownMenuItem(text = { Text("Read from a picture…") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { menu = false; pictureSheet = true })
                    DropdownMenuItem(text = { Text("Copy FEN") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; copyText(context, "FEN", fen) })
                    DropdownMenuItem(
                        text = { Text("Paste FEN") }, leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        onClick = {
                            menu = false
                            if (draft.load(clipboardText(context))) { selected = null; unsure = emptySet(); info = null; changed() } else note = "The clipboard does not hold a position."
                        },
                    )
                    DropdownMenuItem(text = { Text("Play from here") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, enabled = problems.isEmpty(), onClick = { menu = false; onPlay(fen) })
                }
            }
        }
        BoardBody(
            spacing = 10.dp,
            footer = {
                ActionBar {
                    RButton("Clear", { draft.clear(); selected = null; unsure = emptySet(); info = null; changed() }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                    RButton("Start", { draft.startingPosition(); selected = null; unsure = emptySet(); info = null; changed() }, Modifier.weight(1f), style = ButtonStyle.Tonal)
                    RButton("Analyse", { onAnalyse(fen) }, Modifier.weight(2f), enabled = problems.isEmpty(), icon = Icons.AutoMirrored.Filled.ArrowForward)
                }
            },
        ) {
            ChessboardView(
                modifier = Modifier.fillMaxWidth().testTag("board"),
                board = board,
                colors = settings.boardTheme.colors(),
                orientation = orientation,
                selectedSquare = selected,
                marked = unsure,
                coordinates = settings.coordinates,
                highlightLastMove = false,
                showLegalMoves = false,
                onSquareClick = ::tap,
                onMove = ::drag,
            )
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (color in listOf(PieceColor.WHITE, PieceColor.BLACK)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (type in listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)) {
                            val piece = Piece(type, color)
                            PaletteCell(painterResource(pieceDrawableRes(piece)), "${color.name.lowercase()} ${type.name.lowercase()}", tool == EditTool.Put(piece), Modifier.weight(1f).testTag("palette-${piece.fenChar}")) {
                                tool = if (tool == EditTool.Put(piece)) EditTool.Move else EditTool.Put(piece); selected = null
                            }
                        }
                        // The two that are not pieces: pick up and move what is there, or clear squares.
                        if (color == PieceColor.WHITE) PaletteCell(rememberVectorPainter(Icons.Default.OpenWith), "Move pieces", tool == EditTool.Move, Modifier.weight(1f).testTag("tool-move")) { tool = EditTool.Move; selected = null }
                        else PaletteCell(rememberVectorPainter(Icons.Default.Delete), "Erase", tool == EditTool.Erase, Modifier.weight(1f).testTag("tool-erase")) { tool = EditTool.Erase; selected = null }
                    }
                }
                Segmented(listOf(PieceColor.WHITE to "White to move", PieceColor.BLACK to "Black to move"), draft.turn, { draft.turn = it; changed() })
                // Castling: a dot for the colour, then the side. A right with no king or rook at home cannot be switched on.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (color in listOf(PieceColor.WHITE, PieceColor.BLACK)) for ((kingside, label) in listOf(true to "O-O", false to "O-O-O")) {
                        CastleChip(color, label, draft.castling(color, kingside), draft.canCastle(color, kingside), Modifier.weight(1f)) {
                            draft.setCastling(color, kingside, !draft.castling(color, kingside)); changed()
                        }
                    }
                }
                if (scanning) Text("Reading the picture…", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                else info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.muted) }
                (note ?: problems.firstOrNull())?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PictureSheet(onDismiss: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit) {
    val c = Palette.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.cardHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Read a position from a picture", style = MaterialTheme.typography.titleLarge, color = c.text)
            Text("A screenshot of a board, or a diagram from a book. The pieces are read on this phone; the picture is not sent anywhere.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Spacer(Modifier.height(4.dp))
            RButton("Choose from the gallery", onGallery, Modifier.fillMaxWidth(), icon = Icons.Default.PhotoLibrary)
            RButton("Take a photo", onCamera, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal, icon = Icons.Default.PhotoCamera)
        }
    }
}

@Composable
private fun PaletteCell(icon: Painter, description: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Palette.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(48.dp).clip(shape).background(if (on) c.brandCard else c.button)
            .then(if (on) Modifier.border(BorderStroke(1.dp, c.brand), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(icon, description, Modifier.size(34.dp), colorFilter = if (description == "Move pieces" || description == "Erase") ColorFilter.tint(c.text) else null)
    }
}

@Composable
private fun CastleChip(color: PieceColor, label: String, on: Boolean, available: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Palette.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(if (on) c.brandCard else c.button)
            .then(if (on) Modifier.border(BorderStroke(1.dp, c.brand), shape) else Modifier)
            .clickable(enabled = available, onClick = onClick).alpha(if (available) 1f else 0.35f),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (color == PieceColor.WHITE) Color.White else Color.Black).border(1.dp, c.muted, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.brandCardText else c.text, maxLines = 1)
    }
}

private fun clipboardText(context: Context): String =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
