package com.timursarsembayev.cubesorter

import android.app.Application

class CubeSorterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BillingManager.init(this)
        // Ads/UMP инициализируются в SorterActivity, чтобы гарантировать наличие Activity-контекста.
    }
}
