package wrklabs.chess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

fun appColors(): AppColors {
    val t = AppTones
    val on = t.on
    val muted = t.onVariant.copy(alpha = 0.78f)
    return AppColors(
        bg = t.surface,
        card = t.container,
        cardHigh = t.high,
        field = t.lowest,
        text = on,
        muted = muted,
        border = Color.White.copy(alpha = 0.08f),
        brand = t.primary,
        brandInk = t.onPrimary,
        brandCard = t.primaryContainer,
        brandCardText = t.onPrimaryContainer,
        button = Color.White.copy(alpha = 0.085f),
        win = Color(0xFF7FC35F),
        danger = Color(0xFFEF6B5F),
        warn = Color(0xFFE0B25B),
        barLight = Color(0xFFECECEE),
        barDark = t.high,
        dark = true,
    )
}

object Palette {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ChessTheme(content: @Composable () -> Unit) {
    val rc = remember { appColors() }
    val t = AppTones
    val scheme = remember(rc, t) {
        darkColorScheme().copy(
            primary = t.primary, onPrimary = t.onPrimary,
            primaryContainer = t.primaryContainer, onPrimaryContainer = t.onPrimaryContainer,
            secondary = t.primary, onSecondary = t.onPrimary,
            secondaryContainer = t.high, onSecondaryContainer = t.on,
            tertiary = t.primary,
            background = rc.bg, onBackground = t.on,
            surface = rc.bg, onSurface = t.on, onSurfaceVariant = rc.muted,
            surfaceVariant = rc.card,
            surfaceContainerLowest = t.lowest, surfaceContainerLow = rc.card, surfaceContainer = rc.card,
            surfaceContainerHigh = rc.cardHigh, surfaceContainerHighest = rc.cardHigh,
            outline = rc.border, outlineVariant = rc.border,
            error = rc.danger,
        )
    }
    CompositionLocalProvider(LocalAppColors provides rc) {
        MaterialTheme(colorScheme = scheme, typography = Typography, shapes = AppShapes, content = content)
    }
}

/** A colour over the page, for tints that must stay opaque. */
fun Color.over(bg: Color): Color = compositeOver(bg)
