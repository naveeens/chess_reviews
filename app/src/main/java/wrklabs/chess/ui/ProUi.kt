package wrklabs.chess.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import wrklabs.chess.Graph
import wrklabs.chess.billing.ProductIds
import wrklabs.chess.ui.theme.Palette

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The Pro purchase: a Buy button with the store's price, and a way to get it back on a new phone. */
@Composable
fun ProUnlock(modifier: Modifier = Modifier) {
    val c = Palette.colors
    val ui by Graph.billing.uiState.collectAsState()
    val activity = LocalContext.current.activity()
    val price = ui.products[ProductIds.PRO]?.oneTimePurchaseOfferDetails?.formattedPrice
    Column(modifier.fillMaxWidth()) {
        RButton(
            if (price != null) "Unlock Pro · $price" else "Unlock Pro",
            { if (activity != null) Graph.billing.launchPurchaseFlow(activity, ProductIds.PRO) },
            Modifier.fillMaxWidth(),
            enabled = price != null && activity != null,
        )
        if (price == null) Text("The store isn't available right now.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        ui.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger, modifier = Modifier.padding(top = 6.dp)) }
        RButton("Restore purchase", Graph.billing::restore, Modifier.fillMaxWidth().padding(top = 8.dp), style = ButtonStyle.Ghost, compact = true)
    }
}
