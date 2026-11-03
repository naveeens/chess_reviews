package wrklabs.chess.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wrklabs.chess.training.Pace
import wrklabs.chess.training.TimedRun
import wrklabs.chess.ui.theme.Palette

enum class SetPhase { Idle, Asking, Revealing, Done }

/**
 * A timed set of problems for a drill: [Pace.SET_SIZE] problems, each against its own clock.
 * A wrong or late answer shows the right one until you tap Next.
 * The state lives here, not in the composable, so that callbacks never read a stale copy.
 */
class TimedSession<T>(private val context: Context, private val key: String, private val make: () -> T) {
    var phase by mutableStateOf(SetPhase.Idle)
        private set
    var level by mutableIntStateOf(Pace.load(context, key))
        private set
    private var run by mutableStateOf<TimedRun<T>?>(null)
    var current by mutableStateOf<T?>(null)
        private set
    /** Counts every problem shown, so the clock restarts even if the next one looks the same. */
    var token by mutableIntStateOf(0)
        private set
    var fraction by mutableFloatStateOf(1f)
        private set
    var lateOnLast by mutableStateOf(false)
        private set
    var wasRight by mutableStateOf<Boolean?>(null)
        private set
    /** After a set: +1 faster, -1 slower, 0 the same. */
    var shift by mutableIntStateOf(0)
        private set

    val limitMs get() = Pace.limit(level)
    val firstTry get() = run?.firstTry ?: 0
    val missed get() = run?.missed ?: 0
    val seen get() = run?.seen ?: 0

    /** Changes the drill without leaving the set: reload the level for another key. */
    fun reload(newKey: String) { level = Pace.load(context, newKey) }

    fun start() {
        run = TimedRun(Pace.SET_SIZE, make)
        wasRight = null; lateOnLast = false; shift = 0
        advance()
    }

    private fun advance() {
        val r = run ?: return
        if (r.done) { finish(r); return }
        current = r.current
        fraction = 1f
        token++
        phase = SetPhase.Asking
    }

    private fun finish(r: TimedRun<T>) {
        val next = Pace.after(level, r.missed)
        shift = next.compareTo(level)
        level = next
        Pace.save(context, key, next)
        phase = SetPhase.Done
    }

    /** An answer to the problem on the board. A wrong one holds the screen to show the right answer. */
    fun answer(right: Boolean, late: Boolean = false) {
        if (phase != SetPhase.Asking) return
        val r = run ?: return
        r.answer(right)
        wasRight = right; lateOnLast = late
        if (right) advance() else phase = SetPhase.Revealing
    }

    /** Leaves the answer card for the next problem. */
    fun next() { if (phase == SetPhase.Revealing) advance() }

    /** Drives the clock. The answer card waits for [next]. */
    suspend fun tick() {
        when (phase) {
            SetPhase.Asking -> {
                val limit = limitMs
                var elapsed = 0L
                val begin = withFrameMillis { it }
                while (elapsed < limit) {
                    elapsed = withFrameMillis { it } - begin
                    fraction = (1f - elapsed.toFloat() / limit).coerceIn(0f, 1f)
                }
                answer(false, late = true)
            }
            else -> Unit
        }
    }
}

/** Runs the session's clock. Put it once in the drill's composable. */
@Composable
fun <T> TimedSession<T>.Drive() {
    LaunchedEffect(phase, token) { tick() }
}

/** The line above the board while a set runs: what is asked, how far through, and the clock. */
@Composable
fun <T> TimedSession<T>.Status(question: String) {
    val c = Palette.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
            Text(
                "${minOf(if (phase == SetPhase.Revealing) seen else seen + 1, Pace.SET_SIZE)} of ${Pace.SET_SIZE}",
                style = MaterialTheme.typography.titleSmall, color = c.muted,
            )
        }
        LinearProgressIndicator(
            progress = { if (phase == SetPhase.Asking) fraction else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (fraction < 0.25f) c.danger else c.brand,
            trackColor = c.muted.copy(alpha = 0.25f),
        )
    }
}

/** What to say after a miss, above the answer itself. */
fun <T> TimedSession<T>.missLine() = if (lateOnLast) "Time's up" else "Not quite"

/** The result card of a finished set. */
@Composable
fun <T> TimedSession<T>.ResultCard() {
    val c = Palette.colors
    RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
        Text("$firstTry of ${Pace.SET_SIZE} right", style = MaterialTheme.typography.titleMedium, color = c.brandCardText)
        Text(
            when {
                shift > 0 && level == Pace.top -> "Clean set. Top speed: ${Pace.label(level)} per question."
                shift > 0 -> "Clean set. Faster now: ${Pace.label(level)} per question."
                shift < 0 -> "Slower for now: ${Pace.label(level)} per question."
                else -> "Same pace: ${Pace.label(level)} per question. A set with no misses goes faster."
            },
            style = MaterialTheme.typography.bodySmall, color = c.brandCardText,
        )
    }
}

/** The goal and the pace, for the screen before a set. */
@Composable
fun <T> TimedSession<T>.Goal() {
    Text(
        "Goal: ${Pace.SET_SIZE} in a row with no misses, at ${Pace.label(level)} each. Do it and the clock gets shorter.",
        style = MaterialTheme.typography.bodySmall, color = Palette.colors.muted,
    )
    LinearProgressIndicator(
        progress = { (level + 1).toFloat() / (Pace.top + 1) },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = Palette.colors.brand,
        trackColor = Palette.colors.muted.copy(alpha = 0.25f),
    )
}
