package com.vibeflow.mobile.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase

/**
 * RevenueCat (Google Play billing) glue — the Android mirror of iOS's revenuecat.ts.
 *
 * The RevenueCat App User ID is set to the Supabase user id, so the RevenueCat→Supabase
 * webhook flips `profiles.is_pro` for the right account (same trick as iOS). Pro is
 * enforced SERVER-SIDE via `is_pro` (see SupabaseAuth.Quota), so this layer only has to
 * (a) make the purchase and (b) identify the user; the existing quota flow then reflects Pro.
 *
 * Inert until GOOGLE_KEY is filled — every call is a safe no-op, so the app runs unchanged.
 */
object RevenueCatManager {

    // TODO(founder): paste the RevenueCat Google public SDK key ("goog_XXXXXXXX").
    private const val GOOGLE_KEY = "goog_REPLACE_WITH_YOUR_KEY"
    private const val ENTITLEMENT_ID = "pro"

    @Volatile
    var configured = false
        private set

    /** Configure once at app start. No-op until a real key is set. */
    fun configure(context: Context) {
        if (configured || GOOGLE_KEY.contains("REPLACE")) return
        runCatching {
            Purchases.logLevel = LogLevel.WARN
            Purchases.configure(
                PurchasesConfiguration.Builder(context.applicationContext, GOOGLE_KEY).build(),
            )
            configured = true
        }
    }

    /** Tie purchases to the signed-in Supabase account (call after sign-in). */
    suspend fun logIn(userId: String) {
        if (!configured || userId.isBlank()) return
        runCatching { Purchases.sharedInstance.awaitLogIn(userId) }
    }

    suspend fun logOut() {
        if (!configured) return
        runCatching { Purchases.sharedInstance.awaitLogOut() }
    }

    /** True if the "pro" entitlement is active right now (secondary to server is_pro). */
    suspend fun isProActive(): Boolean {
        if (!configured) return false
        return runCatching {
            Purchases.sharedInstance.awaitCustomerInfo().entitlements.active.containsKey(ENTITLEMENT_ID)
        }.getOrDefault(false)
    }

    /** Packages in the current offering (empty until the goog_ key + Play products exist). */
    suspend fun currentPackages(): List<Package> {
        if (!configured) return emptyList()
        return runCatching {
            Purchases.sharedInstance.awaitOfferings().current?.availablePackages
        }.getOrNull().orEmpty()
    }

    /** Launch the Google Play purchase flow for [pkg]. Returns true on success. */
    suspend fun purchase(activity: Activity, pkg: Package): Boolean {
        if (!configured) return false
        return runCatching {
            Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            true
        }.getOrDefault(false)
    }
}
