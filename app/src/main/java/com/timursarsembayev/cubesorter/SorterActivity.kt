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
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SorterActivity : Activity() {

    private lateinit var textLevel: TextView
    private lateinit var textTimer: TextView
    private lateinit var textMoves: TextView
    private lateinit var sorterGameView: SorterGameView
    // Drawer + records
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recordsTable: TableLayout
    private lateinit var buttonOpenDrawer: ImageButton
    private lateinit var buttonCloseDrawer: ImageButton

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
        setupDrawer()
        startNewGame()
        restoreProgressIfAny()
    }

    private fun initializeViews() {
        textLevel = findViewById(R.id.textLevel)
        textTimer = findViewById(R.id.textTimer)
        textMoves = findViewById(R.id.textMoves)
        sorterGameView = findViewById(R.id.sorterGameView)
        drawerLayout = findViewById(R.id.drawerLayout)
        recordsTable = findViewById(R.id.recordsTable)
        buttonOpenDrawer = findViewById(R.id.buttonOpenDrawer)
        buttonCloseDrawer = findViewById(R.id.buttonCloseDrawer)
    }

    private fun setupDrawer() {
        buttonOpenDrawer.setOnClickListener {
            populateRecordsTable()
            drawerLayout.openDrawer(GravityCompat.START)
        }
        buttonCloseDrawer.setOnClickListener {
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
            if (moves == 1 && !isTimerRunning) startTimer()
        }

        sorterGameView.onRoundChanged = { round, _ ->
            textLevel.text = round.toString()
            resetTimer()
            saveLevel(round)
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
            if (newTimeRecord) bestTimeView.setTextColor(Color.parseColor("#2E7D32")) else bestTimeView.setTextColor(Color.parseColor("#1976D2"))
        } else bestTimeView.visibility = View.GONE

        if (currentBestMoves != Int.MAX_VALUE) {
            bestMovesView.text = "Best: $currentBestMoves"
            bestMovesView.visibility = View.VISIBLE
            if (newMovesRecord) bestMovesView.setTextColor(Color.parseColor("#2E7D32")) else bestMovesView.setTextColor(Color.parseColor("#1976D2"))
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
            dialog.dismiss(); sorterGameView.nextRound()
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

    // Заполнение таблицы рекордов в Drawer
    private fun populateRecordsTable() {
        recordsTable.removeAllViews()
        // Заголовок
        val header = TableRow(this)
        header.addView(makeHeaderCell(getString(R.string.records_header_level)))
        header.addView(makeHeaderCell(getString(R.string.records_header_time)))
        header.addView(makeHeaderCell(getString(R.string.records_header_moves)))
        header.addView(makeHeaderCell(getString(R.string.records_header_date)))
        recordsTable.addView(header)
        // Строки уровней
        for (lv in 1..SorterGameView.MAX_LEVEL) {
            val row = TableRow(this)
            val bt = getBestTime(lv)
            val bm = getBestMoves(lv)
            val rd = getRecordDate(lv)
            row.addView(makeCell(lv.toString()))
            row.addView(makeCell(if (bt == Long.MAX_VALUE) getString(R.string.dash) else formatElapsed(bt)))
            row.addView(makeCell(if (bm == Int.MAX_VALUE) getString(R.string.dash) else bm.toString()))
            row.addView(makeCell(formatDate(rd)))
            // Чередование фона строк для удобства чтения
            if (lv % 2 == 0) {
                row.setBackgroundColor(getColor(R.color.records_row_alt_bg))
            }
            recordsTable.addView(row)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun makeHeaderCell(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setPadding(dp(12), dp(8), dp(12), dp(8))
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setTextColor(getColor(R.color.primaryColor))
        tv.textSize = 14f
        return tv
    }

    private fun makeCell(text: String, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setPadding(dp(12), dp(6), dp(12), dp(6))
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setTextColor(getColor(R.color.text_color))
        tv.textSize = 13f
        return tv
    }

    override fun onDestroy() {
        super.onDestroy()
        resetTimer()
        levelDialog?.dismiss()
    }
}