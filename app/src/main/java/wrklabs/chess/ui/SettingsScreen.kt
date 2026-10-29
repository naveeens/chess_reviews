package wrklabs.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import wrklabs.chess.Graph
import wrklabs.chess.engine.EngineStatus
import wrklabs.chess.settings.AppSettings
import wrklabs.chess.settings.ReviewSpeed
import wrklabs.chess.ui.theme.AccentChoice
import wrklabs.chess.ui.theme.Palette

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: AppSettings) {
    val store = Graph.settings
    val engine by Graph.engine.status.collectAsState()
    val c = Palette.colors
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // A field doesn't keep its cursor once the keyboard is put away, or when you tap elsewhere.
    val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp) { if (!keyboardUp) focus.clearFocus() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings")
        Column(Modifier.weight(1f).pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Group("Accounts", "Your public games from these sites show up in Games. Nothing to sign in to.") {
                UsernameField("Chess.com", settings.chesscomUser) { v -> store.update { copy(chesscomUser = v) } }
                Spacer(Modifier.height(8.dp))
                UsernameField("Lichess", settings.lichessUser) { v -> store.update { copy(lichessUser = v) } }
            }

            Group("Games") {
                Toggle("Review a game when it opens", settings.autoReview) { v -> store.update { copy(autoReview = v) } }
            }

            val pro by Graph.pro.owned.collectAsState()
            Group("Pro") {
                if (pro) {
                    Text("Unlimited game reviews are unlocked. Thank you for supporting Chess Reviews.", style = MaterialTheme.typography.bodyMedium, color = c.text)
                } else {
                    Text(
                        "Free: ${wrklabs.chess.billing.ReviewQuota.FREE_PER_DAY} game reviews a day. Pro: unlimited reviews, bought once.",
                        style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    ProUnlock()
                }
            }

            Group("Accent") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (a in AccentChoice.entries) {
                        val on = a == settings.accent
                        // The chosen one gets a ring with a gap; the ring is inside the 44dp, so the row doesn't shift.
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = a.title) { store.update { copy(accent = a) } }
                                .let { if (on) it.border(2.dp, c.text, CircleShape) else it }
                                .padding(if (on) 5.dp else 3.dp)
                                .clip(CircleShape).background(a.color),
                        )
                    }
                }
            }

            Group("Board") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScrollRow()) {
                    for (t in BoardTheme.entries) BoardChoice(t, selected = t == settings.boardTheme) { store.update { copy(boardTheme = t) } }
                }
                Spacer(Modifier.height(6.dp))
                Toggle("Coordinates", settings.coordinates) { v -> store.update { copy(coordinates = v) } }
                Toggle("Highlight the last move", settings.highlightLastMove) { v -> store.update { copy(highlightLastMove = v) } }
                Toggle("Show legal moves", settings.showLegalMoves) { v -> store.update { copy(showLegalMoves = v) } }
                Toggle("Material difference", settings.showMaterial) { v -> store.update { copy(showMaterial = v) } }
                Toggle("Move sounds", settings.sound) { v -> store.update { copy(sound = v) } }
            }

            var licences by remember { mutableStateOf(false) }
            Group("Stockfish", (engine as? EngineStatus.Error)?.let { "The engine didn't start: ${it.message}" }) {
                Text("Engine strength", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(6.dp))
                Segmented(ReviewSpeed.entries.map { it to it.label }, settings.reviewSpeed, { v -> store.update { copy(reviewSpeed = v) } })
                Text(settings.reviewSpeed.hint, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            }

            Group("About") {
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                Row(
                    Modifier.fillMaxWidth().bleed(12.dp).clip(RoundedCornerShape(12.dp)).clickable { uri.openUri("https://github.com/rookahead/rookahead") }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Based on Rookahead", style = MaterialTheme.typography.bodyLarge, color = c.text)
                        Text("github.com/rookahead/rookahead", style = MaterialTheme.typography.bodySmall, color = c.brand)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = c.muted)
                }
                Row(
                    Modifier.fillMaxWidth().bleed(12.dp).clip(RoundedCornerShape(12.dp)).clickable { licences = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Open-source licences", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                    Text("›", style = MaterialTheme.typography.titleLarge, color = c.muted)
                }
            }
            if (licences) LicencesSheet { licences = false }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollRow() = this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable
private fun Group(title: String, lead: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Palette.colors
    RCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
        if (lead != null) Text(lead, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** One site's username: a box and a "+" while there is none; once set, the name as plain text with a "×" to remove it. */
@Composable
private fun UsernameField(site: String, saved: String, onSave: (String) -> Unit) {
    val c = Palette.colors
    if (saved.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(site, style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(saved, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            IconTap(Icons.Default.Close, "Remove $site username", { onSave("") }, modifier = Modifier.background(c.button, CircleShape), size = 36)
        }
        return
    }
    var text by remember { mutableStateOf("") }
    val ready = text.isNotBlank()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("$site username") },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (ready) onSave(text.trim()); focus.clearFocus(); keyboard?.hide() }),
        trailingIcon = {
            IconTap(Icons.Default.Add, "Set $site username", { onSave(text.trim()) }, modifier = Modifier.background(if (ready) c.brand else c.button, CircleShape), enabled = ready, size = 36, tint = if (ready) c.brandInk else c.text)
        },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.brand, unfocusedBorderColor = c.border, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
    )
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Palette.colors
    // The press highlight reaches 12dp past the text and the switch, into the card's own padding, so it has room around them.
    Row(
        Modifier.fillMaxWidth().bleed(12.dp).clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.brand, checkedThumbColor = c.brandInk, uncheckedTrackColor = c.field, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.border),
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, hint: String? = null, onChange: (Int) -> Unit) {
    val c = Palette.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        IconTap(Icons.Default.Remove, "Less", { onChange((value - 1).coerceAtLeast(range.first)) }, enabled = value > range.first)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.widthIn(min = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconTap(Icons.Default.Add, "More", { onChange((value + 1).coerceAtMost(range.last)) }, enabled = value < range.last)
    }
}

@Composable
private fun BoardChoice(theme: BoardTheme, selected: Boolean, onClick: () -> Unit) {
    val c = Palette.colors
    val colors = theme.colors()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).then(if (selected) Modifier.border(2.dp, c.brand, RoundedCornerShape(10.dp)) else Modifier.border(1.dp, c.border, RoundedCornerShape(10.dp)))) {
            Column {
                for (r in 0 until 2) Row(Modifier.weight(1f)) {
                    for (f in 0 until 2) Box(Modifier.weight(1f).fillMaxHeight().background(if ((r + f) % 2 == 0) colors.light else colors.dark))
                }
            }
        }
        Text(theme.title, style = MaterialTheme.typography.labelSmall, color = if (selected) c.text else c.muted, modifier = Modifier.padding(top = 4.dp))
    }
}
