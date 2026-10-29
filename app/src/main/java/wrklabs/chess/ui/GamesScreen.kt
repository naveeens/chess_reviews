package wrklabs.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.ui.theme.Palette

@Composable
fun GamesScreen(vm: GamesViewModel, settings: AppSettings, onOpen: (GameItem) -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsState()
    val c = Palette.colors
    LaunchedEffect(Unit) { vm.reload() }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Games",
            subtitle = if (settings.hasAccounts) (if (state.items.isNotEmpty()) "%,d games · ".format(state.items.size) else "") + listOf(settings.chesscomUser, settings.lichessUser).filter { it.isNotBlank() }.joinToString(" · ") else null,
            actions = {
                if (settings.hasAccounts) IconTap(Icons.Default.Refresh, "Refresh games", vm::refresh, enabled = !state.loading)
            },
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.brand, trackColor = c.field)

        when {
            !settings.hasAccounts -> Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                EmptyState(
                    "Bring in your games",
                    "Add your Chess.com or Lichess username and your recent games show up here, ready to review.",
                    action = { RButton("Add username", onOpenSettings, style = ButtonStyle.OnCard, icon = Icons.Default.Settings) },
                )
            }
            state.items.isEmpty() -> Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.ready) {
                    repeat(7) { SkeletonRow() }
                } else {
                    EmptyState(
                        state.error ?: "No games found",
                        if (state.error != null) "Check the username in Settings, or try again." else "Nothing came back for that username yet. Play a game or check the spelling.",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RButton("Try again", vm::refresh, style = ButtonStyle.OnCard, icon = Icons.Default.Download)
                                RButton("Settings", onOpenSettings, style = ButtonStyle.OnCardSoft)
                            }
                        },
                    )
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { err -> item { Text(err, color = c.danger, style = MaterialTheme.typography.bodySmall) } }
                items(state.items, key = { it.game.key }) { item -> GameRow(item, onClick = { onOpen(item) }) }
            }
        }
    }
}

@Composable
private fun GameRow(item: GameItem, onClick: () -> Unit) {
    val c = Palette.colors
    val outcomeColor = when (item.outcome) {
        "win" -> c.win
        "loss" -> c.danger
        else -> c.muted
    }
    RCard(onClick = onClick, padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(outcomeColor))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.opponent, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    item.opponentRating?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = c.muted) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOf(
                            if (item.myColorIsWhite) "White" else "Black",
                            item.game.timeClass.replaceFirstChar { it.uppercase() },
                            DateUtils.getRelativeTimeSpanString(item.game.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when (item.outcome) { "win" -> "Won"; "loss" -> "Lost"; else -> "Draw" },
                    style = MaterialTheme.typography.labelLarge, color = outcomeColor,
                )
                if (item.accuracy != null) Chip(accuracyChip(item.accuracy), color = c.brandCard, textColor = c.brandCardText)
                else if (item.reviewed) Chip("Reviewed")
            }
        }
    }
}

/** A game row's shape, shown while the list is still being read, so an empty list is never mistaken for "no games". */
@Composable
private fun SkeletonRow() {
    val c = Palette.colors
    RCard(padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(c.field))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(150.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(c.field))
                Box(Modifier.width(210.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(c.field))
            }
            Box(Modifier.width(36.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(c.field))
        }
    }
}
