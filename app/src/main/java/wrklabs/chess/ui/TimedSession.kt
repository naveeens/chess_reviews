package wrklabs.chess.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wrklabs.chess.training.Attempt
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
    /** Scores of this drill's last sets, oldest first, the set just finished included. */
    var history by mutableStateOf(Pace.history(context, key))
        private set
    /** After a set: +1 faster, -1 slower, 0 the same. */
    var shift by mutableIntStateOf(0)
        private set

    val limitMs get() = Pace.limit(level)
    val firstTry get() = run?.firstTry ?: 0
    val missed get() = run?.missed ?: 0
    val seen get() = run?.seen ?: 0
    /** Every problem of the set so far, with how it went. */
    val attempts: List<Attempt<T>> get() = run?.attempts ?: emptyList()

    /** Changes the drill without leaving the set: reload the level for another key. */
    fun reload(newKey: String) { level = Pace.load(context, newKey); history = Pace.history(context, newKey) }

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
        history = Pace.pushed(history, r.firstTry)
        Pace.saveHistory(context, key, history)
        phase = SetPhase.Done
    }

    /** Leaves the summary for the setup screen. */
    fun close() { if (phase == SetPhase.Done) phase = SetPhase.Idle }

    /** An answer to the problem on the board. A wrong one holds the screen to show the right answer. */
    fun answer(right: Boolean, late: Boolean = false) {
        if (phase != SetPhase.Asking) return
        val r = run ?: return
        r.answer(right, late, if (late) limitMs.toLong() else ((1f - fraction) * limitMs).toLong())
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
fun <T> TimedSession<T>.Status(question: String) = Status(AnnotatedString(question), bold = true)

/** [question] may carry its own styling and [inline] pieces; [bold] sets the weight of the rest. */
@Composable
fun <T> TimedSession<T>.Status(question: AnnotatedString, inline: Map<String, InlineTextContent> = emptyMap(), bold: Boolean = false) {
    val c = Palette.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                question, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, inlineContent = inline,
            )
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

/** Just how far through the set, and the clock: for a drill that asks its question below the board. */
@Composable
fun <T> TimedSession<T>.Progress() {
    val c = Palette.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${minOf(if (phase == SetPhase.Revealing) seen else seen + 1, Pace.SET_SIZE)} of ${Pace.SET_SIZE}",
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
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

private fun seconds(ms: Long) = "%.1f s".format(ms / 1000.0)

/** The twelve pace steps, filled up to the one you are at. */
@Composable
fun PaceLadder(level: Int, modifier: Modifier = Modifier) {
    val c = Palette.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0..Pace.top) {
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= level) c.brand else c.muted.copy(alpha = 0.25f)))
        }
    }
}

/** Where you are on the pace ladder, and what it takes to move up. */
@Composable
fun <T> TimedSession<T>.PaceCard() {
    val c = Palette.colors
    RCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Your pace", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text("${Pace.label(level)} per question", style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.Bold)
            }
            Text("Step ${level + 1} of ${Pace.top + 1}", style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        Spacer(Modifier.height(10.dp))
        PaceLadder(level)
        Spacer(Modifier.height(10.dp))
        Text(
            if (level == Pace.top) "Top speed. Keep it with no more than one miss a set."
            else "${Pace.SET_SIZE} right in a row with no misses brings it down to ${Pace.label(level + 1)}.",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        history.lastOrNull()?.let { Text("Last set: $it of ${Pace.SET_SIZE}", style = MaterialTheme.typography.bodySmall, color = c.muted) }
    }
}

/**
 * The screen before a set: what the drill is, where you are, the options, and Start pinned at the foot.
 * There is no board here; it comes when the first question does.
 */
@Composable
fun <T> ColumnScope.SetupScreen(
    session: TimedSession<T>, title: String?, blurb: String, howTo: String,
    options: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Palette.colors
    var showHow by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.Bold)
        Text(blurb, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        session.PaceCard()
        if (options != null) RCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = options) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showHow = !showHow }.padding(vertical = 4.dp)) {
            Row {
                Text("How it works", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(if (showHow) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = c.muted)
            }
            if (showHow) Text(howTo, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
    RButton("Start", session::start, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp))
}

/**
 * The screen after a set: score, how each question went, speed, the pace ladder, recent sets and what was missed.
 * [describe] says a missed problem and its answer in a line; [onReview] opens the misses one by one, if the drill can.
 */
@Composable
fun <T> ColumnScope.SummaryScreen(session: TimedSession<T>, describe: (T) -> String, onReview: (() -> Unit)? = null) {
    val c = Palette.colors
    val attempts = session.attempts
    val misses = attempts.filter { !it.right }
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RCard(Modifier.fillMaxWidth(), color = c.brandCard) {
            Text("${session.firstTry} of ${Pace.SET_SIZE} right", style = MaterialTheme.typography.headlineMedium, color = c.brandCardText, fontWeight = FontWeight.Bold)
            Text(
                when {
                    session.shift > 0 && session.level == Pace.top -> "Clean set. Top speed: ${Pace.label(session.level)} per question."
                    session.shift > 0 -> "Clean set. Faster now: ${Pace.label(session.level)} per question."
                    session.shift < 0 -> "Slower for now: ${Pace.label(session.level)} per question."
                    else -> "Same pace: ${Pace.label(session.level)} per question. A set with no misses goes faster."
                },
                style = MaterialTheme.typography.bodyMedium, color = c.brandCardText,
            )
        }
        RCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                attempts.forEach { a ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (a.right) c.win else if (a.late) c.warn else c.danger))
                }
            }
            if (attempts.isNotEmpty()) {
                val late = attempts.count { it.late }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat("Average", seconds(attempts.map { it.ms }.average().toLong()), Modifier.weight(1f))
                    Stat("Slowest", seconds(attempts.maxOf { it.ms }), Modifier.weight(1f))
                    Stat("Too slow", "$late", Modifier.weight(1f))
                }
            }
        }
        if (misses.isNotEmpty()) {
            RCard(Modifier.fillMaxWidth()) {
                Text("Missed", style = MaterialTheme.typography.labelMedium, color = c.muted)
                attempts.forEachIndexed { i, a ->
                    if (!a.right) Row(Modifier.padding(top = 6.dp)) {
                        Text("${i + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        Text(describe(a.problem) + if (a.late) " (too slow)" else "", style = MaterialTheme.typography.bodyMedium, color = c.text)
                    }
                }
            }
        }
        if (session.history.size >= 2) {
            RCard(Modifier.fillMaxWidth()) {
                Text("Last ${session.history.size} sets", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    session.history.forEachIndexed { i, score ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$score", style = MaterialTheme.typography.labelSmall, color = c.muted)
                            Box(
                                Modifier.fillMaxWidth().height((4 + score * 4).dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (i == session.history.lastIndex) c.brand else c.muted.copy(alpha = 0.35f)),
                            )
                        }
                    }
                }
            }
        }
    }
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RButton("Another set", session::start, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (misses.isNotEmpty() && onReview != null) RButton("Review misses", onReview, Modifier.weight(1f), style = ButtonStyle.Tonal)
            RButton("Change drill", session::close, Modifier.weight(1f), style = ButtonStyle.Tonal)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Palette.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    }
}
