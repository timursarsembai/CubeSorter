package com.timursarsembayev.cubesorter

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

class RemoveAdsActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var priceButton: Button
    private lateinit var restoreButton: Button
    private lateinit var benefitsText: TextView
    private var fallbackPosted = false

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var buttonOpenDrawer: ImageButton
    private lateinit var menuStartGame: TextView
    private lateinit var menuDifficulty: TextView
    private lateinit var menuRecords: TextView
    private lateinit var menuReset: TextView
    private lateinit var menuRemoveAds: TextView

    // Admin section
    private var adminSection: LinearLayout? = null
    private var adminSwitch: Switch? = null
    private var adminStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remove_ads)

        // Drawer + кнопка
        drawerLayout = findViewById(R.id.drawerLayout)
        buttonOpenDrawer = findViewById(R.id.buttonOpenDrawer)
        buttonOpenDrawer.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        // Пункты меню
        menuStartGame = findViewById(R.id.menuStartGame)
        menuDifficulty = findViewById(R.id.menuDifficulty)
        menuRecords = findViewById(R.id.menuRecords)
        menuReset = findViewById(R.id.menuReset)
        menuRemoveAds = findViewById(R.id.menuRemoveAds)
        attachMenuHandlers()

        statusText = findViewById(R.id.textStatus)
        priceButton = findViewById(R.id.buttonBuy)
        restoreButton = findViewById(R.id.buttonRestore)
        benefitsText = findViewById(R.id.textBenefits)

        benefitsText.text = buildString {
            append("• ").append(getString(R.string.remove_ads_benefit_1)).append('\n')
            append("• ").append(getString(R.string.remove_ads_benefit_2)).append('\n')
            append("• ").append(getString(R.string.remove_ads_benefit_3))
        }

        // Admin UI
        adminSection = findViewById(R.id.adminSection)
        adminSwitch = findViewById(R.id.switchAdminAds)
        adminStatus = findViewById(R.id.textAdminAdsStatus)

        // Инициализируем биллинг (безопасно вызывать повторно)
        BillingManager.init(applicationContext)

        BillingManager.onStatusChanged = { runOnUiThread { updateUI(); updateAdminUI() } }
        updateUI()
        setupAdminUIIfNeeded()

        priceButton.setOnClickListener {
            if (!BillingManager.isAdsRemoved()) {
                BillingManager.launchPurchase(this)
            }
        }
        restoreButton.setOnClickListener {
            BillingManager.init(applicationContext)
        }

        // Fallback: если через 5 секунд цена не подгрузилась, показать placeholder
        if (!fallbackPosted) {
            fallbackPosted = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    if (BillingManager.formattedPrice() == null && !BillingManager.isAdsRemoved()) {
                        // Подставим известную цену как текст (жестко $1.49) если товар не загрузился
                        if (priceButton.text == getString(R.string.remove_ads_loading)) {
                            priceButton.text = getString(R.string.remove_ads_buy, "$1.49")
                            priceButton.isEnabled = BillingManager.isBillingReady() // активируем если биллинг готов, даже если не пришли детали
                        }
                    }
                }
            }, 5000L)
        }
    }

    private fun setupAdminUIIfNeeded() {
        val section = adminSection ?: return
        val sw = adminSwitch
        val st = adminStatus
        if (CubeSorterApplication.isAdminMode) {
            section.visibility = android.view.View.VISIBLE
            // Инициализация состояния
            updateAdminUI()
            sw?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    BillingManager.setAdsOverride(BillingManager.OVERRIDE_FORCE_HIDE) // Отключить рекламу
                } else {
                    BillingManager.setAdsOverride(BillingManager.OVERRIDE_FORCE_SHOW) // Включить рекламу
                }
                updateAdminUI()
            }
        } else {
            section.visibility = android.view.View.GONE
            // На всякий случай сбросим override, если не в админ-режиме
            // BillingManager.clearAdsOverride() // не трогаем, чтобы сохранённое состояние осталось
        }
    }

    private fun updateAdminUI() {
        val section = adminSection ?: return
        if (section.visibility != android.view.View.VISIBLE) return
        val override = BillingManager.getAdsOverride()
        val removed = BillingManager.isAdsRemoved()
        // Обновим текст статуса
        adminStatus?.text = when (override) {
            BillingManager.OVERRIDE_FORCE_HIDE -> getString(R.string.admin_ads_status_force_hide)
            BillingManager.OVERRIDE_FORCE_SHOW -> getString(R.string.admin_ads_status_force_show)
            else -> getString(R.string.admin_ads_status_none)
        }
        // Логика свитча: включено = принудительно скрыть рекламу
        adminSwitch?.isChecked = when (override) {
            BillingManager.OVERRIDE_FORCE_HIDE -> true
            BillingManager.OVERRIDE_FORCE_SHOW -> false
            else -> removed // если override нет, выставим по факту (необязательно)
        }
    }

    private fun updateUI() {
        val removed = BillingManager.isAdsRemoved()
        val price = BillingManager.formattedPrice()
        statusText.text = if (removed) {
            getString(R.string.remove_ads_status_active)
        } else {
            getString(R.string.remove_ads_status_not)
        }
        val buttonText = if (removed) {
            getString(R.string.ads_removed_label)
        } else if (price != null) {
            getString(R.string.remove_ads_buy, price)
        } else {
            getString(R.string.remove_ads_loading)
        }
        priceButton.text = buttonText
        priceButton.isEnabled = !removed && (price != null || BillingManager.isBillingReady())
        restoreButton.isEnabled = !removed
    }

    private fun attachMenuHandlers() {
        menuStartGame.setOnClickListener {
            // Возврат к игре
            startActivity(Intent(this, SorterActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
            finish()
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
            // Уже здесь — просто закрыть
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
}
