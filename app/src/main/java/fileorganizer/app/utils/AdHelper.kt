package fileorganizer.app.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdHelper {
    private const val TAG = "AdHelper"

    // Temporary diagnostic mode on main: Google's official demo interstitial.
    // Premium/Billing gating is enabled in this step to test production entitlement logic.
    private const val DIAGNOSTIC_TEST_MODE = false
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var isMobileAdsInitialized = false
    private var isMobileAdsInitializing = false

    private fun isBlockedByEntitlement(): Boolean {
        return !DIAGNOSTIC_TEST_MODE &&
            (!BillingManager.entitlementReady.value || BillingManager.isPremium.value)
    }

    fun syncForEntitlement(context: Context) {
        Log.d(
            TAG,
            "syncForEntitlement: diagnostic=$DIAGNOSTIC_TEST_MODE, " +
                "entitlementReady=${BillingManager.entitlementReady.value}, " +
                "isPremium=${BillingManager.isPremium.value}"
        )

        if (isBlockedByEntitlement()) {
            Log.d(TAG, "Ads blocked by Premium entitlement state.")
            clearInterstitialAd()
            return
        }

        ensureMobileAdsInitialized(context)
    }

    fun loadInterstitialAd(context: Context) {
        if (isBlockedByEntitlement()) {
            Log.d(TAG, "Ad load skipped because entitlement blocks ads.")
            clearInterstitialAd()
            return
        }

        if (!isMobileAdsInitialized) {
            ensureMobileAdsInitialized(context)
            return
        }

        loadInterstitialAdInternal(context)
    }

    private fun ensureMobileAdsInitialized(context: Context) {
        if (isBlockedByEntitlement()) {
            Log.d(TAG, "AdMob initialization skipped because entitlement blocks ads.")
            clearInterstitialAd()
            return
        }

        if (isMobileAdsInitialized) {
            loadInterstitialAdInternal(context)
            return
        }

        if (isMobileAdsInitializing) {
            Log.d(TAG, "AdMob initialization already in progress.")
            return
        }

        isMobileAdsInitializing = true
        Log.d(TAG, "Initializing Google Mobile Ads SDK. diagnostic=$DIAGNOSTIC_TEST_MODE")

        try {
            MobileAds.initialize(context.applicationContext) {
                isMobileAdsInitializing = false
                isMobileAdsInitialized = true
                Log.d(TAG, "Google Mobile Ads SDK initialized successfully.")

                if (isBlockedByEntitlement()) {
                    Log.d(TAG, "Ads became blocked while AdMob was initializing.")
                    clearInterstitialAd()
                    return@initialize
                }

                loadInterstitialAdInternal(context.applicationContext)
            }
        } catch (e: Exception) {
            isMobileAdsInitializing = false
            Log.e(TAG, "AdMob initialization failed.", e)
        }
    }

    private fun loadInterstitialAdInternal(context: Context) {
        if (isBlockedByEntitlement()) {
            Log.d(TAG, "Internal ad load skipped because entitlement blocks ads.")
            clearInterstitialAd()
            return
        }

        if (!isMobileAdsInitialized || mInterstitialAd != null || isAdLoading) return

        isAdLoading = true
        Log.d(TAG, "Loading interstitial ad. diagnostic=$DIAGNOSTIC_TEST_MODE")
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(
                        TAG,
                        "Ad failed to load: code=${adError.code}, " +
                            "domain=${adError.domain}, message=${adError.message}"
                    )
                    mInterstitialAd = null
                    isAdLoading = false
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    isAdLoading = false
                    if (isBlockedByEntitlement()) {
                        Log.d(TAG, "Loaded ad discarded because entitlement now blocks ads.")
                        mInterstitialAd = null
                        return
                    }
                    Log.d(TAG, "Interstitial ad loaded successfully.")
                    mInterstitialAd = interstitialAd
                }
            }
        )
    }

    fun showInterstitialAd(context: Context, onAdDismissed: () -> Unit) {
        Log.d(
            TAG,
            "showInterstitialAd: diagnostic=$DIAGNOSTIC_TEST_MODE, " +
                "entitlementReady=${BillingManager.entitlementReady.value}, " +
                "isPremium=${BillingManager.isPremium.value}, " +
                "adReady=${mInterstitialAd != null}"
        )

        if (isBlockedByEntitlement()) {
            Log.d(TAG, "Ad show skipped because entitlement blocks ads.")
            onAdDismissed()
            return
        }

        val activity = findActivity(context)
        if (activity != null && mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed fullscreen content.")
                    mInterstitialAd = null
                    loadInterstitialAd(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(
                        TAG,
                        "Ad failed to show: code=${adError.code}, " +
                            "domain=${adError.domain}, message=${adError.message}"
                    )
                    mInterstitialAd = null
                    loadInterstitialAd(activity)
                    onAdDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed fullscreen content.")
                }
            }
            mInterstitialAd?.show(activity)
        } else {
            Log.d(
                TAG,
                "Interstitial ad not ready. activityAvailable=${activity != null}; requesting load."
            )
            if (activity != null) {
                loadInterstitialAd(activity)
            }
            onAdDismissed()
        }
    }

    private fun clearInterstitialAd() {
        mInterstitialAd = null
        isAdLoading = false
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }
}
