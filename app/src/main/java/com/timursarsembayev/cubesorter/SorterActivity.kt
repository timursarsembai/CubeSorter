package com.timursarsembayev.cubesorter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.pm.ApplicationInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.graphics.Typeface

class SorterActivity : Activity() {

    private lateinit var textLevel: TextView
    private lateinit var textTimer: TextView
    private lateinit var textMoves: TextView
    private lateinit var sorterGameView: SorterGameView
    // Drawer
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var buttonOpenDrawer: ImageButton
    // Убрана кнопка Reset

    // Пункты меню в Drawer
    private lateinit var menuStartGame: TextView
    private lateinit var menuDifficulty: TextView
    private lateinit var menuRecords: TextView
    private lateinit var menuReset: TextView
    private lateinit var menuRemoveAds: TextView

    // Жизни
    private lateinit var textLivesHeader: TextView
    // Сложность (индикатор слева от сердечек)
    private lateinit var textDifficulty: TextView

    private var startTime: Long = 0
    private var isTimerRunning = false

    // Обратный отсчет
    private var timeLimitMs: Long = 0L
    private var deadlineMs: Long = 0L
    private var timeUpHandled: Boolean = false

    // Диалог завершения уровня
    private var levelDialog: AlertDialog? = null

    // Хранение прогресса
    private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

    // Ключи и модель сложности/жизней
    private enum class Difficulty { EASY, NORM, HARD, EXTREME }
    private val KEY_DIFFICULTY = "difficulty"
    private val KEY_LIVES_CURRENT = "lives_current"
    private val livesMax = 5
    private var livesCurrent = livesMax
    private var difficulty: Difficulty = Difficulty.NORM

