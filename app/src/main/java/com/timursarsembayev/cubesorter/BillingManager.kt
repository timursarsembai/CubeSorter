package com.timursarsembayev.cubesorter

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple billing manager for a single non-consumable product: remove ads.
 */
object BillingManager : PurchasesUpdatedListener {
    private const val PRODUCT_ID_REMOVE_ADS = "remove_ads" // Configure in Play Console
    private const val PREFS = "billing_prefs"
    private const val KEY_ADS_REMOVED = "ads_removed"

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private val isInitializing = AtomicBoolean(false)
    private var isReady = false
    private var pendingLaunch = false

    private lateinit var appContext: Context

    var onStatusChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (isInitializing.get() || isReady) return
        isInitializing.set(true)
        appContext = context.applicationContext
        billingClient = BillingClient.newBuilder(appContext)
            .enablePendingPurchases()
            .setListener(this)
            .build()
        billingClient?.startConnection(object: BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.w("Billing", "Service disconnected")
                isInitializing.set(false)
                isReady = false
            }
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isReady = true
                    isInitializing.set(false)
                    Log.i("Billing", "Setup finished")
                    queryProductDetails()
                    restoreIfNeeded()
                } else {
                    Log.e("Billing", "Setup failed: ${result.responseCode}")
                    isInitializing.set(false)
                }
            }
        })
    }

    fun isBillingReady(): Boolean = isReady

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAdsRemoved(): Boolean = prefs().getBoolean(KEY_ADS_REMOVED, false)

    private fun setAdsRemoved() {
        if (!isAdsRemoved()) {
            prefs().edit().putBoolean(KEY_ADS_REMOVED, true).apply()
            onStatusChanged?.invoke()
        }
    }

    private fun queryProductDetails() {
        val bc = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_REMOVE_ADS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()))
            .build()
        bc.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && list.isNotEmpty()) {
                productDetails = list.first()
                Log.i("Billing", "Product details loaded")
                onStatusChanged?.invoke()
            } else {
                Log.w("Billing", "Product details query failed: ${result.responseCode}")
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        if (!isReady || productDetails == null || pendingLaunch) return
        if (isAdsRemoved()) return
        val bc = billingClient ?: return
        val details = productDetails ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            ))
            .build()
        val res = bc.launchBillingFlow(activity, params)
        pendingLaunch = res.responseCode == BillingClient.BillingResponseCode.OK
        Log.i("Billing", "Launch flow rc=${res.responseCode}")
    }

    private fun restoreIfNeeded() {
        val bc = billingClient ?: return
        bc.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any { it.products.contains(PRODUCT_ID_REMOVE_ADS) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (owned) {
                    setAdsRemoved()
                    purchases.forEach { acknowledgeIfNeeded(it) }
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        pendingLaunch = false
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                if (purchase.products.contains(PRODUCT_ID_REMOVE_ADS) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    setAdsRemoved()
                    acknowledgeIfNeeded(purchase)
                }
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i("Billing", "Purchase canceled")
        } else if (result.responseCode != BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Log.w("Billing", "Purchase failed: ${result.responseCode}")
        } else {
            // Already owned
            setAdsRemoved()
        }
        onStatusChanged?.invoke()
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val bc = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        bc.acknowledgePurchase(params) { br ->
            Log.i("Billing", "Acknowledge rc=${br.responseCode}")
        }
    }

    fun formattedPrice(): String? = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
}
