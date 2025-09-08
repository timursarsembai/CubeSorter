package com.timursarsembayev.cubesorter

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.util.Locale

class SorterActivity : Activity() {

    private lateinit var textLevel: TextView
    private lateinit var textTimer: TextView
    private lateinit var textMoves: TextView
    private lateinit var sorterGameView: SorterGameView

    private var startTime: Long = 0
    private var isTimerRunning = false
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
            if (moves == 1 && !isTimerRunning) {
                startTimer()
            }
        }

        sorterGameView.onRoundChanged = { round, _ ->
            textLevel.text = round.toString()
            // Сброс таймера при переходе на новый уровень
            resetTimer()
        }

        sorterGameView.onRoundCompleted = { _, _ ->
            // Логика завершения раунда
        }
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
