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
    // Test Interstitial Ad Unit ID provided by Google
    private const val AD_UNIT_ID = "ca-app-pub-5529222451841351/3631189983"

    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var isMobileAdsInitialized = false
    private var isMobileAdsInitializing = false

    /**
     * Keep ad state aligned with the latest verified Premium entitlement.
     * AdMob is initialized only after Google Play confirms the user is not Premium.
     */
    fun syncForEntitlement(context: Context) {
        if (!BillingManager.entitlementReady.value || BillingManager.isPremium.value) {
            clearInterstitialAd()
            return
        }

        ensureMobileAdsInitialized(context)
    }

    fun loadInterstitialAd(context: Context) {
        if (!BillingManager.entitlementReady.value || BillingManager.isPremium.value) {
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
        if (!BillingManager.entitlementReady.value || BillingManager.isPremium.value) {
            clearInterstitialAd()
            return
        }

        if (isMobileAdsInitialized) {
            loadInterstitialAdInternal(context)
            return
        }

        if (isMobileAdsInitializing) return
        isMobileAdsInitializing = true

        try {
            MobileAds.initialize(context.applicationContext) {
                isMobileAdsInitializing = false
                isMobileAdsInitialized = true

                // Entitlement may have changed while the SDK was initializing.
                if (!BillingManager.entitlementReady.value || BillingManager.isPremium.value) {
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
        if (!BillingManager.entitlementReady.value || BillingManager.isPremium.value) {
            clearInterstitialAd()
            return
        }

        if (!isMobileAdsInitialized || mInterstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Ad failed to load: ${adError.message}")
                    mInterstitialAd = null
                    isAdLoading = false
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    isAdLoading = false
                    if (!BillingManager.entitlementReady.value || BillingManager.isPremium.value) {
                        // Entitlement may have changed while the ad was loading.
                        mInterstitialAd = null
                        return
                    }
                    Log.d(TAG, "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                }
            }
        )
    }

    fun showInterstitialAd(context: Context, onAdDismissed: () -> Unit) {
        // Premium users (and users whose entitlement has not been verified yet)
        // continue directly without initializing, loading, or displaying AdMob.
        if (!BillingManager.entitlementReady.value || BillingManager.isPremium.value) {
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
                    Log.e(TAG, "Ad failed to show fullscreen content.")
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
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
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
