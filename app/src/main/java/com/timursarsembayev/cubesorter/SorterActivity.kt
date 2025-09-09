package com.timursarsembayev.cubesorter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
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

    // Диалог завершения уровня
    private var levelDialog: AlertDialog? = null

    // Хранение прогресса
    private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

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
        restoreProgressIfAny()
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
            saveLevel(round)
        }

        sorterGameView.onRoundCompleted = { round, moves ->
            pauseTimer()
            showLevelCompletedDialog(round, moves)
        }

        sorterGameView.onAllCompleted = {
            // Все уровни завершены
            resetTimer()
            saveLevel(SorterGameView.MAX_LEVEL) // сохраняем финальный уровень
            startCongratulations()
        }
    }

    private fun showLevelCompletedDialog(round: Int, moves: Int) {
        levelDialog?.dismiss()
        val inflaterContext = ContextThemeWrapper(this, R.style.LevelCompleteDialogTheme)
        val view = LayoutInflater.from(inflaterContext).inflate(R.layout.dialog_level_completed, null, false)
        val title = view.findViewById<TextView>(R.id.textTitle)
        val message = view.findViewById<TextView>(R.id.textMessage)
        val statTime = view.findViewById<TextView>(R.id.textStatTime)
        val statMoves = view.findViewById<TextView>(R.id.textStatMoves)
        val btnRepeat = view.findViewById<ImageButton>(R.id.buttonRepeat)
        val btnNext = view.findViewById<ImageButton>(R.id.buttonNext)

        title.text = getString(R.string.level_completed_congrats)
        val timeStr = textTimer.text.toString()
        message.text = getString(R.string.level_completed_message)
        statTime.text = timeStr
        statMoves.text = moves.toString()

        val dialog = AlertDialog.Builder(inflaterContext)
            .setView(view)
            .setCancelable(false)
            .create()
        levelDialog = dialog

        btnRepeat.setOnClickListener {
            dialog.dismiss()
            sorterGameView.jumpToLevel(sorterGameView.currentRound)
        }

        btnNext.setOnClickListener {
            dialog.dismiss()
            sorterGameView.nextRound()
        }

        dialog.show()
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

    private fun pauseTimer() {
        isTimerRunning = false
        handler.removeCallbacks(timerRunnable)
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
        levelDialog?.dismiss()
    }
}