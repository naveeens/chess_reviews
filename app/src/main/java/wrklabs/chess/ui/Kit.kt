package wrklabs.chess.ui

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wrklabs.chess.ui.theme.Display
import wrklabs.chess.ui.theme.Palette

/** A screen's title and, below it, one line about what it shows. */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.colors.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.colors.muted)
        }
        actions()
    }
}

/** A rounded panel on the page. */
@Composable
fun RCard(
    modifier: Modifier = Modifier,
    color: Color = Palette.colors.card,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.clip(shape).background(color).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
        content = content,
    )
}

enum class ButtonStyle { Primary, Tonal, Ghost }

@Composable
fun RButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = Palette.colors
    val (bg, fg) = when (style) {
        ButtonStyle.Primary -> c.brand to c.brandInk
        ButtonStyle.Tonal -> c.button to c.text
        ButtonStyle.Ghost -> Color.Transparent to c.brand
    }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 8.dp else 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/** A tappable icon with a round, tinted touch target. */
@Composable
fun IconTap(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, size: Int = 40, tint: Color = Palette.colors.text) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick, interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(22.dp))
    }
}

/** A row of options; the chosen one is lifted. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Palette.colors
    Row(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(c.field).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) c.cardHigh else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.text else c.muted)
            }
        }
    }
}

/** A small pill of text. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = Palette.colors.button, textColor: Color = Palette.colors.text) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

/** An empty screen: what is missing, and the one thing to do about it. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Palette.colors
    RCard(modifier.fillMaxWidth(), color = c.brandCard, padding = PaddingValues(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.brandCardText)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = c.brandCardText)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = Palette.colors.muted)
}

/** The small mono number style used for evaluations and depths. */
@Composable
fun monoStyle() = MaterialTheme.typography.labelMedium.copy(fontFamily = wrklabs.chess.ui.theme.Mono, fontWeight = FontWeight.Medium)

@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Palette.colors.border))

@Composable
fun outlineBorder(): BorderStroke = BorderStroke(1.dp, Palette.colors.border)

fun Modifier.outlined(shape: androidx.compose.ui.graphics.Shape, color: Color) = this.border(1.dp, color, shape)

/** A move class as a coloured disc: its picture, or its glyph when it has none. */
@Composable
fun ClassBadge(cls: wrklabs.chess.classifier.MoveClass, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(cls.composeColor), contentAlignment = Alignment.Center) {
        val icon = cls.icon
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        else Text(cls.glyph, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp, maxLines = 1)
    }
}

/** Lets a row reach `by` past its content on both sides while it keeps its width in the layout: a highlight gets room, the content stays put. */
fun Modifier.bleed(by: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val extra = by.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra))
    layout(placeable.width - 2 * extra, placeable.height) { placeable.placeRelative(-extra, 0) }
}
