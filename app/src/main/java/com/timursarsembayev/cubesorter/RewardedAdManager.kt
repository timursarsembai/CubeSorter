package com.timursarsembayev.cubesorter

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * Менеджер Rewarded (или rewarded interstitial) рекламы для бонуса времени.
 * Загружает одно объявление, пере-загружает после показа/ошибки.
 */
class RewardedAdManager(
    private val context: Context,
    private val adUnitId: String,
    private val useRewardedInterstitial: Boolean = true // включаем формат interstitial с вознаграждением по умолчанию
) {
    private var rewardedAd: RewardedAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var loading = false
    private var loadAttempts = 0
    private val maxLoadAttempts = 3
    private val handler = Handler(Looper.getMainLooper())

    var onAdShow: (() -> Unit)? = null
    var onAdDismiss: (() -> Unit)? = null
    var onAdFailedToShow: ((AdError) -> Unit)? = null
    var onReward: ((RewardItem) -> Unit)? = null
    var onAdLoaded: (() -> Unit)? = null

    fun isReady(): Boolean = if (useRewardedInterstitial) rewardedInterstitialAd != null else rewardedAd != null
    fun isLoading(): Boolean = loading

    fun preloadIfNeeded() {
        if (isReady() || loading) return
        load()
    }

    private fun load(delayMs: Long = 0L) {
        if (loading) return
        loading = true
        if (delayMs > 0) handler.postDelayed({ actuallyLoad() }, delayMs) else actuallyLoad()
    }

    private fun actuallyLoad() {
        val request = AdRequest.Builder().build()
        if (useRewardedInterstitial) {
            android.util.Log.i("Ads", "Loading RewardedInterstitial (attempt ${loadAttempts+1})")
            RewardedInterstitialAd.load(context, adUnitId, request, object: RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    loading = false
                    loadAttempts = 0
                    android.util.Log.i("Ads", "RewardedInterstitial loaded")
                    setInterstitialCallbacks(ad)
                    onAdLoaded?.invoke()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialAd = null
                    loading = false
                    android.util.Log.w("Ads", "RewardedInterstitial failed load: ${error.code} ${error.message}")
                    scheduleRetry(error.code)
                }
            })
        } else {
            android.util.Log.i("Ads", "Loading Rewarded (attempt ${loadAttempts+1})")
            RewardedAd.load(context, adUnitId, request, object: RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                    loadAttempts = 0
                    android.util.Log.i("Ads", "Rewarded loaded")
                    setRewardedCallbacks(ad)
                    onAdLoaded?.invoke()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                    android.util.Log.w("Ads", "Rewarded failed load: ${error.code} ${error.message}")
                    scheduleRetry(error.code)
                }
            })
        }
    }

    private fun scheduleRetry(code: Int) {
        if (loadAttempts >= maxLoadAttempts) {
            android.util.Log.w("Ads", "Rewarded retries exhausted")
            return
        }
        loadAttempts++
        val backoff = if (code == 3) 2000L * loadAttempts else 3000L
        android.util.Log.w("Ads", "Retry rewarded ${loadAttempts}/$maxLoadAttempts in ${backoff}ms")
        load(backoff)
    }

    private fun setRewardedCallbacks(ad: RewardedAd) {
        ad.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { onAdShow?.invoke(); android.util.Log.i("Ads", "Rewarded shown") }
            override fun onAdDismissedFullScreenContent() {
                android.util.Log.i("Ads", "Rewarded dismissed")
                rewardedAd = null
                preloadIfNeeded()
                onAdDismiss?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                android.util.Log.w("Ads", "Rewarded failed to show: ${adError.code} ${adError.message}")
                rewardedAd = null
                preloadIfNeeded()
                onAdFailedToShow?.invoke(adError)
            }
        }
    }

    private fun setInterstitialCallbacks(ad: RewardedInterstitialAd) {
        ad.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { onAdShow?.invoke(); android.util.Log.i("Ads", "RewardedInterstitial shown") }
            override fun onAdDismissedFullScreenContent() {
                android.util.Log.i("Ads", "RewardedInterstitial dismissed")
                rewardedInterstitialAd = null
                preloadIfNeeded()
                onAdDismiss?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                android.util.Log.w("Ads", "RewardedInterstitial failed to show: ${adError.code} ${adError.message}")
                rewardedInterstitialAd = null
                preloadIfNeeded()
                onAdFailedToShow?.invoke(adError)
            }
        }
    }

    fun showIfReady(activity: Activity, onRewardEarned: () -> Unit): Boolean {
        return if (useRewardedInterstitial) {
            val ad = rewardedInterstitialAd ?: return false
            ad.show(activity) { rewardItem ->
                android.util.Log.i("Ads", "Reward (interstitial) earned: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned(); onReward?.invoke(rewardItem)
            }
            rewardedInterstitialAd = null
            true
        } else {
            val ad = rewardedAd ?: return false
            ad.show(activity) { rewardItem ->
                android.util.Log.i("Ads", "Reward earned: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned(); onReward?.invoke(rewardItem)
            }
            rewardedAd = null
            true
        }
    }
}
