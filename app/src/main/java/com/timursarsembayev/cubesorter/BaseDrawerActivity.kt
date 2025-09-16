package com.timursarsembayev.cubesorter

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

open class BaseDrawerActivity : AppCompatActivity() {

    protected lateinit var drawerLayout: DrawerLayout
    private var backCallback: OnBackPressedCallback? = null

    protected fun setContentWithDrawer(@LayoutRes contentLayoutRes: Int) {
        setContentView(R.layout.activity_with_drawer)
        val container: FrameLayout = findViewById(R.id.contentContainer)
        layoutInflater.inflate(contentLayoutRes, container, true)
        setupDrawerInternal()
        // Регистрация обработчика системной кнопки/жеста Назад
        backCallback?.remove()
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (this@BaseDrawerActivity::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Передаём дальше по цепочке (снимаем се��я, чтобы не зациклит��ся)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)
    }

    private fun setupDrawerInternal() {
        drawerLayout = findViewById(R.id.drawerLayout)
        // Кнопка открытия, если присутствует в контенте
        findViewById<ImageButton?>(R.id.buttonOpenDrawer)?.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        // Пункты меню
        findViewById<TextView?>(R.id.menuStartGame)?.setOnClickListener {
            // Переход на главный экран (игра)
            val intent = Intent(this, SorterActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<TextView?>(R.id.menuDifficulty)?.setOnClickListener {
            if (this !is DifficultyActivity) {
                startActivity(Intent(this, DifficultyActivity::class.java))
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<TextView?>(R.id.menuRecords)?.setOnClickListener {
            if (this !is RecordsActivity) {
                startActivity(Intent(this, RecordsActivity::class.java))
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<TextView?>(R.id.menuReset)?.setOnClickListener {
            if (this !is ResetActivity) {
                startActivity(Intent(this, ResetActivity::class.java))
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
}
