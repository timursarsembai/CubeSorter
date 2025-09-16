package com.timursarsembayev.cubesorter

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.GravityCompat

class ResetActivity : BaseDrawerActivity() {

    private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithDrawer(R.layout.activity_reset)

        // Заголовок и кнопка открытия меню уже в layout; BaseDrawerActivity свяжет drawer
        findViewById<TextView>(R.id.textTitle).text = getString(R.string.reset_progress_title)
        findViewById<TextView>(R.id.textDescription).text = getString(R.string.reset_progress_message)

        findViewById<ImageButton>(R.id.buttonOpenDrawer).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<Button>(R.id.buttonResetCurrent).setOnClickListener {
            prefs.edit().putBoolean("pending_reset_current", true).apply()
            // Вернуться на экран игры, сохраняя стек чистым
            val intent = Intent(this, SorterActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        findViewById<Button>(R.id.buttonResetAll).setOnClickListener {
            prefs.edit().putBoolean("pending_reset_all", true).apply()
            val intent = Intent(this, SorterActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }
}

