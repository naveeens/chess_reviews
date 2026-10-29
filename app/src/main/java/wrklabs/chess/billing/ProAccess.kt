package wrklabs.chess.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the one-time Pro purchase is owned. Play is the source of truth; this keeps its last answer so the app works offline. */
class ProAccess(context: Context) {
    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val _owned = MutableStateFlow(prefs.getBoolean(KEY, false))
    val owned: StateFlow<Boolean> = _owned.asStateFlow()

    fun set(owned: Boolean) {
        prefs.edit().putBoolean(KEY, owned).apply()
        _owned.value = owned
    }

    private companion object {
        const val KEY = "owned"
    }
}

/** Free accounts review a few games a week; reviews already made, and a game reviewed again, cost nothing. */
object ReviewQuota {
    const val FREE_PER_WEEK = 5
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** [times] are when this week's reviews were made. */
    fun allowed(pro: Boolean, times: List<Long>): Boolean = pro || times.size < FREE_PER_WEEK

    /** When the oldest review of the week drops out and a free one comes back; null while one is free. */
    fun nextFreeAt(times: List<Long>): Long? =
        if (times.size < FREE_PER_WEEK) null else times.sorted()[times.size - FREE_PER_WEEK] + WEEK_MS
}
