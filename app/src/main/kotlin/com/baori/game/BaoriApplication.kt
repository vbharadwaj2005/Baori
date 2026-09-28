package com.baori.game

import android.app.Activity
import android.app.Application
import com.baori.game.data.AssetReader
import com.baori.game.data.BaoriPreferences
import com.baori.game.data.LevelRepository
import com.baori.game.data.billing.BillingService
import com.baori.game.data.billing.FakeBillingService
import com.baori.game.data.billing.RevenueCatBillingService
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App shell. Owns process-wide singletons (PRD §7.2 app shell) and wires the
 * dependency graph once, so screens and ViewModels never touch Android
 * framework classes beyond what Compose already provides.
 *
 * RevenueCat is configured here at launch (PRD §8.2 step 1) with the key
 * injected at build time (see app/build.gradle.kts). When the key is still
 * the clean-clone placeholder, the app falls back to [FakeBillingService] so
 * a judge can clone-and-run the full experience with zero setup (PRD §14.1).
 *
 * The activity tracker exists solely so the billing layer can present the
 * purchase dialog over the foreground Activity without any screen having to
 * pass one down.
 */
class BaoriApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var preferences: BaoriPreferences
        private set

    lateinit var levelRepository: LevelRepository
        private set

    lateinit var billingService: BillingService
        private set

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        preferences = BaoriPreferences(this)
        levelRepository = LevelRepository(AndroidAssetReader(this))

        billingService = createBillingService()
        if (billingService is RevenueCatBillingService) {
            (billingService as RevenueCatBillingService).activityProvider = {
                currentActivity?.takeIf { !it.isFinishing }
            }
        }
        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    /**
     * Chooses the billing backend once, at launch. A configured RevenueCat
     * key (local.properties or env var — never committed) selects the real
     * SDK against RevenueCat's Test Store; anything else keeps the demo
     * fully playable through the fake.
     */
    private fun createBillingService(): BillingService {
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        return if (apiKey.isNotBlank() && apiKey != PLACEHOLDER_KEY) {
            Purchases.logLevel = com.revenuecat.purchases.LogLevel.DEBUG
            Purchases.configure(
                PurchasesConfiguration.Builder(this, apiKey).build(),
            )
            RevenueCatBillingService(Purchases.sharedInstance)
        } else {
            FakeBillingService(preferences, applicationScope)
        }
    }

    private val activityLifecycleCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            currentActivity = activity
        }

        override fun onActivityStopped(activity: Activity) {
            if (currentActivity === activity) currentActivity = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    companion object {
        /** Must match the fallback in app/build.gradle.kts. */
        const val PLACEHOLDER_KEY = "REPLACE_WITH_YOUR_REVENUECAT_TEST_API_KEY"
    }
}

/** Reads bundled text files (level JSON) through the app's AssetManager. */
private class AndroidAssetReader(private val context: android.content.Context) : AssetReader {
    override fun readText(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
