package com.timursarsembayev.cubesorter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SorterActivity : Activity() {

    private lateinit var textLevel: TextView
    private lateinit var textTimer: TextView
    private lateinit var textMoves: TextView
    private lateinit var sorterGameView: SorterGameView

    private var startTime: Long = 0
    private var isTimerRunning = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sorter)

        initializeViews()
        setupGameCallbacks()
        setupAdminGesture()
        startNewGame()
    }

    private fun initializeViews() {
        textLevel = findViewById(R.id.textLevel)
        textTimer = findViewById(R.id.textTimer)
        textMoves = findViewById(R.id.textMoves)
        sorterGameView = findViewById(R.id.sorterGameView)
    }

    private fun setupGameCallbacks() {
        sorterGameView.onMovesChanged = { moves ->
            textMoves.text = moves.toString()
            if (moves == 1 && !isTimerRunning) startTimer()
        }

        sorterGameView.onRoundChanged = { round, _ ->
            textLevel.text = round.toString()
            resetTimer()
        }

        sorterGameView.onRoundCompleted = { _, _ ->
            // Можно добавить локальную логику между уровнями
        }

        sorterGameView.onAllCompleted = {
            // Завершены все 40 уровней
            resetTimer()
            startCongratulations()
        }
    }

    private fun setupAdminGesture() {
        // Клик по номеру уровня в админ-режиме -> переход по номеру
        textLevel.setOnClickListener {
            if (isAdminMode) showLevelJumpDialog()
        }

        textLevel.setOnTouchListener { _, event ->
            // Если уже админ режим активен — не перехватываем, даём сработать OnClick
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
            // Возвращаем true только пока НЕ админ режим (чтобы не вызывать клик раньше времени)
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
    }

    private fun startTimer() {
        startTime = System.currentTimeMillis()
        isTimerRunning = true
        handler.post(timerRunnable)
    }

    private fun resetTimer() {
        isTimerRunning = false
        handler.removeCallbacks(timerRunnable)
        startTime = 0
        textTimer.text = getString(R.string.time_zero_tenth)
    }

    private fun updateTimer() {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - startTime

        val minutes = (elapsedTime / 60000).toInt()
        val seconds = ((elapsedTime % 60000) / 1000).toInt()
        val tenths = ((elapsedTime % 1000) / 100).toInt()

        textTimer.text = String.format(Locale.getDefault(), "%02d:%02d.%d", minutes, seconds, tenths)
    }

    override fun onDestroy() {
        super.onDestroy()
        resetTimer()
    }
}