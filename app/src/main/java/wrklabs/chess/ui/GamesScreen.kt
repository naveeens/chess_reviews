package wrklabs.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
fun GamesScreen(vm: GamesViewModel, settings: AppSettings, onOpen: (GameItem) -> Unit, onOpenSettings: () -> Unit, onOpenAnalysis: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val c = Palette.colors
    LaunchedEffect(Unit) { vm.reload() }
    val q = state.query
    val visible = remember(state.items, q) { q.apply(state.items) }
    var searching by rememberSaveable { mutableStateOf(q.search.isNotBlank()) }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Games",
            subtitle = if (settings.hasAccounts) (if (state.items.isNotEmpty()) "%,d games · ".format(state.items.size) else "") + listOf(settings.chesscomUser, settings.lichessUser).filter { it.isNotBlank() }.joinToString(" · ")
            else if (state.items.isNotEmpty()) "%,d games".format(state.items.size) else null,
            actions = {
                IconTap(Icons.Default.Analytics, "Analysis board", onOpenAnalysis)
                if (state.items.isNotEmpty()) {
                    IconTap(Icons.Default.Search, "Search games", { searching = !searching; if (!searching) vm.setQuery { copy(search = "") } })
                    Box {
                        IconTap(Icons.AutoMirrored.Filled.Sort, "Sort games", { sortMenu = true })
                        DropdownMenu(sortMenu, { sortMenu = false }, containerColor = c.cardHigh) {
                            GameSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    trailingIcon = { if (q.sort == sort) Icon(Icons.Default.Check, "Chosen", tint = c.brand) },
                                    onClick = { sortMenu = false; vm.setQuery { copy(sort = sort) } },
                                )
                            }
                        }
                    }
                }
                if (settings.hasAccounts) IconTap(Icons.Default.Refresh, "Refresh games", vm::refresh, enabled = !state.loading)
            },
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.brand, trackColor = c.field)

        if (state.items.isNotEmpty()) {
            if (searching) SearchField(q.search) { text -> vm.setQuery { copy(search = text) } }
            FilterRow(q, vm)
            Text(summaryOf(visible), Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }

        when {
            // Games played here (against Maia) need no account.
            !settings.hasAccounts && state.items.isEmpty() -> Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
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
            visible.isEmpty() -> Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                EmptyState(
                    "No games match",
                    "Nothing in your list fits the search and filters.",
                    action = { RButton("Clear filters", { vm.setQuery { cleared() }; searching = false }, style = ButtonStyle.OnCard) },
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { err -> item { Text(err, color = c.danger, style = MaterialTheme.typography.bodySmall) } }
                items(visible, key = { it.game.key }) { item -> GameRow(item, onClick = { onOpen(item) }) }
            }
        }
    }
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit) {
    val c = Palette.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (text.isEmpty()) focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(c.field).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Opponent name", style = MaterialTheme.typography.bodyLarge, color = c.muted)
            BasicTextField(
                text, onChange, Modifier.fillMaxWidth().focusRequester(focus).testTag("games-search"), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text), cursorBrush = SolidColor(c.brand),
            )
        }
        if (text.isNotEmpty()) IconTap(Icons.Default.Close, "Clear search", { onChange("") }, size = 28)
    }
}

/** The filters as a row of chips, each opening a menu of its choices; a chip that is on shows its choice. */
@Composable
private fun FilterRow(q: GameQuery, vm: GamesViewModel) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChipMenu("Result", ResultFilter.entries, q.result, { it.label }) { v -> vm.setQuery { copy(result = v) } }
        FilterChipMenu("Colour", ColourFilter.entries, q.colour, { it.label }) { v -> vm.setQuery { copy(colour = v) } }
        FilterChipMenu("Time", TimeGroup.entries, q.time, { it.label }) { v -> vm.setQuery { copy(time = v) } }
        FilterChipMenu("Source", SourceFilter.entries, q.source, { it.label }) { v -> vm.setQuery { copy(source = v) } }
        FilterChipMenu("Reviewed", ReviewedFilter.entries, q.reviewed, { it.label }) { v -> vm.setQuery { copy(reviewed = v) } }
        FilterChipMenu("Date", DateFilter.entries, q.date, { it.label }) { v -> vm.setQuery { copy(date = v) } }
        FilterChipMenu("Opponent", OpponentFilter.entries, q.opponent, { it.label }) { v -> vm.setQuery { copy(opponent = v) } }
        if (q.active) Text(
            "Clear", Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.setQuery { cleared() } }.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, color = Palette.colors.brand,
        )
    }
}

@Composable
private fun <T> FilterChipMenu(label: String, options: List<T>, selected: T?, name: (T) -> String, onSelect: (T?) -> Unit) {
    val c = Palette.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.testTag("filter-$label").clip(RoundedCornerShape(10.dp)).background(if (selected != null) c.brandCard else c.field).clickable { open = true }.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tone = if (selected != null) c.brandCardText else c.text
            Text(if (selected != null) name(selected) else label, style = MaterialTheme.typography.labelLarge, color = tone, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null, tint = tone, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, { open = false }, containerColor = c.cardHigh) {
            DropdownMenuItem(modifier = Modifier.testTag("filter-$label-Any"), text = { Text("Any") }, trailingIcon = { if (selected == null) Icon(Icons.Default.Check, "Chosen", tint = c.brand) }, onClick = { open = false; onSelect(null) })
            options.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.testTag("filter-$label-${name(option)}"),
                    text = { Text(name(option)) },
                    trailingIcon = { if (option == selected) Icon(Icons.Default.Check, "Chosen", tint = c.brand) },
                    onClick = { open = false; onSelect(option) },
                )
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
