package com.linkshare.app.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LinkoBillingManager
 *
 * Stub for Google Play Billing integration.
 * Full implementation activates in Phase 22 (Monetization Implementation).
 *
 * To implement:
 * 1. Add Google Play Billing dependency:
 *    implementation("com.android.billingclient:billing-ktx:7.0.0")
 * 2. Replace all TODO stubs below with real BillingClient calls
 * 3. Set up server-side purchase token validation via Google Play Developer API
 *
 * Plans:
 * - "linko_pro_monthly" → Pro ($4.99/month)
 * - "linko_unlimited_monthly" → Unlimited ($9.99/month)
 */
class LinkoBillingManager(private val context: Context) {

    private val _subscriptionState = MutableStateFlow(LinkoSubscriptionState.Free)
    val subscriptionState: StateFlow<LinkoSubscriptionState> = _subscriptionState.asStateFlow()

    /**
     * Initialize the billing client and query existing subscription status.
     * Called on app startup after authentication.
     */
    fun initialize() {
        // TODO: Initialize BillingClient
        // TODO: Query existing purchases to restore subscription state
        // For MVP: all users are on Free plan
        _subscriptionState.value = LinkoSubscriptionState.Free
    }

    /**
     * Launch the Play Store purchase flow for the specified plan.
     * @param activity The current Activity (required by Play Billing)
     * @param planId "linko_pro_monthly" or "linko_unlimited_monthly"
     */
    fun purchasePlan(activity: Activity, planId: String) {
        // TODO: Query available products via BillingClient.queryProductDetailsAsync
        // TODO: Launch BillingFlowParams with the selected product
        // TODO: Handle purchase result in onPurchasesUpdated callback
        // TODO: Validate purchase token with backend POST /v1/subscriptions/validate
    }

    /**
     * Restore existing purchases (e.g. after reinstall).
     */
    fun restorePurchases() {
        // TODO: BillingClient.queryPurchasesAsync(QueryPurchasesParams)
        // TODO: Validate and apply each purchase
    }

    /**
     * Called when the user navigates away — disconnect the billing client.
     */
    fun destroy() {
        // TODO: billingClient.endConnection()
    }

    companion object {
        const val PLAN_PRO = "linko_pro_monthly"
        const val PLAN_UNLIMITED = "linko_unlimited_monthly"
    }
}
