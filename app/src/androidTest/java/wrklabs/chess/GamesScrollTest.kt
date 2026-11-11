package wrklabs.chess

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wrklabs.chess.ui.ScrollToTopForNew

/** The games list, as new games arrive at the top of it. */
@RunWith(AndroidJUnit4::class)
class GamesScrollTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var state: androidx.compose.foundation.lazy.LazyListState
    private val items = mutableStateOf((1..60).map { "g$it" })
    private val query = mutableStateOf("")

    private fun show() {
        rule.setContent {
            state = rememberLazyListState()
            ScrollToTopForNew(state, items.value.firstOrNull(), query.value)
            LazyColumn(Modifier.height(400.dp), state = state) { items(items.value, key = { it }) { Text(it, Modifier.height(60.dp)) } }
        }
    }

    @Test fun newGamesAtTheTopAreShownWhenTheListWasAtTheTop() {
        show()
        rule.runOnIdle { items.value = listOf("n1", "n2", "n3") + items.value }
        rule.waitForIdle()
        assertEquals(0, rule.runOnIdle { state.firstVisibleItemIndex })
    }

    @Test fun aListScrolledDownIsLeftWhereItIs() {
        show()
        rule.runOnIdle { runBlocking { state.scrollToItem(20) } }
        rule.waitForIdle()
        rule.runOnIdle { items.value = listOf("n1", "n2", "n3") + items.value }
        rule.waitForIdle()
        // The same row is first on screen as before: the three new ones above it moved it down by three.
        assertEquals(23, rule.runOnIdle { state.firstVisibleItemIndex })
    }

    @Test fun aNewSearchOrSortStartsAtTheTop() {
        show()
        rule.runOnIdle { runBlocking { state.scrollToItem(20) } }
        rule.waitForIdle()
        rule.runOnIdle { query.value = "other"; items.value = items.value.reversed() }
        rule.waitForIdle()
        assertEquals(0, rule.runOnIdle { state.firstVisibleItemIndex })
    }
}
