package com.silpa.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One free app; a one-time "Silpa Pro" in-app product removes ads.
 * Create the product with ID [PRODUCT_ID] in Play Console → Monetize → In-app products.
 */
class ProManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("pro", 0)
    private val _isPro = MutableStateFlow(prefs.getBoolean("pro", false))
    val isPro: StateFlow<Boolean> = _isPro
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price
    private var details: ProductDetails? = null

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(PurchasesUpdatedListener { r, p -> onPurchasesUpdated(r, p) })
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init { connect() }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode != BillingClient.BillingResponseCode.OK) return
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { _, list -> handle(list, restoring = true) }
                client.queryProductDetailsAsync(
                    QueryProductDetailsParams.newBuilder().setProductList(listOf(
                        QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build(),
                    )).build(),
                ) { _, res ->
                    details = res.productDetailsList.firstOrNull()
                    _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
                }
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Returns false if the store isn't ready (e.g. offline or product not configured yet). */
    fun buy(activity: Activity): Boolean {
        val d = details ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build(),
        )).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun onPurchasesUpdated(r: BillingResult, purchases: List<Purchase>?) {
        if (r.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty(), restoring = false)
    }

    private fun handle(list: List<Purchase>, restoring: Boolean) {
        val owned = list.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        owned.filter { !it.isAcknowledged }.forEach { p ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
        }
        if (owned.isNotEmpty() || restoring) {
            _isPro.value = owned.isNotEmpty()
            prefs.edit().putBoolean("pro", owned.isNotEmpty()).apply()
        }
    }

    companion object {
        const val PRODUCT_ID = "silpa_pro"
        @Volatile private var instance: ProManager? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: ProManager(context.applicationContext).also { instance = it } }
    }
}
