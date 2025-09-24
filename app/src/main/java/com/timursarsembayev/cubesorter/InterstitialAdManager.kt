package com.timursarsembayev.cubesorter

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(private val context: Context, private val adUnitId: String) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var loadAttempts = 0
    private val maxLoadAttempts = 3
    private val handler = Handler(Looper.getMainLooper())

    fun preloadIfNeeded() {
        if (interstitialAd != null || isLoading) return
        load()
    }

    private fun load(delayMs: Long = 0L) {
        if (isLoading) return
        isLoading = true
        if (delayMs > 0) {
            handler.postDelayed({ actuallyLoad() }, delayMs)
        } else {
            actuallyLoad()
        }
    }

    private fun actuallyLoad() {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(context, adUnitId, request, object: InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                isLoading = false
                loadAttempts = 0
                android.util.Log.i("Ads", "Interstitial loaded")
                setCallbacks(ad)
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                android.util.Log.w("Ads", "Interstitial failed load: ${error.code} ${error.message}")
                isLoading = false
                interstitialAd = null
                if (error.code == 3) { // No fill — экспоненциальная задержка до 3 попыток
                    if (loadAttempts < maxLoadAttempts) {
                        loadAttempts++
                        val backoff = 2000L * loadAttempts
                        android.util.Log.w("Ads", "Retry interstitial ${loadAttempts}/$maxLoadAttempts in ${backoff}ms")
                        load(backoff)
                    }
                } else {
                    // Другие ошибки — одна повторная попытка
                    if (loadAttempts < maxLoadAttempts) {
                        loadAttempts++
                        load(3000L)
                    }
                }
            }
        })
    }

    private fun setCallbacks(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                android.util.Log.i("Ads", "Interstitial dismissed")
                interstitialAd = null
                preloadIfNeeded()
                onDismissCallback?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                android.util.Log.w("Ads", "Interstitial failed to show: ${adError.code} ${adError.message}")
                interstitialAd = null
                preloadIfNeeded()
                onDismissCallback?.invoke()
            }
            override fun onAdShowedFullScreenContent() {
                android.util.Log.i("Ads", "Interstitial shown")
                onShowCallback?.invoke()
            }
        }
    }

    var onDismissCallback: (() -> Unit)? = null
    var onShowCallback: (() -> Unit)? = null

    fun showIfReady(activity: Activity): Boolean {
        val ad = interstitialAd ?: return false
        ad.show(activity)
        interstitialAd = null
        return true
    }
}

