package wrklabs.chess.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The one-time (non-consumable) product. Create it in Play Console under Monetize > Products > In-app products with this exact ID. */
object ProductIds {
    const val PRO = "pro"

    val ALL = listOf(PRO)
}

data class BillingUiState(
    val isConnected: Boolean = false,
    val ownsPro: Boolean = false,
    val products: Map<String, ProductDetails> = emptyMap(),
    // One-shot feedback for a purchase attempt that failed after the user
    // tried to pay (not shown for a plain user-cancelled flow).
    val errorMessage: String? = null
)

/** Thin wrapper around BillingClient for the app's single non-consumable Pro product. [onEntitlementsChanged] reports what Play says is owned. */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val onEntitlementsChanged: (ownsPro: Boolean) -> Unit
) {
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            _uiState.value = _uiState.value.copy(errorMessage = "Purchase failed: ${result.debugMessage}")
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _uiState.value = _uiState.value.copy(isConnected = true)
                    scope.launch {
                        queryProducts()
                        queryOwnedPurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _uiState.value = _uiState.value.copy(isConnected = false)
            }
        })
    }

    private suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProductIds.ALL.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val byId = result.productDetailsList.orEmpty().associateBy { it.productId }
        _uiState.value = _uiState.value.copy(products = byId)
    }

    /** Restores ownership on a fresh install or a new device: Play remembers non-consumable purchases. */
    fun restore() {
        if (billingClient.isReady) scope.launch { queryOwnedPurchases() } else startConnection()
    }

    private suspend fun queryOwnedPurchases() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        result.purchasesList.forEach { handlePurchase(it) }
        // Play's answer is final: a refunded purchase no longer shows up here.
        val owns = result.purchasesList.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(ProductIds.PRO) }
        _uiState.value = _uiState.value.copy(ownsPro = owns)
        onEntitlementsChanged(owns)
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val details = _uiState.value.products[productId] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val ownsPro = purchase.products.contains(ProductIds.PRO) || _uiState.value.ownsPro
        _uiState.value = _uiState.value.copy(ownsPro = ownsPro)
        onEntitlementsChanged(ownsPro)

        if (!purchase.isAcknowledged) {
            scope.launch {
                billingClient.acknowledgePurchase(
                    com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun endConnection() {
        billingClient.endConnection()
    }

    companion object {
        private const val TAG = "BillingManager"
    }
}
