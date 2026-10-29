package wrklabs.chess.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import wrklabs.chess.engine.PieceColor
import wrklabs.chess.model.ChessBoard
import wrklabs.chess.model.Move
import wrklabs.chess.model.Piece
import wrklabs.chess.model.Square
import wrklabs.chess.model.PieceType
import wrklabs.chess.ui.theme.Palette

/** The moves from [from] to [to] when that is a promotion: one for each piece to promote to. Empty for any other move. */
fun promotionMoves(board: ChessBoard, from: Square, to: Square): List<Move> =
    board.legalMoves().filter { it.from == from && it.to == to && it.promotion != null }

private val PROMOTE_TO = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

/** Asks which piece a pawn becomes. Dismissing it leaves the pawn where it was. */
@Composable
fun PromotionPicker(color: PieceColor, onPick: (PieceType) -> Unit, onCancel: () -> Unit) {
    val c = Palette.colors
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(c.card).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Promote to", style = MaterialTheme.typography.titleSmall, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in PROMOTE_TO) {
                    // A light tile, so the dark pieces show on the dark card.
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEEED2)).clickable { onPick(type) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(pieceDrawableRes(Piece(type, color))), type.name.lowercase(), Modifier.size(50.dp))
                    }
                }
            }
        }
    }
}