    // Админ режим
    private var isAdminMode = false
    private var isLevelPressing = false
    private val longPressThresholdMs = 10_000L
    private val levelPressHandler = Handler(Looper.getMainLooper())
    private var levelLongPressRunnable: Runnable? = null

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                updateTimer()
                handler.postDelayed(this, 100)
            }
        }
    }

    // Флаг, чтобы не инициализировать рекламу повторно
    private var adsInitialized = false

    // Реклама
    private var bannerAdView: AdView? = null
    private var bannerFallbackTried = false
    private var mainBannerHadFill = false
    private var mainBannerRetryAttempts = 0
    private val maxMainBannerRetries = 3
    // Нативная реклама для диалога завершения уровня
    private var nativeAd: com.google.android.gms.ads.nativead.NativeAd? = null
    private var nativeAdLoadAttempts = 0
    private val maxNativeAdRetries = 2
    // Баннер-фолбэк для диалога
    private var dialogBannerAdView: AdView? = null
    private var dialogBannerFixedTried = false
    private var dialogBannerHadFill = false

    // Прелоад для модального окна
    private var preloadedNativeAd: com.google.android.gms.ads.nativead.NativeAd? = null
    private var isPreloadingNative = false
    private var preloadedDialogBanner: AdView? = null
    private var isPreloadingBanner = false
    private var preloadedBannerLoaded = false

    // Interstitial (каждые 5 раундов)
    private var interstitialManager: InterstitialAdManager? = null
    private val interstitialTargetRounds = setOf(5,10,15,20,25,30,35,40)
    private var pendingShowInterstitialAfterDialog = false

    // Rewarded (time bonus)
    private var rewardedManager: RewardedAdManager? = null
    private var rewardUsedThisTimeUp = false
    private var rewardUsedThisRound = false
    private var pendingRewardEarned = false
    // Ссылки на открытый диалог TimeUp для обновления статуса при поздней загрузке
    private var timeUpDialog: AlertDialog? = null
    private var timeUpAddButton: android.widget.Button? = null
    private var timeUpRewardStatus: TextView? = null

    // Rewarded extra life
    private var rewardedExtraLifeManager: RewardedAdManager? = null
    private var extraLifeUsedThisGameOver = false
    private var extraLifeDialog: AlertDialog? = null
    private var extraLifeButton: android.widget.Button? = null
    private var extraLifeStatus: TextView? = null

    // Временные метрики для рекламы
    private var lastBannerLoadStart: Long = 0L
    private var forcingBannerReload = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sorter)

        initializeViews()
        loadDifficultyAndLives()
        updateDifficultyUI()
        setupGameCallbacks()
        setupAdminGesture()
        setupDrawer()
        // setupResetButton() удалён — логика вынесена в ResetActivity
        startNewGame()
        restoreProgressIfAny()
        // Инициализация лимита времени для текущего уровня на первом запуске,
        // т.к. onRoundChanged мог отработать до установки колбэков в этой активити
        if (timeLimitMs == 0L) {
            timeLimitMs = computeTimeLimitMs(sorterGameView.currentRound)
        }
        updateLivesUI()
        if (BillingManager.isAdsRemoved()) {
            // Скрываем контейнер баннера, не инициализируем MobileAds/UMP
            findViewById<FrameLayout?>(R.id.adContainer)?.visibility = View.GONE
        } else {
            requestConsentThenInitAds()
        }
    }

    override fun onResume() {
        super.onResume()
        // Обработка сбросов, инициированных на экране Reset
        if (prefs.getBoolean("pending_reset_all", false)) {
            prefs.edit().putBoolean("pending_reset_all", false).apply()
            clearAllRecords()
            saveLevel(1)
            sorterGameView.resetAll()
            livesCurrent = livesMax
            prefs.edit().putInt(KEY_LIVES_CURRENT, livesCurrent).apply()
            resetTimer()
            updateLivesUI()
            Toast.makeText(this, getString(R.string.toast_reset_all_done), Toast.LENGTH_SHORT).show()
        } else if (prefs.getBoolean("pending_reset_current", false)) {
            prefs.edit().putBoolean("pending_reset_current", false).apply()
            val current = sorterGameView.currentRound
            clearLevelRecords(current)
            sorterGameView.jumpToLevel(current)
            resetTimer()
            Toast.makeText(this, getString(R.string.toast_reset_current_done), Toast.LENGTH_SHORT).show()
        }

        // Подхватываем возможное изменение сложности на отдельном экране
        val prevDifficulty = difficulty
        loadDifficultyAndLives()
        if (difficulty != prevDifficulty) {
            // Сложность изменилась — перезапускаем текущий уровень без сброса рекордов
            sorterGameView.jumpToLevel(sorterGameView.currentRound)
            // onRoundChanged выполнит resetTimer() и пересчитает timeLimitMs
        }
        updateDifficultyUI()
        updateLivesUI()

        // Возобновляем AdView, если он есть
        bannerAdView?.resume()
        // Если на старте не удалось загрузить баннер — попробуем снова
        if (!mainBannerHadFill && bannerAdView == null) {
            android.util.Log.i("Ads", "Retry bottom banner onResume")
            loadBannerAdIfPresent()
        }
    }

    override fun onPause() {
        super.onPause()
        // Приостанавливаем AdView
        bannerAdView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        resetTimer()
        levelDialog?.dismiss()
        // Освобождаем ресурсы AdView
        bannerAdView?.destroy()
        bannerAdView = null
    }

    private fun initializeViews() {
        textLevel = findViewById(R.id.textLevel)
        textTimer = findViewById(R.id.textTimer)
        textMoves = findViewById(R.id.textMoves)
        sorterGameView = findViewById(R.id.sorterGameView)
        drawerLayout = findViewById(R.id.drawerLayout)
        buttonOpenDrawer = findViewById(R.id.buttonOpenDrawer)
        textLivesHeader = findViewById(R.id.textLivesHeader)
        textDifficulty = findViewById(R.id.textDifficulty)
        // Пункты меню
        menuStartGame = findViewById(R.id.menuStartGame)
        menuDifficulty = findViewById(R.id.menuDifficulty)
        menuRecords = findViewById(R.id.menuRecords)
        menuReset = findViewById(R.id.menuReset)
        menuRemoveAds = findViewById(R.id.menuRemoveAds)
    }

    private fun setupDrawer() {
        buttonOpenDrawer.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        menuStartGame.setOnClickListener {
            // Просто закрываем меню и остаёмся на экране игры
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        menuDifficulty.setOnClickListener {
            startActivity(Intent(this, DifficultyActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        menuRecords.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        menuReset.setOnClickListener {
            startActivity(Intent(this, ResetActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        menuRemoveAds.setOnClickListener {
            startActivity(Intent(this, RemoveAdsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    // ---- Records helpers ----
    private fun bestTimeKey(level: Int) = "best_time_$level" // Long (ms)
    private fun bestMovesKey(level: Int) = "best_moves_$level" // Int
    private fun recordDateKey(level: Int) = "record_date_$level" // Long (ms)
    private fun getBestTime(level: Int): Long = prefs.getLong(bestTimeKey(level), Long.MAX_VALUE)
    private fun getBestMoves(level: Int): Int = prefs.getInt(bestMovesKey(level), Int.MAX_VALUE)
    private fun getRecordDate(level: Int): Long = prefs.getLong(recordDateKey(level), Long.MAX_VALUE)
    private fun saveBestTime(level: Int, v: Long) { prefs.edit().putLong(bestTimeKey(level), v).apply() }
    private fun saveBestMoves(level: Int, v: Int) { prefs.edit().putInt(bestMovesKey(level), v).apply() }
    private fun saveRecordDate(level: Int, v: Long) { prefs.edit().putLong(recordDateKey(level), v).apply() }
    private fun formatElapsed(ms: Long): String {
        if (ms == Long.MAX_VALUE) return "--:--.-"
        val m = (ms / 60000).toInt(); val s = ((ms % 60000)/1000).toInt(); val t = ((ms % 1000)/100).toInt()
        return String.format(Locale.getDefault(), "%02d:%02d.%d", m, s, t)
    }
    private fun formatDate(ms: Long): String {
        if (ms == Long.MAX_VALUE) return getString(R.string.dash)
        return SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(ms))
    }

    private fun setupGameCallbacks() {
        sorterGameView.onMovesChanged = { moves ->
            textMoves.text = moves.toString()
            if (moves == 1 && !isTimerRunning) {
                startTimer()
                if (timeLimitMs > 0L) {
                    deadlineMs = startTime + timeLimitMs
                    timeUpHandled = false
                }
            }
        }

        sorterGameView.onRoundChanged = { round, _ ->
            textLevel.text = round.toString()
            resetTimer()
            // Расчет лимита времени согласно сложности
            timeLimitMs = computeTimeLimitMs(round)
            saveLevel(round)
            // Пробуем прелоадить рекламу для модалки
            if (!isPreloadingNative && preloadedNativeAd == null) preloadNativeAd()
            if (!isPreloadingBanner && (preloadedDialogBanner == null || !preloadedBannerLoaded)) preloadDialogBanner()
            // Прелоадим интерстициал если нужно
            interstitialManager?.preloadIfNeeded()
            rewardUsedThisRound = false
        }

        sorterGameView.onRoundCompleted = { round, moves ->
            val elapsedMillis = if (startTime > 0) System.currentTimeMillis() - startTime else 0L
            pauseTimer()
            showLevelCompletedDialog(round, moves, elapsedMillis)
        }

        sorterGameView.onAllCompleted = {
            // Все уровни завершены
            resetTimer()
            saveLevel(SorterGameView.MAX_LEVEL) // сохраняем финальный уровень
            startCongratulations()
        }
    }

    // setupResetButton() — УДАЛЁН

    private fun clearLevelRecords(level: Int) {
        prefs.edit()
            .remove(bestTimeKey(level))
            .remove(bestMovesKey(level))
            .remove(recordDateKey(level))
            .apply()
    }

    private fun clearAllRecords() {
        val e = prefs.edit()
        for (lv in 1..SorterGameView.MAX_LEVEL) {
            e.remove(bestTimeKey(lv))
            e.remove(bestMovesKey(lv))
            e.remove(recordDateKey(lv))
        }
        e.remove("current_level")
        e.apply()
    }

    // Заменяем старую версию: теперь с elapsedMillis и рекордами
    private fun showLevelCompletedDialog(round: Int, moves: Int, elapsedMillis: Long) {
        levelDialog?.dismiss()
        val ctx = ContextThemeWrapper(this, R.style.LevelCompleteDialogTheme)
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_level_completed, null, false)

        val message = view.findViewById<TextView>(R.id.textMessage)
        val statTime = view.findViewById<TextView>(R.id.textStatTime)
        val statMoves = view.findViewById<TextView>(R.id.textStatMoves)
        val bestTimeView = view.findViewById<TextView>(R.id.textBestTime)
        val bestMovesView = view.findViewById<TextView>(R.id.textBestMoves)
        val btnRepeat = view.findViewById<ImageButton>(R.id.buttonRepeat)
        val btnNext = view.findViewById<ImageButton>(R.id.buttonNext)
        val nativeContainer = view.findViewById<FrameLayout>(R.id.nativeAdContainer)

        val prevBestTime = getBestTime(round)
        val prevBestMoves = getBestMoves(round)
        var newTimeRecord = false
        var newMovesRecord = false
        if (elapsedMillis < prevBestTime) { saveBestTime(round, elapsedMillis); newTimeRecord = true }
        if (moves < prevBestMoves) { saveBestMoves(round, moves); newMovesRecord = true }
        // Если установлен хотя бы один новый рекорд – сохраняем дату
        if (newTimeRecord || newMovesRecord) saveRecordDate(round, System.currentTimeMillis())

        val currentBestTime = getBestTime(round)
        val currentBestMoves = getBestMoves(round)

        // Сообщение
        message.text = when {
            newTimeRecord && newMovesRecord -> getString(R.string.record_both_congrats)
            newTimeRecord -> getString(R.string.record_time_congrats)
            newMovesRecord -> getString(R.string.record_moves_congrats)
            else -> getString(R.string.level_completed_message)
        }

        // Текущие значения
        statTime.text = formatElapsed(elapsedMillis)
        statMoves.text = moves.toString()

        // Best значения
        if (currentBestTime != Long.MAX_VALUE) {
            bestTimeView.text = "Best: ${formatElapsed(currentBestTime)}"
            bestTimeView.visibility = View.VISIBLE
            if (newTimeRecord) bestTimeView.setTextColor(android.graphics.Color.parseColor("#2E7D32")) else bestTimeView.setTextColor(android.graphics.Color.parseColor("#1976D2"))
        } else bestTimeView.visibility = View.GONE

        if (currentBestMoves != Int.MAX_VALUE) {
            bestMovesView.text = "Best: $currentBestMoves"
            bestMovesView.visibility = View.VISIBLE
            if (newMovesRecord) bestMovesView.setTextColor(android.graphics.Color.parseColor("#2E7D32")) else bestMovesView.setTextColor(android.graphics.Color.parseColor("#1976D2"))
        } else bestMovesView.visibility = View.GONE

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .setCancelable(false)
            .create()
        levelDialog = dialog

        btnRepeat.setOnClickListener {
            dialog.dismiss(); sorterGameView.jumpToLevel(sorterGameView.currentRound)
        }
        btnNext.setOnClickListener {
            dialog.dismiss()
            // Логика показа межстраничной рекламы
            if (round in interstitialTargetRounds) {
                val manager = interstitialManager
                if (manager != null) {
                    // Настраиваем коллбек, чтобы перейти к следующему раунду после закрытия
                    manager.onDismissCallback = {
                        // Сбрасываем флаг и идём к следующему уровню
                        pendingShowInterstitialAfterDialog = false
                        sorterGameView.nextRound()
                    }
                    manager.onShowCallback = {
                        android.util.Log.i("Ads", "Interstitial start for round $round")
                    }
                    val shown = manager.showIfReady(this)
                    if (shown) {
                        pendingShowInterstitialAfterDialog = true
                      } else {
                        // Не готово — просто продолжаем
                        sorterGameView.nextRound()
                        manager.preloadIfNeeded()
                      }
                } else {
                  sorterGameView.nextRound()
                }
            } else {
              sorterGameView.nextRound()
            }
        }

        // Рекламный контейнер: если реклама отключена — скрываем и не грузим
        if (BillingManager.isAdsRemoved()) {
            nativeContainer.visibility = View.GONE
        } else {
            // Сначала пробуем показать прелоад, чтобы не делать запросы в момент показа
            nativeContainer?.let { container ->
                container.visibility = View.GONE
                when {
                    preloadedNativeAd != null -> {
                        val ad = preloadedNativeAd!!
                        preloadedNativeAd = null
                        val adView = layoutInflater.inflate(R.layout.ad_native_level_completed, null) as com.google.android.gms.ads.nativead.NativeAdView
                        bindNativeAdToView(ad, adView)
                        container.removeAllViews(); container.addView(adView)
                        container.visibility = View.VISIBLE
                        android.util.Log.i("Ads", "Shown preloaded Native in dialog")
                        // Сразу запускаем следующий прелоад
                        preloadNativeAd()
                    }
                    preloadedDialogBanner != null && preloadedBannerLoaded -> {
                        val banner = preloadedDialogBanner!!
                        preloadedDialogBanner = null
                        preloadedBannerLoaded = false
                        if (banner.parent != null) (banner.parent as? ViewGroup)?.removeView(banner)
                        container.removeAllViews(); container.addView(banner)
                        container.visibility = View.VISIBLE
                        android.util.Log.i("Ads", "Shown preloaded dialog banner")
                        // Запускаем следующий прелоад
                        preloadDialogBanner()
                    }
                    else -> {
                        // Если прелоада нет — используем текущую цепочку загрузки
                        loadNativeAdIntoContainer(container)
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            // Освобождаем ресурсы нативной рекламы при закрытии диалога
            try { nativeAd?.destroy() } catch (_: Exception) {}
            nativeAd = null
            nativeAdLoadAttempts = 0
            // Освобождаем баннер-фолбэк
            dialogBannerAdView?.let { v ->
                try { v.destroy() } catch (_: Exception) {}
            }
            dialogBannerAdView = null
            dialogBannerFixedTried = false
            // Возобновляем нижний баннер
            try { bannerAdView?.resume() } catch (_: Exception) {}
            // На всякий случай, если ничего не показали — запустим прелоад для следующего раза
            if (!isPreloadingNative && preloadedNativeAd == null) preloadNativeAd()
            if (!isPreloadingBanner && (preloadedDialogBanner == null || !preloadedBannerLoaded)) preloadDialogBanner()
        }

        dialog.show()
        // Делаем диалог широким и приостанавливаем нижний баннер на время показа
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        try { bannerAdView?.pause() } catch (_: Exception) {}
    }

    private fun restoreProgressIfAny() {
        val saved = prefs.getInt("current_level", 1)
        if (saved in 2..SorterGameView.MAX_LEVEL) {
            sorterGameView.jumpToLevel(saved)
        }
    }

    private fun saveLevel(lv: Int) {
        prefs.edit().putInt("current_level", lv.coerceIn(1, SorterGameView.MAX_LEVEL)).apply()
    }

    private fun setupAdminGesture() {
        // Клик по номеру уровня в админ-режиме -> переход по номеру
        textLevel.setOnClickListener {
            if (isAdminMode) showLevelJumpDialog()
        }

        textLevel.setOnTouchListener { _, event ->
            if (isAdminMode) return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isLevelPressing = true
                    levelLongPressRunnable = Runnable {
                        if (isLevelPressing && !isAdminMode) {
                            showAdminCodeDialog()
                        }
                    }
                    levelPressHandler.postDelayed(levelLongPressRunnable!!, longPressThresholdMs)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isLevelPressing = false
                    levelLongPressRunnable?.let { levelPressHandler.removeCallbacks(it) }
                }
            }
            true
        }
    }

    private fun showAdminCodeDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter admin code"
        }
        AlertDialog.Builder(this)
            .setTitle("Administrator Access")
            .setMessage("Hold 10s detected. Enter code:")
            .setView(input)
            .setPositiveButton("OK") { d, _ ->
                val code = input.text.toString().trim()
                if (code == "ROOT") {
                    isAdminMode = true
                    CubeSorterApplication.isAdminMode = true
                    Toast.makeText(this, "Admin mode enabled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Wrong code", Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showLevelJumpDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            hint = "Level (1-40)"
        }
        AlertDialog.Builder(this)
            .setTitle("Jump to Level")
            .setMessage("Enter level number 1..40")
            .setView(input)
            .setPositiveButton("Go") { d, _ ->
                val text = input.text.toString().trim()
                val num = text.toIntOrNull()
                if (num != null && num in 1..SorterGameView.MAX_LEVEL) {
                    sorterGameView.jumpToLevel(num)
                    Toast.makeText(this, "Jumped to level $num", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid level", Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun startCongratulations() {
        startActivity(Intent(this, CongratulationsActivity::class.java))
    }

    private fun startNewGame() {
        textLevel.text = "1"
        textMoves.text = "0"
        textTimer.text = getString(R.string.time_zero_tenth)
        resetTimer()
        extraLifeUsedThisGameOver = false
    }

    private fun startTimer() {
        startTime = System.currentTimeMillis()
        isTimerRunning = true
        handler.post(timerRunnable)
    }

    private fun pauseTimer() {
        isTimerRunning = false
        handler.removeCallbacks(timerRunnable)
    }

    private fun resetTimer() {
        isTimerRunning = false
        handler.removeCallbacks(timerRunnable)
        startTime = 0
        deadlineMs = 0
        timeUpHandled = false
        textTimer.text = getString(R.string.time_zero_tenth)
    }

    private fun updateTimer() {
        val now = System.currentTimeMillis()
        if (deadlineMs > 0L) {
            val remaining = deadlineMs - now
            if (remaining <= 0L) {
                textTimer.text = "00:00.0"
                if (!timeUpHandled) onTimeUp()
                return
            }
            val minutes = (remaining / 60000).toInt()
            val seconds = ((remaining % 60000) / 1000).toInt()
            val tenths = ((remaining % 1000) / 100).toInt()
            textTimer.text = String.format(Locale.getDefault(), "%02d:%02d.%d", minutes, seconds, tenths)
        } else if (startTime > 0L) {
            val elapsedTime = now - startTime
            val minutes = (elapsedTime / 60000).toInt()
            val seconds = ((elapsedTime % 60000) / 1000).toInt()
            val tenths = ((elapsedTime % 1000) / 100).toInt()
            textTimer.text = String.format(Locale.getDefault(), "%02d:%02d.%d", minutes, seconds, tenths)
        }
    }

    private fun onTimeUp() {
        timeUpHandled = true
        pauseTimer()
        // Ранее сразу списывали жизнь. Теперь показываем диалог выбора.
        showTimeUpDialog()
    }

    private fun showTimeUpDialog() {
        rewardUsedThisTimeUp = false
        pendingRewardEarned = false
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_time_up, null, false)
        val btnAdd = view.findViewById<android.widget.Button>(R.id.buttonAddTime)
        val btnTry = view.findViewById<android.widget.Button>(R.id.buttonTryAgain)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.buttonCancel)
        val status = view.findViewById<TextView>(R.id.textRewardStatus)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        timeUpDialog = dialog
        timeUpAddButton = btnAdd
        timeUpRewardStatus = status

        fun updateAddButtonState() { updateTimeUpRewardUI() }

        btnAdd.setOnClickListener {
            val mgr = rewardedManager
            if (mgr != null && mgr.isReady() && !rewardUsedThisTimeUp && !rewardUsedThisRound) {
                btnAdd.isEnabled = false
                status.text = getString(R.string.reward_loading)
                val shown = mgr.showIfReady(this) {
                    // Ставим флаг, но время добавим после закрытия (onAdDismiss)
                    pendingRewardEarned = true
                }
                if (!shown) {
                    status.text = getString(R.string.reward_unavailable)
                    updateAddButtonState()
                }
            } else {
                updateAddButtonState()
            }
        }

        btnTry.setOnClickListener {
            livesCurrent = (livesCurrent - 1).coerceAtLeast(0)
            prefs.edit().putInt(KEY_LIVES_CURRENT, livesCurrent).apply()
            updateLivesUI()
            dialog.dismiss()
            timeUpDialog = null; timeUpAddButton = null; timeUpRewardStatus = null
            if (livesCurrent > 0) {
                sorterGameView.jumpToLevel(sorterGameView.currentRound)
            } else {
                // Если все жизни потрачены — предлагаем rewarded extra life, если ещё не использована
                if (!extraLifeUsedThisGameOver) {
                    showExtraLifeGameOverDialog()
                } else {
                    showSimpleOutOfLivesDialog()
                }
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            timeUpDialog = null; timeUpAddButton = null; timeUpRewardStatus = null
        }

        dialog.setOnShowListener { updateAddButtonState() }
        dialog.setOnDismissListener { if (timeUpDialog === dialog) { timeUpDialog = null; timeUpAddButton = null; timeUpRewardStatus = null } }
        dialog.show()
    }

    private fun updateTimeUpRewardUI() {
        val btn = timeUpAddButton ?: return
        val status = timeUpRewardStatus ?: return
        val mgr = rewardedManager
        when {
            timeUpDialog == null || timeUpDialog?.isShowing != true -> return
            rewardUsedThisRound -> { btn.isEnabled = false; status.text = getString(R.string.reward_already_used) }
            rewardUsedThisTimeUp -> { btn.isEnabled = false; status.text = getString(R.string.reward_already_used) }
            mgr == null || (!mgr.isReady() && !mgr.isLoading()) -> { btn.isEnabled = false; status.text = getString(R.string.reward_unavailable) }
            mgr.isLoading() && !mgr.isReady() -> { btn.isEnabled = false; status.text = getString(R.string.reward_loading) }
            else -> { btn.isEnabled = true; status.text = getString(R.string.reward_add_time_hint) }
        }
    }

    private fun addBonusTimeSeconds(sec: Int) {
        val bonusMs = sec * 1000L
        deadlineMs = System.currentTimeMillis() + bonusMs
        timeUpHandled = false
        startTime = 0L
        if (!isTimerRunning) {
            isTimerRunning = true
            handler.post(timerRunnable)
        }
        updateTimer()
    }

    // ==== Жизни и сложность: утилиты ====
    private fun loadDifficultyAndLives() {
        val stored = prefs.getString(KEY_DIFFICULTY, Difficulty.NORM.name)
        difficulty = try { Difficulty.valueOf(stored ?: Difficulty.NORM.name) } catch (_: Exception) { Difficulty.NORM }
        livesCurrent = prefs.getInt(KEY_LIVES_CURRENT, livesMax).coerceIn(0, livesMax)
    }

    private fun updateLivesUI() {
        val hearts = buildString {
            repeat(livesCurrent) { append("❤️") }
            repeat(livesMax - livesCurrent) { append("🤍") }
        }
        if (::textLivesHeader.isInitialized) textLivesHeader.text = hearts
    }

    private fun updateDifficultyUI() {
        if (!::textDifficulty.isInitialized) return
        val label = when (difficulty) {
            Difficulty.EASY -> "\uD83E\uDD79 Easy"
            Difficulty.NORM -> "\uD83D\uDE0E Normal"
            Difficulty.HARD -> "\uD83D\uDE28 Hard"
            Difficulty.EXTREME -> "\uD83E\uDD75 Extreme"
        }
        textDifficulty.text = label
    }

    private fun difficultyFactor(): Double = when (difficulty) {
        Difficulty.EXTREME -> 1.5    // было 1.0, +50%
        Difficulty.HARD -> 2.1       // было 1.4, +50%
        Difficulty.NORM -> 2.7       // было 1.8, +50%
        Difficulty.EASY -> 3.45      // было 2.3, +50%
    }

    private fun computeTimeLimitMs(round: Int): Long {
        val step = (round - 1) / 5
        val cols = 4 + step
        val rows = 7 + step
        val k = (cols - 1).coerceAtLeast(1) // целевые колонки
        val h = (rows - 1).coerceAtLeast(1) // высота целевой колонки
        val mEst = 1.5 * (k - 1).coerceAtLeast(0) * h // оценка ходов
        val fSize = 1.0 + 0.05 * (cols - 4) + 0.03 * (rows - 7)
        val tMove = 0.6 * fSize // сек/ход
        val tSetup = 3.0 + 0.4 * k // сек
        val tHardSec = tSetup + mEst * tMove
        val tHardClamped = tHardSec.coerceIn(15.0, 600.0)
        val tByDiff = tHardClamped * difficultyFactor()
        val eased = (tByDiff * 1.15).coerceIn(15.0, 600.0) // +15% ко всем сложностям
        return (eased * 1000).toLong()
    }

    // Инициализация UMP + Mobile Ads (покажет форму согласия при необходимости)
    private fun requestConsentThenInitAds() {
        if (BillingManager.isAdsRemoved()) return
        val params = ConsentRequestParameters.Builder().build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        android.util.Log.w("UMP", "Consent form error: ${formError.errorCode} ${formError.message}")
                    }
                    initMobileAdsIfNeeded()
                }
            },
            { requestError ->
                android.util.Log.w("UMP", "Consent info update failed: ${requestError.errorCode} ${requestError.message}")
                initMobileAdsIfNeeded()
            }
        )
    }

    private fun initMobileAdsIfNeeded() {
        if (BillingManager.isAdsRemoved()) return
        if (adsInitialized) return
        adsInitialized = true
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            val testDeviceIds = listOf(AdRequest.DEVICE_ID_EMULATOR)
            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)
        }
        MobileAds.initialize(this) { status ->
            android.util.Log.d("Ads", "MobileAds initialized: $status")
            interstitialManager = InterstitialAdManager(this, getString(R.string.admob_interstitial_rounds))
            interstitialManager?.preloadIfNeeded()
            rewardedManager = RewardedAdManager(this, getString(R.string.admob_reward_time_bonus), useRewardedInterstitial = true).apply {
                onAdLoaded = { runOnUiThread { updateTimeUpRewardUI() } }
                onAdDismiss = {
                    // Реклама закрыта: если пользователь заработал награду, применяем бонус сейчас
                    if (pendingRewardEarned) {
                        pendingRewardEarned = false
                        rewardUsedThisTimeUp = true
                        rewardUsedThisRound = true
                        addBonusTimeSeconds(10)
                        runOnUiThread {
                            Toast.makeText(this@SorterActivity, getString(R.string.reward_granted), Toast.LENGTH_SHORT).show()
                            timeUpDialog?.dismiss()
                        }
                    } else {
                        // Обновим UI в случае отмены
                        runOnUiThread { updateTimeUpRewardUI() }
                    }
                }
            }
            rewardedManager?.preloadIfNeeded()
            rewardedExtraLifeManager = RewardedAdManager(this, getString(R.string.admob_reward_extra_life), useRewardedInterstitial = false).apply {
                onAdLoaded = { runOnUiThread { updateExtraLifeUI() } }
                onAdDismiss = { runOnUiThread { updateExtraLifeUI() } }
            }
            rewardedExtraLifeManager?.preloadIfNeeded()
            loadBannerAdIfPresent()
            if (!isPreloadingNative && preloadedNativeAd == null) preloadNativeAd()
            if (!isPreloadingBanner && (preloadedDialogBanner == null || !preloadedBannerLoaded)) preloadDialogBanner()
        }
    }

    private fun preloadNativeAd() {
        isPreloadingNative = true
        val adUnitId = getString(R.string.admob_native_between_rounds)
        val adLoader = com.google.android.gms.ads.AdLoader.Builder(this, adUnitId)
            .forNativeAd { ad ->
                preloadedNativeAd = ad
                isPreloadingNative = false
                android.util.Log.i("Ads", "Preloaded Native ad for dialog")
            }
            .withAdListener(object: AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isPreloadingNative = false
                    android.util.Log.w("Ads", "Preload Native failed: ${error.code} ${error.message}")
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun preloadDialogBanner() {
        isPreloadingBanner = true
        preloadedBannerLoaded = false
        val adView = AdView(this)
        adView.adUnitId = getString(R.string.admob_banner_dialog_fallback)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                preloadedDialogBanner = adView
                preloadedBannerLoaded = true
                isPreloadingBanner = false
                android.util.Log.i("Ads", "Preloaded dialog banner (adaptive)")
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                isPreloadingBanner = false
                preloadedDialogBanner = null
                preloadedBannerLoaded = false
                android.util.Log.w("Ads", "Preload dialog banner failed: ${adError.code} ${adError.message}")
            }
        }
        // Размер вычисляем от ширины экрана (диалог на MATCH_PARENT)
        val dm = resources.displayMetrics
        val adWidthDp = (dm.widthPixels / dm.density).toInt().coerceAtLeast(1)
        val adaptive = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidthDp)
        adView.setAdSize(adaptive)
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun loadBannerAdIfPresent() {
        if (BillingManager.isAdsRemoved()) {
            findViewById<FrameLayout?>(R.id.adContainer)?.visibility = View.GONE
            return
        }
        val container = findViewById<FrameLayout?>(R.id.adContainer) ?: return
        bannerFallbackTried = false
        loadBannerIntoContainer(container, useAdaptive = true)
    }

    private fun loadBannerIntoContainer(container: FrameLayout, useAdaptive: Boolean, internalFallback: Boolean = false) {
        // Убрали throttle: он мешал немедленному фолбэку (adaptive -> fixed) т.к. повторный вызов происходил <1s
        lastBannerLoadStart = System.currentTimeMillis()

        // Очистим предыдущий баннер, если был
        bannerAdView?.let { old ->
            try { container.removeView(old) } catch (_: Exception) {}
            try { old.destroy() } catch (_: Exception) {}
        }
        bannerAdView = null

        val adView = AdView(this)
        adView.adUnitId = getString(R.string.admob_banner_home)
        // Дополнительный лог
        android.util.Log.i("Ads", "Banner init (useAdaptive=$useAdaptive) unit=${adView.adUnitId}")
        adView.visibility = View.GONE
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                mainBannerHadFill = true
                mainBannerRetryAttempts = 0
                container.visibility = View.VISIBLE
                adView.visibility = View.VISIBLE
                android.util.Log.i("Ads", "Banner loaded (useAdaptive=$useAdaptive) size=${adView.adSize?.width}x${adView.adSize?.height}")
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                android.util.Log.e("Ads", "Banner failed (useAdaptive=$useAdaptive): code=${adError.code} msg=${adError.message}")
                // Если баннер уже показывался — не скрываем контейнер
                if (mainBannerHadFill) return
                adView.visibility = View.GONE
                container.visibility = View.GONE
                // Универсальный фолбэк: первая неудачная попытка адаптивного -> сразу пробуем фиксированный, независимо от кода
                if (useAdaptive && !bannerFallbackTried) {
                    bannerFallbackTried = true
                    android.util.Log.w("Ads", "Adaptive failed (code=${adError.code}), try fixed once ...")
                    loadBannerIntoContainer(container, useAdaptive = false, internalFallback = true)
                    return
                }
                // План ретраев (15 сек * 3) затем keep-alive раз в 60 сек
                if (mainBannerRetryAttempts < maxMainBannerRetries) {
                    mainBannerRetryAttempts++
                    val delay = 15_000L
                    android.util.Log.w("Ads", "Bottom banner retry ${mainBannerRetryAttempts}/$maxMainBannerRetries in ${delay}ms")
                    handler.postDelayed({
                        if (!isFinishing && !isDestroyed && !mainBannerHadFill) {
                            bannerFallbackTried = false
                            loadBannerIntoContainer(container, useAdaptive = true)
                        }
                    }, delay)
                } else {
                    if (!forcingBannerReload) {
                        forcingBannerReload = true
                        android.util.Log.w("Ads", "Schedule keep-alive banner reload every 60s")
                        handler.postDelayed(object: Runnable {
                            override fun run() {
                                if (isFinishing || isDestroyed || mainBannerHadFill) { forcingBannerReload = false; return }
                                android.util.Log.w("Ads", "Keep-alive banner attempt ...")
                                bannerFallbackTried = false
                                loadBannerIntoContainer(container, useAdaptive = true)
                                handler.postDelayed(this, 60_000)
                            }
                        }, 60_000)
                    }
                }
            }
        }

        // Добавляем во вьюиерархию заранее (ещё скрытым), чтобы получить ширину
        if (adView.parent == null) {
            container.removeAllViews()
            container.addView(adView)
        }

        // После раскладки контейнера назначаем адаптивный размер и загружаем
        container.post {
            val request = AdRequest.Builder().build()
            if (useAdaptive) {
                val dm = resources.displayMetrics
                val widthPx = if (container.width > 0) container.width else dm.widthPixels
                val adWidthDp = (widthPx / dm.density).toInt().coerceAtLeast(1)
                val adaptive = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidthDp)
                adView.setAdSize(adaptive)
                android.util.Log.i(
                    "Ads",
                    "Loading banner (adaptive) unit=${adView.adUnitId} size=${adaptive.width}x${adaptive.height} (dp)"
                )
            } else {
                adView.setAdSize(AdSize.BANNER)
                android.util.Log.i(
                    "Ads",
                    "Loading banner (fixed) unit=${adView.adUnitId} size=${AdSize.BANNER.width}x${AdSize.BANNER.height} (dp)"
                )
            }
            adView.loadAd(request)
        }

        // Держим ссылку для pause/resume/destroy
        bannerAdView = adView
    }

    private fun loadNativeAdIntoContainer(container: FrameLayout) {
        // Если ранее был загружен натив — уничтожим
        nativeAd?.let { old ->
            try { old.destroy() } catch (_: Exception) {}
        }
        nativeAd = null
        container.removeAllViews()
        container.visibility = View.GONE

        val adUnitId = getString(R.string.admob_native_between_rounds)
        val adLoader = com.google.android.gms.ads.AdLoader.Builder(this, adUnitId)
            .forNativeAd { ad: com.google.android.gms.ads.nativead.NativeAd ->
                // Успешная загрузка
                nativeAd = ad
                nativeAdLoadAttempts = 0
                android.util.Log.i("Ads", "Native ad loaded for dialog")
                val adView = layoutInflater.inflate(R.layout.ad_native_level_completed, null) as com.google.android.gms.ads.nativead.NativeAdView
                bindNativeAdToView(ad, adView)
                container.removeAllViews()
                container.addView(adView)
                container.visibility = View.VISIBLE
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    android.util.Log.e("Ads", "Native ad failed to load: ${adError.code} ${adError.message}")
                    // Ретрай на No fill (код 3) — до 2 попыток
                    if (adError.code == 3 && nativeAdLoadAttempts < maxNativeAdRetries && (levelDialog?.isShowing == true)) {
                        nativeAdLoadAttempts++
                        android.util.Log.w("Ads", "Native no fill. Retry ${nativeAdLoadAttempts}/$maxNativeAdRetries in 1500ms")
                        container.postDelayed({
                            if (levelDialog?.isShowing == true) {
                                loadNativeAdIntoContainer(container)
                            }
                        }, 1500)
                    } else {
                        // Фолбэк: адаптивный баннер
                        loadDialogBannerFallback(container)
                    }
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun loadDialogBannerFallback(container: FrameLayout) {
        // Убираем возможные старые вью
        container.removeAllViews()
        container.visibility = View.GONE
        // Освобождаем старый баннер, если есть
        dialogBannerAdView?.let { old ->
            try { old.destroy() } catch (_: Exception) {}
        }
        dialogBannerAdView = null
        dialogBannerFixedTried = false
        dialogBannerHadFill = false

        val adView = AdView(this)
        adView.adUnitId = getString(R.string.admob_banner_dialog_fallback)
        adView.visibility = View.GONE
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                dialogBannerHadFill = true
                if (levelDialog?.isShowing == true) {
                    container.visibility = View.VISIBLE
                    adView.visibility = View.VISIBLE
                    android.util.Log.i("Ads", "Dialog banner loaded (fallback)")
                }
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                android.util.Log.e("Ads", "Dialog banner failed: ${adError.code} ${adError.message}")
                if (dialogBannerHadFill) return // не скрываем, если уже был показ
                // Попробуем фиксированный баннер один раз при No fill
                if (adError.code == 3 && !dialogBannerFixedTried && levelDialog?.isShowing == true) {
                    dialogBannerFixedTried = true
                    android.util.Log.w("Ads", "Dialog banner adaptive no fill. Try fixed BANNER once...")
                    loadDialogBannerFallbackFixed(container)
                } else {
                    adView.visibility = View.GONE
                    container.visibility = View.GONE
                }
            }
        }

        // Добавляем в контейнер заранее
        if (adView.parent == null) {
            container.addView(adView)
        }
        // Настраиваем адаптивный размер
        container.post {
            val dm = resources.displayMetrics
            val widthPx = if (container.width > 0) container.width else dm.widthPixels
            val adWidthDp = (widthPx / dm.density).toInt().coerceAtLeast(1)
            val adaptive = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidthDp)
            adView.setAdSize(adaptive)
            android.util.Log.i("Ads", "Loading dialog banner (adaptive) unit=${adView.adUnitId} size=${adaptive.width}x${adaptive.height} (dp)")
            adView.loadAd(AdRequest.Builder().build())
        }

        dialogBannerAdView = adView
    }

    private fun loadDialogBannerFallbackFixed(container: FrameLayout) {
        // Уничтожаем предыдущий, если был
        dialogBannerAdView?.let { old ->
            try { old.destroy() } catch (_: Exception) {}
        }
        dialogBannerAdView = null
        container.removeAllViews()
        container.visibility = View.GONE

        val adView = AdView(this)
        adView.adUnitId = getString(R.string.admob_banner_test_fixed)
        adView.visibility = View.GONE
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                dialogBannerHadFill = true
                if (levelDialog?.isShowing == true) {
                    container.visibility = View.VISIBLE
                    adView.visibility = View.VISIBLE
                    android.util.Log.i("Ads", "Dialog banner loaded (fixed fallback)")
                }
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                android.util.Log.e("Ads", "Dialog fixed banner failed: ${adError.code} ${adError.message}")
                if (dialogBannerHadFill) return // не скрываем, если уже был показ
                adView.visibility = View.GONE
                container.visibility = View.GONE
            }
        }

        if (adView.parent == null) {
            container.addView(adView)
        }
        // Фиксированный размер
        container.post {
            adView.setAdSize(AdSize.BANNER)
            android.util.Log.i("Ads", "Loading dialog banner (fixed) unit=${adView.adUnitId} size=${AdSize.BANNER.width}x${AdSize.BANNER.height} (dp)")
            adView.loadAd(AdRequest.Builder().build())
        }

        dialogBannerAdView = adView
    }

    private fun bindNativeAdToView(ad: com.google.android.gms.ads.nativead.NativeAd, adView: com.google.android.gms.ads.nativead.NativeAdView) {
        // Находим сабвью
        val mediaView = adView.findViewById<com.google.android.gms.ads.nativead.MediaView>(R.id.ad_media)
        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body = adView.findViewById<TextView>(R.id.ad_body)
        val cta = adView.findViewById<android.widget.Button>(R.id.ad_call_to_action)
        val icon = adView.findViewById<android.widget.ImageView>(R.id.ad_app_icon)
        val advertiser = adView.findViewById<TextView>(R.id.ad_advertiser)

        // Присваиваем представления NativeAdView
        adView.mediaView = mediaView
        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = cta
        adView.iconView = icon
        adView.advertiserView = advertiser

        // Заполняем контент
        headline.text = ad.headline

        val bodyText = ad.body
        if (bodyText.isNullOrEmpty()) {
            body.visibility = View.GONE
        } else {
            body.visibility = View.VISIBLE
            body.text = bodyText
        }

        val ctaText = ad.callToAction
        if (ctaText.isNullOrEmpty()) {
            cta.visibility = View.GONE
        } else {
            cta.visibility = View.VISIBLE
            cta.text = ctaText
        }

        val iconDrawable = ad.icon?.drawable
        if (iconDrawable == null) {
            icon.visibility = View.GONE
        } else {
            icon.visibility = View.VISIBLE
            icon.setImageDrawable(iconDrawable)
        }

        val advText = ad.advertiser
        if (advText.isNullOrEmpty()) {
            advertiser.visibility = View.GONE
        } else {
            advertiser.visibility = View.VISIBLE
            advertiser.text = advText
        }

        // Назначаем объявление
        adView.setNativeAd(ad)
        // Необязательно: управление медиа-контролами
        mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
    }

    private fun showSimpleOutOfLivesDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.out_of_lives_title))
            .setMessage(getString(R.string.out_of_lives_message))
            .setPositiveButton(getString(R.string.start_over)) { d, _ ->
                d.dismiss()
                livesCurrent = livesMax
                extraLifeUsedThisGameOver = false
                prefs.edit().putInt(KEY_LIVES_CURRENT, livesCurrent).apply()
                updateLivesUI()
                saveLevel(1)
                sorterGameView.resetAll()
            }
            .setCancelable(false)
            .show()
    }

    private fun showExtraLifeGameOverDialog() {
        if (BillingManager.isAdsRemoved()) { showSimpleOutOfLivesDialog(); return }
        extraLifeDialog?.dismiss()
        // Инициируем прелоад (если вдруг ещё не загрузилось или предыдущие попытки исчерпаны)
        rewardedExtraLifeManager?.preloadIfNeeded()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48,48,48,32)
        }
        val title = TextView(this).apply {
            text = getString(R.string.reward_extra_life_title)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 19f
            setTextColor(Color.BLACK)
        }
        val msg = TextView(this).apply {
            text = getString(R.string.reward_extra_life_message)
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        val status = TextView(this).apply {
            text = getString(R.string.reward_extra_life_loading)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0,16,0,0)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0,24,0,0)
        }
        val btnGet = android.widget.Button(this).apply { text = getString(R.string.reward_extra_life_button) }
        val btnStartOver = android.widget.Button(this).apply { text = getString(R.string.reward_extra_life_start_over); setPadding(32,0,0,0) }
        buttons.addView(btnGet)
        buttons.addView(btnStartOver)
        root.addView(title)
        root.addView(msg)
        root.addView(buttons)
        root.addView(status)

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(false)
            .create()
        extraLifeDialog = dialog
        extraLifeButton = btnGet
        extraLifeStatus = status

        fun update() = updateExtraLifeUI()

        btnGet.setOnClickListener {
            val mgr = rewardedExtraLifeManager
            if (mgr != null && mgr.isReady() && !extraLifeUsedThisGameOver) {
                btnGet.isEnabled = false
                status.text = getString(R.string.reward_extra_life_loading)
                val shown = mgr.showIfReady(this) {
                    handler.post { grantExtraLifeAndResume() }
                }
                if (!shown) {
                    status.text = getString(R.string.reward_extra_life_unavailable)
                    update()
                }
            } else update()
        }
        btnStartOver.setOnClickListener {
            dialog.dismiss()
            livesCurrent = livesMax
            extraLifeUsedThisGameOver = false
            prefs.edit().putInt(KEY_LIVES_CURRENT, livesCurrent).apply()
            updateLivesUI()
            saveLevel(1)
            sorterGameView.resetAll()
        }
        dialog.setOnShowListener { update() }
        dialog.setOnDismissListener {
            if (extraLifeDialog === dialog) {
                extraLifeDialog = null; extraLifeButton = null; extraLifeStatus = null
            }
        }
        dialog.show()
    }

    private fun grantExtraLifeAndResume() {
        if (extraLifeUsedThisGameOver) return
        extraLifeUsedThisGameOver = true
        livesCurrent = 1
        prefs.edit().putInt(KEY_LIVES_CURRENT, livesCurrent).apply()
        updateLivesUI()
        runOnUiThread {
            Toast.makeText(this, getString(R.string.reward_extra_life_granted), Toast.LENGTH_SHORT).show()
            extraLifeDialog?.dismiss()
            sorterGameView.jumpToLevel(sorterGameView.currentRound)
        }
    }

    private fun updateExtraLifeUI() {
        val dlg = extraLifeDialog ?: return
        if (!dlg.isShowing) return
        val btn = extraLifeButton ?: return
        val status = extraLifeStatus ?: return
        val mgr = rewardedExtraLifeManager
        when {
            extraLifeUsedThisGameOver -> { btn.isEnabled = false; status.text = getString(R.string.reward_extra_life_already_used) }
            mgr == null -> { btn.isEnabled = false; status.text = getString(R.string.reward_extra_life_unavailable) }
            mgr.isLoading() && !mgr.isReady() -> { btn.isEnabled = false; status.text = getString(R.string.reward_extra_life_loading) }
            !mgr.isReady() -> { btn.isEnabled = false; status.text = getString(R.string.reward_extra_life_unavailable) }
            else -> { btn.isEnabled = true; status.text = getString(R.string.reward_extra_life_hint) }
        }
    }
}
